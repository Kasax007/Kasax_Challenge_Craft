package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotSenses;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Walks into a structure it has seen (village, shipwreck, ruined portal, ...), or explores for one. */
public final class VisitStructureTask implements BotTask {
    private final java.util.Set<Identifier> ids;
    private final String name;
    private final Explorer explorer = new Explorer(6000);
    private BlockPos target;
    private boolean walking;
    private int tries;

    /** A goal's structure ("minecraft:village", "minecraft:shipwreck", ...): one structure or a tag of them. */
    public VisitStructureTask(ServerLevel level, String target) {
        this.ids = resolve(level, target);
        this.name = Identifier.parse(target).getPath();
    }

    /**
     * The structures a goal target stands for: the structure itself, or, for the family names the
     * goals use (village, shipwreck, ruined portal, ocean ruin, mineshaft, camp), all its variants.
     */
    public static java.util.Set<Identifier> resolve(ServerLevel level, String target) {
        Identifier id = Identifier.parse(switch (target) {
            case "minecraft:jungle_temple" -> "minecraft:jungle_pyramid";
            case "minecraft:ocean_monument" -> "minecraft:monument";
            case "minecraft:woodland_mansion" -> "minecraft:mansion";
            default -> target;
        });
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        if (registry.get(ResourceKey.create(Registries.STRUCTURE, id)).isPresent()) return java.util.Set.of(id);
        java.util.Set<Identifier> out = new java.util.HashSet<>();
        registry.get(net.minecraft.tags.TagKey.create(Registries.STRUCTURE, id))
                .ifPresent(set -> set.forEach(h -> h.unwrapKey().ifPresent(k -> out.add(k.identifier()))));
        return out;
    }

    public static boolean inside(Bot bot, java.util.Set<Identifier> ids) {
        ServerLevel level = (ServerLevel) bot.body().level();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        BlockPos p = bot.body().blockPosition();
        for (Identifier id : ids) {
            Structure structure = registry.get(ResourceKey.create(Registries.STRUCTURE, id)).map(h -> h.value()).orElse(null);
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureWithPieceAt(p.getX(), p.getY(), p.getZ(), structure);
            if (start != null && start != StructureStart.INVALID_START && start.isValid()) return true;
        }
        return false;
    }

    /** The nearest seen structure of these kinds, or null. */
    public static BotSenses.SeenStructure nearest(Bot bot, java.util.Set<Identifier> ids) {
        BotSenses.SeenStructure best = null;
        BlockPos from = bot.body().blockPosition();
        for (Identifier id : ids) {
            BotSenses.SeenStructure s = bot.senses().structure(id);
            if (s != null && (best == null || s.spot().distSqr(from) < best.spot().distSqr(from))) best = s;
        }
        return best;
    }

    @Override
    public Result tick(Bot bot) {
        if (inside(bot, ids)) {
            bot.navigator().stop();
            return Result.DONE;
        }
        BotSenses.SeenStructure seen = nearest(bot, ids);
        if (seen == null) {
            walking = false;
            return explorer.tick(bot);
        }
        explorer.pause(bot);
        if (!walking) {
            // First the spot it saw; if that does not count, somewhere else inside that part.
            BlockPos spot = tries == 0 ? seen.spot() : new BlockPos(
                    seen.box().minX() + bot.body().getRandom().nextInt(Math.max(1, seen.box().getXSpan())),
                    seen.spot().getY(),
                    seen.box().minZ() + bot.body().getRandom().nextInt(Math.max(1, seen.box().getZSpan())));
            target = spot;
            bot.navigator().goNear(spot, 1.5);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) {
            walking = false;
            if (++tries > 8) return Result.FAILED;
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "visit a " + name.replace('_', ' ');
    }
}
