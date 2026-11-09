package net.kasax.challengecraft.bot.lockout;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.lockout.sim.Board;
import net.kasax.challengecraft.bot.lockout.sim.Decision;
import net.kasax.challengecraft.bot.lockout.sim.GoalBook;
import net.kasax.challengecraft.bot.lockout.sim.Ms;
import net.kasax.challengecraft.bot.lockout.sim.Place;
import net.kasax.challengecraft.bot.lockout.sim.PlayerState;
import net.kasax.challengecraft.bot.lockout.sim.Profile;
import net.kasax.challengecraft.bot.lockout.sim.Res;
import net.kasax.challengecraft.bot.lockout.sim.RolloutPolicy;
import net.kasax.challengecraft.bot.lockout.sim.SearchPolicy;
import net.kasax.challengecraft.bot.lockout.sim.Sim;
import net.kasax.challengecraft.bot.lockout.sim.World;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The look-ahead board brain in the real game (the {@code search} knob): the game as it stands is
 * put into the abstract simulator ({@code bot.lockout.sim}) - the board, what Bob carries and
 * where he is, what he knows of the world, the time of day, what can be known of the opponent - and
 * {@link SearchPolicy} plays it out to pick among the tiles Bob knows a way to. What it believes a
 * tile takes (the simulator's calibrated figures, not Bob's own, which are four to nine times too
 * low for monsters) also sets the tile's time allowance.
 *
 * <p>Cheap enough to run on the server thread: a few milliseconds per decision, at most
 * {@link #BUDGET_MILLIS} of wall clock (in the self-play simulation mode the same work whatever the
 * clock says, so a league plays the same at any speed). Anything going wrong falls back to the
 * route of the old brain (null).
 */
final class BoardSearch {
    /** Wall-clock milliseconds a decision may take (outside the simulation mode). */
    static final double BUDGET_MILLIS = 40;

    private final SearchPolicy policy = new SearchPolicy();
    private int decisions;

    BoardSearch() {
        policy.asyncModel = !net.kasax.challengecraft.bot.BotNavigator.SIM;
        policy.budgetMillis = net.kasax.challengecraft.bot.BotNavigator.SIM ? 0 : BUDGET_MILLIS;
    }

    /** What the search decided: the tile (board index), the seconds it believes it takes, a line for the log. */
    record Pick(int tile, double seconds, String why) {
    }

    /**
     * The tile to go for among {@code choices} (board indices Bob knows a way to), or null to fall
     * back to the old route.
     */
    Pick pick(Bot bot, List<Integer> choices) {
        try {
            long t0 = System.nanoTime();
            Sim sim = snapshot(bot);
            if (sim == null) return null;
            policy.solo = soloGame(bot);
            long allowed = 0;
            for (int t : choices) allowed |= 1L << t;
            policy.allowed = allowed;
            Decision d = policy.choose(sim, 0);
            decisions++;
            if (d == null || d.tile < 0 || (allowed & (1L << d.tile)) == 0) return null;
            double ms = (System.nanoTime() - t0) / 1e6;
            return new Pick(d.tile, d.estimate, String.format("look-ahead: %s (~%d s; %s; %.0f ms)", d.tile >= 0 ? sim.board.goal[d.tile].id : "-",
                    Math.round(d.estimate), d.why, ms));
        } catch (RuntimeException e) {
            BotManager.LOG.warn("[Bot] {}: look-ahead failed, the route decides ({})", bot.name, e.toString());
            return null;
        }
    }

    // ---- the game as the simulator sees it ----------------------------------------------------

