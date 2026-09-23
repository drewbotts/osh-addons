package org.sensorhub.test.impl.sensor.power;

import static org.junit.Assert.*;
import org.junit.Test;
import org.sensorhub.impl.sensor.power.BatteryStatus;
import org.sensorhub.impl.sensor.power.parsers.JbdParser;

public class TestJbdParser
{
    // Synthetic 0x03 basic info response, 27 byte payload:
    //   13.28 V, -2.50 A, 85.00 / 100.00 Ah, 12 cycles, prod date 0x2C8F, no balancing,
    //   protection 0x0000, sw version 0x20, RSOC 85 %, both FETs on, 4 cells, 2 NTCs (25.0 / 23.4 Cel)
    // Checksum = 0x10000 - sum(status..payload) = 0x10000 - 0x0476 = 0xFB8A
    static final String BASIC_INFO_FRAME =
        "DD03001B" +
        "0530" + "FF06" + "2134" + "2710" + "000C" + "2C8F" + "0000" + "0000" + "0000" +
        "20" + "55" + "03" + "04" + "02" + "0BA5" + "0B95" +
        "FB8A" + "77";

    // Synthetic 0x04 cell voltage response: 3.321, 3.322, 3.320, 3.323 V
    // Checksum = 0x10000 - 0x041E = 0xFBE2
    static final String CELL_VOLTS_FRAME =
        "DD040008" + "0CF9" + "0CFA" + "0CF8" + "0CFB" + "FBE2" + "77";


    static byte[] hex(String s)
    {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte)Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    /** Documented algorithm: 0x10000 - sum of bytes from status to end of payload */
    static int checksum(byte[] frame)
    {
        int len = frame[3] & 0xFF;
        int sum = 0;
        for (int i = 2; i < 4 + len; i++)
            sum += frame[i] & 0xFF;
        return (0x10000 - sum) & 0xFFFF;
    }

    static int frameChecksum(byte[] frame)
    {
        return ((frame[frame.length - 3] & 0xFF) << 8) | (frame[frame.length - 2] & 0xFF);
    }


    @Test
    public void testSyntheticFrameChecksums()
    {
        // guards the hardcoded frames above against typos
        assertEquals(0xFB8A, checksum(hex(BASIC_INFO_FRAME)));
        assertEquals(0xFBE2, checksum(hex(CELL_VOLTS_FRAME)));
    }


    @Test
    public void testRequestFramesFollowChecksumAlgorithm()
    {
        assertEquals(frameChecksum(JbdParser.REQ_BASIC_INFO), checksum(JbdParser.REQ_BASIC_INFO));
        assertEquals(frameChecksum(JbdParser.REQ_CELL_VOLTS), checksum(JbdParser.REQ_CELL_VOLTS));
    }


    @Test
    public void testValidFrames()
    {
        assertTrue(JbdParser.isValidFrame(hex(BASIC_INFO_FRAME)));
        assertTrue(JbdParser.isValidFrame(hex(CELL_VOLTS_FRAME)));
    }


    @Test
    public void testInvalidFrames()
    {
        assertFalse(JbdParser.isValidFrame(null));
        assertFalse(JbdParser.isValidFrame(new byte[] {(byte)0xDD, 0x03, 0x00}));

        // corrupted payload byte
        byte[] f = hex(BASIC_INFO_FRAME);
        f[5] ^= 0x01;
        assertFalse(JbdParser.isValidFrame(f));

        // corrupted checksum
        f = hex(CELL_VOLTS_FRAME);
        f[f.length - 2] ^= 0x01;
        assertFalse(JbdParser.isValidFrame(f));

        // bad start / stop bytes
        f = hex(CELL_VOLTS_FRAME);
        f[0] = (byte)0xDC;
        assertFalse(JbdParser.isValidFrame(f));
        f = hex(CELL_VOLTS_FRAME);
        f[f.length - 1] = 0x76;
        assertFalse(JbdParser.isValidFrame(f));

        // truncated frame (length byte doesn't match)
        byte[] full = hex(BASIC_INFO_FRAME);
        byte[] truncated = new byte[full.length - 2];
        System.arraycopy(full, 0, truncated, 0, truncated.length);
        assertFalse(JbdParser.isValidFrame(truncated));
    }


    @Test
    public void testParseBasicInfoAndCellVoltages()
    {
        BatteryStatus s = new BatteryStatus();
        JbdParser.parseBasicInfo(hex(BASIC_INFO_FRAME), s);
        JbdParser.parseCellVoltages(hex(CELL_VOLTS_FRAME), s);

        assertTrue(s.ts > 0);
        assertEquals(13.28, s.voltage, 1e-9);
        assertEquals(-2.5, s.current, 1e-9);
        assertEquals(85.0, s.remainingAh, 1e-9);
        assertEquals(100.0, s.fullAh, 1e-9);
        assertEquals(12, s.cycles);
        assertEquals(0, s.protection);
        assertEquals(85.0, s.soc, 1e-9);
        assertTrue(s.chargeFet);
        assertTrue(s.dischargeFet);
        assertArrayEquals(new double[] {25.0, 23.4}, s.temps, 1e-9);
        assertArrayEquals(new double[] {3.321, 3.322, 3.320, 3.323}, s.cells, 1e-9);
        assertEquals(13.28 * -2.5, s.power(), 1e-9);
    }


    @Test
    public void testBasicInfoLeavesCellsUntouched()
    {
        BatteryStatus s = new BatteryStatus();
        JbdParser.parseCellVoltages(hex(CELL_VOLTS_FRAME), s);
        JbdParser.parseBasicInfo(hex(BASIC_INFO_FRAME), s);
        assertEquals(4, s.cells.length);
    }
}
