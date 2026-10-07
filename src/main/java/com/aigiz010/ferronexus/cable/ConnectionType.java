package com.aigiz010.ferronexus.cable;

import net.minecraft.util.StringRepresentable;

/** Состояние стороны провода: ничего, соседний провод или разъём к машине. */
public enum ConnectionType implements StringRepresentable {
    NONE("none"),
    CABLE("cable"),
    PLUG("plug");

    private final String name;

    ConnectionType(String name) { this.name = name; }

    public boolean connected() { return this != NONE; }

    @Override
    public String getSerializedName() { return name; }
}
