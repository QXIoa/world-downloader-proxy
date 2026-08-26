package core.schematic;

/**
 * Recognized {@code /world-downloader-proxy <subcommand>} subcommands for the schematic selection
 * workflow. Kept separate from {@link SelectionCommandRouter} so the set of known subcommands can
 * be inspected/extended (e.g. for a future "clear" subcommand) without touching parsing logic.
 */
public enum SelectionCommand {
    TOGGLE_SELECTION("area-selection"),
    EXPORT("schematic-export"),
    POS1("pos1"),
    POS2("pos2"),
    FLY("fly");

    private final String keyword;

    SelectionCommand(String keyword) {
        this.keyword = keyword;
    }

    public static SelectionCommand fromArgument(String argument) {
        for (SelectionCommand command : values()) {
            if (command.keyword.equalsIgnoreCase(argument)) {
                return command;
            }
        }
        return null;
    }
}
