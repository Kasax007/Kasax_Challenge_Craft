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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The invisible upper half of the slot machine. The marquee reaches 0.75 blocks into the cell above
 * the machine, and a ray aimed at it only ever tests the shapes of the cells it passes through, so
 * without this block right-clicking the "777" sign hit nothing. Like the upper half of a door it
 * owns that cell: it gives the marquee a hitbox, forwards clicks to the machine below (see
 * {@link #base}) and takes the machine with it when broken.
 */
public class CasinoSlotTopBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 2.0, 15.0, 6.0, 15.0),
            Block.box(2.0, 6.0, 3.0, 14.0, 12.0, 14.0));

    public CasinoSlotTopBlock(Properties properties) {
        super(properties);
    }

    /** The slot machine a click on {@code pos} is meant for: the block below for a top half. */
    public static BlockPos base(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CasinoSlotTopBlock ? pos.below() : pos;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() == CasinoRegistry.block(DeviceType.SLOT);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && canSurvive(state, level, pos)) {
            // Break the machine itself so it drops (unless in creative), like hitting a door's top.
            level.destroyBlock(pos.below(), !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(CasinoRegistry.item(DeviceType.SLOT));
    }
}
