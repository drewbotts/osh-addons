package org.sensorhub.impl.sensor.petfeeder;

import net.opengis.swe.v20.AllowedTokens;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.command.CommandException;
import org.sensorhub.impl.sensor.AbstractSensorControl;
import org.vast.swe.SWEHelper;

/**
 * Tasking interface for the pet feeder.
 *
 * <p>Command record schema:
 * <pre>
 *   command       : category — "single_portion" | "dispense_meal" | "set_portion_size"
 *   portion_size  : integer 1–10 (only relevant for set_portion_size command)
 * </pre>
 */
public class FeedingCommandControl extends AbstractSensorControl<PetFeederSensor> {

    private static final String CONTROL_NAME  = "feedingCommand";
    private static final String CONTROL_LABEL = "Feeding Command";

    public static final String CMD_SINGLE_PORTION   = "single_portion";
    public static final String CMD_DISPENSE_MEAL    = "dispense_meal";
    public static final String CMD_SET_PORTION_SIZE = "set_portion_size";

    static final int IDX_COMMAND      = 0;
    static final int IDX_PORTION_SIZE = 1;

    DataComponent commandRecord;
    DataEncoding dataEncoding;

    public FeedingCommandControl(PetFeederSensor parent) {
        super(CONTROL_NAME, parent);
    }

    public void init() {
        SWEHelper fac = new SWEHelper();

        commandRecord = fac.newDataRecord(2);
        commandRecord.setName(getName());
        commandRecord.setLabel(CONTROL_LABEL);
        commandRecord.setDefinition(SWEHelper.getPropertyUri("FeedingCommand"));

        var command = fac.newCategory();
        command.setLabel("Command");
        command.setDefinition(SWEHelper.getPropertyUri("CommandType"));
        command.setDescription("Feeding command to execute on the feeder");
        AllowedTokens cmdAllowed = fac.newAllowedTokens();
        cmdAllowed.addValue(CMD_SINGLE_PORTION);
        cmdAllowed.addValue(CMD_DISPENSE_MEAL);
        cmdAllowed.addValue(CMD_SET_PORTION_SIZE);
        command.setConstraint(cmdAllowed);
        commandRecord.addComponent("command", command);

        var portionSize = fac.newCount();
        portionSize.setLabel("Portion Size");
        portionSize.setDefinition(SWEHelper.getPropertyUri("NumberOfPortions"));
        portionSize.setDescription("Number of portions (1-10). Required for set_portion_size command.");
        portionSize.setOptional(true);
        commandRecord.addComponent("portion_size", portionSize);

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    @Override
    protected boolean execCommand(DataBlock command) throws CommandException {
        String cmdType = command.getStringValue(IDX_COMMAND);
        int portionSize = command.getIntValue(IDX_PORTION_SIZE);

        String prefix = parentSensor.getConfig().topicPrefix;

        try {
            switch (cmdType) {
                case CMD_SINGLE_PORTION:
                    parentSensor.publishMqtt(prefix + "/feedbuttonCmd", "PRESS");
                    parentSensor.getFeedingEventOutput()
                        .notifyFeedStarted(1, "manual_single");
                    parentSensor.getLogger().info("Command: single portion dispensed");
                    break;

                case CMD_DISPENSE_MEAL:
                    parentSensor.publishMqtt(prefix + "/ledbuttonCmd", "PRESS");
                    parentSensor.getFeedingEventOutput()
                        .notifyFeedStarted(parentSensor.getCurrentPortionSize(), "manual_meal");
                    parentSensor.getLogger().info("Command: meal dispensed ({} portions)",
                        parentSensor.getCurrentPortionSize());
                    break;

                case CMD_SET_PORTION_SIZE:
                    if (portionSize < 1 || portionSize > 10) {
                        throw new CommandException("portion_size must be between 1 and 10, got: " + portionSize);
                    }
                    parentSensor.publishMqtt(prefix + "/portion_size", String.valueOf(portionSize));
                    parentSensor.getLogger().info("Command: portion size set to {}", portionSize);
                    break;

                default:
                    throw new CommandException("Unknown command type: " + cmdType);
            }
            return true;

        } catch (Exception e) {
            if (e instanceof CommandException) throw (CommandException) e;
            throw new CommandException("Failed to execute command: " + cmdType, e);
        }
    }

    @Override public String getName()                      { return CONTROL_NAME; }
    @Override public DataComponent getCommandDescription() { return commandRecord; }
    @Override public DataEncoding getCommandEncoding()     { return dataEncoding; }
}
