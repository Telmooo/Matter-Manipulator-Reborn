package lu.kolja.matter_manipulator.common.integration.gtceu.common;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public final class GTUpwardsFacing {

    private GTUpwardsFacing() {}

    public static Direction top(Direction front, Direction relative) {
        if (front.getAxis() == Direction.Axis.Y) {
            return relative;
        }
        return switch (relative) {
            case NORTH -> Direction.UP;
            case SOUTH -> Direction.DOWN;
            case EAST -> front.getCounterClockWise();
            default -> front.getClockWise();
        };
    }

    public static @Nullable Direction relative(Direction front, Direction top) {
        if (front.getAxis() == Direction.Axis.Y) {
            return top.getAxis() == Direction.Axis.Y ? null : top;
        }
        if (top == Direction.UP) {
            return Direction.NORTH;
        }
        if (top == Direction.DOWN) {
            return Direction.SOUTH;
        }
        if (top == front.getCounterClockWise()) {
            return Direction.EAST;
        }
        if (top == front.getClockWise()) {
            return Direction.WEST;
        }
        return null;
    }
}
