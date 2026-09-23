package org.sensorhub.impl.sensor.power;

/** Parsed VE.Direct block from a SmartSolar MPPT. */
public class SolarChargerStatus
{
    public double ts;
    public double batteryVoltage;   // V   (VE.Direct "V", mV)
    public double batteryCurrent;   // A   ("I", mA)
    public double panelVoltage;     // V   ("VPV", mV)
    public double panelPower;       // W   ("PPV")
    public String chargeState;      // from "CS"
    public int    errorCode;        // "ERR"
    public double yieldTotalKWh;    // "H19", 0.01 kWh
    public double yieldTodayKWh;    // "H20", 0.01 kWh
    public double maxPowerTodayW;   // "H21"
}
