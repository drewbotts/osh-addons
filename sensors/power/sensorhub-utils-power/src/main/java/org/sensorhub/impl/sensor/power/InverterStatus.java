package org.sensorhub.impl.sensor.power;

/** Parsed VE.Direct block from a Phoenix inverter. */
public class InverterStatus
{
    public double ts;
    public double batteryVoltage;    // V    ("V", mV)
    public double acVoltage;         // V    ("AC_OUT_V", 0.01 V)
    public double acCurrent;         // A    ("AC_OUT_I", 0.1 A)
    public double acApparentPower;   // VA   ("AC_OUT_S")
    public String deviceState;       // from "CS"
    public int    alarmReason;       // "AR" bitmask
    public int    warnReason;        // "WARN" bitmask
}
