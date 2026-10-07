package com.aigiz010.ferronexus.imprinter;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.conduit.ConduitBundleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Импринтер: копирует структуры, их содержимое и настройки.
 *  - В руке достаёт до блоков в 3 раза дальше обычного.
 *  - Shift + ПКМ — сменить режим.
 *  - R — повернуть вставку на 90° по часовой вокруг блока, на который смотришь (точка опоры).
 *  - «Выделение»: ПКМ по блоку — первый угол, ещё раз — второй.
 *  - «Копирование»: ПКМ — запомнить блоки и их настройки (без содержимого).
 *  - «Копирование с содержимым»: то же, вместе с предметами, жидкостями, энергией.
 *  - «Вставка»: ПКМ по блоку — структура ставится рядом с ним.
 *  - «Применить настройки»: ПКМ по блоку = точка опоры; настройки ложатся на такие же уже стоящие блоки.
 */
public class ImprinterItem extends Item {
    public static final int MAX_SIDE = 64;
    public static final int MAX_BLOCKS = 8192;
    public static final String ROTATE_KEY = "key.ferronexus.imprinter_rotate";
    /** +200% к дальности работы с блоками = в 3 раза дальше. */
    public static final double REACH_BONUS = 2.0;
    private static final String[] CONTENT_KEYS = {"Items", "items", "Inventory", "Fluid", "fluid", "Tank", "Tanks", "Energy", "energy"};

    public ImprinterItem(Item.Properties props) {
        super(props.stacksTo(1).attributes(reach()));
    }

