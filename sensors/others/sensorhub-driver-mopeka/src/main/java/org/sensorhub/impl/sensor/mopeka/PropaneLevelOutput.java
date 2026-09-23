package org.sensorhub.impl.sensor.mopeka;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;

public class PropaneLevelOutput extends AbstractSensorOutput<MopekaSensor>
{
    static final String URI_BASE = "urn:osh:def:tank:";

    DataComponent dataStruct;
    DataEncoding dataEncoding;

    public PropaneLevelOutput(MopekaSensor parent)
    {
        super("propaneLevel", parent);
    }

    protected void init()
    {
        SWEHelper fac = new SWEHelper();

        dataStruct = fac.createRecord()
            .name(getName())
            .definition(URI_BASE + "PropaneLevel")
            .description("Propane tank level from Mopeka sensor")
            .addField("time", fac.createTime().asSamplingTimeIsoUTC())
            .addField("propaneMass", fac.createQuantity()
                .definition(URI_BASE + "PropaneMass")
                .label("Propane Mass")
                .description("Propane remaining in the tank")
                .uomCode("[lb_av]"))
            .addField("fillPercent", fac.createQuantity()
                .definition(URI_BASE + "FillPercent")
                .label("Fill Percent")
                .uomCode("%"))
            .addField("liquidHeight", fac.createQuantity()
                .definition(URI_BASE + "LiquidHeight")
                .label("Liquid Height")
                .description("Calibrated liquid level above the tank bottom")
                .uomCode("[in_i]"))
            .addField("sensorHeight", fac.createQuantity()
                .definition(URI_BASE + "SensorHeight")
                .label("Sensor Height")
                .description("Raw ultrasonic height reported by the sensor")
                .uomCode("[in_i]"))
            .addField("liquidTemperature", fac.createQuantity()
                .definition(URI_BASE + "LiquidTemperature")
                .label("Liquid Temperature")
                .uomCode("Cel"))
            .addField("readingQuality", fac.createCount()
                .definition(URI_BASE + "ReadingQuality")
                .label("Reading Quality")
                .description("Sensor reading quality, 0 = bad, 3 = good")
                .addAllowedInterval(0, 3))
            .addField("sensorBatteryVoltage", fac.createQuantity()
                .definition(URI_BASE + "SensorBatteryVoltage")
                .label("Sensor Battery Voltage")
                .uomCode("V"))
            .addField("sensorBatteryPercent", fac.createQuantity()
                .definition(URI_BASE + "SensorBatteryPercent")
                .label("Sensor Battery Percent")
                .uomCode("%"))
            .addField("signalStrength", fac.createQuantity()
                .definition(URI_BASE + "SignalStrength")
                .label("Signal Strength")
                .description("BLE RSSI as seen by the collector")
                .uomCode("dB[mW]"))
            .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    void handleStatus(PropaneStatus s)
    {
        DataBlock blk = dataStruct.createDataBlock();
        int i = 0;
        blk.setDoubleValue(i++, s.ts);
        blk.setDoubleValue(i++, s.lb);
        blk.setDoubleValue(i++, s.pct);
        blk.setDoubleValue(i++, s.liquidIn);
        blk.setDoubleValue(i++, s.heightIn);
        blk.setDoubleValue(i++, s.tempC);
        blk.setIntValue(i++, s.quality);
        blk.setDoubleValue(i++, s.batteryV);
        blk.setDoubleValue(i++, s.batteryPct);
        blk.setDoubleValue(i++, s.rssi);

        latestRecord = blk;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, blk));
    }

    @Override
    public double getAverageSamplingPeriod() { return 10.0; }

    @Override
    public DataComponent getRecordDescription() { return dataStruct; }

    @Override
    public DataEncoding getRecommendedEncoding() { return dataEncoding; }
}
