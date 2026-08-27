package core.util;

/**
 * Simple immutable pair replacement for javafx.util.Pair.
 */
public class Pair<A, B> {
    private final A key;
    private final B value;

    public Pair(A key, B value) {
        this.key = key;
        this.value = value;
    }

    public A getKey() {
        return key;
    }

    public B getValue() {
        return value;
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
