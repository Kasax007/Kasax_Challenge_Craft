package net.kasax.challengecraft.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * One block class for every casino device. The static body is a JSON block model (see
 * {@code assets/challengecraft/models/block/}); everything that moves — reels, wheel, ball, rocket,
 * chips — is drawn by {@code CasinoWorldRenderer} on top of it, so the block itself needs no block
 * entity. Interaction goes through Fabric's {@code UseBlockCallback} in {@link CasinoGames}.
 *
 * <p>Placing a device registers its position with {@link CasinoDevices}; a device that disappears
 * any other way (explosion, piston, /setblock) is dropped from the registry lazily the next time
 * the registry is validated.
 */
public class CasinoDeviceBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SLOT_SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 2.0, 15.0, 22.0, 15.0),
            Block.box(2.0, 22.0, 3.0, 14.0, 28.0, 14.0));
    private static final VoxelShape ROULETTE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0);
    private static final VoxelShape CRASH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 5.0, 16.0);
    private static final VoxelShape CASHIER_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);

    private final DeviceType type;

    public CasinoDeviceBlock(Properties properties, DeviceType type) {
        super(properties);
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public DeviceType getDeviceType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (type) {
            case SLOT -> SLOT_SHAPE;
            case ROULETTE -> ROULETTE_SHAPE;
            case CRASH -> CRASH_SHAPE;
            case CASHIER -> CASHIER_SHAPE;
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            CasinoDevices.register(serverLevel, pos, type);
        }
    }
}
