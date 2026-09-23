package org.sensorhub.impl.sensor.power.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.sensorhub.impl.sensor.power.InverterStatus;
import org.sensorhub.impl.sensor.power.PowerConstants;
import org.sensorhub.impl.sensor.power.SolarChargerStatus;

/**
 * Streaming parser for the VE.Direct TEXT protocol (19200 8N1).
 *
 * Devices emit blocks of "LABEL\tVALUE\r\n" lines terminated by a
 * "Checksum\t<byte>" field; the sum of every byte in the block (including
 * the checksum byte) must be 0 mod 256. Invalid blocks are dropped.
 *
 * Ref: Victron "VE.Direct Protocol" whitepaper (text-mode section).
 */
public class VeDirectParser
{
    static final int MAX_BLOCK_LEN = 1024;

    /**
     * Blocking read loop: parses blocks from the stream and hands each
     * checksum-valid block (label -> value map) to the consumer.
     * Run on a dedicated reader thread; returns when the stream ends.
     */
    public static void readLoop(InputStream in, Consumer<Map<String, String>> onBlock)
        throws IOException
    {
        Map<String, String> block = new HashMap<>();
        StringBuilder label = new StringBuilder();
        StringBuilder value = new StringBuilder();
        int checksum = 0;
        int blockLen = 0;
        boolean inValue = false;
        boolean inChecksumValue = false;

        int b;
        while ((b = in.read()) >= 0)
        {
            checksum = (checksum + b) & 0xFF;
            blockLen++;

            if (inChecksumValue)
            {
                // the single checksum byte just got added to the sum
                if (checksum == 0 && !block.isEmpty())
                    onBlock.accept(new HashMap<>(block));
                block.clear();
                checksum = 0; blockLen = 0;
                label.setLength(0); value.setLength(0);
                inValue = false; inChecksumValue = false;
                continue;
            }

            if (b == '\n')
            {
                label.setLength(0); value.setLength(0);
                inValue = false;
            }
            else if (b == '\r')
            {
                if (label.length() > 0)
                    block.put(label.toString(), value.toString());
            }
            else if (b == '\t' && !inValue)
            {
                inValue = true;
                if ("Checksum".contentEquals(label))
                    inChecksumValue = true;   // next byte is the checksum
            }
            else if (inValue)
                value.append((char) b);
            else
                label.append((char) b);

            // resync guard against garbage / partial attach
            if (blockLen > MAX_BLOCK_LEN)
            {
                block.clear();
                checksum = 0; blockLen = 0;
                label.setLength(0); value.setLength(0);
                inValue = false; inChecksumValue = false;
            }
        }
    }

    public static SolarChargerStatus toSolarChargerStatus(Map<String, String> f)
    {
        SolarChargerStatus s = new SolarChargerStatus();
        s.ts = System.currentTimeMillis() / 1000.0;
        s.batteryVoltage = intField(f, "V")   / 1000.0;
        s.batteryCurrent = intField(f, "I")   / 1000.0;
        s.panelVoltage   = intField(f, "VPV") / 1000.0;
        s.panelPower     = intField(f, "PPV");
        s.chargeState    = PowerConstants.chargeStateFromVictronCS(intField(f, "CS"));
        s.errorCode      = intField(f, "ERR");
        s.yieldTotalKWh  = intField(f, "H19") * 0.01;
        s.yieldTodayKWh  = intField(f, "H20") * 0.01;
        s.maxPowerTodayW = intField(f, "H21");
        return s;
    }

    public static InverterStatus toInverterStatus(Map<String, String> f)
    {
        InverterStatus s = new InverterStatus();
        s.ts = System.currentTimeMillis() / 1000.0;
        s.batteryVoltage  = intField(f, "V")        / 1000.0;
        s.acVoltage       = intField(f, "AC_OUT_V") / 100.0;
        s.acCurrent       = intField(f, "AC_OUT_I") / 10.0;
        s.acApparentPower = intField(f, "AC_OUT_S");
        s.deviceState     = PowerConstants.chargeStateFromVictronCS(intField(f, "CS"));
        s.alarmReason     = intField(f, "AR");
        s.warnReason      = intField(f, "WARN");
        return s;
    }

    static int intField(Map<String, String> f, String key)
    {
        String v = f.get(key);
        if (v == null) return 0;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private VeDirectParser() {}
}
