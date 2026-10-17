package lu.kolja.matter_manipulator.common.energy;

import lu.kolja.matter_manipulator.common.manipulator.ManipulatorTier;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

public final class StackEnergyProvider implements ICapabilityProvider {

    private final LazyOptional<StackEnergyStorage> storage;

    public StackEnergyProvider(ItemStack stack, ManipulatorTier tier) {
        storage = LazyOptional.of(() -> new StackEnergyStorage(stack, tier));
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (capability == ForgeCapabilities.ENERGY) {
            return storage.cast();
        }
        return LazyOptional.empty();
    }
}
