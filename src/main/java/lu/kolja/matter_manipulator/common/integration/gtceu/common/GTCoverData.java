package lu.kolja.matter_manipulator.common.integration.gtceu.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class GTCoverData {

    private static final String FILTER_ITEM = "filterItem";
    private static final String ITEMS = "Items";
    private static final String SLOT = "Slot";
    private static final String ID = "id";
    private static final String COUNT = "Count";

    private GTCoverData() {}

    public static CompoundTag fromV8(CompoundTag data) {
        var output = data.copy();
        toV7Layout(output);
        return output;
    }

    private static void toV7Layout(CompoundTag tag) {
        for (var key : tag.getAllKeys()) {
            var value = tag.get(key);
            if (value instanceof CompoundTag compound) {
                if (key.equals(FILTER_ITEM) && compound.contains(ITEMS, Tag.TAG_LIST)) {
                    tag.put(key, firstSlot(compound.getList(ITEMS, Tag.TAG_COMPOUND)));
                } else {
                    toV7Layout(compound);
                }
            } else if (value instanceof ListTag list) {
                for (var element : list) {
                    if (element instanceof CompoundTag compound) {
                        toV7Layout(compound);
                    }
                }
            }
        }
        if (tag.contains(ID, Tag.TAG_STRING) && tag.contains(COUNT, Tag.TAG_INT)) {
            int count = tag.getInt(COUNT);
            if (count >= Byte.MIN_VALUE && count <= Byte.MAX_VALUE) {
                tag.putByte(COUNT, (byte) count);
            }
        }
    }

    private static CompoundTag firstSlot(ListTag items) {
        for (var element : items) {
            var slot = (CompoundTag) element;
            if (slot.getInt(SLOT) == 0) {
                var stack = slot.copy();
                stack.remove(SLOT);
                toV7Layout(stack);
                return stack;
            }
        }
        return ItemStack.EMPTY.save(new CompoundTag());
    }
}
