package org.sensorhub.impl.sensor.power;

public class PowerConstants
{
    public static final String URI_BASE = "urn:osh:def:power:";

    // Victron VE.Direct CS register -> charge state token
    public static String chargeStateFromVictronCS(int cs)
    {
        switch (cs)
        {
            case 0:   return "off";
            case 1:   return "low_power";
            case 2:   return "fault";
            case 3:   return "bulk";
            case 4:   return "absorption";
            case 5:   return "float";
            case 7:   return "equalize";
            case 9:   return "inverting";
            case 252: return "ext_control";
            default:  return "unknown";
        }
    }

    private PowerConstants() {}
}
