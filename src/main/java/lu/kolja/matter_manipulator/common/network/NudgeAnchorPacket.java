package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.common.build.BuildPlanner;
import lu.kolja.matter_manipulator.common.item.ManipulatorItem;
import lu.kolja.matter_manipulator.common.localization.MMMessage;
import lu.kolja.matter_manipulator.common.manipulator.PasteAnchor;
import lu.kolja.matter_manipulator.common.state.ToolState;
import lu.kolja.matter_manipulator.common.util.ChatUtil;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record NudgeAnchorPacket(InteractionHand hand, Direction.Axis axis, int amount) {

    public static void encode(NudgeAnchorPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeEnum(packet.axis);
        buffer.writeVarInt(packet.amount);
    }

    public static NudgeAnchorPacket decode(FriendlyByteBuf buffer) {
        return new NudgeAnchorPacket(buffer.readEnum(InteractionHand.class), buffer.readEnum(Direction.Axis.class),
                buffer.readVarInt());
    }

    public static void handle(NudgeAnchorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                apply(player, packet.hand, packet.axis, packet.amount);
            }
        });
        context.setPacketHandled(true);
    }

    public static boolean apply(ServerPlayer player, InteractionHand hand, Direction.Axis axis, int amount) {
        var stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ManipulatorItem item) || amount == 0) {
            return false;
        }
        var state = ToolState.get(stack);
        if (PasteAnchor.state(state, player.level().dimension()) != PasteAnchor.State.PINNED) {
            return false;
        }
        var moved = state.coordC().orElseThrow().relative(axis, amount);
        var error = BuildPlanner.checkPasteAnchor(player, item.tier(), state, moved);
        if (error.isPresent()) {
            ChatUtil.error(player, error.get());
            return false;
        }
        PasteAnchor.setAnchor(state, player.level().dimension(), moved);
        ChatUtil.status(player, MMMessage.ANCHOR_PINNED.text(
                moved.toShortString()));
        ToolStateSyncPacket.send(player, hand);
        return true;
    }
}
