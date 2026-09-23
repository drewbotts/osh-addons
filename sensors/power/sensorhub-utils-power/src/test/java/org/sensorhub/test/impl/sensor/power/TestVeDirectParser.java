package org.sensorhub.test.impl.sensor.power;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.sensorhub.impl.sensor.power.InverterStatus;
import org.sensorhub.impl.sensor.power.SolarChargerStatus;
import org.sensorhub.impl.sensor.power.parsers.VeDirectParser;

public class TestVeDirectParser
{
    static final String MPPT_FIELDS =
        "\r\nPID\t0xA060" +
        "\r\nV\t13280" +
        "\r\nI\t-2500" +
        "\r\nVPV\t36120" +
        "\r\nPPV\t145" +
        "\r\nCS\t3" +
        "\r\nERR\t0" +
        "\r\nH19\t12345" +
        "\r\nH20\t87" +
        "\r\nH21\t412";

    static final String INVERTER_FIELDS =
        "\r\nPID\t0xA231" +
        "\r\nV\t12950" +
        "\r\nAC_OUT_V\t12001" +
        "\r\nAC_OUT_I\t23" +
        "\r\nAC_OUT_S\t276" +
        "\r\nCS\t9" +
        "\r\nAR\t0" +
        "\r\nWARN\t1";


    /** Appends the Checksum field so that the sum of all block bytes is 0 mod 256 */
    static byte[] block(String fields)
    {
        byte[] body = (fields + "\r\nChecksum\t").getBytes(StandardCharsets.US_ASCII);
        int sum = 0;
        for (byte b : body)
            sum += b & 0xFF;
        byte[] blk = new byte[body.length + 1];
        System.arraycopy(body, 0, blk, 0, body.length);
        blk[body.length] = (byte)((256 - (sum & 0xFF)) & 0xFF);
        return blk;
    }

    static List<Map<String, String>> parse(byte[]... blocks) throws IOException
    {
        var bytes = new ByteArrayOutputStream();
        for (byte[] b : blocks)
            bytes.write(b);
        List<Map<String, String>> out = new ArrayList<>();
        VeDirectParser.readLoop(new ByteArrayInputStream(bytes.toByteArray()), out::add);
        return out;
    }


    @Test
    public void testValidSolarChargerBlock() throws Exception
    {
        var blocks = parse(block(MPPT_FIELDS));
        assertEquals(1, blocks.size());
        assertEquals("0xA060", blocks.get(0).get("PID"));
        assertEquals("-2500", blocks.get(0).get("I"));

        SolarChargerStatus s = VeDirectParser.toSolarChargerStatus(blocks.get(0));
        assertEquals(13.28, s.batteryVoltage, 1e-9);
        assertEquals(-2.5, s.batteryCurrent, 1e-9);
        assertEquals(36.12, s.panelVoltage, 1e-9);
        assertEquals(145.0, s.panelPower, 1e-9);
        assertEquals("bulk", s.chargeState);
        assertEquals(0, s.errorCode);
        assertEquals(123.45, s.yieldTotalKWh, 1e-9);
        assertEquals(0.87, s.yieldTodayKWh, 1e-9);
        assertEquals(412.0, s.maxPowerTodayW, 1e-9);
        assertTrue(s.ts > 0);
    }


    @Test
    public void testValidInverterBlock() throws Exception
    {
        var blocks = parse(block(INVERTER_FIELDS));
        assertEquals(1, blocks.size());

        InverterStatus s = VeDirectParser.toInverterStatus(blocks.get(0));
        assertEquals(12.95, s.batteryVoltage, 1e-9);
        assertEquals(120.01, s.acVoltage, 1e-9);
        assertEquals(2.3, s.acCurrent, 1e-9);
        assertEquals(276.0, s.acApparentPower, 1e-9);
        assertEquals("inverting", s.deviceState);
        assertEquals(0, s.alarmReason);
        assertEquals(1, s.warnReason);
    }


    @Test
    public void testCorruptedBlockIsDropped() throws Exception
    {
        byte[] blk = block(MPPT_FIELDS);
        int idx = MPPT_FIELDS.indexOf("13280");
        blk[idx] = '2'; // V 13280 -> 23280, checksum no longer matches
        assertTrue(parse(blk).isEmpty());
    }


    @Test
    public void testBadChecksumByteIsDropped() throws Exception
    {
        byte[] blk = block(MPPT_FIELDS);
        blk[blk.length - 1]++;
        assertTrue(parse(blk).isEmpty());
    }


    @Test
    public void testResyncAfterCorruptedBlock() throws Exception
    {
        byte[] bad = block(MPPT_FIELDS);
        bad[MPPT_FIELDS.indexOf("36120")] = '9';

        var blocks = parse(block(MPPT_FIELDS), bad, block(INVERTER_FIELDS));
        assertEquals(2, blocks.size());
        assertEquals("13280", blocks.get(0).get("V"));
        assertEquals("12950", blocks.get(1).get("V"));
        assertFalse(blocks.get(1).containsKey("VPV"));
    }


    @Test
    public void testMissingAndMalformedFieldsDefaultToZero() throws Exception
    {
        var blocks = parse(block("\r\nV\t13280\r\nI\tabc"));
        assertEquals(1, blocks.size());

        SolarChargerStatus s = VeDirectParser.toSolarChargerStatus(blocks.get(0));
        assertEquals(13.28, s.batteryVoltage, 1e-9);
        assertEquals(0.0, s.batteryCurrent, 1e-9);
        assertEquals(0.0, s.panelPower, 1e-9);
        assertEquals("off", s.chargeState);
    }
}
