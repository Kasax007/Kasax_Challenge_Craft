package net.kasax.challengecraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** World block for the infinite chest, including screen opening and waterlogging behavior. */
public class InfiniteChestBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<InfiniteChestBlock> CODEC = simpleCodec(InfiniteChestBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    protected static final VoxelShape BASE_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    protected static final VoxelShape LATCH_NORTH = Block.box(7.0, 7.0, 0.0, 9.0, 11.0, 1.0);
    protected static final VoxelShape LATCH_SOUTH = Block.box(7.0, 7.0, 15.0, 9.0, 11.0, 16.0);
    protected static final VoxelShape LATCH_EAST = Block.box(15.0, 7.0, 7.0, 16.0, 11.0, 9.0);
    protected static final VoxelShape LATCH_WEST = Block.box(0.0, 7.0, 7.0, 1.0, 11.0, 9.0);

    protected static final VoxelShape SHAPE_NORTH = Shapes.or(BASE_SHAPE, LATCH_NORTH);
    protected static final VoxelShape SHAPE_SOUTH = Shapes.or(BASE_SHAPE, LATCH_SOUTH);
    protected static final VoxelShape SHAPE_EAST = Shapes.or(BASE_SHAPE, LATCH_EAST);
    protected static final VoxelShape SHAPE_WEST = Shapes.or(BASE_SHAPE, LATCH_WEST);

    public InfiniteChestBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteChestBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            return;
        }

        Direction facing = state.getValue(FACING);
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.75;
        double centerZ = pos.getZ() + 0.5;
        double frontX = centerX + facing.getStepX() * 0.44;
        double frontZ = centerZ + facing.getStepZ() * 0.44;
        double sideX = facing.getClockWise().getStepX() * 0.18;
        double sideZ = facing.getClockWise().getStepZ() * 0.18;

        world.addParticle(
                ParticleTypes.ENCHANT,
                frontX + sideX * (random.nextDouble() - 0.5),
                centerY + (random.nextDouble() - 0.5) * 0.18,
                frontZ + sideZ * (random.nextDouble() - 0.5),
                (random.nextDouble() - 0.5) * 0.015,
                0.01 + random.nextDouble() * 0.02,
                (random.nextDouble() - 0.5) * 0.015
        );

        if (random.nextFloat() < 0.35f) {
            world.addAlwaysVisibleParticle(
                    ParticleTypes.END_ROD,
                    centerX + (random.nextDouble() - 0.5) * 0.4,
                    pos.getY() + 0.92 + random.nextDouble() * 0.18,
                    centerZ + (random.nextDouble() - 0.5) * 0.4,
                    (random.nextDouble() - 0.5) * 0.01,
                    0.006 + random.nextDouble() * 0.012,
                    (random.nextDouble() - 0.5) * 0.01
            );
        }

        if (random.nextFloat() < 0.18f) {
            world.addParticle(
                    ParticleTypes.WITCH,
                    frontX,
                    pos.getY() + 0.56 + random.nextDouble() * 0.22,
                    frontZ,
                    0.0,
                    0.0,
                    0.0
            );
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!world.isClientSide()) {
            MenuProvider screenHandlerFactory = state.getMenuProvider(world, pos);
            if (screenHandlerFactory != null) {
                player.openMenu(screenHandlerFactory);
                if (world.getBlockEntity(pos) instanceof InfiniteChestBlockEntity be) {
                    net.kasax.challengecraft.network.PacketHandler.syncInfiniteChest((net.minecraft.server.level.ServerPlayer) player, be);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof InfiniteChestBlockEntity be) {
            builder.withDynamicDrop(ShulkerBoxBlock.CONTENTS, (consumer) -> {
                be.getStorage().getStoredItems().forEach((key, count) -> {
                    long remaining = count;
                    while (remaining > 0) {
                        int toAdd = (int) Math.min(remaining, key.item().getDefaultMaxStackSize());
                        consumer.accept(key.toStack(toAdd));
                        remaining -= toAdd;
                    }
                });
            });
        }
        return super.getDrops(state, builder);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        if (!state.is(world.getBlockState(pos).getBlock())) {
            world.updateNeighbourForOutputSignal(pos, this);
        }
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }
}
