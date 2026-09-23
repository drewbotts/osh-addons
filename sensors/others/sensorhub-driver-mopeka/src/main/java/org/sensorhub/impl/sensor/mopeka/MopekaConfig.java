package org.sensorhub.impl.sensor.mopeka;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.config.DisplayInfo.Required;
import org.sensorhub.api.sensor.SensorConfig;

public class MopekaConfig extends SensorConfig
{
    @Required
    @DisplayInfo(desc="Tank ID used to build the sensor UID")
    public String deviceId = "hawkPropane";

    @DisplayInfo(label="MQTT Broker Host")
    public String brokerHost = "localhost";

    @DisplayInfo(label="MQTT Broker Port")
    public int brokerPort = 1883;

    @DisplayInfo(desc="Topic the BLE collector publishes propane tank JSON to")
    public String topic = "camper/propane/tank";

    @DisplayInfo(desc="MQTT QoS (0-2)")
    public int qos = 0;

    @DisplayInfo(label="Tank Capacity (lb)", desc="Nominal propane capacity of the tank. SensorML metadata only; the collector owns all level calibration")
    public double tankCapacityLb = 10.6;
}
