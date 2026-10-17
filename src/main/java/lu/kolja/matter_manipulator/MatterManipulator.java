package lu.kolja.matter_manipulator;

import lu.kolja.matter_manipulator.client.ClientMatterManipulator;
import lu.kolja.matter_manipulator.common.config.MMConfig;
import lu.kolja.matter_manipulator.common.integration.IntegrationDispatcher;
import lu.kolja.matter_manipulator.common.network.MMNetwork;
import lu.kolja.matter_manipulator.common.registry.ModCreativeTabs;
import lu.kolja.matter_manipulator.common.registry.ModItems;
import lu.kolja.matter_manipulator.datagen.MMDataGenerators;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

@Mod(MatterManipulator.MOD_ID)
public class MatterManipulator {

    public static final String MOD_ID = "matter_manipulator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MatterManipulator() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(IntegrationDispatcher::commonSetup);
        modBus.addListener(MMDataGenerators::gatherData);
        MMConfig.register();
        MMNetwork.register();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientMatterManipulator::init);
    }

    public static ResourceLocation id(String path) {
        return id(MOD_ID, path);
    }

    public static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}
