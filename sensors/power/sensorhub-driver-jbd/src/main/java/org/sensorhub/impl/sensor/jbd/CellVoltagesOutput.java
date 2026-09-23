package org.sensorhub.impl.sensor.jbd;

import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.power.BatteryStatus;
import org.sensorhub.impl.sensor.power.PowerConstants;
import org.sensorhub.impl.sensor.power.PowerHelper;

/**
 * Per-cell voltages as a variable-size DataArray (4 cells for a 12V LiFePO4
 * pack, but sized from the payload so a future 24V pack needs no change).
 */
public class CellVoltagesOutput extends AbstractSensorOutput<JbdSensor>
{
    DataComponent dataStruct;
    DataEncoding dataEncoding;

    public CellVoltagesOutput(JbdSensor parent)
    {
        super("cellVoltages", parent);
    }

    protected void init()
    {
        PowerHelper fac = new PowerHelper();

        dataStruct = fac.createRecord()
            .name(getName())
            .definition(PowerConstants.URI_BASE + "CellVoltages")
            .addField("time", fac.createTime().asSamplingTimeIsoUTC())
            .addField("numCells", fac.createCount()
                .definition(PowerConstants.URI_BASE + "NumCells")
                .label("Number of Cells")
                .id("NUM_CELLS"))
            .addField("cells", fac.createArray()
                .withVariableSize("NUM_CELLS")
                .withElement("cellVoltage", fac.createQuantity()
                    .definition(PowerConstants.URI_BASE + "CellVoltage")
                    .label("Cell Voltage")
                    .uomCode("V"))
                .build())
            .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    void handleStatus(BatteryStatus s)
    {
        int n = s.cells.length;

        // set array size, then create the block so it's sized correctly
        DataArray cellArray = (DataArray)dataStruct.getComponent("cells");
        cellArray.updateSize(n);
        DataBlock blk = dataStruct.createDataBlock();

        int i = 0;
        blk.setDoubleValue(i++, s.ts);
        blk.setIntValue(i++, n);
        for (int c = 0; c < n; c++)
            blk.setDoubleValue(i++, s.cells[c]);

        latestRecord = blk;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, blk));
    }

    @Override
    public double getAverageSamplingPeriod() { return 30.0; }

    @Override
    public DataComponent getRecordDescription() { return dataStruct; }

    @Override
    public DataEncoding getRecommendedEncoding() { return dataEncoding; }
}
