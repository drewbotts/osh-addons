package org.sensorhub.impl.sensor.victron;

import java.io.InputStream;
import java.util.Map;
import org.sensorhub.api.comm.ICommProvider;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.power.parsers.VeDirectParser;
import org.vast.sensorML.SMLHelper;
import net.opengis.sensorml.v20.PhysicalSystem;

/**
 * Driver for Victron devices over VE.Direct serial (text mode).
 * One module instance per physical device; deviceType selects the output.
 */
public class VictronSensor extends AbstractSensorModule<VictronConfig>
{
    ICommProvider<?> commProvider;
    Thread readerThread;
    volatile boolean started;
    volatile long lastBlockTime;

    SolarChargerOutput solarOutput;
    InverterOutput inverterOutput;

    @Override
    protected void doInit() throws SensorHubException
    {
        super.doInit();

        generateUniqueID("urn:osh:sensor:victron:", config.deviceId);
        generateXmlID("VICTRON_", config.deviceId);

        if (config.deviceType == VictronConfig.DeviceType.SOLAR_CHARGER)
        {
            solarOutput = new SolarChargerOutput(this);
            addOutput(solarOutput, false);
            solarOutput.init();
        }
        else
        {
            inverterOutput = new InverterOutput(this);
            addOutput(inverterOutput, false);
            inverterOutput.init();
        }
    }

    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescLock)
        {
            super.updateSensorDescription();
            if (!sensorDescription.isSetDescription())
                sensorDescription.setDescription("Victron " + config.deviceType + " on VE.Direct");

            var sml = new SMLHelper();
            sml.edit((PhysicalSystem)sensorDescription)
                .addIdentifier(sml.identifiers.manufacturer("Victron Energy"))
                .addIdentifier(sml.identifiers.serialNumber(config.deviceId));
        }
    }

    @Override
    protected void doStart() throws SensorHubException
    {
        if (config.commSettings == null)
            throw new SensorHubException("No communication settings specified");

        try
        {
            var moduleReg = getParentHub().getModuleRegistry();
            commProvider = (ICommProvider<?>)moduleReg.loadSubModule(config.commSettings, true);
            commProvider.start();
            InputStream in = commProvider.getInputStream();

            started = true;
            readerThread = new Thread(() -> {
                try
                {
                    VeDirectParser.readLoop(in, this::handleBlock);
                }
                catch (Exception e)
                {
                    if (started)
                        getLogger().error("VE.Direct read error", e);
                }
            }, "vedirect-" + config.deviceId);
            readerThread.setDaemon(true);
            readerThread.start();
        }
        catch (Exception e)
        {
            throw new SensorHubException("Cannot start VE.Direct comm provider", e);
        }
    }

    void handleBlock(Map<String, String> fields)
    {
        lastBlockTime = System.currentTimeMillis();
        if (solarOutput != null)
            solarOutput.handleStatus(VeDirectParser.toSolarChargerStatus(fields));
        else if (inverterOutput != null)
            inverterOutput.handleStatus(VeDirectParser.toInverterStatus(fields));
    }

    @Override
    protected void doStop() throws SensorHubException
    {
        started = false;
        if (readerThread != null)
        {
            readerThread.interrupt();
            readerThread = null;
        }
        if (commProvider != null)
        {
            commProvider.stop();
            commProvider = null;
        }
    }

    @Override
    public void cleanup() throws SensorHubException {}

    @Override
    public boolean isConnected()
    {
        // healthy = checksum-valid block seen in the last 10 s
        return started && (System.currentTimeMillis() - lastBlockTime) < 10000;
    }
}
