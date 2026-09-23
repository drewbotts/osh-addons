package org.sensorhub.impl.sensor.mopeka;

import com.google.gson.annotations.SerializedName;

/**
 * Propane tank observation as published by the Python collector
 * (Mopeka Pro Check over BLE). Deserialized from collector JSON; all
 * calibration (mass, percent, tank geometry) is done by the collector and
 * the values are ingested as-is.
 */
public class PropaneStatus
{
    /** unix seconds, set by the collector when the reading was taken */
    public double ts;

    /** raw sensor height reading, in */
    @SerializedName("height_in")
    public double heightIn;

    /** calibrated liquid height, in */
    @SerializedName("liquid_in")
    public double liquidIn;

    /** propane mass remaining, lb */
    public double lb;

    /** fill level, % */
    public double pct;

    /** liquid temperature, Cel */
    @SerializedName("temp_c")
    public double tempC;

    /** reading quality, 0 (bad) - 3 (good) */
    public int quality;

    /** sensor coin cell voltage, V */
    @SerializedName("battery_v")
    public double batteryV;

    /** sensor coin cell level, % */
    @SerializedName("battery_pct")
    public double batteryPct;

    /** BLE signal strength, dBm */
    public int rssi;
}
