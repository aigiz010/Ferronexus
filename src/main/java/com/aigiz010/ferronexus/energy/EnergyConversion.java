package com.aigiz010.ferronexus.energy;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Реестр внешних энергосистем и коэффициентов перевода в RF. */
public final class EnergyConversion {
    private static final Map<String, ForeignEnergyAdapter> ADAPTERS = new LinkedHashMap<>();
    /** Сколько RF даёт одна единица внешней энергии. Заполняется из конфига и датапаков. */
    private static final Map<String, Double> RF_PER_UNIT = new LinkedHashMap<>();

    private EnergyConversion() {}

    public static void register(ForeignEnergyAdapter adapter, double defaultRfPerUnit) {
        ADAPTERS.put(adapter.systemId(), adapter);
        RF_PER_UNIT.putIfAbsent(adapter.systemId(), defaultRfPerUnit);
    }

    public static void setRate(String systemId, double rfPerUnit) {
        if (rfPerUnit > 0) RF_PER_UNIT.put(systemId, rfPerUnit);
    }

    public static double rfPerUnit(String systemId) {
        return RF_PER_UNIT.getOrDefault(systemId, 1.0);
    }

    /** Перевод внешних единиц в RF (округление вниз). */
    public static long toRf(String systemId, long nativeAmount) {
        return (long) Math.floor(nativeAmount * rfPerUnit(systemId));
    }

    /** Сколько внешних единиц нужно для указанного количества RF (округление вверх). */
    public static long fromRf(String systemId, long rf) {
        return (long) Math.ceil(rf / rfPerUnit(systemId));
    }

    public static Collection<ForeignEnergyAdapter> adapters() {
        return Collections.unmodifiableCollection(ADAPTERS.values());
    }
}
