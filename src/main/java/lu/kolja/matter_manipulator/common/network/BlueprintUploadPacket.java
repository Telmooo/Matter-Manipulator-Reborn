package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.common.blueprint.BlueprintServerCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public record BlueprintUploadPacket(int transferId, int index, int count, @Nullable InteractionHand hand,
                                    byte[] chunk) {

    public static final int CHUNK_BYTES = 30_000;

    public static void encode(BlueprintUploadPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.transferId);
        buffer.writeVarInt(packet.index);
        buffer.writeVarInt(packet.count);
        buffer.writeBoolean(packet.hand != null);
        if (packet.hand != null) {
            buffer.writeEnum(packet.hand);
        }
        buffer.writeByteArray(packet.chunk);
    }

    public static BlueprintUploadPacket decode(FriendlyByteBuf buffer) {
        int transferId = buffer.readVarInt();
        int index = buffer.readVarInt();
        int count = buffer.readVarInt();
        var hand = buffer.readBoolean() ? buffer.readEnum(InteractionHand.class) : null;
        return new BlueprintUploadPacket(transferId, index, count, hand, buffer.readByteArray());
    }

    public static void handle(BlueprintUploadPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                BlueprintServerCache.receive(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
