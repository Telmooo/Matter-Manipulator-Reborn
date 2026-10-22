package lu.kolja.matter_manipulator.common.integration.gtceu.common;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class GTShared {
    public record GTCover(
            Direction attachedSide,
            @Nullable ResourceLocation id,
            ItemStack item,
            CompoundTag data,
            List<ItemStack> additionalDrops
    ) {}

    public record GTMachineSettings(
            @Nullable Direction front,
            @Nullable Output items,
            @Nullable Output fluids,
            @Nullable Direction steamOutput
    ) {

        public record Output(Direction side, boolean autoOutput, boolean inputFromOutputSide) {}
    }

    public record GTMaintenance(ItemStack tape, boolean configurable, float durationMultiplier) {}

    public enum GTOutputKind {
        ITEMS,
        FLUIDS
    }

    public record GTPipe(String kind, int connections, int blockedConnections, int coveredSides, int passThroughSides) {

        public boolean isConnected(Direction side) {
            return (connections & sideMask(side)) != 0;
        }

        public boolean hasCover(Direction side) {
            return (coveredSides & sideMask(side)) != 0;
        }

        public boolean passesThrough(Direction side) {
            return (passThroughSides & sideMask(side)) != 0;
        }

        public static int sideMask(Direction side) {
            return 1 << side.ordinal();
        }
    }
}
