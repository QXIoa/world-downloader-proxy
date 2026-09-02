package version.v26_1.components;

import se.llbit.nbt.SpecificTag;

public record DataComponentValue<T>(String type, T value, ComponentCodec<T> codec) {
    public SpecificTag toNbt(ComponentNbtContext context) {
        return codec.toNbt(value, context);
    }
}
