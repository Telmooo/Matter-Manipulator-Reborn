package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.client.blueprint.BlueprintLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BlueprintRequestPacket(String hash, BlockPos size) {

    public static void encode(BlueprintRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.hash, 64);
        buffer.writeVarInt(packet.size.getX());
        buffer.writeVarInt(packet.size.getY());
        buffer.writeVarInt(packet.size.getZ());
    }

    public static BlueprintRequestPacket decode(FriendlyByteBuf buffer) {
        return new BlueprintRequestPacket(buffer.readUtf(64),
                new BlockPos(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
    }

    public static void handle(BlueprintRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> BlueprintLibrary.uploadRequested(packet.hash, packet.size));
        context.setPacketHandled(true);
    }
}
