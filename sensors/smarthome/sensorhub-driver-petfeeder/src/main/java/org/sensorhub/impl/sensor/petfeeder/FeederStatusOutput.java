package org.sensorhub.impl.sensor.petfeeder;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;

/**
 * Real-time feeder status stream.
 *
 * <p>Record schema:
 * <pre>
 *   time           : ISO 8601 UTC timestamp
 *   motor_status   : category — "Idle" | "Feeding" | "Error"
 *   portion_size   : integer 1–10, currently configured portion size
 *   availability   : category — "online" | "offline"
 * </pre>
 */
public class FeederStatusOutput extends AbstractSensorOutput<PetFeederSensor> {

    private static final String OUTPUT_NAME  = "feederStatus";
    private static final String OUTPUT_LABEL = "Feeder Status";

    static final int IDX_TIME         = 0;
    static final int IDX_MOTOR_STATUS = 1;
    static final int IDX_PORTION_SIZE = 2;
    static final int IDX_AVAILABILITY = 3;

    DataComponent dataRecord;
    DataEncoding dataEncoding;

    private String currentMotorStatus  = "Idle";
    private int currentPortionSize     = 1;
    private String currentAvailability = "offline";

    public FeederStatusOutput(PetFeederSensor parent) {
        super(OUTPUT_NAME, parent);
    }

    public void init() {
        var fac = new SWEHelper();

        dataRecord = fac.createRecord()
            .name(getName())
            .label(OUTPUT_LABEL)
            .definition(SWEHelper.getPropertyUri("SystemStatus"))
            .addField("time", fac.createTime().asSamplingTimeIsoUTC())
            .addField("motor_status", fac.createCategory()
                .definition(SWEHelper.getPropertyUri("OperationalStatus"))
                .label("Motor Status")
                .description("Current operational state of the feeder motor")
                .addAllowedValues("Idle", "Feeding", "Error"))
            .addField("portion_size", fac.createCount()
                .definition(SWEHelper.getPropertyUri("NumberOfPortions"))
                .label("Portion Size")
                .description("Number of portions dispensed per scheduled or LED-button feeding"))
            .addField("availability", fac.createCategory()
                .definition(SWEHelper.getPropertyUri("DeviceAvailability"))
                .label("Availability")
                .description("MQTT Last Will and Testament availability state")
                .addAllowedValues("online", "offline"))
            .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    public void onStatusMessage(String payload) {
        currentMotorStatus = payload;
        publish();
    }

    public void onPortionSizeMessage(String payload) {
        try {
            currentPortionSize = Integer.parseInt(payload.trim());
        } catch (NumberFormatException e) {
            parentSensor.getLogger().warn("Invalid portion_size payload: {}", payload);
        }
        publish();
    }

    public void onAvailabilityMessage(String payload) {
        currentAvailability = payload;
        publish();
    }

    private void publish() {
        DataBlock block = dataRecord.createDataBlock();
        block.setDoubleValue(IDX_TIME, System.currentTimeMillis() / 1000.0);
        block.setStringValue(IDX_MOTOR_STATUS, currentMotorStatus);
        block.setIntValue(IDX_PORTION_SIZE, currentPortionSize);
        block.setStringValue(IDX_AVAILABILITY, currentAvailability);

        latestRecord = block;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, block));
    }

    @Override public String getName()                      { return OUTPUT_NAME; }
    @Override public double getAverageSamplingPeriod()     { return 1.0; }
    @Override public DataComponent getRecordDescription()  { return dataRecord; }
    @Override public DataEncoding getRecommendedEncoding() { return dataEncoding; }
}
