package lu.kolja.matter_manipulator.common.integration.gtceu.v8;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.steam.SteamWorkableMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import lu.kolja.matter_manipulator.api.DirectionTransform;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTCoverData;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTShared;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTNbtTransform;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTAccess;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public final class GTV8Access implements GTAccess {

    @Override
    public String name() {
        return "GTM V8";
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
        return upwardsFacing;
    }

    @Override
    public Direction upwardsFacing(Direction front, Direction top) {
        if (top.getAxis() == front.getAxis()) {
            throw new IllegalArgumentException("No upwards facing for front " + front + " and top " + top);
        }
        return top;
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
        pipe.getSyncDataHolder().resyncAllFields();
        pipe.setChanged();
        pipe.notifyBlockUpdate();
        pipe.scheduleRenderUpdate();
    }

    @Override
    public boolean isMachineEntity(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof MetaMachine;
    }

    @Override
    public void updateMachine(BlockEntity blockEntity) {
        if (!(blockEntity instanceof MetaMachine machine)) {
            return;
        }
        machine.setChanged();
        machine.markAsChanged();
        machine.notifyBlockUpdate();
        machine.scheduleRenderUpdate();
    }

    @Override
    public @Nullable GTShared.GTMachineSettings machineSettings(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof MetaMachine machine)) {
            return null;
        }
        var output = machine.getTrait(AutoOutputTrait.class);
        GTShared.GTMachineSettings.Output items = null;
        GTShared.GTMachineSettings.Output fluids = null;
        if (output != null && output.supportsAutoOutputItems() && output.getItemOutputDirection() != null) {
            items = new GTShared.GTMachineSettings.Output(output.getItemOutputDirection(), output.isAutoOutputItems(),
                    output.allowsItemInputFromOutputSide());
        }
        if (output != null && output.supportsAutoOutputFluids() && output.getFluidOutputDirection() != null) {
            fluids = new GTShared.GTMachineSettings.Output(output.getFluidOutputDirection(), output.isAutoOutputFluids(),
                    output.allowsFluidInputFromOutputSide());
        }
        var steamOutput = machine instanceof SteamWorkableMachine steam ? steam.getOutputFacing() : null;
        return new GTShared.GTMachineSettings(machine.getFrontFacing(), items, fluids, steamOutput);
    }

    @Override
    public void setFrontFacing(BlockEntity blockEntity, Direction front) {
        if (blockEntity instanceof MetaMachine machine) {
            machine.setFrontFacing(front);
        }
    }

    @Override
    public void setOutputSide(BlockEntity blockEntity, GTShared.GTOutputKind kind, @Nullable Direction side) {
        var output = autoOutput(blockEntity);
        if (output == null) {
            return;
        }
        if (kind == GTShared.GTOutputKind.ITEMS) {
            output.setItemOutputDirection(side);
        } else {
            output.setFluidOutputDirection(side);
        }
    }

    @Override
    public void setAutoOutput(BlockEntity blockEntity, GTShared.GTOutputKind kind, boolean autoOutput) {
        var output = autoOutput(blockEntity);
        if (output == null) {
            return;
        }
        if (kind == GTShared.GTOutputKind.ITEMS) {
            output.setAllowAutoOutputItems(autoOutput);
        } else {
            output.setAllowAutoOutputFluids(autoOutput);
        }
    }

    @Override
    public void setInputFromOutputSide(BlockEntity blockEntity, GTShared.GTOutputKind kind, boolean allow) {
        var output = autoOutput(blockEntity);
        if (output == null) {
            return;
        }
        if (kind == GTShared.GTOutputKind.ITEMS && output.supportsAutoOutputItems()) {
            output.setAllowItemInputFromOutputSide(allow);
        } else if (kind == GTShared.GTOutputKind.FLUIDS && output.supportsAutoOutputFluids()) {
            output.setAllowFluidInputFromOutputSide(allow);
        }
    }

    @Override
    public void setSteamOutput(BlockEntity blockEntity, Direction side) {
        if (blockEntity instanceof SteamWorkableMachine steam) {
            steam.setOutputFacing(side);
        }
    }

    @Override
    public boolean savesContentsInDrop(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof MetaMachine;
    }

    @Override
    public @Nullable Boolean muffled(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof IMufflableMachine mufflable ? mufflable.isMuffled() : null;
    }

    @Override
    public void setMuffled(BlockEntity blockEntity, boolean muffled) {
        if (blockEntity instanceof IMufflableMachine mufflable) {
            mufflable.setMuffled(muffled);
        }
    }

    @Override
    public @Nullable ItemStack circuit(@Nullable BlockEntity blockEntity) {
        var circuit = blockEntity instanceof MetaMachine machine ? machine.getTrait(ProgrammableCircuitSlotTrait.class) : null;
        return circuit != null && circuit.isEnabled() ? circuit.storage.getStackInSlot(0) : null;
    }

    @Override
    public int circuitConfiguration(ItemStack circuit) {
        return IntCircuitBehaviour.getCircuitConfiguration(circuit);
    }

    @Override
    public boolean setCircuit(@Nullable BlockEntity blockEntity, int configuration) {
        var circuit = blockEntity instanceof MetaMachine machine ? machine.getTrait(ProgrammableCircuitSlotTrait.class) : null;
        if (circuit == null) {
            return false;
        }
        circuit.storage.setStackInSlot(0, IntCircuitBehaviour.stack(configuration));
        return true;
    }

    @Override
    public int unpaintedColor() {
        return IPaintable.UNPAINTED_COLOR;
    }

    @Override
    public @Nullable Integer paintingColor(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof IPaintable paintable ? paintable.getPaintingColor() : null;
    }

    @Override
    public boolean setPaintingColor(@Nullable BlockEntity blockEntity, int color) {
        if (!(blockEntity instanceof IPaintable paintable)) {
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
        var data = GTCoverData.fromV8(cover.getSyncDataHolder().serializeNBT(lookup(blockEntity)));
        return new GTShared.GTCover(cover.attachedSide, GTRegistries.COVERS.getKey(cover.coverDefinition), cover.getAttachItem(),
                data, cover.getAdditionalDrops());
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
            cover.getSyncDataHolder().deserializeNBT(lookup(blockEntity), data);
            cover.getSyncDataHolder().resyncAllFields();
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
        if (!(blockEntity instanceof MaintenanceHatchPartMachine hatch)) {
            return null;
        }
        return new GTShared.GTMaintenance(tapeSlot(hatch).storage.getStackInSlot(0), hatch.isConfigurable(),
                hatch.getDurationMultiplier());
    }

    @Override
    public void setDurationMultiplier(BlockEntity blockEntity, float multiplier) {
        ((MaintenanceHatchPartMachine) blockEntity).setDurationMultiplier(multiplier);
    }

    @Override
    public ItemStack insertTape(BlockEntity blockEntity, ItemStack tape) {
        return tapeSlot((MaintenanceHatchPartMachine) blockEntity).storage.insertItem(0, tape, false);
    }

    @Override
    public CompoundTag transformMachineNbt(CompoundTag blockEntity, DirectionTransform transform) {
        return GTNbtTransform.transformOutputFacingsV8(blockEntity, transform);
    }

    @Override
    public CompoundTag transformCoverNbt(CompoundTag blockEntity, DirectionTransform transform) {
        return GTNbtTransform.transformCoversV8(blockEntity, transform);
    }

    private static @Nullable AutoOutputTrait autoOutput(BlockEntity blockEntity) {
        return blockEntity instanceof MetaMachine machine ? machine.getTrait(AutoOutputTrait.class) : null;
    }

    private static NotifiableItemStackHandler tapeSlot(MaintenanceHatchPartMachine hatch) {
        return hatch.getTraitOrThrow(NotifiableItemStackHandler.class);
    }

    private static @Nullable ICoverable coverable(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof PipeBlockEntity<?, ?> pipe) {
            return pipe.getCoverContainer();
        }
        return blockEntity instanceof MetaMachine machine ? machine.getCoverContainer() : null;
    }

    private static HolderLookup.Provider lookup(BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        return level == null ? GTRegistries.builtinRegistry() : level.registryAccess();
    }
}
