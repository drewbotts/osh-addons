package org.sensorhub.impl.sensor.jbd;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.power.BatteryStatus;
import org.sensorhub.impl.sensor.power.PowerConstants;
import org.sensorhub.impl.sensor.power.PowerHelper;

public class BatteryStatusOutput extends AbstractSensorOutput<JbdSensor>
{
    DataComponent dataStruct;
    DataEncoding dataEncoding;

    public BatteryStatusOutput(JbdSensor parent)
    {
        super("batteryStatus", parent);
    }

    protected void init()
    {
        PowerHelper fac = new PowerHelper();

        dataStruct = fac.createRecord()
            .name(getName())
            .definition(PowerConstants.URI_BASE + "BatteryStatus")
            .description("House battery pack status from BMS")
            .addField("time", fac.createTime().asSamplingTimeIsoUTC())
            .addField("voltage", fac.createBatteryVoltage())
            .addField("current", fac.createBatteryCurrent())
            .addField("power", fac.createElectricalPower("Battery Power",
                PowerConstants.URI_BASE + "BatteryPower"))
            .addField("stateOfCharge", fac.createStateOfCharge())
            .addField("remainingCapacity", fac.createCapacity("Remaining Capacity",
                PowerConstants.URI_BASE + "RemainingCapacity"))
            .addField("fullCapacity", fac.createCapacity("Full Capacity",
                PowerConstants.URI_BASE + "FullCapacity"))
            .addField("cycleCount", fac.createCount()
                .definition(PowerConstants.URI_BASE + "CycleCount")
                .label("Cycle Count"))
            .addField("temp1", fac.createTemperature("BMS Temp 1",
                PowerConstants.URI_BASE + "PackTemperature"))
            .addField("temp2", fac.createTemperature("BMS Temp 2",
                PowerConstants.URI_BASE + "PackTemperature"))
            .addField("chargeFetEnabled", fac.createBoolean()
                .definition(PowerConstants.URI_BASE + "ChargeFetEnabled")
                .label("Charge FET Enabled"))
            .addField("dischargeFetEnabled", fac.createBoolean()
                .definition(PowerConstants.URI_BASE + "DischargeFetEnabled")
                .label("Discharge FET Enabled"))
            .addField("protectionFlags", fac.createCount()
                .definition(PowerConstants.URI_BASE + "ProtectionFlags")
                .label("Protection Flags")
                .description("JBD 16-bit protection bitmask; 0 = OK"))
            .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    void handleStatus(BatteryStatus s)
    {
        DataBlock blk = dataStruct.createDataBlock();
        int i = 0;
        blk.setDoubleValue(i++, s.ts);
        blk.setDoubleValue(i++, s.voltage);
        blk.setDoubleValue(i++, s.current);
        blk.setDoubleValue(i++, s.power());
        blk.setDoubleValue(i++, s.soc);
        blk.setDoubleValue(i++, s.remainingAh);
        blk.setDoubleValue(i++, s.fullAh);
        blk.setIntValue(i++, s.cycles);
        blk.setDoubleValue(i++, temp(s, 0));
        blk.setDoubleValue(i++, temp(s, 1));
        blk.setBooleanValue(i++, s.chargeFet);
        blk.setBooleanValue(i++, s.dischargeFet);
        blk.setIntValue(i++, s.protection);

        latestRecord = blk;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, blk));
    }

    static double temp(BatteryStatus s, int idx)
    {
        return (s.temps != null && s.temps.length > idx) ? s.temps[idx] : Double.NaN;
    }

    @Override
    public double getAverageSamplingPeriod() { return 5.0; }

    @Override
    public DataComponent getRecordDescription() { return dataStruct; }

    @Override
    public DataEncoding getRecommendedEncoding() { return dataEncoding; }
}
