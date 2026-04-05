package net.kasax.challengecraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InfiniteChestBlock extends BlockWithEntity implements Waterloggable {
    public static final MapCodec<InfiniteChestBlock> CODEC = createCodec(InfiniteChestBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    protected static final VoxelShape BASE_SHAPE = Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    protected static final VoxelShape LATCH_NORTH = Block.createCuboidShape(7.0, 7.0, 0.0, 9.0, 11.0, 1.0);
    protected static final VoxelShape LATCH_SOUTH = Block.createCuboidShape(7.0, 7.0, 15.0, 9.0, 11.0, 16.0);
    protected static final VoxelShape LATCH_EAST = Block.createCuboidShape(15.0, 7.0, 7.0, 16.0, 11.0, 9.0);
    protected static final VoxelShape LATCH_WEST = Block.createCuboidShape(0.0, 7.0, 7.0, 1.0, 11.0, 9.0);

    protected static final VoxelShape SHAPE_NORTH = VoxelShapes.union(BASE_SHAPE, LATCH_NORTH);
    protected static final VoxelShape SHAPE_SOUTH = VoxelShapes.union(BASE_SHAPE, LATCH_SOUTH);
    protected static final VoxelShape SHAPE_EAST = VoxelShapes.union(BASE_SHAPE, LATCH_EAST);
    protected static final VoxelShape SHAPE_WEST = VoxelShapes.union(BASE_SHAPE, LATCH_WEST);

    public InfiniteChestBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        FluidState fluidState = context.getWorld().getFluidState(context.getBlockPos());
        return this.getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()).with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteChestBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (state.get(WATERLOGGED)) {
            return;
        }

        Direction facing = state.get(FACING);
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.75;
        double centerZ = pos.getZ() + 0.5;
        double frontX = centerX + facing.getOffsetX() * 0.44;
        double frontZ = centerZ + facing.getOffsetZ() * 0.44;
        double sideX = facing.rotateYClockwise().getOffsetX() * 0.18;
        double sideZ = facing.rotateYClockwise().getOffsetZ() * 0.18;

        world.addParticleClient(
                ParticleTypes.ENCHANT,
                frontX + sideX * (random.nextDouble() - 0.5),
                centerY + (random.nextDouble() - 0.5) * 0.18,
                frontZ + sideZ * (random.nextDouble() - 0.5),
                (random.nextDouble() - 0.5) * 0.015,
                0.01 + random.nextDouble() * 0.02,
                (random.nextDouble() - 0.5) * 0.015
        );

        if (random.nextFloat() < 0.35f) {
            world.addImportantParticleClient(
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
            world.addParticleClient(
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
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
            if (screenHandlerFactory != null) {
                player.openHandledScreen(screenHandlerFactory);
                if (world.getBlockEntity(pos) instanceof InfiniteChestBlockEntity be) {
                    net.kasax.challengecraft.network.PacketHandler.syncInfiniteChest((net.minecraft.server.network.ServerPlayerEntity) player, be);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootWorldContext.Builder builder) {
        BlockEntity blockEntity = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        if (blockEntity instanceof InfiniteChestBlockEntity be) {
            builder.addDynamicDrop(ShulkerBoxBlock.CONTENTS_DYNAMIC_DROP_ID, (consumer) -> {
                be.getStorage().getStoredItems().forEach((key, count) -> {
                    long remaining = count;
                    while (remaining > 0) {
                        int toAdd = (int) Math.min(remaining, key.item().getMaxCount());
                        consumer.accept(key.toStack(toAdd));
                        remaining -= toAdd;
                    }
                });
            });
        }
        return super.getDroppedStacks(state, builder);
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!state.isOf(world.getBlockState(pos).getBlock())) {
            world.updateComparators(pos, this);
        }
        super.onStateReplaced(state, world, pos, moved);
    }
}
