package net.kasax.challengecraft.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The master block of every casino device. The static body is a JSON block model (see
 * {@code assets/challengecraft/models/block/}); everything that moves or shows numbers — reels,
 * wheel, ball, rocket, balls, screens and chips — is drawn by {@code CasinoWorldRenderer} on top of
 * it, so the block needs no block entity. Interaction goes through Fabric's {@code UseBlockCallback}
 * in {@link CasinoGames}, which works out which part of the device was aimed at.
 *
 * <p>The rest of a device's cells are {@link CasinoPartBlock}s placed together with the master; if
 * one of them goes missing (an explosion, say) the whole device breaks and drops its item.
 *
 * <p>Placing a device registers its position with {@link CasinoDevices}; a device that disappears
 * any other way is dropped from the registry lazily the next time the registry is validated.
 */
public class CasinoDeviceBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final DeviceType.Part MASTER = new DeviceType.Part(0, 0, 0);

    private final DeviceType type;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public CasinoDeviceBlock(Properties properties, DeviceType type) {
        super(properties);
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes.put(d, DeviceSpace.cellShape(DeviceLayouts.outline(type), d, MASTER));
        }
    }

    public DeviceType getDeviceType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        for (DeviceType.Part part : type.parts) {
            BlockPos p = DeviceSpace.partPos(context.getClickedPos(), facing, part);
            if (p.getY() > level.getMaxY() || !level.getWorldBorder().isWithinBounds(p)
                    || !level.getBlockState(p).canBeReplaced(context)) {
                return null; // no room for the whole device
            }
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Direction facing = state.getValue(FACING);
        Block partBlock = CasinoRegistry.part(type);
        for (int i = 0; i < type.parts.size(); i++) {
            BlockPos p = DeviceSpace.partPos(pos, facing, type.parts.get(i));
            level.setBlock(p, partBlock.defaultBlockState().setValue(CasinoPartBlock.FACING, facing)
                    .setValue(CasinoPartBlock.PART, i + 1), Block.UPDATE_ALL);
        }
        if (level instanceof ServerLevel serverLevel) {
            CasinoDevices.register(serverLevel, pos, type);
        }
    }

    /** Whether every part cell of the device at {@code pos} is still there. */
    public boolean complete(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < type.parts.size(); i++) {
            BlockState s = level.getBlockState(DeviceSpace.partPos(pos, facing, type.parts.get(i)));
            if (!(s.getBlock() instanceof CasinoPartBlock p) || p.getOwner() != type
                    || s.getValue(CasinoPartBlock.PART) != i + 1 || s.getValue(CasinoPartBlock.FACING) != facing) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (!type.parts.isEmpty()) ticks.scheduleTick(pos, this, 1);
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!complete(level, pos, state)) {
            level.destroyBlock(pos, true);
        }
    }
}
