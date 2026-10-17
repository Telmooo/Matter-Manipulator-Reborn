package lu.kolja.matter_manipulator.common.registry;

import lu.kolja.matter_manipulator.MatterManipulator;
import lu.kolja.matter_manipulator.common.item.BlueprintItem;
import lu.kolja.matter_manipulator.common.item.ManipulatorItem;
import lu.kolja.matter_manipulator.common.manipulator.ManipulatorTier;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MatterManipulator.MOD_ID);

    public static final Map<RegistryObject<Item>, String> ENGLISH_NAMES = new LinkedHashMap<>();

    public static final RegistryObject<Item> PROTOTYPE = item(
            "prototype_matter_manipulator", "Prototype Matter Manipulator",
            () -> new ManipulatorItem(ManipulatorTier.PROTOTYPE, new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> MKI = item(
            "matter_manipulator_mki", "Matter Manipulator MKI",
            () -> new ManipulatorItem(ManipulatorTier.MKI, new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> MKII = item(
            "matter_manipulator_mkii", "Matter Manipulator MKII",
            () -> new ManipulatorItem(ManipulatorTier.MKII, new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> MKIII = item(
            "matter_manipulator_mkiii", "Matter Manipulator MKIII",
            () -> new ManipulatorItem(ManipulatorTier.MKIII, new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> BLUEPRINT = item(
            "blueprint", "Blueprint",
            () -> new BlueprintItem(new Item.Properties().stacksTo(1))
    );

    private ModItems() {}

    private static RegistryObject<Item> item(String id, String englishName, Supplier<Item> factory) {
        var object = ITEMS.register(id, factory);
        ENGLISH_NAMES.put(object, englishName);
        return object;
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
