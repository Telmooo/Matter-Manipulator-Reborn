package lu.kolja.matter_manipulator.common.integration.gtceu.common;

import lu.kolja.matter_manipulator.api.DirectionTransform;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public interface GTAccess {

    String name();

    boolean isPipe(BlockState state);

    boolean isMachine(BlockState state);

    @Nullable
    String pipeKind(BlockState state);

    @Nullable
    DirectionProperty frontProperty(BlockState state);

    @Nullable
    DirectionProperty upwardsProperty(BlockState state);

    Direction top(Direction front, Direction upwardsFacing);

    Direction upwardsFacing(Direction front, Direction top);

    @Nullable
    GTShared.GTPipe pipe(@Nullable BlockEntity blockEntity);

    void setPipeConnections(BlockEntity pipe, int connections);

    void setPipeBlockedConnections(BlockEntity pipe, int blockedConnections);

    void syncPipe(BlockEntity pipe);

    boolean isMachineEntity(@Nullable BlockEntity blockEntity);

    void updateMachine(BlockEntity machine);

    @Nullable
    GTShared.GTMachineSettings machineSettings(@Nullable BlockEntity blockEntity);

    void setFrontFacing(BlockEntity machine, Direction front);

    void setOutputSide(BlockEntity machine, GTShared.GTOutputKind kind, @Nullable Direction side);

    void setAutoOutput(BlockEntity machine, GTShared.GTOutputKind kind, boolean autoOutput);

    void setInputFromOutputSide(BlockEntity machine, GTShared.GTOutputKind kind, boolean allow);

    void setSteamOutput(BlockEntity machine, Direction side);

    boolean savesContentsInDrop(@Nullable BlockEntity blockEntity);

    @Nullable
    Boolean muffled(@Nullable BlockEntity blockEntity);

    void setMuffled(BlockEntity machine, boolean muffled);

    @Nullable
    ItemStack circuit(@Nullable BlockEntity blockEntity);

    int circuitConfiguration(ItemStack circuit);

    boolean setCircuit(@Nullable BlockEntity blockEntity, int configuration);

    int unpaintedColor();

    @Nullable
    Integer paintingColor(@Nullable BlockEntity blockEntity);

    boolean setPaintingColor(@Nullable BlockEntity blockEntity, int color);

    boolean isCoverable(@Nullable BlockEntity blockEntity);

    @Nullable
    GTShared.GTCover cover(BlockEntity coverable, Direction side);

    boolean hasCoverDefinition(ResourceLocation id);

    boolean placeCover(
            BlockEntity coverable,
            Direction side,
            ResourceLocation id,
            ItemStack item,
            @Nullable ServerPlayer player,
            CompoundTag data
    );

    void coversChanged(BlockEntity coverable);

    @Nullable
    GTShared.GTMaintenance maintenance(@Nullable BlockEntity blockEntity);

    void setDurationMultiplier(BlockEntity hatch, float multiplier);

    ItemStack insertTape(BlockEntity hatch, ItemStack tape);

    CompoundTag transformMachineNbt(CompoundTag blockEntity, DirectionTransform transform);

    CompoundTag transformCoverNbt(CompoundTag blockEntity, DirectionTransform transform);
}
