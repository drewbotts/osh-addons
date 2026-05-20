package org.sensorhub.impl.sensor.petfeeder;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;

/**
 * Diagnostic telemetry from the ESP-12F.
 *
 * <p>Record schema:
 * <pre>
 *   time  : ISO 8601 UTC timestamp
 *   rssi  : integer dBm, WiFi signal strength (negative)
 *   ip    : string, IPv4 address of the ESP-12F on the local network
 * </pre>
 */
public class DiagnosticsOutput extends AbstractSensorOutput<PetFeederSensor> {

    private static final String OUTPUT_NAME  = "diagnostics";
    private static final String OUTPUT_LABEL = "ESP Diagnostics";

    static final int IDX_TIME = 0;
    static final int IDX_RSSI = 1;
    static final int IDX_IP   = 2;

    DataComponent dataRecord;
    DataEncoding dataEncoding;

    private int currentRssi  = 0;
    private String currentIp = "";

    public DiagnosticsOutput(PetFeederSensor parent) {
        super(OUTPUT_NAME, parent);
    }

    public void init() {
        var fac = new SWEHelper();

        dataRecord = fac.createRecord()
            .name(getName())
            .label(OUTPUT_LABEL)
            .definition(SWEHelper.getPropertyUri("DiagnosticMeasurement"))
            .addField("time", fac.createTime().asSamplingTimeIsoUTC())
            .addField("rssi", fac.createQuantity()
                .definition(SWEHelper.getPropertyUri("ReceivedSignalStrengthIndicator"))
                .label("WiFi RSSI")
                .description("WiFi received signal strength of the ESP-12F")
                .uomCode("dB[mW]"))
            .addField("ip", fac.createText()
                .definition(SWEHelper.getPropertyUri("NetworkAddress"))
                .label("IP Address")
                .description("IPv4 address of the ESP-12F on the local network"))
            .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    public void onRssiMessage(String payload) {
        try {
            currentRssi = Integer.parseInt(payload.trim());
            publish();
        } catch (NumberFormatException e) {
            parentSensor.getLogger().warn("Invalid RSSI payload: {}", payload);
        }
    }

    public void onIpMessage(String payload) {
        currentIp = payload.trim();
        publish();
    }

    private void publish() {
        DataBlock block = dataRecord.createDataBlock();
        block.setDoubleValue(IDX_TIME, System.currentTimeMillis() / 1000.0);
        block.setDoubleValue(IDX_RSSI, currentRssi);
        block.setStringValue(IDX_IP, currentIp);

        latestRecord = block;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, block));
    }

    @Override public String getName()                      { return OUTPUT_NAME; }
    @Override public double getAverageSamplingPeriod()     { return 60.0; }
    @Override public DataComponent getRecordDescription()  { return dataRecord; }
    @Override public DataEncoding getRecommendedEncoding() { return dataEncoding; }
}
