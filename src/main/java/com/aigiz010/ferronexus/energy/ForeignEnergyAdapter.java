package com.aigiz010.ferronexus.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Модуль совместимости для одной внешней энергосистемы.
 * Регистрируется в {@link EnergyConversion}, только если эта система присутствует в сборке.
 */
public interface ForeignEnergyAdapter {
    /** Идентификатор системы, например "example:joules". Используется в конфиге коэффициентов. */
    String systemId();

    /** Может ли соседний блок отдавать энергию этой системы с указанной стороны. */
    boolean canExtract(BlockEntity source, Direction side);

    /**
     * Забрать энергию у соседа в его собственных единицах.
     * @param maxNative максимум в единицах внешней системы
     * @param simulate  true — только проверить, ничего не забирая
     * @return сколько единиц было (или было бы) забрано
     */
    long extractNative(BlockEntity source, Direction side, long maxNative, boolean simulate);
}
