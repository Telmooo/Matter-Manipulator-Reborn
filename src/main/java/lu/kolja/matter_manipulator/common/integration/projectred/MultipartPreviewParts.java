package lu.kolja.matter_manipulator.common.integration.projectred;

import codechicken.multipart.block.TileMultipart;
import codechicken.multipart.init.MultiPartRegistries;
import lu.kolja.matter_manipulator.api.DirectionTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class MultipartPreviewParts {

    public static final ResourceLocation PAYLOAD = MultipartIntegration.ID;

    private MultipartPreviewParts() {}

    public static @Nullable TileMultipart tile(CompoundTag payload) {
        if (payload.contains("refused", Tag.TAG_COMPOUND)) {
            return null;
        }
        var tag = new CompoundTag();
        tag.put("parts", payload.getList("parts", Tag.TAG_COMPOUND).copy());
        return TileMultipart.fromNBT(tag, BlockPos.ZERO);
    }

    public static @Nullable CompoundTag turnedParts(TileMultipart source, DirectionTransform transform) {
        var parts = new ListTag();
        for (var part : source.getPartList()) {
            var saved = MultiPartRegistries.savePart(new CompoundTag(), part);
            if (!PartTransforms.isIdentity(transform) && PartTransforms.transform(saved, transform) == PartTransforms.Outcome.CANNOT_FACE) {
                return null;
            }
            parts.add(saved);
        }
        var payload = new CompoundTag();
        payload.put("parts", parts);
        return payload;
    }

    public static CompoundTag routed(CompoundTag template, Set<Direction> neighbours) {
        return ModList.get().isLoaded("projectred_transmission")
                ? WireCableIntegration.routed(template, neighbours)
                : template;
    }

    public static boolean isIdentity(DirectionTransform transform) {
        return PartTransforms.isIdentity(transform);
    }

    public static ItemStack icon(CompoundTag payload) {
        for (var drops : payload.getList("drops", Tag.TAG_LIST)) {
            var list = (ListTag) drops;
            for (int i = 0; i < list.size(); i++) {
                var stack = ItemStack.of(list.getCompound(i));
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
