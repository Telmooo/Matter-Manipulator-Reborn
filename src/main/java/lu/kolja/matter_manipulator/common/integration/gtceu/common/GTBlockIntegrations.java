package lu.kolja.matter_manipulator.common.integration.gtceu.common;

import lu.kolja.matter_manipulator.api.BlockIntegration;
import lu.kolja.matter_manipulator.api.CableIntegration;
import lu.kolja.matter_manipulator.api.DirectionTransform;
import lu.kolja.matter_manipulator.api.IntegrationContext;
import lu.kolja.matter_manipulator.api.IntegrationRegistrar;
import lu.kolja.matter_manipulator.api.ItemRequirement;
import lu.kolja.matter_manipulator.api.PlacementPreparationPlan;
import lu.kolja.matter_manipulator.api.PlacementPurpose;
import lu.kolja.matter_manipulator.common.integration.gtceu.GTIntegration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GTBlockIntegrations {

    private static final String PIPE_CONNECTIONS = "pipe_connections";
    private static final String PIPE_BLOCKED_CONNECTIONS = "pipe_blocked_connections";
    private static final String PAINTING_COLOR = "painting_color";
    private static final String FACING_DIR = "front_facing";
    private static final String ITEM_OUTPUT_SIDE = "output_direction_item";
    private static final String ITEM_AUTO_OUTPUT = "item_auto_output";
    private static final String ALLOW_ITEM_IN_FROM_OUT = "allow_input_from_output_item";
    private static final String FLUID_OUTPUT_SIDE = "output_direction_fluid";
    private static final String FLUID_AUTO_OUTPUT = "fluid_auto_output";
    private static final String ALLOW_FLUID_IN_FROM_OUT = "allow_input_from_output_fluid";
    private static final String STEAM_OUTPUT_SIDE = "output_direction_steam";
    private static final String MUFFLED = "muffled";
    private static final String CIRCUIT = "circuit_config";
    private static final String NONE_DIRECTION = "null";
    private static final String COVERS = "covers";
    private static final String COVER_ID_TAG = "id";
    private static final String COVER_ITEM = "item";
    private static final String COVER_DATA = "data";
    private static final String COVER_ADDITIONAL = "additional";
    private static final String CIRCUIT_ITEM = "circuit_item";
    private static final String MAINTENANCE_TAPE = "maintenance_tape";
    private static final String DURATION_MULTIPLIER = "duration_multiplier";
    private static final String FUNDED_ITEM = "_funded_item";
    private static final String FUNDED_ITEMS = "_funded_items";

    private GTBlockIntegrations() {}

    public static void register(IntegrationRegistrar registrar, GTAccess access) {
        var pipe = new PipeIntegration(access);
        registrar.block(GTIntegration.PIPE_ID, pipe, access::isPipe);
        registrar.cable(GTIntegration.PIPE_ID, pipe, access::isPipe);
        registrar.block(GTIntegration.MACHINE_ID, new MachineIntegration(access), GTBlockIntegrations::isGtceuBlock);
        registrar.block(GTIntegration.MUFFLER_ID, new MufflerIntegration(access), GTBlockIntegrations::isGtceuBlock);
        registrar.block(GTIntegration.CIRCUIT_ID, new CircuitIntegration(access), GTBlockIntegrations::isGtceuBlock);
        registrar.block(GTIntegration.PAINT_ID, new PaintIntegration(access), GTBlockIntegrations::isGtceuBlock);
        registrar.block(GTIntegration.COVER_ID, new CoverIntegration(access), GTBlockIntegrations::isGtceuBlock);
        registrar.block(GTIntegration.MAINTENANCE_ID, new MaintenanceHatchIntegration(access),
                GTBlockIntegrations::isGtceuBlock);
    }

    private static boolean isGtceuBlock(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("gtceu");
    }

    private record PipeIntegration(GTAccess access) implements BlockIntegration, CableIntegration {

        @Override
            public CompoundTag capture(IntegrationContext.Capture context) {
                return capturePipe(access, context.blockEntity());
            }

            @Override
            public CompoundTag captureExchange(IntegrationContext.Exchange context) {
                return capturePipe(access, context.blockEntity());
            }

            @Override
            public ApplyResult apply(IntegrationContext.Apply context, CompoundTag payload) {
                var blockEntity = context.blockEntity();
                var pipe = access.pipe(blockEntity);
                if (pipe == null) {
                    return ApplyResult.REJECTED_UNCHANGED;
                }
                if (context.purpose() == PlacementPurpose.MOVE_TRANSFER) {
                    return pipe.connections() == payload.getInt(PIPE_CONNECTIONS)
                        && pipe.blockedConnections() == payload.getInt(PIPE_BLOCKED_CONNECTIONS)
                        ? ApplyResult.APPLIED
                        : ApplyResult.REJECTED_UNCHANGED;
                }
                if (payload.contains(PIPE_CONNECTIONS)) {
                    access.setPipeConnections(blockEntity, payload.getInt(PIPE_CONNECTIONS));
                }
                if (payload.contains(PIPE_BLOCKED_CONNECTIONS)) {
                    access.setPipeBlockedConnections(blockEntity, payload.getInt(PIPE_BLOCKED_CONNECTIONS));
                }
                context.afterTick(() -> finalizePipeConnections(access, blockEntity));
                return ApplyResult.APPLIED;
            }

            @Override
            public CompoundTag transform(IntegrationContext.Transform context, CompoundTag payload) {
                var copy = payload.copy();
                if (copy.contains(PIPE_CONNECTIONS)) {
                    copy.putInt(PIPE_CONNECTIONS,
                        GTNbtTransform.transformDirectionMask(copy.getInt(PIPE_CONNECTIONS), context.transform()));
                }
                if (copy.contains(PIPE_BLOCKED_CONNECTIONS)) {
                    copy.putInt(
                        PIPE_BLOCKED_CONNECTIONS,
                        GTNbtTransform.transformDirectionMask(copy.getInt(PIPE_BLOCKED_CONNECTIONS), context.transform())
                    );
                }
                return copy;
            }

            @Override
            public CompoundTag transformBlockEntity(IntegrationContext.Transform context, CompoundTag blockEntity) {
                return GTNbtTransform.transformPipeConnections(blockEntity, context.transform());
            }

            @Override
            public CablePlacementData prepare(IntegrationContext.Cable context) {
                var tag = capturePipe(access, context.blockEntity());
                int connections = tag.getInt(PIPE_CONNECTIONS);
                int blockedConnections = tag.getInt(PIPE_BLOCKED_CONNECTIONS);
                for (var direction : context.lineDirections()) {
                    int sideMask = 1 << direction.ordinal();
                    connections |= sideMask;
                    blockedConnections &= ~sideMask;
                }
                if (connections != 0) {
                    tag.putInt(PIPE_CONNECTIONS, connections);
                }
                if (tag.contains(PIPE_BLOCKED_CONNECTIONS)) {
                    tag.putInt(PIPE_BLOCKED_CONNECTIONS, blockedConnections);
                }
                return CablePlacementData.afterPath(tag);
            }

            @Override
            public boolean canReuse(IntegrationContext.CableReuse context) {
                return context.existing().getBlock() == context.target().getBlock();
            }

            @Override
            public boolean canReconfigure(IntegrationContext.Placement context, CompoundTag existing, CompoundTag desired) {
                return true;
            }

            @Override
            public Set<BlockPos> additionalChangedPositions(IntegrationContext.SideEffects context, CompoundTag payload) {
                var kind = access.pipeKind(context.state());
                if (!payload.contains(PIPE_CONNECTIONS) || kind == null) {
                    return Set.of();
                }
                var live = access.pipe(context.blockEntity());
                if (context.purpose() == PlacementPurpose.RECONFIGURE && live == null) {
                    return Set.of();
                }
                int connections = payload.getInt(PIPE_CONNECTIONS);
                var neighbours = new HashSet<BlockPos>();
                for (var side : Direction.values()) {
                    boolean opens = (connections & GTShared.GTPipe.sideMask(side)) != 0;
                    if ((live != null && live.isConnected(side)) == opens) {
                        continue;
                    }
                    var neighbourPos = context.pos().relative(side);
                    if (!context.level().isLoaded(neighbourPos)) {
                        neighbours.add(neighbourPos);
                        continue;
                    }
                    var neighbour = access.pipe(context.level().getBlockEntity(neighbourPos));
                    if (neighbour == null) {
                        continue;
                    }
                    var facing = side.getOpposite();
                    boolean changes = opens
                        ? neighbour.kind().equals(kind) && !neighbour.isConnected(facing)
                        : !live.passesThrough(side) && neighbour.isConnected(facing) && !neighbour.hasCover(facing);
                    if (changes) {
                        neighbours.add(neighbourPos);
                    }
                }
                return neighbours;
            }
        }

    private record MachineIntegration(GTAccess access) implements BlockIntegration {

        @Override
            public boolean dropCarriesContents(IntegrationContext.Removal context, ItemStack drop) {
                return access.savesContentsInDrop(context.blockEntity())
                    && drop.is(context.state().getBlock().asItem())
                    && drop.hasTag();
            }

            @Override
            public CompoundTag capture(IntegrationContext.Capture context) {
                var tag = new CompoundTag();
                var settings = access.machineSettings(context.blockEntity());
                if (settings == null) {
                    return tag;
                }
                tag.putString(FACING_DIR, directionToString(settings.front()));
                var items = settings.items();
                if (items != null) {
                    tag.putString(ITEM_OUTPUT_SIDE, directionToString(items.side()));
                    tag.putBoolean(ITEM_AUTO_OUTPUT, items.autoOutput());
                    tag.putBoolean(ALLOW_ITEM_IN_FROM_OUT, items.inputFromOutputSide());
                }
                var fluids = settings.fluids();
                if (fluids != null) {
                    tag.putString(FLUID_OUTPUT_SIDE, directionToString(fluids.side()));
                    tag.putBoolean(FLUID_AUTO_OUTPUT, fluids.autoOutput());
                    tag.putBoolean(ALLOW_FLUID_IN_FROM_OUT, fluids.inputFromOutputSide());
                }
                if (settings.steamOutput() != null) {
                    tag.putString(STEAM_OUTPUT_SIDE, directionToString(settings.steamOutput()));
                }
                return tag;
            }

            @Override
            public ApplyResult apply(IntegrationContext.Apply context, CompoundTag tag) {
                var machine = context.blockEntity();
                if (!access.isMachineEntity(machine)) {
                    return ApplyResult.REJECTED_UNCHANGED;
                }
                if (tag.contains(FACING_DIR)) {
                    var direction = stringToDirection(tag.getString(FACING_DIR));
                    if (direction != null) {
                        access.setFrontFacing(machine, direction);
                    }
                }
                if (tag.contains(ITEM_OUTPUT_SIDE)) {
                    access.setOutputSide(machine, GTShared.GTOutputKind.ITEMS, stringToDirection(tag.getString(ITEM_OUTPUT_SIDE)));
                }
                if (tag.contains(ITEM_AUTO_OUTPUT)) {
                    access.setAutoOutput(machine, GTShared.GTOutputKind.ITEMS, tag.getBoolean(ITEM_AUTO_OUTPUT));
                }
                if (tag.contains(ALLOW_ITEM_IN_FROM_OUT)) {
                    access.setInputFromOutputSide(machine, GTShared.GTOutputKind.ITEMS, tag.getBoolean(ALLOW_ITEM_IN_FROM_OUT));
                }
                if (tag.contains(FLUID_OUTPUT_SIDE)) {
                    access.setOutputSide(machine, GTShared.GTOutputKind.FLUIDS, stringToDirection(tag.getString(FLUID_OUTPUT_SIDE)));
                }
                if (tag.contains(FLUID_AUTO_OUTPUT)) {
                    access.setAutoOutput(machine, GTShared.GTOutputKind.FLUIDS, tag.getBoolean(FLUID_AUTO_OUTPUT));
                }
                if (tag.contains(ALLOW_FLUID_IN_FROM_OUT)) {
                    access.setInputFromOutputSide(machine, GTShared.GTOutputKind.FLUIDS, tag.getBoolean(ALLOW_FLUID_IN_FROM_OUT));
                }
                if (tag.contains(STEAM_OUTPUT_SIDE)) {
                    var side = stringToDirection(tag.getString(STEAM_OUTPUT_SIDE));
                    if (side != null) {
                        access.setSteamOutput(machine, side);
                    }
                }
                access.updateMachine(machine);
                return ApplyResult.APPLIED;
            }

            @Override
            public CompoundTag transform(IntegrationContext.Transform context, CompoundTag payload) {
                var copy = payload.copy();
                transformDirectionString(copy, context.transform(), FACING_DIR);
                transformDirectionString(copy, context.transform(), ITEM_OUTPUT_SIDE);
                transformDirectionString(copy, context.transform(), FLUID_OUTPUT_SIDE);
                transformDirectionString(copy, context.transform(), STEAM_OUTPUT_SIDE);
                return copy;
            }

            @Override
            public @Nullable BlockState transformState(IntegrationContext.Transform context, @Nullable BlockState transformed) {
                var state = context.state();
                var frontProperty = access.frontProperty(state);
                if (frontProperty == null) {
                    return transformed;
                }
                var front = state.getValue(frontProperty);
                var newFront = context.transform().transform(front);
                if (!frontProperty.getPossibleValues().contains(newFront)) {
                    return null;
                }
                var result = state.setValue(frontProperty, newFront);
                var upwardsProperty = access.upwardsProperty(state);
                if (upwardsProperty == null) {
                    return result;
                }
                var top = access.top(front, state.getValue(upwardsProperty));
                var upwards = access.upwardsFacing(newFront, context.transform().transform(top));
                return result.setValue(upwardsProperty, upwards);
            }

            @Override
            public BlockState storedState(BlockState state) {
                var frontProperty = access.frontProperty(state);
                var upwardsProperty = access.upwardsProperty(state);
                if (frontProperty == null || upwardsProperty == null) {
                    return state;
                }
                var front = state.getValue(frontProperty);
                var stored = GTUpwardsFacing.relative(front, access.top(front, state.getValue(upwardsProperty)));
                return stored == null || !upwardsProperty.getPossibleValues().contains(stored)
                    ? state
                    : state.setValue(upwardsProperty, stored);
            }

            @Override
            public BlockState loadedState(BlockState stored) {
                var frontProperty = access.frontProperty(stored);
                var upwardsProperty = access.upwardsProperty(stored);
                if (frontProperty == null || upwardsProperty == null) {
                    return stored;
                }
                var front = stored.getValue(frontProperty);
                var top = GTUpwardsFacing.top(front, stored.getValue(upwardsProperty));
                if (top.getAxis() == front.getAxis()) {
                    return stored;
                }
                var upwards = access.upwardsFacing(front, top);
                return upwardsProperty.getPossibleValues().contains(upwards) ? stored.setValue(upwardsProperty, upwards) : stored;
            }

            @Override
            public CompoundTag transformBlockEntity(IntegrationContext.Transform context, CompoundTag blockEntity) {
                return transformsBlockEntity(context.state())
                    ? access.transformMachineNbt(blockEntity, context.transform())
                    : blockEntity;
            }

            @Override
            public boolean transformsBlockEntity(BlockState state) {
                return access.isMachine(state);
            }

            @Override
            public boolean canReconfigure(IntegrationContext.Placement context, CompoundTag existing, CompoundTag desired) {
                return true;
            }
        }

    private record MufflerIntegration(GTAccess access) implements BlockIntegration {

        @Override
            public boolean transformsBlockEntity(BlockState state) {
                return false;
            }

            @Override
            public CompoundTag capture(IntegrationContext.Capture context) {
                var tag = new CompoundTag();
                var muffled = access.muffled(context.blockEntity());
                if (muffled != null) {
                    tag.putBoolean(MUFFLED, muffled);
                }
                return tag;
            }

            @Override
            public ApplyResult apply(IntegrationContext.Apply context, CompoundTag payload) {
                var machine = context.blockEntity();
                if (payload.contains(MUFFLED) && access.muffled(machine) != null) {
                    access.setMuffled(machine, payload.getBoolean(MUFFLED));
                    access.updateMachine(machine);
                }
                return ApplyResult.APPLIED;
            }

            @Override
            public boolean canReconfigure(IntegrationContext.Placement context, CompoundTag existing, CompoundTag desired) {
                return true;
            }
        }

    private record CoverIntegration(GTAccess access) implements BlockIntegration {

        @Override
            public CompoundTag capture(IntegrationContext.Capture context) {
                var blockEntity = context.blockEntity();
                var output = new CompoundTag();
                if (!access.isCoverable(blockEntity)) {
                    return output;
                }
                var covers = new CompoundTag();
                for (var side : Direction.values()) {
                    var cover = access.cover(blockEntity, side);
                    if (cover == null || cover.id() == null) {
                        continue;
                    }
                    var coverTag = new CompoundTag();
                    coverTag.putString(COVER_ID_TAG, cover.id().toString());
                    coverTag.put(COVER_ITEM, cover.item().save(new CompoundTag()));
                    coverTag.put(COVER_DATA, cover.data());
                    var additional = new ListTag();
                    cover.additionalDrops().forEach(stack -> additional.add(stack.save(new CompoundTag())));
                    coverTag.put(COVER_ADDITIONAL, additional);
                    covers.put(side.getName(), coverTag);
                }
                if (!covers.isEmpty()) {
                    output.put(COVERS, covers);
                }
                return output;
            }

            @Override
            public PlacementPreparationPlan preparation(IntegrationContext.Preparation context, CompoundTag payload) {
                var output = payload.copy();
                output.put(COVERS, new CompoundTag());
                var plan = PlacementPreparationPlan.builder(output);
                var covers = payload.getCompound(COVERS);
                for (var sideName : covers.getAllKeys()) {
                    var cover = covers.getCompound(sideName).copy();
                    var coverItem = ItemStack.of(cover.getCompound(COVER_ITEM));
                    var parent = plan.component(ItemRequirement.exact(coverItem), (prepared, acquired) -> {
                        if (!acquired.isEmpty()) {
                            var funded = cover.copy();
                            var items = new ListTag();
                            items.add(acquired.save(new CompoundTag()));
                            funded.put(FUNDED_ITEMS, items);
                            prepared.getCompound(COVERS).put(sideName, funded);
                        }
                    });
                    for (var value : cover.getList(COVER_ADDITIONAL, Tag.TAG_COMPOUND)) {
                        var configured = ItemStack.of((CompoundTag) value);
                        var base = new ItemStack(configured.getItem(), configured.getCount());
                        parent.component(ItemRequirement.withFallback(configured, base), (prepared, acquired) -> {
                            var funded = prepared.getCompound(COVERS).getCompound(sideName);
                            if (acquired.isEmpty()) {
                                removeStackFromTag(funded.getCompound(COVER_DATA), configured);
                            } else {
                                funded.getList(FUNDED_ITEMS, Tag.TAG_COMPOUND).add(acquired.save(new CompoundTag()));
                            }
                        });
                    }
                }
                return plan.build();
            }

            @Override
            public ApplyResult apply(IntegrationContext.Apply context, CompoundTag payload) {
                var blockEntity = context.blockEntity();
                if (!access.isCoverable(blockEntity) || context.purpose() == PlacementPurpose.RECONFIGURE) {
                    return ApplyResult.REJECTED_UNCHANGED;
                }
                if (context.purpose() == PlacementPurpose.MOVE_TRANSFER) {
                    return matchesTransferred(blockEntity, payload) ? ApplyResult.APPLIED : ApplyResult.REJECTED_UNCHANGED;
                }
                boolean partiallyApplied = false;
                var covers = payload.getCompound(COVERS);
                for (var sideName : covers.getAllKeys()) {
                    var side = Direction.byName(sideName);
                    var coverTag = covers.getCompound(sideName);
                    var id = ResourceLocation.tryParse(coverTag.getString(COVER_ID_TAG));
                    if (side == null || id == null || !access.hasCoverDefinition(id)) {
                        refundFunded(context, coverTag);
                        partiallyApplied = true;
                        continue;
                    }
                    var stack = ItemStack.of(coverTag.getCompound(COVER_ITEM));
                    if (!access.placeCover(blockEntity, side, id, stack, context.player(), coverTag.getCompound(COVER_DATA))) {
                        refundFunded(context, coverTag);
                        partiallyApplied = true;
                    }
                }
                access.coversChanged(blockEntity);
                if (access.isMachineEntity(blockEntity)) {
                    access.updateMachine(blockEntity);
                } else if (access.pipe(blockEntity) != null) {
                    context.afterTick(() -> finalizePipeConnections(access, blockEntity));
                } else if (blockEntity != null) {
                    blockEntity.setChanged();
                    context.level().sendBlockUpdated(
                        context.pos(),
                        context.state(),
                        context.state(),
                        Block.UPDATE_CLIENTS
                    );
                }
                return partiallyApplied ? ApplyResult.PARTIAL_RESOURCES_SETTLED : ApplyResult.APPLIED;
            }

            @Override
            public CompoundTag transform(IntegrationContext.Transform context, CompoundTag payload) {
                var output = payload.copy();
                var transformed = new CompoundTag();
                var covers = payload.getCompound(COVERS);
                for (var sideName : covers.getAllKeys()) {
                    var side = Direction.byName(sideName);
                    if (side != null) {
                        var cover = covers.getCompound(sideName).copy();
                        cover.put(COVER_DATA, GTNbtTransform.transformCoverData(
                            cover.getString(COVER_ID_TAG), cover.getCompound(COVER_DATA), context.transform()));
                        transformed.put(context.transform().transform(side).getName(), cover);
                    }
                }
                output.put(COVERS, transformed);
                return output;
            }

            @Override
            public CompoundTag transformBlockEntity(IntegrationContext.Transform context, CompoundTag blockEntity) {
                return transformsBlockEntity(context.state())
                    ? access.transformCoverNbt(blockEntity, context.transform())
                    : blockEntity;
            }

            @Override
            public boolean transformsBlockEntity(BlockState state) {
                return access.isMachine(state) || access.isPipe(state);
            }

            private boolean matchesTransferred(BlockEntity blockEntity, CompoundTag payload) {
                var covers = payload.getCompound(COVERS);
                for (var side : Direction.values()) {
                    var cover = access.cover(blockEntity, side);
                    if (!covers.contains(side.getName(), Tag.TAG_COMPOUND)) {
                        if (cover != null) {
                            return false;
                        }
                        continue;
                    }
                    var expected = covers.getCompound(side.getName());
                    if (cover == null || cover.attachedSide() != side) {
                        return false;
                    }
                    if (cover.id() == null || !cover.id().toString().equals(expected.getString(COVER_ID_TAG))
                        || !cover.data().equals(expected.getCompound(COVER_DATA))) {
                        return false;
                    }
                }
                return true;
            }
        }

    private record CircuitIntegration(GTAccess access) implements BlockIntegration {

        @Override
            public boolean transformsBlockEntity(BlockState state) {
                return false;
            }

            @Override
            public CompoundTag capture(IntegrationContext.Capture context) {
                var tag = new CompoundTag();
                var circuitStack = access.circuit(context.blockEntity());
                if (circuitStack == null) {
                    return tag;
                }
                int circuit = access.circuitConfiguration(circuitStack);
                if (circuit != 0) {
                    tag.putInt(CIRCUIT, circuit);
                    tag.put(CIRCUIT_ITEM, circuitStack.save(new CompoundTag()));
                }
                return tag;
            }

            @Override
            public PlacementPreparationPlan preparation(IntegrationContext.Preparation context, CompoundTag payload) {
                if (!payload.contains(CIRCUIT_ITEM, Tag.TAG_COMPOUND)) {
                    payload.remove(CIRCUIT);
                    return PlacementPreparationPlan.unchanged(payload);
                }
                var configured = ItemStack.of(payload.getCompound(CIRCUIT_ITEM));
                var base = new ItemStack(configured.getItem(), configured.getCount());
                var plan = PlacementPreparationPlan.builder(payload);
                plan.component(ItemRequirement.withFallback(configured, base), (prepared, acquired) -> {
                    if (acquired.isEmpty()) {
                        prepared.remove(CIRCUIT);
                        prepared.remove(CIRCUIT_ITEM);
                    } else {
                        prepared.put(FUNDED_ITEM, acquired.save(new CompoundTag()));
                    }
                });
                return plan.build();
            }

            @Override
            public ApplyResult apply(IntegrationContext.Apply context, CompoundTag payload) {
                if (context.purpose() == PlacementPurpose.RECONFIGURE) {
                    return ApplyResult.REJECTED_UNCHANGED;
                }
                var machine = context.blockEntity();
                if (payload.contains(CIRCUIT) && access.setCircuit(machine, payload.getInt(CIRCUIT))) {
                    access.updateMachine(machine);
                } else if (payload.contains(CIRCUIT)) {
                    context.refund(ItemStack.of(payload.getCompound(FUNDED_ITEM)));
                    return ApplyResult.PARTIAL_RESOURCES_SETTLED;
                }
                return ApplyResult.APPLIED;
            }
        }

    private record PaintIntegration(GTAccess access) implements BlockIntegration {

        @Override
            public boolean transformsBlockEntity(BlockState state) {
                return false;
            }

            @Override
            public CompoundTag capture(IntegrationContext.Capture context) {
                var tag = new CompoundTag();
                var color = access.paintingColor(context.blockEntity());
                if (color != null) {
                    tag.putInt(PAINTING_COLOR, color);
                }
                return tag;
            }

            @Override
            public CompoundTag captureExchange(IntegrationContext.Exchange context) {
                if (!access.isPipe(context.sourceState()) || !access.isPipe(context.targetState())) {
                    return new CompoundTag();
                }
                var tag = new CompoundTag();
                var color = access.paintingColor(context.blockEntity());
                if (color != null) {
                    tag.putInt(PAINTING_COLOR, color);
                }
                return tag;
            }

            @Override
            public ApplyResult apply(IntegrationContext.Apply context, CompoundTag payload) {
                var blockEntity = context.blockEntity();
                if (payload.contains(PAINTING_COLOR) && access.setPaintingColor(blockEntity, payload.getInt(PAINTING_COLOR))) {
                    blockEntity.setChanged();
                    if (access.isMachineEntity(blockEntity)) {
                        access.updateMachine(blockEntity);
                    } else if (access.pipe(blockEntity) != null) {
                        context.afterTick(() -> finalizePipeConnections(access, blockEntity));
                    }
                }
                return ApplyResult.APPLIED;
            }

            @Override
            public boolean canReconfigure(IntegrationContext.Placement context, CompoundTag existing, CompoundTag desired) {
                return true;
            }
        }

    private record MaintenanceHatchIntegration(GTAccess access) implements BlockIntegration {

        @Override
            public boolean transformsBlockEntity(BlockState state) {
                return false;
            }

            @Override
            public CompoundTag capture(IntegrationContext.Capture context) {
                var hatch = access.maintenance(context.blockEntity());
                return hatch == null ? new CompoundTag() : captureMaintenance(hatch);
            }

            @Override
            public PlacementPreparationPlan preparation(IntegrationContext.Preparation context, CompoundTag payload) {
                if (!payload.contains(MAINTENANCE_TAPE, Tag.TAG_COMPOUND)) {
                    return PlacementPreparationPlan.unchanged(payload);
                }
                var plan = PlacementPreparationPlan.builder(payload);
                plan.component(ItemRequirement.exact(ItemStack.of(payload.getCompound(MAINTENANCE_TAPE))), (prepared, acquired) -> {
                    if (acquired.isEmpty()) {
                        prepared.remove(MAINTENANCE_TAPE);
                    } else {
                        prepared.put(FUNDED_ITEM, acquired.save(new CompoundTag()));
                    }
                });
                return plan.build();
            }

            @Override
            public ApplyResult apply(IntegrationContext.Apply context, CompoundTag payload) {
                var blockEntity = context.blockEntity();
                var hatch = access.maintenance(blockEntity);
                if (hatch == null) {
                    return ApplyResult.REJECTED_UNCHANGED;
                }
                if (context.purpose() == PlacementPurpose.MOVE_TRANSFER) {
                    return captureMaintenance(hatch).equals(payload) ? ApplyResult.APPLIED : ApplyResult.REJECTED_UNCHANGED;
                }
                if (context.purpose() == PlacementPurpose.RECONFIGURE
                    && !Objects.equals(captureMaintenance(hatch).get(MAINTENANCE_TAPE), payload.get(MAINTENANCE_TAPE))) {
                    return ApplyResult.REJECTED_UNCHANGED;
                }
                if (hatch.configurable() && payload.contains(DURATION_MULTIPLIER, Tag.TAG_FLOAT)) {
                    access.setDurationMultiplier(blockEntity, Mth.clamp(payload.getFloat(DURATION_MULTIPLIER), 0.9f, 1.1f));
                }
                var result = ApplyResult.APPLIED;
                if (payload.contains(FUNDED_ITEM, Tag.TAG_COMPOUND)) {
                    var remainder = access.insertTape(blockEntity, ItemStack.of(payload.getCompound(FUNDED_ITEM)));
                    if (!remainder.isEmpty()) {
                        context.refund(remainder);
                        result = ApplyResult.PARTIAL_RESOURCES_SETTLED;
                    }
                }
                access.updateMachine(blockEntity);
                return result;
            }

            @Override
            public boolean canReconfigure(IntegrationContext.Placement context, CompoundTag existing, CompoundTag desired) {
                return Objects.equals(existing.get(MAINTENANCE_TAPE), desired.get(MAINTENANCE_TAPE));
            }
        }

    private static CompoundTag captureMaintenance(GTShared.GTMaintenance hatch) {
        var tag = new CompoundTag();
        if (!hatch.tape().isEmpty()) {
            tag.put(MAINTENANCE_TAPE, hatch.tape().save(new CompoundTag()));
        }
        if (hatch.configurable()) {
            tag.putFloat(DURATION_MULTIPLIER, hatch.durationMultiplier());
        }
        return tag;
    }

    private static CompoundTag capturePipe(GTAccess access, @Nullable BlockEntity blockEntity) {
        var tag = new CompoundTag();
        var pipe = access.pipe(blockEntity);
        if (pipe != null) {
            tag.putInt(PIPE_CONNECTIONS, pipe.connections());
            tag.putInt(PIPE_BLOCKED_CONNECTIONS, pipe.blockedConnections());
        }
        return tag;
    }

    private static void finalizePipeConnections(GTAccess access, BlockEntity pipe) {
        var level = pipe.getLevel();
        var pos = pipe.getBlockPos();
        if (level == null || pipe.isRemoved() || level.getBlockEntity(pos) != pipe) {
            return;
        }
        access.syncPipe(pipe);
        var state = pipe.getBlockState();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        for (var direction : Direction.values()) {
            level.neighborChanged(pos.relative(direction), state.getBlock(), pos);
        }
    }

    private static void refundFunded(IntegrationContext.Apply context, CompoundTag payload) {
        if (payload.contains(FUNDED_ITEM, Tag.TAG_COMPOUND)) {
            context.refund(ItemStack.of(payload.getCompound(FUNDED_ITEM)));
        }
        for (var value : payload.getList(FUNDED_ITEMS, Tag.TAG_COMPOUND)) {
            context.refund(ItemStack.of((CompoundTag) value));
        }
    }

    private static void removeStackFromTag(CompoundTag tag, ItemStack expected) {
        for (var key : List.copyOf(tag.getAllKeys())) {
            var value = tag.get(key);
            if (value instanceof CompoundTag compound) {
                var stack = ItemStack.of(compound);
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, expected)) {
                    tag.remove(key);
                } else {
                    removeStackFromTag(compound, expected);
                }
            } else if (value instanceof ListTag list) {
                for (var child : list) {
                    if (child instanceof CompoundTag compound) {
                        removeStackFromTag(compound, expected);
                    }
                }
            }
        }
    }

    private static String directionToString(@Nullable Direction direction) {
        return direction == null ? NONE_DIRECTION : direction.getName();
    }

    private static @Nullable Direction stringToDirection(String value) {
        return NONE_DIRECTION.equalsIgnoreCase(value) ? null : Direction.byName(value);
    }

    private static void transformDirectionString(CompoundTag tag, DirectionTransform transform, String key) {
        if (!tag.contains(key)) {
            return;
        }
        var direction = stringToDirection(tag.getString(key));
        if (direction != null) {
            tag.putString(key, transform.transform(direction).getName());
        }
    }
}
