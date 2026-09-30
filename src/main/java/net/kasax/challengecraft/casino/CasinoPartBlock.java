package net.kasax.challengecraft.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * A cell of a multiblock device other than its master: the slot machine's marquee, the second half
 * of the plinko board and the crash console, the rest of the roulette table. Like the upper half of
 * a door it owns its cell: it gives that part of the device its hitbox (a ray aimed at the marquee
 * only ever tests the cells it passes through), forwards clicks to the master (see {@link #master})
 * and takes the whole device with it when broken. It disappears when its master does.
 *
 * <p>Most parts are invisible because the master's model already draws the whole device; the
 * roulette table is wider than a block model may reach, so its parts carry slices of the table.
 */
public class CasinoPartBlock extends Block {
    public static final EnumProperty<Direction> FACING = CasinoDeviceBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 1, 7);

    private final DeviceType owner;
    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);

    public CasinoPartBlock(Properties properties, DeviceType owner) {
        super(properties);
        this.owner = owner;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 1));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape[] perPart = new VoxelShape[owner.parts.size()];
            for (int i = 0; i < perPart.length; i++) {
                perPart[i] = DeviceSpace.cellShape(DeviceLayouts.outline(owner), d, owner.parts.get(i));
            }
            shapes.put(d, perPart);
        }
    }

    public DeviceType getOwner() {
        return owner;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    private DeviceType.Part part(BlockState state) {
        int i = Math.min(owner.parts.size(), state.getValue(PART)) - 1;
        return owner.parts.get(Math.max(0, i));
    }

    /** The master block a click on {@code pos} is meant for: the device itself for a part. */
    public static BlockPos master(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CasinoPartBlock part) {
            return DeviceSpace.masterOf(pos, state.getValue(FACING), part.part(state));
        }
        return pos;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        VoxelShape[] perPart = shapes.get(state.getValue(FACING));
        return perPart[Math.max(0, Math.min(perPart.length, state.getValue(PART)) - 1)];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos master = DeviceSpace.masterOf(pos, state.getValue(FACING), part(state));
        BlockState m = level.getBlockState(master);
        return m.getBlock() instanceof CasinoDeviceBlock device && device.getDeviceType() == owner
                && m.getValue(CasinoDeviceBlock.FACING) == state.getValue(FACING);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && canSurvive(state, level, pos)) {
            // Break the device itself so it drops (unless in creative), like hitting a door's top.
            level.destroyBlock(DeviceSpace.masterOf(pos, state.getValue(FACING), part(state)), !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(CasinoRegistry.item(owner));
    }
}
