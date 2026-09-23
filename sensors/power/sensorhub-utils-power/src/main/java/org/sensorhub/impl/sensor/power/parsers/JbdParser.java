package org.sensorhub.impl.sensor.power.parsers;

import org.sensorhub.impl.sensor.power.BatteryStatus;

/**
 * Parser for JBD/Xiaoxiang BMS response frames (UART or BLE notify payload).
 *
 * Frame: DD <cmd> <status> <len> <payload...> <chk_hi> <chk_lo> 77
 * Checksum = 0x10000 - sum(status..payload). Requests:
 *   0x03 basic info, 0x04 cell voltages.
 *
 * Kept in Java so the driver can go direct-UART later without touching the
 * MQTT collector; mirrors the hawk-bms-prototype TypeScript library.
 */
public class JbdParser
{
    public static final byte[] REQ_BASIC_INFO = {(byte)0xDD, (byte)0xA5, 0x03, 0x00, (byte)0xFF, (byte)0xFD, 0x77};
    public static final byte[] REQ_CELL_VOLTS = {(byte)0xDD, (byte)0xA5, 0x04, 0x00, (byte)0xFF, (byte)0xFC, 0x77};

    /** Returns true if frame structure + checksum are valid. */
    public static boolean isValidFrame(byte[] frame)
    {
        if (frame == null || frame.length < 7) return false;
        if ((frame[0] & 0xFF) != 0xDD || (frame[frame.length - 1] & 0xFF) != 0x77) return false;
        int len = frame[3] & 0xFF;
        if (frame.length != len + 7) return false;

        int sum = 0;
        for (int i = 2; i < 4 + len; i++)
            sum += frame[i] & 0xFF;
        int chk = ((frame[4 + len] & 0xFF) << 8) | (frame[5 + len] & 0xFF);
        return ((sum + chk) & 0xFFFF) == 0;
    }

    /** Parse a 0x03 basic-info response into the target status (cells untouched). */
    public static void parseBasicInfo(byte[] frame, BatteryStatus out)
    {
        int o = 4; // payload offset
        out.ts          = System.currentTimeMillis() / 1000.0;
        out.voltage     = u16(frame, o)     * 0.01;          // 10 mV
        out.current     = s16(frame, o + 2) * 0.01;          // 10 mA, signed
        out.remainingAh = u16(frame, o + 4) * 0.01;          // 10 mAh
        out.fullAh      = u16(frame, o + 6) * 0.01;
        out.cycles      = u16(frame, o + 8);
        out.protection  = u16(frame, o + 16);
        out.soc         = frame[o + 19] & 0xFF;              // RSOC %
        int fet         = frame[o + 20] & 0xFF;
        out.chargeFet    = (fet & 0x01) != 0;
        out.dischargeFet = (fet & 0x02) != 0;
        int ntcCount    = frame[o + 22] & 0xFF;
        out.temps = new double[ntcCount];
        for (int i = 0; i < ntcCount; i++)
            out.temps[i] = (u16(frame, o + 23 + 2 * i) - 2731) / 10.0;
    }

    /** Parse a 0x04 cell-voltage response into the target status. */
    public static void parseCellVoltages(byte[] frame, BatteryStatus out)
    {
        int len = frame[3] & 0xFF;
        int n = len / 2;
        out.cells = new double[n];
        for (int i = 0; i < n; i++)
            out.cells[i] = u16(frame, 4 + 2 * i) / 1000.0;   // mV
    }

    static int u16(byte[] b, int i) { return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF); }
    static int s16(byte[] b, int i) { return (short)(((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF)); }

    private JbdParser() {}
}
