package org.sensorhub.impl.sensor.victron;

import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.module.JarModuleProvider;

public class Descriptor extends JarModuleProvider implements IModuleProvider
{
    @Override
    public Class<? extends IModule<?>> getModuleClass()
    {
        return VictronSensor.class;
    }

    @Override
    public Class<? extends ModuleConfig> getModuleConfigClass()
    {
        return VictronConfig.class;
    }
}
