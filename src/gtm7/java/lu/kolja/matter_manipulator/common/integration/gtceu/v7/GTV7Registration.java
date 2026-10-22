package lu.kolja.matter_manipulator.common.integration.gtceu.v7;

import lu.kolja.matter_manipulator.MatterManipulator;
import lu.kolja.matter_manipulator.common.integration.gtceu.GTIntegration;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

@Mod.EventBusSubscriber(modid = MatterManipulator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GTV7Registration {

    private GTV7Registration() {}

    @SubscribeEvent
    public static void construct(FMLConstructModEvent event) {
        if (GTIntegration.installedIn(GTIntegration.GTM7_VERSIONS)) {
            GTIntegration.install(new GTV7Access());
        }
    }
}
