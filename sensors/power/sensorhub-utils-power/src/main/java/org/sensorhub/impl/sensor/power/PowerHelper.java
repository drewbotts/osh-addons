package org.sensorhub.impl.sensor.power;

import net.opengis.swe.v20.Category;
import net.opengis.swe.v20.Count;
import net.opengis.swe.v20.Quantity;
import org.vast.swe.SWEHelper;

/**
 * SWE Common helper for electrical power / battery outputs.
 * Use this in driver Output classes instead of plain SWEHelper so every
 * driver (JBD, Victron, future shunt/alternator) produces identical field
 * names, definition URIs, and units.
 */
public class PowerHelper extends SWEHelper
{
    public Quantity createBatteryVoltage()
    {
        return createQuantity()
            .definition(SWEHelper.getPropertyUri("BatteryVoltage"))
            .label("Battery Voltage")
            .uomCode("V")
            .build();
    }

    /** Signed: positive = charging, negative = discharging */
    public Quantity createBatteryCurrent()
    {
        return createQuantity()
            .definition(PowerConstants.URI_BASE + "BatteryCurrent")
            .label("Battery Current")
            .description("Signed battery current; positive = charging")
            .uomCode("A")
            .build();
    }

    public Quantity createStateOfCharge()
    {
        return createQuantity()
            .definition(PowerConstants.URI_BASE + "StateOfCharge")
            .label("State of Charge")
            .uomCode("%")
            .build();
    }

    public Quantity createElectricalPower(String label, String definition)
    {
        return createQuantity()
            .definition(definition)
            .label(label)
            .uomCode("W")
            .build();
    }

    public Quantity createCapacity(String label, String definition)
    {
        return createQuantity()
            .definition(definition)
            .label(label)
            .uomCode("A.h")
            .build();
    }

    public Quantity createTemperature(String label, String definition)
    {
        return createQuantity()
            .definition(definition)
            .label(label)
            .uomCode("Cel")
            .build();
    }

    /** Victron CS register / generic charger state */
    public Category createChargeState()
    {
        return createCategory()
            .definition(PowerConstants.URI_BASE + "ChargeState")
            .label("Charge State")
            .addAllowedValues("off", "low_power", "fault", "bulk",
                              "absorption", "float", "equalize", "inverting",
                              "ext_control", "unknown")
            .build();
    }

    /** Raw device error/alarm register, decoded meaning left to clients */
    public Count createErrorCode(String label, String definition)
    {
        return createCount()
            .definition(definition)
            .label(label)
            .build();
    }
}
