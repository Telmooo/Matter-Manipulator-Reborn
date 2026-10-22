package lu.kolja.matter_manipulator.client.integration.gtceu.v8;

import lu.kolja.matter_manipulator.MatterManipulator;
import lu.kolja.matter_manipulator.client.integration.gtceu.GTClientIntegration;
import lu.kolja.matter_manipulator.common.integration.gtceu.GTIntegration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

@Mod.EventBusSubscriber(modid = MatterManipulator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GTV8ClientRegistration {

    private GTV8ClientRegistration() {}

    @SubscribeEvent
    public static void construct(FMLConstructModEvent event) {
        if (GTIntegration.installedIn(GTIntegration.GTM8_VERSIONS)) {
            GTClientIntegration.install(new GTV8ClientAccess());
        }
    }
}
