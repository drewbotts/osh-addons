package org.sensorhub.impl.sensor.power;

/**
 * Transport-agnostic battery observation (JBD BMS or compatible).
 * Produced by JbdParser (raw frames) or deserialized from collector JSON.
 */
public class BatteryStatus
{
    public double ts;            // unix seconds
    public double voltage;       // V
    public double current;       // A, signed (+ = charging)
    public double soc;           // %
    public double remainingAh;   // Ah
    public double fullAh;        // Ah
    public int    cycles;
    public double[] temps;       // Cel, NTC probes
    public boolean chargeFet;
    public boolean dischargeFet;
    public int    protection;    // 16-bit protection bitmask, 0 = OK
    public double[] cells;       // V per cell

    public double power() { return voltage * current; }
}
