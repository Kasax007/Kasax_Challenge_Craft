package net.kasax.challengecraft.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The croupier's booth is built from these: look-alikes of vanilla blocks (their models point at the
 * vanilla ones) that nothing in the game can remove. They cannot be mined (hardness -1), resist any
 * explosion, are immune to withers and the dragon (block tags), do not burn, cannot be pushed by
 * pistons and never let a fluid in — so the House stays open whatever happens around it.
 */
public class CasinoBoothBlock extends Block implements LiquidBlockContainer {
    public enum Kind {
        FLOOR("booth_floor", Shapes.block()),
        TRIM("booth_trim", Shapes.block()),
        CARPET("booth_carpet", Block.box(0, 0, 0, 16, 1, 16)),
        POST("booth_post", Block.box(6, 0, 6, 10, 16, 10)),
        LANTERN("booth_lantern", Shapes.or(Block.box(5, 0, 5, 11, 7, 11), Block.box(6, 7, 6, 10, 9, 10)));

        public final String id;
        final VoxelShape shape;

        Kind(String id, VoxelShape shape) {
            this.id = id;
            this.shape = shape;
        }
    }

    private final Kind kind;

    public CasinoBoothBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return kind.shape;
    }

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity entity, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }
}
