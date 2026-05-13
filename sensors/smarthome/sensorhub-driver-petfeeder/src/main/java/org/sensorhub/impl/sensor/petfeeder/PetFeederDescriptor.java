package org.sensorhub.impl.sensor.petfeeder;

import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.module.JarModuleProvider;

public class PetFeederDescriptor extends JarModuleProvider implements IModuleProvider {

    static final String NAME        = "Smart Pet Feeder Driver";
    static final String DESCRIPTION =
        "Driver for smart pet feeders running the SmartHomeGeeks " +
        "ESP-12F firmware. Communicates via MQTT. Provides feeding event telemetry, " +
        "feeder status, ESP diagnostics, and remote feed commands via the " +
        "OGC Connected Systems API.";
    static final String VERSION     = "1.0.0";
    static final String VENDOR      = "GeoRobotix Innovative Research";

    @Override public String getModuleName()        { return NAME; }
    @Override public String getModuleDescription() { return DESCRIPTION; }
    @Override public String getModuleVersion()     { return VERSION; }
    @Override public String getProviderName()      { return VENDOR; }

    @Override
    public Class<? extends IModule<?>> getModuleClass() {
        return PetFeederSensor.class;
    }

    @Override
    public Class<? extends ModuleConfig> getModuleConfigClass() {
        return PetFeederConfig.class;
    }
}
