package lu.kolja.matter_manipulator.common.integration.gtceu.v7;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.steam.SteamWorkableMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.lowdragmc.lowdraglib.syncdata.accessor.IManagedAccessor;
import lu.kolja.matter_manipulator.api.DirectionTransform;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTShared;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTNbtTransform;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTAccess;
import lu.kolja.matter_manipulator.mixins.integration.gtceu.v7.AccessorMaintenanceHatchPartMachine;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public final class GTV7Access implements GTAccess {

    @Override
    public String name() {
        return "GTM V7";
    }

    @Override
    public boolean isPipe(BlockState state) {
        return state.getBlock() instanceof PipeBlock<?, ?, ?>;
    }

    @Override
    public boolean isMachine(BlockState state) {
        return state.getBlock() instanceof MetaMachineBlock;
    }

    @Override
    public @Nullable String pipeKind(BlockState state) {
        return state.getBlock() instanceof PipeBlock<?, ?, ?> block ? block.pipeType.getClass().getName() : null;
    }

    @Override
    public @Nullable DirectionProperty frontProperty(BlockState state) {
        return state.getBlock() instanceof MetaMachineBlock block && block.getRotationState() != RotationState.NONE
                ? block.getRotationState().property
                : null;
    }

    @Override
    public @Nullable DirectionProperty upwardsProperty(BlockState state) {
        return state.hasProperty(GTBlockStateProperties.UPWARDS_FACING) ? GTBlockStateProperties.UPWARDS_FACING : null;
    }

    @Override
    public Direction top(Direction front, Direction upwardsFacing) {
        return RelativeDirection.UP.getRelative(front, upwardsFacing, false);
    }

    @Override
    public Direction upwardsFacing(Direction front, Direction top) {
        for (var candidate : Direction.Plane.HORIZONTAL) {
            if (RelativeDirection.UP.getRelative(front, candidate, false) == top) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No upwards facing for front " + front + " and top " + top);
    }

    @Override
    public @Nullable GTShared.GTPipe pipe(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof PipeBlockEntity<?, ?> pipe)) {
            return null;
        }
        int coveredSides = 0;
        int passThroughSides = 0;
        for (var side : Direction.values()) {
            var cover = pipe.getCoverContainer().getCoverAtSide(side);
            if (cover != null) {
                coveredSides |= GTShared.GTPipe.sideMask(side);
                if (cover.canPipePassThrough()) {
                    passThroughSides |= GTShared.GTPipe.sideMask(side);
                }
            }
        }
        return new GTShared.GTPipe(pipe.getPipeType().getClass().getName(), pipe.getConnections(), pipe.getBlockedConnections(),
                coveredSides, passThroughSides);
    }

    @Override
    public void setPipeConnections(BlockEntity blockEntity, int connections) {
        var pipe = (PipeBlockEntity<?, ?>) blockEntity;
        for (var direction : Direction.values()) {
            pipe.setConnection(direction, PipeBlockEntity.isConnected(connections, direction), false);
        }
        pipe.setConnections(connections);
    }

    @Override
    public void setPipeBlockedConnections(BlockEntity blockEntity, int blockedConnections) {
        var pipe = (PipeBlockEntity<?, ?>) blockEntity;
        for (var direction : Direction.values()) {
            pipe.setBlocked(direction, PipeBlockEntity.isFaceBlocked(blockedConnections, direction));
        }
        pipe.setBlockedConnections(blockedConnections);
    }

    @Override
    public void syncPipe(BlockEntity blockEntity) {
        var pipe = (PipeBlockEntity<?, ?>) blockEntity;
        pipe.getRootStorage().markAllDirty();
        pipe.setChanged();
        pipe.syncNow(true);
        pipe.notifyBlockUpdate();
        pipe.scheduleRenderUpdate();
    }

    @Override
    public boolean isMachineEntity(@Nullable BlockEntity blockEntity) {
        return machine(blockEntity) != null;
    }

    @Override
    public void updateMachine(BlockEntity blockEntity) {
        var machine = machine(blockEntity);
        if (machine == null) {
            return;
        }
        machine.markDirty();
        machine.notifyBlockUpdate();
        machine.scheduleRenderUpdate();
    }

    @Override
    public @Nullable GTShared.GTMachineSettings machineSettings(@Nullable BlockEntity blockEntity) {
        var machine = machine(blockEntity);
        if (machine == null) {
            return null;
        }
        GTShared.GTMachineSettings.Output items = null;
        if (machine instanceof IAutoOutputItem itemOutput && itemOutput.getOutputFacingItems() != null) {
            items = new GTShared.GTMachineSettings.Output(itemOutput.getOutputFacingItems(), itemOutput.isAutoOutputItems(),
                    itemOutput.isAllowInputFromOutputSideItems());
        }
        GTShared.GTMachineSettings.Output fluids = null;
        if (machine instanceof IAutoOutputFluid fluidOutput && fluidOutput.getOutputFacingFluids() != null) {
            fluids = new GTShared.GTMachineSettings.Output(fluidOutput.getOutputFacingFluids(), fluidOutput.isAutoOutputFluids(),
                    fluidOutput.isAllowInputFromOutputSideFluids());
        }
        var steamOutput = machine instanceof SteamWorkableMachine steam ? steam.getOutputFacing() : null;
        return new GTShared.GTMachineSettings(machine.getFrontFacing(), items, fluids, steamOutput);
    }

    @Override
    public void setFrontFacing(BlockEntity blockEntity, Direction front) {
        var machine = machine(blockEntity);
        if (machine != null) {
            machine.setFrontFacing(front);
        }
    }

    @Override
    public void setOutputSide(BlockEntity blockEntity, GTShared.GTOutputKind kind, @Nullable Direction side) {
        var machine = machine(blockEntity);
        if (kind == GTShared.GTOutputKind.ITEMS && machine instanceof IAutoOutputItem itemOutput) {
            itemOutput.setOutputFacingItems(side);
        } else if (kind == GTShared.GTOutputKind.FLUIDS && machine instanceof IAutoOutputFluid fluidOutput) {
            fluidOutput.setOutputFacingFluids(side);
        }
    }

    @Override
    public void setAutoOutput(BlockEntity blockEntity, GTShared.GTOutputKind kind, boolean autoOutput) {
        var machine = machine(blockEntity);
        if (kind == GTShared.GTOutputKind.ITEMS && machine instanceof IAutoOutputItem itemOutput) {
            itemOutput.setAutoOutputItems(autoOutput);
        } else if (kind == GTShared.GTOutputKind.FLUIDS && machine instanceof IAutoOutputFluid fluidOutput) {
            fluidOutput.setAutoOutputFluids(autoOutput);
        }
    }

    @Override
    public void setInputFromOutputSide(BlockEntity blockEntity, GTShared.GTOutputKind kind, boolean allow) {
        var machine = machine(blockEntity);
        if (kind == GTShared.GTOutputKind.ITEMS && machine instanceof IAutoOutputItem itemOutput) {
            itemOutput.setAllowInputFromOutputSideItems(allow);
        } else if (kind == GTShared.GTOutputKind.FLUIDS && machine instanceof IAutoOutputFluid fluidOutput) {
            fluidOutput.setAllowInputFromOutputSideFluids(allow);
        }
    }

    @Override
    public void setSteamOutput(BlockEntity blockEntity, Direction side) {
        if (machine(blockEntity) instanceof SteamWorkableMachine steam) {
            steam.setOutputFacing(side);
        }
    }

    @Override
    public boolean savesContentsInDrop(@Nullable BlockEntity blockEntity) {
        return machine(blockEntity) instanceof IDropSaveMachine saving && saving.saveBreak();
    }

    @Override
    public @Nullable Boolean muffled(@Nullable BlockEntity blockEntity) {
        return machine(blockEntity) instanceof IMufflableMachine mufflable ? mufflable.isMuffled() : null;
    }

    @Override
    public void setMuffled(BlockEntity blockEntity, boolean muffled) {
        if (machine(blockEntity) instanceof IMufflableMachine mufflable) {
            mufflable.setMuffled(muffled);
        }
    }

    @Override
    public @Nullable ItemStack circuit(@Nullable BlockEntity blockEntity) {
        return machine(blockEntity) instanceof IHasCircuitSlot circuitMachine && circuitMachine.isCircuitSlotEnabled()
                ? circuitMachine.getCircuitInventory().getStackInSlot(0)
                : null;
    }

    @Override
    public int circuitConfiguration(ItemStack circuit) {
        return IntCircuitBehaviour.getCircuitConfiguration(circuit);
    }

    @Override
    public boolean setCircuit(@Nullable BlockEntity blockEntity, int configuration) {
        if (!(machine(blockEntity) instanceof IHasCircuitSlot circuitMachine)) {
            return false;
        }
        circuitMachine.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(configuration));
        return true;
    }

    @Override
    public int unpaintedColor() {
        return IPaintable.UNPAINTED_COLOR;
    }

    @Override
    public @Nullable Integer paintingColor(@Nullable BlockEntity blockEntity) {
        var paintable = paintable(blockEntity);
        return paintable == null ? null : paintable.getPaintingColor();
    }

    @Override
    public boolean setPaintingColor(@Nullable BlockEntity blockEntity, int color) {
        var paintable = paintable(blockEntity);
        if (paintable == null) {
            return false;
        }
        paintable.setPaintingColor(color);
        return true;
    }

    @Override
    public boolean isCoverable(@Nullable BlockEntity blockEntity) {
        return coverable(blockEntity) != null;
    }

    @Override
    public @Nullable GTShared.GTCover cover(BlockEntity blockEntity, Direction side) {
        var coverable = coverable(blockEntity);
        var cover = coverable == null ? null : coverable.getCoverAtSide(side);
        if (cover == null) {
            return null;
        }
        return new GTShared.GTCover(cover.attachedSide, GTRegistries.COVERS.getKey(cover.coverDefinition), cover.getAttachItem(),
                IManagedAccessor.readManagedFields(cover, new CompoundTag()), cover.getAdditionalDrops());
    }

    @Override
    public boolean hasCoverDefinition(ResourceLocation id) {
        return GTRegistries.COVERS.get(id) != null;
    }

    @Override
    public boolean placeCover(
            BlockEntity blockEntity,
            Direction side,
            ResourceLocation id,
            ItemStack item,
            @Nullable ServerPlayer player,
            CompoundTag data
    ) {
        var coverable = coverable(blockEntity);
        var definition = GTRegistries.COVERS.get(id);
        if (coverable == null || definition == null || !coverable.placeCoverOnSide(side, item, definition, player)) {
            return false;
        }
        var cover = coverable.getCoverAtSide(side);
        if (cover != null) {
            IManagedAccessor.writePersistedFields(data, cover.getSyncStorage().getPersistedFields());
            cover.onChanged();
            cover.scheduleRenderUpdate();
        }
        return true;
    }

    @Override
    public void coversChanged(BlockEntity blockEntity) {
        var coverable = coverable(blockEntity);
        if (coverable != null) {
            coverable.notifyBlockUpdate();
            coverable.scheduleNeighborShapeUpdate();
        }
    }

    @Override
    public @Nullable GTShared.GTMaintenance maintenance(@Nullable BlockEntity blockEntity) {
        if (!(machine(blockEntity) instanceof MaintenanceHatchPartMachine hatch)) {
            return null;
        }
        var tape = ((AccessorMaintenanceHatchPartMachine) hatch).getItemStackHandler().getStackInSlot(0);
        return new GTShared.GTMaintenance(tape, hatch.isConfigurable(), hatch.getDurationMultiplier());
    }

    @Override
    public void setDurationMultiplier(BlockEntity blockEntity, float multiplier) {
        ((AccessorMaintenanceHatchPartMachine) machine(blockEntity)).setDurationMultiplier(multiplier);
    }

    @Override
    public ItemStack insertTape(BlockEntity blockEntity, ItemStack tape) {
        return ((AccessorMaintenanceHatchPartMachine) machine(blockEntity)).getItemStackHandler().insertItem(0, tape, false);
    }

    @Override
    public CompoundTag transformMachineNbt(CompoundTag blockEntity, DirectionTransform transform) {
        return GTNbtTransform.transformOutputFacings(blockEntity, transform);
    }

    @Override
    public CompoundTag transformCoverNbt(CompoundTag blockEntity, DirectionTransform transform) {
        return GTNbtTransform.transformCovers(blockEntity, transform);
    }

    private static @Nullable MetaMachine machine(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof MetaMachineBlockEntity machineBlockEntity ? machineBlockEntity.getMetaMachine() : null;
    }

    private static @Nullable ICoverable coverable(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof PipeBlockEntity<?, ?> pipe) {
            return pipe.getCoverContainer();
        }
        var machine = machine(blockEntity);
        return machine == null ? null : machine.getCoverContainer();
    }

    private static @Nullable IPaintable paintable(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof IPaintable paintable) {
            return paintable;
        }
        return machine(blockEntity);
    }
}
