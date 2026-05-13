package org.sensorhub.impl.sensor.petfeeder;

import org.eclipse.paho.client.mqttv3.*;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.vast.sensorML.SMLHelper;

/**
 * OSH sensor driver for the Smart Pet Feeder (ESP-12F firmware).
 *
 * <p>Communicates with the feeder's ESP-12F module via MQTT (Eclipse Paho client).
 * The MQTT broker (Mosquitto) runs on the same Pi 4 as this OSH node.
 *
 * <p>Subscribed topics (ESP → OSH):
 * <ul>
 *   <li>{@code <prefix>/status}        — "Idle" | "Feeding" | "Error"</li>
 *   <li>{@code <prefix>/availability}  — "online" | "offline" (LWT)</li>
 *   <li>{@code <prefix>/portion_size}  — "1"–"10"</li>
 *   <li>{@code <prefix>/rssi}          — WiFi RSSI in dBm</li>
 *   <li>{@code <prefix>/ip}            — ESP IPv4 address</li>
 *   <li>{@code <prefix>/playStatus}    — "ON" | "OFF"</li>
 * </ul>
 *
 * <p>Published topics (OSH → ESP):
 * <ul>
 *   <li>{@code <prefix>/feedbuttonCmd}  — triggers single portion</li>
 *   <li>{@code <prefix>/ledbuttonCmd}   — triggers portionSize portions</li>
 *   <li>{@code <prefix>/portion_size}   — sets and persists portion size</li>
 *   <li>{@code <prefix>/playbuttonCmd}  — plays recorded meal call</li>
 *   <li>{@code <prefix>/playCmd}        — "ON"|"OFF" enables/disables recording</li>
 * </ul>
 */
public class PetFeederSensor extends AbstractSensorModule<PetFeederConfig> {

    // Outputs
    FeedingEventOutput feedingEventOutput;
    FeederStatusOutput feederStatusOutput;
    DiagnosticsOutput  diagnosticsOutput;

    // Control
    FeedingCommandControl feedingCommandControl;

    // MQTT
    MqttClient mqttClient;

    // Cached state needed by control
    private int currentPortionSize = 1;

    @Override
    public void init() throws SensorHubException {
        super.init();

        generateUniqueID("urn:osh:sensor:petfeeder:", config.topicPrefix);
        generateXmlID("PET_FEEDER_", config.topicPrefix);

        // Wire outputs
        feedingEventOutput = new FeedingEventOutput(this);
        addOutput(feedingEventOutput, false);
        feedingEventOutput.init();

        feederStatusOutput = new FeederStatusOutput(this);
        addOutput(feederStatusOutput, false);
        feederStatusOutput.init();

        diagnosticsOutput = new DiagnosticsOutput(this);
        addOutput(diagnosticsOutput, false);
        diagnosticsOutput.init();

        // Wire control
        feedingCommandControl = new FeedingCommandControl(this);
        addControlInput(feedingCommandControl);
        feedingCommandControl.init();
    }

    @Override
    public void start() throws SensorHubException {
        try {
            String brokerUri = "tcp://" + config.brokerHost + ":" + config.brokerPort;
            mqttClient = new MqttClient(brokerUri, config.clientId);

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);
            opts.setKeepAliveInterval(30);
            if (config.mqttUsername != null && !config.mqttUsername.isBlank()) {
                opts.setUserName(config.mqttUsername);
                opts.setPassword(config.mqttPassword.toCharArray());
            }

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    getLogger().warn("MQTT connection lost: {}", cause.getMessage());
                    feederStatusOutput.onAvailabilityMessage("offline");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // no-op
                }
            });

            mqttClient.connect(opts);
            getLogger().info("Connected to MQTT broker at {}", brokerUri);

            // Subscribe to all ESP-published topics
            String p = config.topicPrefix;
            mqttClient.subscribe(p + "/status");
            mqttClient.subscribe(p + "/availability");
            mqttClient.subscribe(p + "/portion_size");
            mqttClient.subscribe(p + "/rssi");
            mqttClient.subscribe(p + "/ip");
            mqttClient.subscribe(p + "/playStatus");

            getLogger().info("Subscribed to feeder topics under prefix '{}'", p);

        } catch (MqttException e) {
            throw new SensorHubException("Failed to connect to MQTT broker", e);
        }
    }

    @Override
    public void stop() throws SensorHubException {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                getLogger().info("MQTT client disconnected");
            }
        } catch (MqttException e) {
            getLogger().error("Error stopping MQTT client", e);
        }
    }

    @Override
    protected void updateSensorDescription() {
        synchronized (sensorDescLock) {
            super.updateSensorDescription();

            sensorDescription.setDescription(
                "Smart Pet Feeder (ESP-12F) integrated via MQTT. " +
                "Provides feeding event telemetry and supports remote feed commands " +
                "via the OGC Connected Systems API."
            );

            SMLHelper sml = new SMLHelper();
            sml.edit((net.opengis.sensorml.v20.PhysicalSystem) sensorDescription)
                .addIdentifier(sml.identifiers.shortName(config.feederName))
                .addIdentifier(sml.identifiers.longName(
                    "Smart Pet Feeder — " + config.topicPrefix))
                .addClassifier(sml.classifiers.sensorType("Automated Pet Feeder"))
                .addCharacteristicList("hardware", sml.createCharacteristicList()
                    .label("Hardware Characteristics")
                    .add("temperature", sml.conditions.temperatureRange(-10., 60., "Cel"))
                );

            if (config.feederLocation != null && !config.feederLocation.isBlank()) {
                sensorDescription.setDescription(
                    sensorDescription.getDescription() + " Location: " + config.feederLocation
                );
            }
        }
    }

    @Override
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    private void handleMessage(String topic, String payload) {
        String p = config.topicPrefix;
        getLogger().debug("MQTT [{}]: {}", topic, payload);

        if (topic.equals(p + "/status")) {
            feederStatusOutput.onStatusMessage(payload);
            feedingEventOutput.onStatusUpdate(payload);

        } else if (topic.equals(p + "/availability")) {
            feederStatusOutput.onAvailabilityMessage(payload);

        } else if (topic.equals(p + "/portion_size")) {
            try {
                currentPortionSize = Integer.parseInt(payload.trim());
            } catch (NumberFormatException e) {
                getLogger().warn("Ignoring invalid portion_size: {}", payload);
            }
            feederStatusOutput.onPortionSizeMessage(payload);

        } else if (topic.equals(p + "/rssi")) {
            diagnosticsOutput.onRssiMessage(payload);

        } else if (topic.equals(p + "/ip")) {
            diagnosticsOutput.onIpMessage(payload);

        } else if (topic.equals(p + "/playStatus")) {
            getLogger().debug("Play recording status: {}", payload);
        }
    }

    void publishMqtt(String topic, String payload) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        MqttMessage msg = new MqttMessage(payload.getBytes());
        msg.setQos(1);
        mqttClient.publish(topic, msg);
        getLogger().debug("MQTT publish [{}]: {}", topic, payload);
    }

    FeedingEventOutput getFeedingEventOutput() { return feedingEventOutput; }
    int getCurrentPortionSize()               { return currentPortionSize; }
    PetFeederConfig getConfig()               { return config; }
}
