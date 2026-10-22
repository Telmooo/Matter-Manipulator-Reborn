package lu.kolja.matter_manipulator.client.integration.gtceu.v7;

import lu.kolja.matter_manipulator.MatterManipulator;
import lu.kolja.matter_manipulator.client.integration.gtceu.GTClientIntegration;
import lu.kolja.matter_manipulator.common.integration.gtceu.GTIntegration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

@Mod.EventBusSubscriber(modid = MatterManipulator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GTV7ClientRegistration {

    private GTV7ClientRegistration() {}

    @SubscribeEvent
    public static void construct(FMLConstructModEvent event) {
        if (GTIntegration.installedIn(GTIntegration.GTM7_VERSIONS)) {
            GTClientIntegration.install(new GTV7ClientAccess());
        }
    }
}
