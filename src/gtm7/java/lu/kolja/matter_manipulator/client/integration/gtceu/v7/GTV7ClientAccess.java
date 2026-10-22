package lu.kolja.matter_manipulator.client.integration.gtceu.v7;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.client.model.GTModelProperties;
import lu.kolja.matter_manipulator.client.integration.gtceu.GTClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public final class GTV7ClientAccess implements GTClientAccess {

    @Override
    public ModelData pipeModelData(BlockAndTintGetter level, BlockPos pos, int connections, int blockedConnections) {
        return ModelData.builder()
                .with(GTModelProperties.LEVEL, level)
                .with(GTModelProperties.POS, pos)
                .with(GTModelProperties.PIPE_CONNECTION_MASK, connections)
                .with(GTModelProperties.PIPE_BLOCKED_MASK, blockedConnections)
                .build();
    }

    @Override
    public @Nullable BlockEntity previewPipe(
            BlockState state,
            BlockPos pos,
            Level level,
            int connections,
            int blockedConnections,
            @Nullable Integer paintingColor
    ) {
        if (!(state.getBlock() instanceof PipeBlock<?, ?, ?> pipeBlock)) {
            return null;
        }
        PipeBlockEntity<?, ?> pipe = pipeBlock.newBlockEntity(pos, state);
        if (pipe == null) {
            return null;
        }
        pipe.setLevel(level);
        pipe.setConnections(connections);
        pipe.setBlockedConnections(blockedConnections);
        if (paintingColor != null) {
            pipe.setPaintingColor(paintingColor);
        }
        return pipe;
    }
}
