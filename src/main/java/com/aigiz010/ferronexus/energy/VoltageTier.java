package com.aigiz010.ferronexus.energy;

/** Ярусы мощности: максимальный вход машины в RF/t (после конвертации). */
public enum VoltageTier {
    ULV(8), LV(32), MV(128), HV(512), EV(2048), IV(8192), LUV(32768), ZPM(131072), UV(524288);

    private final long maxRfPerTick;

    VoltageTier(long maxRfPerTick) { this.maxRfPerTick = maxRfPerTick; }

    public long maxRfPerTick() { return maxRfPerTick; }

    public static VoltageTier fromRfPerTick(long rf) {
        for (VoltageTier t : values()) if (rf <= t.maxRfPerTick) return t;
        return UV;
    }
}
