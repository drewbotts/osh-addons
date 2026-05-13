package org.sensorhub.impl.sensor.petfeeder;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;

/**
 * Publishes a feeding event each time the feeder completes (or fails) a dispense cycle.
 *
 * <p>Record schema:
 * <pre>
 *   time          : ISO 8601 UTC timestamp (driver-generated on MQTT receipt)
 *   portions      : integer 1–10, portions requested
 *   trigger_type  : category — "manual_single" | "manual_meal" | "scheduled"
 *   result        : category — "success" | "error"
 * </pre>
 */
public class FeedingEventOutput extends AbstractSensorOutput<PetFeederSensor> {

    private static final String OUTPUT_NAME  = "feedingEvent";
    private static final String OUTPUT_LABEL = "Feeding Event";

    static final int IDX_TIME         = 0;
    static final int IDX_PORTIONS     = 1;
    static final int IDX_TRIGGER_TYPE = 2;
    static final int IDX_RESULT       = 3;

    DataComponent dataRecord;
    DataEncoding dataEncoding;

    private int pendingPortions   = 0;
    private String pendingTrigger = "manual_single";

    public FeedingEventOutput(PetFeederSensor parent) {
        super(OUTPUT_NAME, parent);
    }

    public void init() {
        var fac = new SWEHelper();

        dataRecord = fac.createRecord()
            .name(getName())
            .label(OUTPUT_LABEL)
            .definition(SWEHelper.getPropertyUri("FeedingEvent"))
            .addField("time", fac.createTime().asSamplingTimeIsoUTC())
            .addField("portions", fac.createCount()
                .definition(SWEHelper.getPropertyUri("NumberOfPortions"))
                .label("Portions")
                .description("Number of food portions requested for this feeding event"))
            .addField("trigger_type", fac.createCategory()
                .definition(SWEHelper.getPropertyUri("TriggerType"))
                .label("Trigger Type")
                .description("How this feeding event was initiated")
                .addAllowedValues("manual_single", "manual_meal", "scheduled"))
            .addField("result", fac.createCategory()
                .definition(SWEHelper.getPropertyUri("FeedingResult"))
                .label("Result")
                .description("Outcome of the feeding cycle")
                .addAllowedValues("success", "error"))
            .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    public void notifyFeedStarted(int portions, String triggerType) {
        this.pendingPortions = portions;
        this.pendingTrigger = triggerType;
    }

    public void onStatusUpdate(String statusPayload) {
        if ("Idle".equals(statusPayload) || "Error".equals(statusPayload)) {
            String result = "Idle".equals(statusPayload) ? "success" : "error";
            publishEvent(pendingPortions > 0 ? pendingPortions : 1, pendingTrigger, result);
            pendingPortions = 0;
            pendingTrigger = "manual_single";
        }
    }

    private void publishEvent(int portions, String triggerType, String result) {
        DataBlock block = dataRecord.createDataBlock();
        block.setDoubleValue(IDX_TIME, System.currentTimeMillis() / 1000.0);
        block.setIntValue(IDX_PORTIONS, portions);
        block.setStringValue(IDX_TRIGGER_TYPE, triggerType);
        block.setStringValue(IDX_RESULT, result);

        latestRecord = block;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, block));
    }

    @Override public String getName()                      { return OUTPUT_NAME; }
    @Override public double getAverageSamplingPeriod()     { return 3600.0; }
    @Override public DataComponent getRecordDescription()  { return dataRecord; }
    @Override public DataEncoding getRecommendedEncoding() { return dataEncoding; }
}
