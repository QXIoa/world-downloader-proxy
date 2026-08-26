package version.v26_2.schematic;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

/**
 * Simple holder for the active {@link CreativeMode} instance so that the clientbound
 * packet handler (which doesn't have a reference to the serverbound handler that owns
 * the CreativeMode) can forward GameEvent packets to it.
 */
public final class CreativeModeRegistry {
    private static volatile CreativeMode instance;

    public static void set(CreativeMode mode) {
        instance = mode;
    }

    public static CreativeMode get() {
        return instance;
    }

    public static void clear() {
        instance = null;
    }

    private CreativeModeRegistry() {}
}
