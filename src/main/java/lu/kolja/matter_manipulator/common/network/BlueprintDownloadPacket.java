package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.client.blueprint.BlueprintLibrary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.function.Supplier;

public record BlueprintDownloadPacket(int transferId, int index, int count, byte[] chunk) {

    public static final int CHUNK_BYTES = 30_000;

    private static int nextTransfer;

    public static void encode(BlueprintDownloadPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.transferId);
        buffer.writeVarInt(packet.index);
        buffer.writeVarInt(packet.count);
        buffer.writeByteArray(packet.chunk);
    }

    public static BlueprintDownloadPacket decode(FriendlyByteBuf buffer) {
        return new BlueprintDownloadPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByteArray(CHUNK_BYTES));
    }

    public static void handle(BlueprintDownloadPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> BlueprintLibrary.receive(packet));
        context.setPacketHandled(true);
    }

    public static void send(ServerPlayer player, byte[] stream) {
        int transferId = nextTransfer++;
        int count = Math.max(1, (stream.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        for (int index = 0; index < count; index++) {
            int from = index * CHUNK_BYTES;
            var chunk = Arrays.copyOfRange(stream, from, Math.min(stream.length, from + CHUNK_BYTES));
            MMNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new BlueprintDownloadPacket(transferId, index, count, chunk));
        }
    }
}
