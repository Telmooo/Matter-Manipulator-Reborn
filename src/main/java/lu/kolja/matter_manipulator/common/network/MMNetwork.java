package lu.kolja.matter_manipulator.common.network;

import lu.kolja.matter_manipulator.MatterManipulator;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class MMNetwork {

    private static final String PROTOCOL = "5";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            MatterManipulator.id("main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static final Optional<NetworkDirection> TO_SERVER = Optional.of(NetworkDirection.PLAY_TO_SERVER);
    private static final Optional<NetworkDirection> TO_CLIENT = Optional.of(NetworkDirection.PLAY_TO_CLIENT);

    private static int id;

    private MMNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(id++, ConfigActionPacket.class, ConfigActionPacket::encode, ConfigActionPacket::decode,
                ConfigActionPacket::handle, TO_SERVER);
        CHANNEL.registerMessage(id++, PickBlockPacket.class, PickBlockPacket::encode, PickBlockPacket::decode,
                PickBlockPacket::handle, TO_SERVER);
        CHANNEL.registerMessage(id++, ToolStateSyncPacket.class, ToolStateSyncPacket::encode, ToolStateSyncPacket::decode,
                ToolStateSyncPacket::handle, TO_CLIENT);
        CHANNEL.registerMessage(id++, GuiStatusRequestPacket.class, GuiStatusRequestPacket::encode,
                GuiStatusRequestPacket::decode, GuiStatusRequestPacket::handle, TO_SERVER);
        CHANNEL.registerMessage(id++, GuiStatusPacket.class, GuiStatusPacket::encode, GuiStatusPacket::decode,
                GuiStatusPacket::handle, TO_CLIENT);
        CHANNEL.registerMessage(id++, PasteAnchorPacket.class, PasteAnchorPacket::encode, PasteAnchorPacket::decode,
                PasteAnchorPacket::handle, TO_SERVER);
        CHANNEL.registerMessage(id++, NudgeAnchorPacket.class, NudgeAnchorPacket::encode, NudgeAnchorPacket::decode,
                NudgeAnchorPacket::handle, TO_SERVER);
        CHANNEL.registerMessage(id++, EditSelectionPacket.class, EditSelectionPacket::encode,
                EditSelectionPacket::decode, EditSelectionPacket::handle, TO_SERVER);
        CHANNEL.registerMessage(id++, SaveBlueprintPacket.class, SaveBlueprintPacket::encode,
                SaveBlueprintPacket::decode, SaveBlueprintPacket::handle, TO_SERVER);
        CHANNEL.registerMessage(id++, BlueprintDownloadPacket.class, BlueprintDownloadPacket::encode,
                BlueprintDownloadPacket::decode, BlueprintDownloadPacket::handle, TO_CLIENT);
        CHANNEL.registerMessage(id++, BlueprintUploadPacket.class, BlueprintUploadPacket::encode,
                BlueprintUploadPacket::decode, BlueprintUploadPacket::handle, TO_SERVER);
        CHANNEL.registerMessage(id++, BlueprintRequestPacket.class, BlueprintRequestPacket::encode,
                BlueprintRequestPacket::decode, BlueprintRequestPacket::handle, TO_CLIENT);
    }
}
