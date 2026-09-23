package org.sensorhub.impl.sensor.mopeka;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.vast.sensorML.SMLHelper;
import net.opengis.sensorml.v20.PhysicalSystem;

/**
 * Driver for a Mopeka Pro Check propane tank sensor whose BLE link is
 * handled by a Python collector process publishing PropaneStatus JSON over
 * local MQTT (retained, every ~10 s). Transport lives outside the JVM on
 * purpose: BlueZ from Java on a Pi is the fragile path.
 *
 * The collector owns calibration: mass, fill percent and tank geometry are
 * ingested exactly as published and never recomputed here.
 */
public class MopekaSensor extends AbstractSensorModule<MopekaConfig>
{
    /** payload older than this (by its own ts) means the collector is down */
    static final double MAX_PAYLOAD_AGE_SEC = 120.0;

    MqttClient mqtt;
    Gson gson = new Gson();

    PropaneLevelOutput levelOutput;

    /** ts of the most recent payload, unix seconds; 0 = none yet */
    volatile double lastPayloadTs = 0;

    @Override
    protected void doInit() throws SensorHubException
    {
        super.doInit();

        generateUniqueID("urn:osh:sensor:mopeka:", config.deviceId);
        generateXmlID("MOPEKA_", config.deviceId);

        levelOutput = new PropaneLevelOutput(this);
        addOutput(levelOutput, false);
        levelOutput.init();
    }

    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescLock)
        {
            super.updateSensorDescription();
            if (!sensorDescription.isSetDescription())
                sensorDescription.setDescription("Mopeka Pro Check propane tank level sensor via BLE/MQTT bridge");

            var sml = new SMLHelper();
            sml.edit((PhysicalSystem)sensorDescription)
                .addIdentifier(sml.identifiers.manufacturer("Mopeka"))
                .addIdentifier(sml.identifiers.modelNumber("Pro Check"))
                .addIdentifier(sml.identifiers.serialNumber(config.deviceId))
                .addCharacteristicList("tank", sml.createCharacteristicList()
                    .label("Tank")
                    .add("capacity", sml.createQuantity()
                        .definition(PropaneLevelOutput.URI_BASE + "TankCapacity")
                        .label("Tank Capacity")
                        .description("Nominal propane capacity of the monitored tank")
                        .uomCode("[lb_av]")
                        .value(config.tankCapacityLb))
                    .build());
        }
    }

    @Override
    protected void doStart() throws SensorHubException
    {
        try
        {
            mqtt = new MqttClient(
                "tcp://" + config.brokerHost + ":" + config.brokerPort,
                "osh-mopeka-" + config.deviceId,
                new MemoryPersistence());

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);

            mqtt.connect(opts);
            mqtt.subscribe(config.topic, config.qos, (topic, msg) -> {
                try
                {
                    PropaneStatus s = gson.fromJson(
                        new String(msg.getPayload(), StandardCharsets.UTF_8),
                        PropaneStatus.class);
                    if (s == null)
                        return;
                    if (s.ts <= 0)
                    {
                        // observation time always comes from the payload,
                        // never from when the message arrived
                        getLogger().warn("Dropping propane payload without ts on {}", topic);
                        return;
                    }
                    if (s.ts == lastPayloadTs)
                    {
                        // retained message redelivered on (re)subscribe
                        getLogger().debug("Ignoring duplicate propane payload ts={}", s.ts);
                        return;
                    }
                    lastPayloadTs = s.ts;
                    levelOutput.handleStatus(s);
                }
                catch (Exception e)
                {
                    getLogger().error("Bad propane payload on {}", topic, e);
                }
            });
        }
        catch (MqttException e)
        {
            throw new SensorHubException("MQTT connection failed", e);
        }
    }

    @Override
    protected void doStop() throws SensorHubException
    {
        try
        {
            if (mqtt != null)
            {
                if (mqtt.isConnected())
                    mqtt.disconnect();
                mqtt.close();
                mqtt = null;
            }
        }
        catch (MqttException e)
        {
            getLogger().error("Error stopping MQTT client", e);
        }
    }

    @Override
    public void cleanup() throws SensorHubException {}

    /**
     * Connected means the broker link is up AND the collector is alive.
     * Liveness is judged by the payload's own ts: the collector publishes
     * with retain=true, so a stale message is redelivered on every
     * subscribe and arrival time says nothing about the sensor.
     */
    @Override
    public boolean isConnected()
    {
        if (mqtt == null || !mqtt.isConnected())
            return false;
        double ageSec = System.currentTimeMillis() / 1000.0 - lastPayloadTs;
        return lastPayloadTs > 0 && ageSec <= MAX_PAYLOAD_AGE_SEC;
    }
}
