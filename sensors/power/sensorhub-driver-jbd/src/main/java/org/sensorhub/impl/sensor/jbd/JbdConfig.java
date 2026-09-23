package org.sensorhub.impl.sensor.jbd;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.config.DisplayInfo.Required;
import org.sensorhub.api.sensor.SensorConfig;

public class JbdConfig extends SensorConfig
{
    @Required
    @DisplayInfo(desc="Battery ID used to build the sensor UID")
    public String deviceId = "houseBattery";

    @DisplayInfo(label="MQTT Broker Host")
    public String brokerHost = "localhost";

    @DisplayInfo(label="MQTT Broker Port")
    public int brokerPort = 1883;

    @DisplayInfo(desc="Topic the BLE collector publishes BatteryStatus JSON to")
    public String topic = "camper/battery/houseBattery";

    @DisplayInfo(desc="MQTT QoS (0-2)")
    public int qos = 0;
}