    /** The game now as a simulator game, Bob as player 0; null if it cannot be put so (no board, three teams...). */
    static Sim snapshot(Bot bot) {
        MinecraftServer server = bot.server();
        List<Chal_40_LockoutBingo.BoardTile> tiles = Chal_40_LockoutBingo.board(server);
        if (tiles.size() != Board.SIZE) return null;
        LockoutBingoTeam mine = Chal_40_LockoutBingo.teamOf(server, bot.id);
        if (mine == null) return null;
        // The opponent: the best other team (a game of more teams is played as against the strongest).
        ServerPlayer rival = null;
        LockoutBingoTeam rivalTeam = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            LockoutBingoTeam t = Chal_40_LockoutBingo.teamOf(server, p.getUUID());
            if (t == null || t == mine) continue;
            if (rival == null || Chal_40_LockoutBingo.score(server, t) > Chal_40_LockoutBingo.score(server, rivalTeam)) {
                rival = p;
                rivalTeam = t;
            }
        }
        List<String> ids = new ArrayList<>(), cats = new ArrayList<>(), types = new ArrayList<>();
        for (Chal_40_LockoutBingo.BoardTile t : tiles) {
            LockoutBingoGoal g = t.goal();
            if (GoalBook.get(g.id()) == null) return null; // (a goal the simulator does not know yet)
            ids.add(g.id());
            cats.add(g.category().name());
            types.add(g.type().name());
        }
        Board board = Board.of(ids, cats, types);
        double elapsed = Chal_40_LockoutBingo.elapsedTicks(server) / 20.0;
        // The simulator's clock: the game's seconds, moved on (less than a day) to the day's time, so
        // that its nights are the real ones.
        double tod = (bot.body().level().getOverworldClockTime() % 24000) / 20.0;
        double now = elapsed + ((tod - elapsed) % 1200 + 1200) % 1200;
        for (Chal_40_LockoutBingo.BoardTile t : tiles) {
            if (t.claimedBy() != null) board.claim(t.index(), t.claimedBy() == mine ? 0 : 1, now);
        }
        double left = net.kasax.challengecraft.bot.BotVersus.secondsLeft();
        double length = left >= 0 ? now + left : Math.max(5400, now + 2400);
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getRespawnData().pos();
        World world = World.sample(overworld.getSeed() ^ 0x5EA4C4L);
        long known = placeKnowledge(bot, world, spawn);
        boolean rivalBot = rival != null && BotManager.isBot(rival);
        Profile me = Profile.bob(), them = rivalBot ? Profile.bob() : Profile.expert();
        Sim sim = new Sim(world, board, me, them, new RolloutPolicy(true), new RolloutPolicy(false), length, overworld.getSeed() ^ (long) now);
        sim.now = now;
        PlayerState p = sim.players[0];
        carry(bot, p);
        p.known = known;
        p.fedAt = now + 120;
        BlockPos at = bot.body().blockPosition();
        if (bot.body().level().dimension() == Level.NETHER) {
            p.dim = 1;
            BlockPos portal = bot.senses().knownPortal(at);
            p.x = portal == null ? 0 : at.getX() - portal.getX();
            p.z = portal == null ? 0 : at.getZ() - portal.getZ();
        } else {
            p.x = at.getX() - spawn.getX();
            p.z = at.getZ() - spawn.getZ();
            int surface = bot.body().level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            p.layer = at.getY() >= surface - 8 ? 0 : at.getY() >= 0 ? 1 : 2;
        }
        if (bot.senses().knownPortal(at) != null) {
            p.ms |= Ms.PORTAL.bit;
            sim.portalLit = true;
        }
        // The opponent: what a player can know - its tiles (the board) and its advancements.
        PlayerState o = sim.players[1];
        if (rival != null) {
            o.ms = advancements(server, rival);
            o.dim = rival.level().dimension() == Level.NETHER ? 1 : 0;
        }
        o.known = world.knownAtStart(true);
        return sim;
    }

    /** Nobody on another team (a field test, a game alone). */
    private static boolean soloGame(Bot bot) {
        MinecraftServer server = bot.server();
        LockoutBingoTeam mine = Chal_40_LockoutBingo.teamOf(server, bot.id);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            LockoutBingoTeam t = Chal_40_LockoutBingo.teamOf(server, p.getUUID());
            if (t != null && t != mine) return false;
        }
        return true;
    }

    /** Milestones a player's advancements give away. */
    private static int advancements(MinecraftServer server, ServerPlayer p) {
        int ms = 0;
        if (done(server, p, "minecraft:story/mine_stone")) ms |= Ms.STONE.bit | Ms.WOOD.bit;
        if (done(server, p, "minecraft:story/iron_tools")) ms |= Ms.IRON_PICK.bit;
        if (done(server, p, "minecraft:story/enter_the_nether")) ms |= Ms.NETHER_VISITED.bit | Ms.PORTAL.bit;
        if (done(server, p, "minecraft:story/obtain_armor")) ms |= Ms.ARMOR.bit;
        return ms;
    }

    private static boolean done(MinecraftServer server, ServerPlayer p, String id) {
        var holder = server.getAdvancements().get(Identifier.parse(id));
        return holder != null && p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    // ---- what Bob carries -----------------------------------------------------------------------

    private static final Map<Item, Res> RESOURCES = Map.ofEntries(
            Map.entry(Items.IRON_INGOT, Res.IRON), Map.entry(Items.RAW_IRON, Res.IRON),
            Map.entry(Items.GOLD_INGOT, Res.GOLD), Map.entry(Items.RAW_GOLD, Res.GOLD),
            Map.entry(Items.COPPER_INGOT, Res.COPPER), Map.entry(Items.RAW_COPPER, Res.COPPER),
            Map.entry(Items.COAL, Res.COAL), Map.entry(Items.CHARCOAL, Res.COAL),
            Map.entry(Items.DIAMOND, Res.DIAMOND), Map.entry(Items.REDSTONE, Res.REDSTONE), Map.entry(Items.LAPIS_LAZULI, Res.LAPIS),
            Map.entry(Items.EMERALD, Res.EMERALD), Map.entry(Items.STRING, Res.STRING), Map.entry(Items.BONE, Res.BONE),
            Map.entry(Items.GUNPOWDER, Res.GUNPOWDER), Map.entry(Items.ENDER_PEARL, Res.ENDER_PEARL), Map.entry(Items.SLIME_BALL, Res.SLIME),
            Map.entry(Items.LEATHER, Res.LEATHER), Map.entry(Items.FEATHER, Res.FEATHER), Map.entry(Items.INK_SAC, Res.INK),
            Map.entry(Items.PAPER, Res.PAPER), Map.entry(Items.SUGAR_CANE, Res.PAPER), Map.entry(Items.CLAY_BALL, Res.CLAY),
            Map.entry(Items.SAND, Res.SAND), Map.entry(Items.RED_SAND, Res.SAND), Map.entry(Items.FLINT, Res.FLINT),
            Map.entry(Items.WHEAT, Res.WHEAT), Map.entry(Items.OBSIDIAN, Res.OBSIDIAN), Map.entry(Items.BLAZE_ROD, Res.BLAZE_ROD),
            Map.entry(Items.QUARTZ, Res.QUARTZ), Map.entry(Items.GLOWSTONE_DUST, Res.GLOWSTONE), Map.entry(Items.NETHER_WART, Res.NETHER_WART),
            Map.entry(Items.MAGMA_CREAM, Res.MAGMA_CREAM));

    /** Bob's pack (and what he wears) as milestones and resources. */
    static void carry(Bot bot, PlayerState p) {
        var body = bot.body();
        List<ItemStack> all = new ArrayList<>(body.getInventory().getNonEquipmentItems());
        for (EquipmentSlot s : EquipmentSlot.values()) all.add(body.getItemBySlot(s));
        int ms = 0, planks = 0;
        for (ItemStack st : all) {
            if (st.isEmpty()) continue;
            Item i = st.getItem();
            Res r = RESOURCES.get(i);
            if (r != null) p.res[r.ordinal()] += st.getCount();
            if (st.is(net.minecraft.tags.ItemTags.WOOL)) p.res[Res.WOOL.ordinal()] += st.getCount();
            if (st.has(net.minecraft.core.component.DataComponents.FOOD)) p.res[Res.FOOD.ordinal()] += st.getCount();
            if (st.is(net.minecraft.tags.ItemTags.PLANKS)) planks += st.getCount();
            if (st.is(net.minecraft.tags.ItemTags.LOGS)) planks += 4 * st.getCount();
            if (st.is(net.minecraft.tags.ItemTags.BEDS)) ms |= Ms.BED.bit;
            if (i == Items.WOODEN_PICKAXE || i == Items.CRAFTING_TABLE) ms |= Ms.WOOD.bit;
            if (i == Items.STONE_PICKAXE) ms |= Ms.WOOD.bit | Ms.STONE.bit;
            if (i == Items.IRON_PICKAXE || i == Items.DIAMOND_PICKAXE || i == Items.NETHERITE_PICKAXE) ms |= Ms.WOOD.bit | Ms.STONE.bit | Ms.IRON_PICK.bit;
            if (i == Items.DIAMOND_PICKAXE || i == Items.NETHERITE_PICKAXE) ms |= Ms.DIAMOND_PICK.bit;
            if (i == Items.FURNACE) ms |= Ms.FURNACE.bit;
            if (i == Items.BUCKET || i == Items.WATER_BUCKET || i == Items.LAVA_BUCKET || i == Items.MILK_BUCKET) ms |= Ms.BUCKET.bit;
            if (i == Items.WATER_BUCKET) ms |= Ms.WATER_BUCKET.bit;
            if (i == Items.SHIELD) ms |= Ms.SHIELD.bit;
            if (i == Items.SHEARS) ms |= Ms.SHEARS.bit;
            if (i == Items.FLINT_AND_STEEL) ms |= Ms.FLINT_STEEL.bit;
            if (i == Items.BOW) ms |= Ms.BOW.bit;
            if (i == Items.FISHING_ROD) ms |= Ms.ROD.bit;
            if (i == Items.IRON_CHESTPLATE || i == Items.DIAMOND_CHESTPLATE || i == Items.NETHERITE_CHESTPLATE) ms |= Ms.ARMOR.bit;
            if (i == Items.COPPER_CHESTPLATE || i == Items.COPPER_LEGGINGS) ms |= Ms.COPPER_ARMOR.bit;
            if (i == Items.BREWING_STAND) ms |= Ms.BREWING_STAND.bit;
            if (i == Items.ENCHANTING_TABLE) ms |= Ms.ENCHANTING_TABLE.bit;
            if (i == Items.BUNDLE) ms |= Ms.BUNDLE.bit;
            if (i == Items.BLAZE_POWDER) p.res[Res.BLAZE_ROD.ordinal()] += st.getCount() / 2;
            if (i == Items.GLOWSTONE) p.res[Res.GLOWSTONE.ordinal()] += 4 * st.getCount();
            if (i == Items.CLAY) p.res[Res.CLAY.ordinal()] += 4 * st.getCount();
            if (i == Items.BONE_MEAL) p.res[Res.BONE.ordinal()] += st.getCount() / 3;
        }
        if (planks >= 4) ms |= Ms.WOOD.bit;
        if (body.level().dimension() == Level.NETHER) ms |= Ms.NETHER_VISITED.bit;
        p.ms = ms;
    }

    // ---- what Bob knows of the world ----------------------------------------------------------------

    /** The structures of each place (as the goals name them). */
    private static final Map<Place, String> STRUCTURES = Map.ofEntries(
            Map.entry(Place.VILLAGE, "minecraft:village"), Map.entry(Place.CAMP, "minecraft:abandoned_camp"),
            Map.entry(Place.RUINED_PORTAL, "minecraft:ruined_portal"), Map.entry(Place.SHIPWRECK, "minecraft:shipwreck"),
            Map.entry(Place.OCEAN_RUIN, "minecraft:ocean_ruin"), Map.entry(Place.MONUMENT, "minecraft:ocean_monument"),
            Map.entry(Place.DESERT_PYRAMID, "minecraft:desert_pyramid"), Map.entry(Place.JUNGLE_TEMPLE, "minecraft:jungle_temple"),
            Map.entry(Place.IGLOO, "minecraft:igloo"), Map.entry(Place.WITCH_HUT, "minecraft:swamp_hut"),
            Map.entry(Place.OUTPOST, "minecraft:pillager_outpost"), Map.entry(Place.MANSION, "minecraft:woodland_mansion"),
            Map.entry(Place.MINESHAFT, "minecraft:mineshaft"), Map.entry(Place.ANCIENT_CITY, "minecraft:ancient_city"),
            Map.entry(Place.TRIAL_CHAMBERS, "minecraft:trial_chambers"), Map.entry(Place.FORTRESS, "minecraft:fortress"),
            Map.entry(Place.BASTION, "minecraft:bastion_remnant"));

    /** The biomes of each place. */
    private static final Map<Place, List<String>> BIOMES = Map.ofEntries(
            Map.entry(Place.OCEAN, List.of("ocean", "deep_ocean", "cold_ocean", "lukewarm_ocean", "deep_cold_ocean", "deep_lukewarm_ocean")),
            Map.entry(Place.WARM_OCEAN, List.of("warm_ocean")), Map.entry(Place.DESERT, List.of("desert")),
            Map.entry(Place.SAVANNA, List.of("savanna", "savanna_plateau", "windswept_savanna")), Map.entry(Place.SWAMP, List.of("swamp")),
            Map.entry(Place.MANGROVE, List.of("mangrove_swamp")), Map.entry(Place.JUNGLE, List.of("jungle", "sparse_jungle")),
            Map.entry(Place.BAMBOO, List.of("bamboo_jungle")),
            Map.entry(Place.SNOWY, List.of("snowy_plains", "snowy_taiga", "ice_spikes", "grove", "snowy_slopes")),
            Map.entry(Place.BADLANDS, List.of("badlands", "eroded_badlands", "wooded_badlands")), Map.entry(Place.MUSHROOM, List.of("mushroom_fields")),
            Map.entry(Place.CHERRY, List.of("cherry_grove")), Map.entry(Place.DARK_FOREST, List.of("dark_forest")),
            Map.entry(Place.DAPPLED, List.of("dappled_forest")),
            Map.entry(Place.MOUNTAIN, List.of("jagged_peaks", "frozen_peaks", "stony_peaks", "meadow", "windswept_hills")),
            Map.entry(Place.DRIPSTONE, List.of("dripstone_caves")), Map.entry(Place.LUSH, List.of("lush_caves")),
            Map.entry(Place.DEEP_DARK, List.of("deep_dark")), Map.entry(Place.WASTES, List.of("nether_wastes")),
            Map.entry(Place.CRIMSON, List.of("crimson_forest")), Map.entry(Place.WARPED, List.of("warped_forest")),
            Map.entry(Place.SOUL_VALLEY, List.of("soul_sand_valley")), Map.entry(Place.DELTAS, List.of("basalt_deltas")));

    /**
     * Puts what Bob knows into the world - the structures and biomes he has seen or senses, the lava
     * pools he remembers - at their real places (Overworld: from the spawn; Nether: from the portal
     * he knows there); returns the places known (bit per place).
     */
    static long placeKnowledge(Bot bot, World world, BlockPos spawn) {
        long known = World.bit(Place.HERE) | World.bit(Place.CAVE) | World.bit(Place.DEEP) | World.bit(Place.WATER) | World.bit(Place.NETHER);
        ServerLevel level = (ServerLevel) bot.body().level();
        boolean nether = level.dimension() == Level.NETHER;
        BlockPos origin = nether ? java.util.Objects.requireNonNullElse(bot.senses().knownPortal(bot.body().blockPosition()), bot.body().blockPosition()) : spawn;
        for (var e : STRUCTURES.entrySet()) {
            if (e.getKey().dim == 1 != nether) continue;
            var seen = net.kasax.challengecraft.bot.task.VisitStructureTask.nearest(bot, net.kasax.challengecraft.bot.task.VisitStructureTask.resolve(level, e.getValue()));
            if (seen == null) continue;
            world.set(e.getKey(), true, seen.spot().getX() - origin.getX(), seen.spot().getZ() - origin.getZ());
            known |= World.bit(e.getKey());
        }
        for (var e : BIOMES.entrySet()) {
            if (e.getKey().dim == 1 != nether) continue;
            BlockPos best = null;
            for (String b : e.getValue()) {
                BlockPos at = bot.senses().biome(Identifier.parse("minecraft:" + b));
                if (at != null && (best == null || at.distSqr(bot.body().blockPosition()) < best.distSqr(bot.body().blockPosition()))) best = at;
            }
            if (best == null) continue;
            world.set(e.getKey(), true, best.getX() - origin.getX(), best.getZ() - origin.getZ());
            known |= World.bit(e.getKey());
        }
        if (!nether) {
            BlockPos lava = bot.memory().nearest(level, bot.body().blockPosition(),
                    st -> st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && st.getFluidState().isSource(), java.util.Set.of());
            if (lava != null) {
                world.set(Place.LAVA_POOL, true, lava.getX() - origin.getX(), lava.getZ() - origin.getZ());
                known |= World.bit(Place.LAVA_POOL);
            }
        }
        return known;
    }

    int decisions() {
        return decisions;
    }
}
