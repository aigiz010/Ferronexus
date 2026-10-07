package com.aigiz010.ferronexus.nexus;

/** Типы каналов, которые могут сосуществовать в одном блоке Nexus-шины. */
public enum NexusChannel {
    ENERGY, FLUID, GAS, ITEM, REDSTONE, NETWORK;

    /** Максимум каналов в одном блоке. */
    public static final int MAX_PER_BLOCK = 9;
}
