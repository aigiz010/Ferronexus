package com.aigiz010.ferronexus.imprinter;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Импринтер: копирует структуры, их содержимое и настройки.
 * TODO 0.7: выделение двух углов, сериализация в компонент данных, предпросмотр.
 */
public class ImprinterItem extends Item {
    public ImprinterItem(Item.Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        // TODO: записать угол выделения / вставить схему
        return InteractionResult.PASS;
    }
}
