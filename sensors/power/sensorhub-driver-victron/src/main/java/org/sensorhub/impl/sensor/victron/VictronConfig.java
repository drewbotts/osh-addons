package org.sensorhub.impl.sensor.victron;

import org.sensorhub.api.comm.CommProviderConfig;
import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.config.DisplayInfo.Required;
import org.sensorhub.api.sensor.SensorConfig;

public class VictronConfig extends SensorConfig
{
    public enum DeviceType { SOLAR_CHARGER, INVERTER }

    @Required
    @DisplayInfo(desc="Serial number or short ID used to build the sensor UID (one module instance per device)")
    public String deviceId = "victron001";

    @Required
    @DisplayInfo(label="Device Type", desc="Which VE.Direct device this instance talks to")
    public DeviceType deviceType = DeviceType.SOLAR_CHARGER;

    @DisplayInfo(label="Communication Settings",
        desc="VE.Direct serial link (19200 8N1), e.g. RxtxSerialCommProvider on /dev/ttyUSB0")
    public CommProviderConfig<?> commSettings;

    @DisplayInfo(desc="Minimum seconds between published observations (device emits ~1 Hz)")
    public double publishPeriod = 5.0;
}
