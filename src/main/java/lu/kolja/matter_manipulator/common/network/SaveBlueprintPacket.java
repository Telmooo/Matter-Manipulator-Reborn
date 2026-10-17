package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.common.blueprint.Blueprint;
import lu.kolja.matter_manipulator.common.build.BuildManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SaveBlueprintPacket(InteractionHand hand, String name) {

    public static void encode(SaveBlueprintPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeUtf(packet.name, Blueprint.MAX_NAME_LENGTH);
    }

    public static SaveBlueprintPacket decode(FriendlyByteBuf buffer) {
        return new SaveBlueprintPacket(buffer.readEnum(InteractionHand.class), buffer.readUtf(Blueprint.MAX_NAME_LENGTH));
    }

    public static void handle(SaveBlueprintPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                BuildManager.saveBlueprint(player, packet.hand, packet.name);
            }
        });
        context.setPacketHandled(true);
    }
}
