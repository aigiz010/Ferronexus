package com.aigiz010.ferronexus.conduit;

/** Режим стороны провода/трубы: что она делает с соседним блоком. */
public enum FaceMode {
    OUTPUT("output"),   // кладёт в соседа
    INPUT("input"),     // забирает из соседа
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
