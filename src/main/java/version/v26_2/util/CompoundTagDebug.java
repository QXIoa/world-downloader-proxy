package version.v26_2.util;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.SpecificTag;

public class CompoundTagDebug extends CompoundTag {
    @Override
    public void add(String name, SpecificTag tag) {
        super.add(name, tag);

        //new Exception("Added " + name).printStackTrace();
    }
}
