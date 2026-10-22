package lu.kolja.matter_manipulator.client.integration.gtceu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public interface GTClientAccess {

    ModelData pipeModelData(BlockAndTintGetter level, BlockPos pos, int connections, int blockedConnections);

    @Nullable
    BlockEntity previewPipe(
            BlockState state,
            BlockPos pos,
            Level level,
            int connections,
            int blockedConnections,
            @Nullable Integer paintingColor
    );
}
