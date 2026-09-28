package net.kasax.challengecraft.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * The coordinate frame every device is designed in, shared by the block shapes, the hit zones on the
 * server and the drawing on the client.
 *
 * <p>Device space is measured in model pixels (1/16 block) from the <b>front-left corner</b> of the
 * master block, as seen by a player standing in front of the device: {@code u} runs to that player's
 * right, {@code v} away from them (to the back) and {@code y} up. A device's {@code FACING} points
 * towards that player.
 *
 * <p>For a device facing north this is {@code x = 16 - u}, {@code z = v} in the block model's own
 * coordinates, which is how the JSON models (authored facing north) relate to it.
 */
public final class DeviceSpace {
    private DeviceSpace() {
    }

    /** The player's right while looking at the front of a device facing {@code facing}. */
    public static Direction right(Direction facing) {
        return facing.getOpposite().getClockWise();
    }

    public static Direction back(Direction facing) {
        return facing.getOpposite();
    }

    public static BlockPos partPos(BlockPos master, Direction facing, DeviceType.Part part) {
        return master.relative(right(facing), part.right()).above(part.up()).relative(back(facing), part.back());
    }

    /** The master block of a part at {@code partPos}. */
    public static BlockPos masterOf(BlockPos partPos, Direction facing, DeviceType.Part part) {
        return partPos.relative(right(facing), -part.right()).below(part.up()).relative(back(facing), -part.back());
    }

    private static double cornerX(BlockPos master, Direction facing) {
        Direction r = right(facing);
        return master.getX() + 0.5 + facing.getStepX() * 0.5 - r.getStepX() * 0.5;
    }

    private static double cornerZ(BlockPos master, Direction facing) {
        Direction r = right(facing);
        return master.getZ() + 0.5 + facing.getStepZ() * 0.5 - r.getStepZ() * 0.5;
    }

    /** A world point in device space: {@code (u, y, v)} in pixels (x = u, y = y, z = v). */
    public static Vec3 toDevice(BlockPos master, Direction facing, Vec3 world) {
        Direction r = right(facing);
        Direction b = back(facing);
        double dx = world.x - cornerX(master, facing);
        double dz = world.z - cornerZ(master, facing);
        double u = (dx * r.getStepX() + dz * r.getStepZ()) * 16.0;
        double v = (dx * b.getStepX() + dz * b.getStepZ()) * 16.0;
        return new Vec3(u, (world.y - master.getY()) * 16.0, v);
    }

    /** A device-space point (pixels) in world coordinates. */
    public static Vec3 toWorld(BlockPos master, Direction facing, double u, double y, double v) {
        Direction r = right(facing);
        Direction b = back(facing);
        double x = cornerX(master, facing) + (r.getStepX() * u + b.getStepX() * v) / 16.0;
        double z = cornerZ(master, facing) + (r.getStepZ() * u + b.getStepZ() * v) / 16.0;
        return new Vec3(x, master.getY() + y / 16.0, z);
    }

    /**
     * The part of a device's outline that falls into one of its cells, as a block-local shape.
     * {@code boxes} are {u0, y0, v0, u1, y1, v1} in device space.
     */
    public static VoxelShape cellShape(List<double[]> boxes, Direction facing, DeviceType.Part cell) {
        Direction r = right(facing);
        Direction b = back(facing);
        double cx = 8 + facing.getStepX() * 8 - r.getStepX() * 8;
        double cz = 8 + facing.getStepZ() * 8 - r.getStepZ() * 8;
        double ou = cell.right() * 16.0, oy = cell.up() * 16.0, ov = cell.back() * 16.0;
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            double u0 = Math.max(box[0], ou) - ou, u1 = Math.min(box[3], ou + 16) - ou;
            double y0 = Math.max(box[1], oy) - oy, y1 = Math.min(box[4], oy + 16) - oy;
            double v0 = Math.max(box[2], ov) - ov, v1 = Math.min(box[5], ov + 16) - ov;
            if (u1 - u0 < 0.01 || y1 - y0 < 0.01 || v1 - v0 < 0.01) continue;
            double xa = cx + r.getStepX() * u0 + b.getStepX() * v0;
            double xb = cx + r.getStepX() * u1 + b.getStepX() * v1;
            double za = cz + r.getStepZ() * u0 + b.getStepZ() * v0;
            double zb = cz + r.getStepZ() * u1 + b.getStepZ() * v1;
            shape = Shapes.or(shape, Block.box(Math.min(xa, xb), y0, Math.min(za, zb), Math.max(xa, xb), y1, Math.max(za, zb)));
        }
        return shape.optimize();
    }
}
