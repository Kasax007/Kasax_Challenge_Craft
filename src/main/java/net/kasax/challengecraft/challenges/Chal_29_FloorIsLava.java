package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Flag holder for floor-contact damage handled by movement hooks. */
public class Chal_29_FloorIsLava {
    private static boolean active = false;

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal29] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Whether a block counts as untouched ground, which burns the moment you stand on it.
     *
     * <p>The point of the challenge is that the world's own floor is off limits and you have to
     * carry your own. The old test named three blocks — grass, stone, deepslate — so the challenge
     * did essentially nothing: dirt, sand, gravel, netherrack, snow, terracotta, sandstone, logs and
     * every other surface you actually walk on were all "safe", and only the 3-second standing-still
     * rule ever fired.
     *
     * <p>Vanilla already maintains a list of what terrain is made of: the carver-replaceable tags
     * are the blocks caves cut through, which is as close to "this generated here" as vanilla data
     * gets, and they pull in the whole stone family, dirt/grass/mud/moss, sand, gravel, sandstone,
     * terracotta, snow, calcite, ores, and the nether's netherrack/basalt/blackstone/nylium/soul
     * blocks. Building on tags rather than a hand-written set also means new vanilla terrain blocks
     * are covered without anyone remembering to add them.
     *
     * <p>Everything not listed here — planks, slabs, wool, concrete, glass, anything crafted — stays
     * safe to stand on for a few seconds, which is what makes bridging a viable answer.
     */
    public static boolean isNaturalGround(BlockState state) {
        if (state.isAir()) return false;

        return state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)   // stone family, dirt/grass, sand, gravel, terracotta, snow…
                || state.is(BlockTags.NETHER_CARVER_REPLACEABLES)  // netherrack, basalt, blackstone, nylium, soul sand/soil
                || state.is(BlockTags.ICE)
                || state.is(BlockTags.CORAL_BLOCKS)
                || state.is(Blocks.END_STONE)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SCULK)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.GLOWSTONE);
    }
}
