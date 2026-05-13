package org.sensorhub.impl.sensor.petfeeder;

import org.sensorhub.api.sensor.SensorConfig;

public class PetFeederConfig extends SensorConfig {

    public String brokerHost = "localhost";

    public int brokerPort = 1883;

    public String clientId = "osh-pet-feeder";

    public String topicPrefix = "pet_feeder";

    public String mqttUsername = "";

    public String mqttPassword = "";

    public String feederName = "Smart Pet Feeder";

    public String feederLocation = "";
}
