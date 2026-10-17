package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.client.ClientToolStateSync;
import lu.kolja.matter_manipulator.common.state.ToolState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record ToolStateSyncPacket(InteractionHand hand, CompoundTag tag) {

    public static void encode(ToolStateSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeNbt(packet.tag);
    }

    public static ToolStateSyncPacket decode(FriendlyByteBuf buffer) {
        var hand = buffer.readEnum(InteractionHand.class);
        var tag = buffer.readNbt();
        return new ToolStateSyncPacket(hand, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(ToolStateSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> ClientToolStateSync.apply(packet.hand, packet.tag));
        context.setPacketHandled(true);
    }

    public static void syncHeld(ServerPlayer player) {
        var hand = NetworkUtil.heldManipulatorHand(player);
        if (hand != null) {
            send(player, hand);
        }
    }

    public static void send(ServerPlayer player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        MMNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ToolStateSyncPacket(hand, ToolState.stackTag(stack).copy())
        );
    }
}
