package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.common.build.BuildManager;
import lu.kolja.matter_manipulator.common.item.ManipulatorItem;
import lu.kolja.matter_manipulator.common.manipulator.ManipulatorMode;
import lu.kolja.matter_manipulator.common.manipulator.RemoveMode;
import lu.kolja.matter_manipulator.common.manipulator.ShapeType;
import lu.kolja.matter_manipulator.common.state.ToolState;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public record ConfigActionPacket(Action action, InteractionHand hand, int selection) {

    public static final int NO_SELECTION = -1;

    public ConfigActionPacket(Action action) {
        this(action, null, NO_SELECTION);
    }

    public ConfigActionPacket(Action action, InteractionHand hand) {
        this(action, hand, NO_SELECTION);
    }

    public static void encode(ConfigActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeBoolean(packet.hand != null);
        if (packet.hand != null) {
            buffer.writeEnum(packet.hand);
        }
        buffer.writeVarInt(packet.selection);
    }

    public static ConfigActionPacket decode(FriendlyByteBuf buffer) {
        var action = buffer.readEnum(Action.class);
        var hand = buffer.readBoolean() ? buffer.readEnum(InteractionHand.class) : null;
        int selection = buffer.readVarInt();
        return new ConfigActionPacket(action, hand, selection);
    }

    public static void handle(ConfigActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) {
                return;
            }
            var stack = packet.hand == null
                    ? NetworkUtil.heldManipulator(player)
                    : player.getItemInHand(packet.hand);
            if (!(stack.getItem() instanceof ManipulatorItem)) {
                return;
            }
            switch (packet.action) {
                case REQUEST_MISSING -> BuildManager.requestMissing(player, packet.hand);
                case START -> BuildManager.start(player, packet.hand);
                default -> {
                    applyStateAction(stack, packet.action, packet.selection);
                    if (packet.hand == null) {
                        ToolStateSyncPacket.syncHeld(player);
                    } else {
                        ToolStateSyncPacket.send(player, packet.hand);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    public static boolean applyStateAction(ItemStack stack, Action action, int selection) {
        if (!(stack.getItem() instanceof ManipulatorItem item)
                || action == Action.START
                || action == Action.REQUEST_MISSING) {
            return false;
        }
        var state = ToolState.get(stack);
        switch (action) {
            case CYCLE_MODE -> state.setMode(state.mode().next());
            case CYCLE_SHAPE -> state.setShape(state.shape().next());
            case CYCLE_REMOVE -> state.setRemoveMode(state.removeMode().next());
            case CYCLE_CABLE_ROUTE -> state.cycleCableRouteMode();
            case SET_SHAPE, SET_REMOVE -> applySelection(state, action, selection);
            case TOGGLE_FLOATING_PLACEMENT -> state.toggleFloatingPlacement();
            case TOGGLE_AUTO_CRAFT_MISSING -> state.toggleAutoCraftMissing();
            case CLEAR_COORDS -> state.clearSelection();
            case CLEAR_BLUEPRINT -> {
                state.clearBlueprintRef();
                state.clearCoord("C");
            }
            case MODE_GEOMETRY -> state.setMode(ManipulatorMode.GEOMETRY);
            case MODE_CABLE -> state.setMode(ManipulatorMode.CABLE);
            case MODE_EXCHANGE -> state.setMode(ManipulatorMode.EXCHANGE);
            case MODE_COPY -> state.setMode(ManipulatorMode.COPY);
            case MODE_MOVE -> state.setMode(ManipulatorMode.MOVE);
            case MODE_BLUEPRINT -> state.setMode(ManipulatorMode.BLUEPRINT);
            case ROTATE_X -> state.rotateTransform(Direction.Axis.X);
            case ROTATE_Y -> state.rotateTransform(Direction.Axis.Y);
            case ROTATE_Z -> state.rotateTransform(Direction.Axis.Z);
            case MIRROR_X -> state.toggleMirrorX();
            case MIRROR_Y -> state.toggleMirrorY();
            case MIRROR_Z -> state.toggleMirrorZ();
            case STACK_PLUS -> state.changeStackCount(1);
            case STACK_MINUS -> state.changeStackCount(-1);
            case STACK_AXIS -> state.cycleStackAxis();
            case SET_PLACEMENT_SPEED -> {
                if (selection <= 0) {
                    return false;
                }
                ToolState.setPlacementSpeed(stack, Math.min(selection, item.tier().blocksPerSecond()));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean applySelection(ToolState state, Action action, int selection) {
        return switch (action) {
            case SET_SHAPE -> select(ShapeType.values(), selection, state::setShape);
            case SET_REMOVE -> select(RemoveMode.values(), selection, state::setRemoveMode);
            default -> false;
        };
    }

    private static <T> boolean select(T[] values, int selection, Consumer<T> setter) {
        if (selection < 0 || selection >= values.length) {
            return false;
        }
        setter.accept(values[selection]);
        return true;
    }

    public enum Action {
        CYCLE_MODE,
        CYCLE_SHAPE,
        CYCLE_REMOVE,
        CYCLE_CABLE_ROUTE,
        SET_SHAPE,
        SET_REMOVE,
        TOGGLE_FLOATING_PLACEMENT,
        TOGGLE_AUTO_CRAFT_MISSING,
        CLEAR_COORDS,
        CLEAR_BLUEPRINT,
        MODE_GEOMETRY,
        MODE_CABLE,
        MODE_EXCHANGE,
        MODE_COPY,
        MODE_MOVE,
        MODE_BLUEPRINT,
        ROTATE_X,
        ROTATE_Y,
        ROTATE_Z,
        MIRROR_X,
        MIRROR_Y,
        MIRROR_Z,
        STACK_PLUS,
        STACK_MINUS,
        STACK_AXIS,
        SET_PLACEMENT_SPEED,
        REQUEST_MISSING,
        START
    }
}
