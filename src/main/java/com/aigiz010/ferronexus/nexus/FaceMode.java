package com.aigiz010.ferronexus.nexus;

/** Режим стороны шины: что шина делает с соседним блоком. */
public enum FaceMode {
    OUTPUT("output"),   // шина кладёт в соседа
    INPUT("input"),     // шина забирает из соседа
    BOTH("both"),       // и то, и другое
    DISABLED("disabled");

    private final String id;

    FaceMode(String id) { this.id = id; }

    public String id() { return id; }

    public boolean isInput() { return this == INPUT || this == BOTH; }

    public boolean isOutput() { return this == OUTPUT || this == BOTH; }

    public FaceMode next() { return values()[(ordinal() + 1) % values().length]; }

    public static FaceMode byOrdinal(int i) {
        return i >= 0 && i < values().length ? values()[i] : OUTPUT;
    }
}
