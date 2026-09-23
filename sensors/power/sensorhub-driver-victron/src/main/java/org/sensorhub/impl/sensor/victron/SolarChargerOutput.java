package org.sensorhub.impl.sensor.victron;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.power.PowerConstants;
import org.sensorhub.impl.sensor.power.PowerHelper;
import org.sensorhub.impl.sensor.power.SolarChargerStatus;

public class SolarChargerOutput extends AbstractSensorOutput<VictronSensor>
{
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    long lastPublishMs;

    public SolarChargerOutput(VictronSensor parent)
    {
        super("solarCharger", parent);
    }

    protected void init()
    {
        PowerHelper fac = new PowerHelper();

        dataStruct = fac.createRecord()
            .name(getName())
            .definition(PowerConstants.URI_BASE + "SolarChargerStatus")
            .description("MPPT solar charge controller status")
            .addField("time", fac.createTime().asSamplingTimeIsoUTC())
            .addField("batteryVoltage", fac.createBatteryVoltage())
            .addField("batteryCurrent", fac.createBatteryCurrent())
            .addField("panelVoltage", fac.createQuantity()
                .definition(PowerConstants.URI_BASE + "PanelVoltage")
                .label("PV Voltage").uomCode("V"))
            .addField("panelPower", fac.createElectricalPower("PV Power",
                PowerConstants.URI_BASE + "PanelPower"))
            .addField("chargeState", fac.createChargeState())
            .addField("errorCode", fac.createErrorCode("Error Code",
                PowerConstants.URI_BASE + "VictronErrorCode"))
            .addField("yieldToday", fac.createQuantity()
                .definition(PowerConstants.URI_BASE + "YieldToday")
                .label("Yield Today").uomCode("kW.h"))
            .addField("maxPowerToday", fac.createElectricalPower("Max Power Today",
                PowerConstants.URI_BASE + "MaxPowerToday"))
            .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    void handleStatus(SolarChargerStatus s)
    {
        long now = System.currentTimeMillis();
        if (now - lastPublishMs < (long)(parentSensor.getConfiguration().publishPeriod * 1000))
            return;
        lastPublishMs = now;

        DataBlock blk = dataStruct.createDataBlock();
        int i = 0;
        blk.setDoubleValue(i++, s.ts);
        blk.setDoubleValue(i++, s.batteryVoltage);
        blk.setDoubleValue(i++, s.batteryCurrent);
        blk.setDoubleValue(i++, s.panelVoltage);
        blk.setDoubleValue(i++, s.panelPower);
        blk.setStringValue(i++, s.chargeState);
        blk.setIntValue(i++, s.errorCode);
        blk.setDoubleValue(i++, s.yieldTodayKWh);
        blk.setDoubleValue(i++, s.maxPowerTodayW);

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
