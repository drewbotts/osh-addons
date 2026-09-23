package org.sensorhub.impl.sensor.jbd;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.power.BatteryStatus;
import org.vast.sensorML.SMLHelper;
import net.opengis.sensorml.v20.PhysicalSystem;

/**
 * Driver for a JBD/Xiaoxiang BMS whose BLE link is handled by the Python
 * collector process (collector/jbd_ble_mqtt.py) publishing BatteryStatus
 * JSON over local MQTT. Transport lives outside the JVM on purpose: BlueZ
 * from Java on a Pi is the fragile path. If the BMS is later wired via
 * UART, swap this module's MQTT client for commSettings + JbdParser
 * without touching the outputs.
 */
public class JbdSensor extends AbstractSensorModule<JbdConfig>
{
    MqttClient mqtt;
    Gson gson = new Gson();

    BatteryStatusOutput statusOutput;
    CellVoltagesOutput cellOutput;

    @Override
    protected void doInit() throws SensorHubException
    {
        super.doInit();

        generateUniqueID("urn:osh:sensor:jbd-bms:", config.deviceId);
        generateXmlID("JBD_BMS_", config.deviceId);

        statusOutput = new BatteryStatusOutput(this);
        addOutput(statusOutput, false);
        statusOutput.init();

        cellOutput = new CellVoltagesOutput(this);
        addOutput(cellOutput, false);
        cellOutput.init();
    }

    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescLock)
        {
            super.updateSensorDescription();
            if (!sensorDescription.isSetDescription())
                sensorDescription.setDescription("JBD BMS LiFePO4 house battery via BLE/MQTT bridge");

            var sml = new SMLHelper();
            sml.edit((PhysicalSystem)sensorDescription)
                .addIdentifier(sml.identifiers.manufacturer("JBD (Jiabaida)"))
                .addIdentifier(sml.identifiers.serialNumber(config.deviceId));
        }
    }

    @Override
    protected void doStart() throws SensorHubException
    {
        try
        {
            mqtt = new MqttClient(
                "tcp://" + config.brokerHost + ":" + config.brokerPort,
                "osh-jbd-" + config.deviceId,
                new MemoryPersistence());

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);

            mqtt.connect(opts);
            mqtt.subscribe(config.topic, config.qos, (topic, msg) -> {
                try
                {
                    BatteryStatus s = gson.fromJson(
                        new String(msg.getPayload(), StandardCharsets.UTF_8),
                        BatteryStatus.class);
                    if (s == null)
                        return;
                    if (s.ts <= 0)
                        s.ts = System.currentTimeMillis() / 1000.0;
                    statusOutput.handleStatus(s);
                    if (s.cells != null && s.cells.length > 0)
                        cellOutput.handleStatus(s);
                }
                catch (Exception e)
                {
                    getLogger().error("Bad battery payload on {}", topic, e);
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

    @Override
    public boolean isConnected()
    {
        return mqtt != null && mqtt.isConnected();
    }
}
