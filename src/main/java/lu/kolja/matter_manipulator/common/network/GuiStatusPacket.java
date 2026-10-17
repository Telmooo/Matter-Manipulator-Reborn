package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.client.ClientGuiStatus;
import lu.kolja.matter_manipulator.common.build.ItemRequirementStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record GuiStatusPacket(
        InteractionHand hand,
        int requestId,
        int stateHash,
        boolean valid,
        Component error,
        int totalOperations,
        List<ItemRequirementStatus> materials,
        boolean compiling
) {

    public GuiStatusPacket {
        error = error == null ? Component.empty() : error;
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    public static GuiStatusPacket invalid(
            InteractionHand hand,
            int requestId,
            int stateHash,
            Component error
    ) {
        return new GuiStatusPacket(hand, requestId, stateHash, false, error, 0, List.of(), false);
    }

    public static GuiStatusPacket compiling(InteractionHand hand, int requestId, int stateHash, Component progress) {
        return new GuiStatusPacket(hand, requestId, stateHash, false, progress, 0, List.of(), true);
    }

    public Component notice() {
        return valid ? error : Component.empty();
    }

    public GuiStatusPacket withRequestId(int newRequestId) {
        return new GuiStatusPacket(hand, newRequestId, stateHash, valid, error, totalOperations, materials, compiling);
    }

    public boolean matches(InteractionHand expectedHand, int expectedRequestId, int expectedStateHash) {
        return hand == expectedHand && requestId == expectedRequestId && stateHash == expectedStateHash;
    }

    public static void encode(GuiStatusPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeVarInt(packet.requestId);
        buffer.writeInt(packet.stateHash);
        buffer.writeBoolean(packet.valid);
        buffer.writeComponent(packet.error);
        buffer.writeVarInt(packet.totalOperations);
        buffer.writeVarInt(packet.materials.size());
        for (var material : packet.materials) {
            buffer.writeItem(material.stack());
            buffer.writeItem(material.fallback());
            buffer.writeUtf(material.description());
            buffer.writeLong(material.available());
            buffer.writeLong(material.required());
            buffer.writeCollection(material.alternatives(), FriendlyByteBuf::writeItem);
            buffer.writeBoolean(material.variants());
        }
        buffer.writeBoolean(packet.compiling);
    }

    public static GuiStatusPacket decode(FriendlyByteBuf buffer) {
        var hand = buffer.readEnum(InteractionHand.class);
        int requestId = buffer.readVarInt();
        int stateHash = buffer.readInt();
        boolean valid = buffer.readBoolean();
        var error = buffer.readComponent();
        int totalOperations = buffer.readVarInt();
        int size = buffer.readVarInt();
        var materials = new ArrayList<ItemRequirementStatus>(size);
        for (int i = 0; i < size; i++) {
            materials.add(new ItemRequirementStatus(
                    buffer.readItem(),
                    buffer.readItem(),
                    buffer.readUtf(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readList(FriendlyByteBuf::readItem),
                    buffer.readBoolean()
            ));
        }
        return new GuiStatusPacket(hand, requestId, stateHash, valid, error, totalOperations, materials,
                buffer.readBoolean());
    }

    public static void handle(GuiStatusPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> ClientGuiStatus.apply(packet));
        context.setPacketHandled(true);
    }
}
