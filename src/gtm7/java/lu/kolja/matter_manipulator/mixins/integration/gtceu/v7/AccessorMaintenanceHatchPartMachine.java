package lu.kolja.matter_manipulator.mixins.integration.gtceu.v7;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import lu.kolja.matter_manipulator.common.integration.gtceu.GTIntegration;
import lu.kolja.mixinloadconditions.LoadCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@LoadCondition(loadIf = "gtceu", matchVersion = GTIntegration.GTM7_VERSIONS)
@Mixin(value = MaintenanceHatchPartMachine.class, remap = false)
public interface AccessorMaintenanceHatchPartMachine {
    @Accessor("itemStackHandler")
    NotifiableItemStackHandler getItemStackHandler();

    @Accessor("durationMultiplier")
    void setDurationMultiplier(float durationMultiplier);
}
