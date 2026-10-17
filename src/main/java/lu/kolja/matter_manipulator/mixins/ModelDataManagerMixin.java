package lu.kolja.matter_manipulator.mixins;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import lu.kolja.mixinloadconditions.LoadCondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.model.data.ModelDataManager;

/**
 * Copied from <a href="https://github.com/embeddedt/ModernFix/blob/1.20/src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/model_data_manager_cme/ModelDataManagerMixin.java">ModernFix</a>
 */
@LoadCondition(ignoreIf = "modernfix")
@Mixin(value = ModelDataManager.class, remap = false)
public abstract class ModelDataManagerMixin {

    @Shadow
    @Final
    private Map<ChunkPos, Set<BlockPos>> needModelDataRefresh;

    @Shadow
    protected abstract void refreshAt(ChunkPos chunk);

    @ModifyArg(method = "requestRefresh", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", ordinal = 0), index = 1, remap = false)
    private Function<ChunkPos, Set<BlockPos>> changeTypeOfSetUsed(Function<ChunkPos, Set<BlockPos>> mappingFunction) {
        return pos -> ConcurrentHashMap.newKeySet();
    }

    @Redirect(
            method = "getAt(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/Map;",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/model/data/ModelDataManager;refreshAt(Lnet/minecraft/world/level/ChunkPos;)V")
    )
    private void matterManipulator$refreshOnMainThread(ModelDataManager manager, ChunkPos chunk) {
        if (!Minecraft.getInstance().isSameThread() || needModelDataRefresh.isEmpty()) {
            return;
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                refreshAt(new ChunkPos(chunk.x + x, chunk.z + z));
            }
        }
    }
}
