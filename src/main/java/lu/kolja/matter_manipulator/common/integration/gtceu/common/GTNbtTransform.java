package lu.kolja.matter_manipulator.common.integration.gtceu.common;

import lu.kolja.matter_manipulator.api.DirectionTransform;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Locale;

public final class GTNbtTransform {

    static final String MACHINE_CONTROLLER = "gtceu:machine_controller";
    private static final String COVERS = "cover";
    private static final String UID = "uid";
    private static final String ID = "id";
    private static final String SIDE = "side";
    private static final String PAYLOAD = "payload";
    private static final String DATA = "d";
    private static final String COVER_TYPE = "coverType";
    private static final String COVER_DATA = "data";
    private static final String CONTROLLER_MODE = "controllerMode";
    private static final String COVER_MODE_PREFIX = "COVER_";
    private static final String TRAIT_HOLDER = "traitHolder";
    private static final String STEAM_OUTPUT = "outputFacing";
    private static final List<String> OUTPUT_FACINGS = List.of("outputFacingItems", "outputFacingFluids", "outputFacing");
    private static final List<String> TRAIT_OUTPUT_FACINGS = List.of("itemOutputDirection", "fluidOutputDirection");
    private static final List<String> PIPE_CONNECTIONS = List.of("connections", "blockedConnections");

    private GTNbtTransform() {}

    public static CompoundTag transformCovers(CompoundTag blockEntity, DirectionTransform transform) {
        var output = blockEntity.copy();
        if (!blockEntity.contains(COVERS, Tag.TAG_COMPOUND)) {
            return output;
        }
        var covers = blockEntity.getCompound(COVERS);
        var transformed = covers.copy();
        for (var side : Direction.values()) {
            transformed.remove(side.getName());
        }
        for (var side : Direction.values()) {
            var cover = covers.get(side.getName());
            if (cover != null) {
                transformed.put(transform.transform(side).getName(),
                        cover instanceof CompoundTag compound ? transformCover(compound, transform) : cover.copy());
            }
        }
        output.put(COVERS, transformed);
        return output;
    }

    public static CompoundTag transformCoversV8(CompoundTag blockEntity, DirectionTransform transform) {
        var output = blockEntity.copy();
        if (!blockEntity.contains(COVERS, Tag.TAG_COMPOUND)) {
            return output;
        }
        var covers = blockEntity.getCompound(COVERS);
        var transformed = covers.copy();
        for (var side : Direction.values()) {
            transformed.remove(side.getName());
        }
        for (var side : Direction.values()) {
            var cover = covers.get(side.getName());
            if (cover != null) {
                transformed.put(transform.transform(side).getName(),
                        cover instanceof CompoundTag compound ? transformCoverV8(compound, transform) : cover.copy());
            }
        }
        output.put(COVERS, transformed);
        return output;
    }

    public static CompoundTag transformCoverData(String coverId, CompoundTag data, DirectionTransform transform) {
        var output = data.copy();
        if (!MACHINE_CONTROLLER.equals(coverId) || !data.contains(CONTROLLER_MODE, Tag.TAG_STRING)) {
            return output;
        }
        var mode = data.getString(CONTROLLER_MODE);
        if (!mode.startsWith(COVER_MODE_PREFIX)) {
            return output;
        }
        var side = Direction.byName(mode.substring(COVER_MODE_PREFIX.length()).toLowerCase(Locale.ROOT));
        if (side != null) {
            output.putString(CONTROLLER_MODE, COVER_MODE_PREFIX + transform.transform(side).name());
        }
        return output;
    }

    public static CompoundTag transformOutputFacings(CompoundTag blockEntity, DirectionTransform transform) {
        var output = blockEntity.copy();
        for (var key : OUTPUT_FACINGS) {
            if (!blockEntity.contains(key, Tag.TAG_STRING)) {
                continue;
            }
            var side = Direction.byName(blockEntity.getString(key));
            if (side != null) {
                output.putString(key, transform.transform(side).getName());
            }
        }
        return output;
    }

    public static CompoundTag transformOutputFacingsV8(CompoundTag blockEntity, DirectionTransform transform) {
        var output = blockEntity.copy();
        transformEnumDirection(output, STEAM_OUTPUT, transform);
        for (var key : blockEntity.getAllKeys()) {
            if (key.equals(COVERS) || !blockEntity.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            var compound = output.getCompound(key);
            transformTraitOutputs(compound, transform);
            if (key.equals(TRAIT_HOLDER)) {
                for (var trait : compound.getAllKeys()) {
                    if (compound.contains(trait, Tag.TAG_COMPOUND)) {
                        transformTraitOutputs(compound.getCompound(trait), transform);
                    }
                }
            }
        }
        return output;
    }

    public static CompoundTag transformPipeConnections(CompoundTag blockEntity, DirectionTransform transform) {
        var output = blockEntity.copy();
        for (var key : PIPE_CONNECTIONS) {
            if (blockEntity.contains(key, Tag.TAG_INT)) {
                output.putInt(key, transformDirectionMask(blockEntity.getInt(key), transform));
            }
        }
        return output;
    }

    public static int transformDirectionMask(int mask, DirectionTransform transform) {
        int transformed = 0;
        for (var direction : Direction.values()) {
            if ((mask & (1 << direction.ordinal())) != 0) {
                transformed |= 1 << transform.transform(direction).ordinal();
            }
        }
        return transformed;
    }

    private static CompoundTag transformCover(CompoundTag cover, DirectionTransform transform) {
        var output = cover.copy();
        var uid = output.getCompound(UID);
        int side = uid.getInt(SIDE);
        if (!uid.contains(SIDE, Tag.TAG_INT) || side < 0 || side >= Direction.values().length) {
            throw new IllegalArgumentException("GTCEu cover has no valid side: " + uid);
        }
        uid.putInt(SIDE, transform.transform(Direction.values()[side]).ordinal());
        var payload = output.getCompound(PAYLOAD);
        if (payload.contains(DATA, Tag.TAG_COMPOUND)) {
            payload.put(DATA, transformCoverData(uid.getString(ID), payload.getCompound(DATA), transform));
        }
        return output;
    }

    private static CompoundTag transformCoverV8(CompoundTag cover, DirectionTransform transform) {
        var output = cover.copy();
        if (!cover.contains(SIDE)) {
            return output;
        }
        var side = cover.contains(SIDE, Tag.TAG_STRING) ? Direction.byName(cover.getString(SIDE)) : null;
        if (side == null) {
            throw new IllegalArgumentException("GTCEu cover has no valid side: " + cover);
        }
        output.putString(SIDE, transform.transform(side).getName());
        if (cover.contains(COVER_DATA, Tag.TAG_COMPOUND)) {
            output.put(COVER_DATA, transformCoverData(cover.getString(COVER_TYPE), cover.getCompound(COVER_DATA), transform));
        }
        return output;
    }

    private static void transformTraitOutputs(CompoundTag trait, DirectionTransform transform) {
        for (var key : TRAIT_OUTPUT_FACINGS) {
            transformEnumDirection(trait, key, transform);
        }
    }

    private static void transformEnumDirection(CompoundTag tag, String key, DirectionTransform transform) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return;
        }
        var value = tag.getString(key);
        var side = Direction.byName(value.toLowerCase(Locale.ROOT));
        if (side == null) {
            return;
        }
        var transformed = transform.transform(side);
        tag.putString(key, value.equals(side.name()) ? transformed.name() : transformed.getName());
    }
}
