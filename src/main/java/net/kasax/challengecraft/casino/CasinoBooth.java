package net.kasax.challengecraft.casino;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/**
 * The croupier's booth next to world spawn: a small carpeted platform with a cashier counter, four
 * lantern posts and the croupier behind the counter. Built once per run, the first time the spawn
 * chunk is loaded with the challenge active; the croupier is respawned if he ever goes missing.
 *
 * <p>Blocks are looked up by id rather than through {@code Blocks} fields — 26.x folded the dyed
 * variants into grouped fields, and an id is the one name that is certain to survive that.
 */
public final class CasinoBooth {
    private static final int OFFSET_X = 4;
    private static int missingChecks;

    private CasinoBooth() {
    }

    private static BlockState block(String id) {
        Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + id));
        return b == null ? Blocks.AIR.defaultBlockState() : b.defaultBlockState();
    }

    public static BlockPos anchor(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().pos();
        int x = spawn.getX() + OFFSET_X;
        int z = spawn.getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // Next to a Skyblock island (or a cliff) the column can drop into the void; the booth then
        // stands at spawn height on its own platform instead of at the bottom of the world.
        if (y < spawn.getY() - 6 || y > spawn.getY() + 12) y = spawn.getY();
        return new BlockPos(x, y, z);
    }

    /** Called every few seconds while the challenge runs. */
    public static void ensure(MinecraftServer server) {
        ServerLevel level = server.overworld();
        CasinoSavedData data = CasinoSavedData.get(server);
        BlockPos spawn = level.getRespawnData().pos();
        if (!level.isLoaded(spawn)) return;

        if (!data.isBoothBuilt()) {
            BlockPos a = anchor(level);
            build(level, a);
            data.setBoothBuilt(true, a.asLong());
            ChallengeCraft.LOGGER.info("[Casino] booth built at {}", a);
        }
        BlockPos a = BlockPos.of(data.getBoothPos());
        if (!level.isLoaded(a)) return;

        CroupierEntity croupier = croupier(server);
        if (croupier == null) {
            // Entities load a little after their chunk: adopt one that is already standing there
            // (and clear out any duplicate) before concluding he is gone.
            java.util.List<CroupierEntity> present = level.getEntitiesOfClass(CroupierEntity.class,
                    new net.minecraft.world.phys.AABB(a).inflate(12));
            if (!present.isEmpty()) {
                data.setCroupier(present.get(0).getUUID().toString());
                for (int i = 1; i < present.size(); i++) present.get(i).discard();
                return;
            }
            // A croupier who existed before may just not be loaded yet; a brand-new booth needs no wait.
            if (!data.getCroupier().isEmpty() && ++missingChecks < 3) return;
            missingChecks = 0;
            CroupierEntity fresh = CasinoRegistry.CROUPIER.create(level, EntitySpawnReason.EVENT);
            if (fresh == null) return;
            fresh.snapTo(a.getX() + 1.5, a.getY(), a.getZ() + 0.5, 90f, 0f);
            fresh.setYHeadRot(90f);
            fresh.setCustomName(net.minecraft.network.chat.Component.translatable("entity.challengecraft.croupier"));
            fresh.setCustomNameVisible(true);
            level.addFreshEntity(fresh);
            data.setCroupier(fresh.getUUID().toString());
        }
    }

    public static CroupierEntity croupier(MinecraftServer server) {
        CasinoSavedData data = CasinoSavedData.get(server);
        if (data.getCroupier().isEmpty()) return null;
        try {
            Entity e = server.overworld().getEntity(UUID.fromString(data.getCroupier()));
            return e instanceof CroupierEntity c && c.isAlive() ? c : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void gesture(MinecraftServer server, int kind) {
        CroupierEntity c = croupier(server);
        if (c != null) c.gesture(kind);
    }

    /**
     * Layout, seen from above with spawn to the west (left):
     * <pre>
     *   L . . . L        L lantern post
     *   . . C . .        C cashier counter (three wide, facing west)
     *   . . C D .        D the croupier
     *   . . C . .        . carpet on polished blackstone
     *   L . . . L
     * </pre>
     */
    private static void build(ServerLevel level, BlockPos a) {
        BlockState floor = block("polished_blackstone_bricks");
        BlockState trim = block("gold_block");
        BlockState carpet = block("red_carpet");
        BlockState post = block("dark_oak_fence");
        BlockState lantern = block("lantern");
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                level.setBlockAndUpdate(a.offset(dx, -1, dz), corner ? trim : floor);
                for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(a.offset(dx, dy, dz), air);
                if (corner) {
                    level.setBlockAndUpdate(a.offset(dx, 0, dz), post);
                    level.setBlockAndUpdate(a.offset(dx, 1, dz), post);
                    level.setBlockAndUpdate(a.offset(dx, 2, dz), lantern);
                } else {
                    level.setBlockAndUpdate(a.offset(dx, 0, dz), carpet);
                }
            }
        }
        BlockState counter = CasinoRegistry.block(DeviceType.CASHIER).defaultBlockState()
                .setValue(CasinoDeviceBlock.FACING, Direction.WEST);
        for (int dz = -1; dz <= 1; dz++) {
            level.setBlockAndUpdate(a.offset(0, 0, dz), counter);
        }
        // Carpet under the croupier's feet is part of the platform already.
    }
}
