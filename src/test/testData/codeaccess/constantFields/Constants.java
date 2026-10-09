package com.example.constants;

public class Constants {
    public static final boolean BOOLEAN = true;
    public static final byte BYTE = 1;
    public static final short SHORT = 2;
    public static final char CHAR = 'c';
    public static final int INT = 42;
    public static final long LONG = 43L;
    public static final float FLOAT = 44.0f;
    public static final double DOUBLE = 45.0;
    public static final java.lang.String TEXT = "inline";
    public static final int EXPRESSION = INT + 1;
    public final int INSTANCE = 46;
    public static final int RUNTIME = runtimeValue();
    public static final java.lang.String RUNTIME_TEXT = runtimeText();
    public static final Object OBJECT = "object";
    public static int MUTABLE = 47;
    public static final int INITIALIZED;
    public final int instanceInitialized;

    static {
        INITIALIZED = 48;
    }

    public Constants() {
        instanceInitialized = 49;
    }

    private static int runtimeValue() { return 50; }
    private static java.lang.String runtimeText() { return "runtime"; }
}
