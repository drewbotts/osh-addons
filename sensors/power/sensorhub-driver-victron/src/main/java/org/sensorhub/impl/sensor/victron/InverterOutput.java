package org.sensorhub.impl.sensor.victron;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.power.InverterStatus;
import org.sensorhub.impl.sensor.power.PowerConstants;
import org.sensorhub.impl.sensor.power.PowerHelper;

public class InverterOutput extends AbstractSensorOutput<VictronSensor>
{
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    long lastPublishMs;

    public InverterOutput(VictronSensor parent)
    {
        super("inverter", parent);
    }

    protected void init()
    {
        PowerHelper fac = new PowerHelper();

        dataStruct = fac.createRecord()
            .name(getName())
            .definition(PowerConstants.URI_BASE + "InverterStatus")
            .description("DC/AC inverter status")
            .addField("time", fac.createTime().asSamplingTimeIsoUTC())
            .addField("batteryVoltage", fac.createBatteryVoltage())
            .addField("acVoltage", fac.createQuantity()
                .definition(PowerConstants.URI_BASE + "AcOutputVoltage")
                .label("AC Output Voltage").uomCode("V"))
            .addField("acCurrent", fac.createQuantity()
                .definition(PowerConstants.URI_BASE + "AcOutputCurrent")
                .label("AC Output Current").uomCode("A"))
            .addField("acApparentPower", fac.createQuantity()
                .definition(PowerConstants.URI_BASE + "AcApparentPower")
                .label("AC Apparent Power").uomCode("V.A"))
            .addField("deviceState", fac.createChargeState())
            .addField("alarmReason", fac.createErrorCode("Alarm Reason",
                PowerConstants.URI_BASE + "VictronAlarmReason"))
            .addField("warnReason", fac.createErrorCode("Warning Reason",
                PowerConstants.URI_BASE + "VictronWarnReason"))
            .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    void handleStatus(InverterStatus s)
    {
        long now = System.currentTimeMillis();
        if (now - lastPublishMs < (long)(parentSensor.getConfiguration().publishPeriod * 1000))
            return;
        lastPublishMs = now;

        DataBlock blk = dataStruct.createDataBlock();
        int i = 0;
        blk.setDoubleValue(i++, s.ts);
        blk.setDoubleValue(i++, s.batteryVoltage);
        blk.setDoubleValue(i++, s.acVoltage);
        blk.setDoubleValue(i++, s.acCurrent);
        blk.setDoubleValue(i++, s.acApparentPower);
        blk.setStringValue(i++, s.deviceState);
        blk.setIntValue(i++, s.alarmReason);
        blk.setIntValue(i++, s.warnReason);

        latestRecord = blk;
        latestRecordTime = now;
        eventHandler.publish(new DataEvent(latestRecordTime, this, blk));
    }

    @Override
    public double getAverageSamplingPeriod()
    {
        return parentSensor.getConfiguration().publishPeriod;
    }

    @Override
    public DataComponent getRecordDescription() { return dataStruct; }

    @Override
    public DataEncoding getRecommendedEncoding() { return dataEncoding; }
}