    private static ItemAttributeModifiers reach() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.BLOCK_INTERACTION_RANGE,
                        new AttributeModifier(Identifier.fromNamespaceAndPath(Ferronexus.MOD_ID, "imprinter_reach"),
                                REACH_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                        EquipmentSlotGroup.HAND)
                .build();
    }

    // ---------- Данные в предмете ----------
    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ImprintMode mode(ItemStack stack) {
        ImprintMode[] v = ImprintMode.values();
        return v[Math.floorMod(data(stack).getIntOr("Mode", 0), v.length)];
    }

    /** Поворот схемы: 0..3 (шаг 90° по часовой). */
    public static int rot(ItemStack stack) {
        return Math.floorMod(data(stack).getIntOr("Rot", 0), 4);
    }

    public static Rotation rotation(int r) {
        return switch (Math.floorMod(r, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * Поворот координат (x, z) схемы вокруг точки опоры (0, 0) — блока, на который смотришь.
     * Структура «разворачивается» вокруг этого блока, а не остаётся в том же углу.
     * Направления совпадают с Rotation: север (0,-1) → восток (1,0) при повороте на 90°.
     * sx, sz оставлены для совместимости вызовов.
     */
    public static int[] rotateXZ(int x, int z, int sx, int sz, int r) {
        return switch (Math.floorMod(r, 4)) {
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            case 3 -> new int[] {z, -x};
            default -> new int[] {x, z};
        };
    }

    // ---------- Поворот настроек сторон блока проводов ----------
    private static int rotateMask(int mask, Rotation rot) {
        int out = 0;
        for (Direction d : Direction.values()) {
            if ((mask & (1 << d.ordinal())) != 0) out |= 1 << rot.rotate(d).ordinal();
        }
        return out;
    }

    private static int rotateModes(int m, Rotation rot) {
        int out = 0;
        for (Direction d : Direction.values()) {
            int v = (m >> (d.ordinal() * 2)) & 3;
            out |= v << (rot.rotate(d).ordinal() * 2);
        }
        return out;
    }

    /** Режимы сторон (M), соединения (C), разъёмы (P), заглушки (S) поворачиваются вместе с блоком. */
    public static void rotateConduitTag(CompoundTag t, Rotation rot) {
        if (rot == Rotation.NONE) return;
        for (int i = 0; i < 32; i++) {
            if (t.contains("M" + i)) t.putInt("M" + i, rotateModes(t.getIntOr("M" + i, 0), rot));
            if (t.contains("C" + i)) t.putInt("C" + i, rotateMask(t.getIntOr("C" + i, 0), rot));
            if (t.contains("P" + i)) t.putInt("P" + i, rotateMask(t.getIntOr("P" + i, 0), rot));
            if (t.contains("S" + i)) t.putInt("S" + i, rotateMask(t.getIntOr("S" + i, 0), rot));
        }
    }

    private static void rotateSettings(BlockState st, CompoundTag t, Rotation rot) {
        if (st.getBlock() instanceof ConduitBundleBlock) rotateConduitTag(t, rot);
    }

    /** Вызывается с сервера по нажатию R. */
    public static void rotate(ItemStack stack, Player player) {
        CompoundTag tag = data(stack);
        int r = Math.floorMod(tag.getIntOr("Rot", 0) + 1, 4);
        tag.putInt("Rot", r);
        save(stack, tag);
        tell(player, Component.translatable("message.ferronexus.imprinter.rotated", r * 90, Component.keybind(ROTATE_KEY)));
    }

    private static void tell(Player player, Component msg) {
        if (player instanceof ServerPlayer sp) sp.sendSystemMessage(msg, true);
    }

    private static Component modeName(ImprintMode m) {
        return Component.translatable("imprinter.ferronexus.mode." + m.name().toLowerCase());
    }

    private static void cycle(ItemStack stack, Player player) {
        CompoundTag tag = data(stack);
        int next = Math.floorMod(tag.getIntOr("Mode", 0) + 1, ImprintMode.values().length);
        tag.putInt("Mode", next);
        save(stack, tag);
        ImprintMode m = ImprintMode.values()[next];
        if (m == ImprintMode.PASTE || m == ImprintMode.APPLY_SETTINGS) {
            tell(player, Component.translatable("message.ferronexus.imprinter.mode_rotate", modeName(m), Component.keybind(ROTATE_KEY)));
        } else {
            tell(player, Component.translatable("message.ferronexus.imprinter.mode", modeName(m)));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide()) cycle(player.getItemInHand(hand), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player != null && player.isShiftKeyDown()) {
            cycle(stack, player);
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = ctx.getClickedPos();
        switch (mode(stack)) {
            case SELECT -> select(stack, player, pos);
            case COPY -> copy(level, stack, player, false);
            case COPY_CONTENTS -> copy(level, stack, player, true);
            case PASTE -> paste(level, stack, player, pos.relative(ctx.getClickedFace()));
            case APPLY_SETTINGS -> apply(level, stack, player, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // ---------- Выделение ----------
    private static void select(ItemStack stack, Player player, BlockPos pos) {
        CompoundTag tag = data(stack);
        boolean first = !tag.getBooleanOr("HasA", false) || tag.getBooleanOr("HasB", false);
        if (first) {
            tag.putLong("A", pos.asLong());
            tag.putBoolean("HasA", true);
            tag.putBoolean("HasB", false);
        } else {
            tag.putLong("B", pos.asLong());
            tag.putBoolean("HasB", true);
        }
        save(stack, tag);
        tell(player, Component.translatable("message.ferronexus.imprinter.corner", first ? 1 : 2, pos.getX(), pos.getY(), pos.getZ()));
    }

    // ---------- Копирование ----------
    private static void stripContents(CompoundTag t) {
        for (String k : CONTENT_KEYS) t.remove(k);
        for (int i = 0; i < 32; i++) t.remove("E" + i);
    }

    private static void copy(Level level, ItemStack stack, Player player, boolean contents) {
        CompoundTag tag = data(stack);
        if (!tag.getBooleanOr("HasA", false) || !tag.getBooleanOr("HasB", false)) {
            tell(player, Component.translatable("message.ferronexus.imprinter.need_selection"));
            return;
        }
        BlockPos a = BlockPos.of(tag.getLongOr("A", 0L)), b = BlockPos.of(tag.getLongOr("B", 0L));
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        int sx = max.getX() - min.getX() + 1, sy = max.getY() - min.getY() + 1, sz = max.getZ() - min.getZ() + 1;
        if (sx > MAX_SIDE || sy > MAX_SIDE || sz > MAX_SIDE) {
            tell(player, Component.translatable("message.ferronexus.imprinter.too_big", MAX_SIDE, MAX_BLOCKS));
            return;
        }
        ListTag list = new ListTag();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            BlockPos p = min.offset(x, y, z);
            BlockState st = level.getBlockState(p);
            if (st.isAir()) continue;
            if (list.size() >= MAX_BLOCKS) {
                tell(player, Component.translatable("message.ferronexus.imprinter.too_big", MAX_SIDE, MAX_BLOCKS));
                return;
            }
            CompoundTag e = new CompoundTag();
            e.putInt("X", x);
            e.putInt("Y", y);
            e.putInt("Z", z);
            e.put("S", NbtUtils.writeBlockState(st));
            BlockEntity be = level.getBlockEntity(p);
            if (be != null) {
                CompoundTag t = be.saveCustomOnly(level.registryAccess());
                if (!contents) stripContents(t);
                e.put("T", t);
            }
            list.add(e);
        }
        tag.put("Blocks", list);
        tag.putBoolean("Contents", contents);
        tag.putInt("SX", sx);
        tag.putInt("SY", sy);
        tag.putInt("SZ", sz);
        tag.putInt("Rot", 0);
        save(stack, tag);
        tell(player, Component.translatable("message.ferronexus.imprinter.copied", list.size(), sx, sy, sz));
    }

    // ---------- Вставка ----------
    private static boolean takeItem(Player player, BlockState st) {
        if (player == null || player.getAbilities().instabuild) return true;
        Item need = st.getBlock().asItem();
        if (need == null || need == net.minecraft.world.item.Items.AIR) return false;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == need) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void loadInto(Level level, BlockPos p, CompoundTag t) {
        BlockEntity be = level.getBlockEntity(p);
        if (be == null) return;
        be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), t));
        be.setChanged();
        BlockState st = level.getBlockState(p);
        level.sendBlockUpdated(p, st, st, 3);
        level.updateNeighborsAt(p, st.getBlock());
    }

    private static BlockPos target(CompoundTag tag, CompoundTag e, BlockPos origin, int r) {
        int sx = Math.max(1, tag.getIntOr("SX", 1)), sz = Math.max(1, tag.getIntOr("SZ", 1));
        int[] xz = rotateXZ(e.getIntOr("X", 0), e.getIntOr("Z", 0), sx, sz, r);
        return origin.offset(xz[0], e.getIntOr("Y", 0), xz[1]);
    }

    private static void paste(Level level, ItemStack stack, Player player, BlockPos origin) {
        CompoundTag tag = data(stack);
        ListTag list = tag.getListOrEmpty("Blocks");
        if (list.isEmpty()) {
            tell(player, Component.translatable("message.ferronexus.imprinter.empty"));
            return;
        }
        int r = Math.floorMod(tag.getIntOr("Rot", 0), 4);
        Rotation rotation = rotation(r);
        boolean creative = player != null && player.getAbilities().instabuild;
        var blocks = level.holderLookup(Registries.BLOCK);
        int placed = 0, skipped = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            BlockPos p = target(tag, e, origin, r);
            BlockState st = NbtUtils.readBlockState(blocks, e.getCompoundOrEmpty("S")).rotate(rotation);
            if (st.isAir() || !level.getBlockState(p).canBeReplaced() || !takeItem(player, st)) {
                skipped++;
                continue;
            }
            level.setBlock(p, st, 3);
            if (e.contains("T")) {
                CompoundTag t = e.getCompoundOrEmpty("T").copy();
                if (!creative) stripContents(t);
                rotateSettings(st, t, rotation);
                loadInto(level, p, t);
            }
            placed++;
        }
        tell(player, Component.translatable("message.ferronexus.imprinter.pasted", placed, skipped));
    }

    // ---------- Применить настройки ----------
    private static void apply(Level level, ItemStack stack, Player player, BlockPos origin) {
        CompoundTag tag = data(stack);
        ListTag list = tag.getListOrEmpty("Blocks");
        if (list.isEmpty()) {
            tell(player, Component.translatable("message.ferronexus.imprinter.empty"));
            return;
        }
        int r = Math.floorMod(tag.getIntOr("Rot", 0), 4);
        Rotation rotation = rotation(r);
        var blocks = level.holderLookup(Registries.BLOCK);
        int applied = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            if (!e.contains("T")) continue;
            BlockPos p = target(tag, e, origin, r);
            BlockState want = NbtUtils.readBlockState(blocks, e.getCompoundOrEmpty("S"));
            BlockState here = level.getBlockState(p);
            if (here.getBlock() != want.getBlock()) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            CompoundTag cur = be.saveCustomOnly(level.registryAccess());
            CompoundTag set = e.getCompoundOrEmpty("T").copy();
            stripContents(set);
            rotateSettings(here, set, rotation);
            for (String k : CONTENT_KEYS) if (cur.contains(k)) set.put(k, cur.get(k).copy());
            for (int n = 0; n < 32; n++) if (cur.contains("E" + n)) set.put("E" + n, cur.get("E" + n).copy());
            loadInto(level, p, set);
            applied++;
        }
        tell(player, Component.translatable("message.ferronexus.imprinter.applied", applied));
    }
}
