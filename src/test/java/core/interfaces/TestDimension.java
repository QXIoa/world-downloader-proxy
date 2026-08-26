package core.interfaces;

import java.nio.file.Path;

/**
 * Minimal IDimension implementation for core tests that don't need
 * version-specific dimension behavior.
 */
public class TestDimension implements IDimension {
    private final String name;
    private final String type;
    private final boolean nether;

    public TestDimension(String name) {
        this(name, name, false);
    }

    public TestDimension(String name, String type, boolean nether) {
        this.name = name;
        this.type = type;
        this.nether = nether;
    }

    public static TestDimension overworld() {
        return new TestDimension("minecraft:overworld", "minecraft:overworld", false);
    }

    public static TestDimension nether() {
        return new TestDimension("minecraft:the_nether", "minecraft:the_nether", true);
    }

    public static TestDimension end() {
        return new TestDimension("minecraft:the_end", "minecraft:the_end", false);
    }

    @Override
    public String getType() { return type; }

    @Override
    public String getPath() { return "dimensions/" + name.replace(":", "/"); }

    @Override
    public void write(Path prefix) { }

    @Override
    public String toString() { return name; }

    @Override
    public String getName() { return name; }

    @Override
    public void setType(String dimensionType) { }

    @Override
    public boolean isNether() { return nether; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestDimension that)) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }
}
