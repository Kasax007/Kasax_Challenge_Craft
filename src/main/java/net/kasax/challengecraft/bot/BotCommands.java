package net.kasax.challengecraft.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kasax.challengecraft.bot.task.FollowTask;
import net.kasax.challengecraft.bot.task.GoToTask;
import net.kasax.challengecraft.bot.lockout.LockoutBrain;
import net.kasax.challengecraft.bot.task.EatTask;
import net.kasax.challengecraft.bot.task.KillTask;
import net.kasax.challengecraft.bot.task.MineTask;
import net.kasax.challengecraft.bot.task.ObtainTask;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.util.ModPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * {@code /challengecraft_bot ...}: bring bots in and out and give them simple orders by hand (for
 * testing the body before a brain drives it). Operators only.
 */
final class BotCommands {
    private BotCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> BOT_NAMES = (ctx, b) ->
            SharedSuggestionProvider.suggest(BotManager.all().stream().map(bot -> bot.name), b);

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("challengecraft_bot")
                        .requires(ModPermissions::isOp)
                        .then(Commands.literal("spawn").then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    // Where the command comes from (the console: the world spawn, on the ground).
                                    var level = ctx.getSource().getLevel();
                                    var at = ctx.getSource().getPosition();
                                    if (ctx.getSource().getEntity() == null) {
                                        level.getChunk((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4); // generated, so the ground is known
                                        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
                                        at = new net.minecraft.world.phys.Vec3(Math.floor(at.x) + 0.5, y, Math.floor(at.z) + 0.5);
                                    }
                                    String name = StringArgumentType.getString(ctx, "name");
                                    Bot bot = BotManager.spawn(ctx.getSource().getServer(), name, level, at);
                                    ok(ctx, "Bot " + bot.name + " joined");
                                    return 1;
                                })))
                        .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    BotManager.remove(ctx.getSource().getServer(), bot);
                                    ok(ctx, "Bot " + bot.name + " left");
                                    return 1;
                                })))
                        .then(Commands.literal("stop").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.setBrain(null);
                                    return 1;
                                })))
                        .then(Commands.literal("goto").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
                                    bot.doNow(new GoToTask(pos, 0));
                                    return 1;
                                }))))
                        .then(Commands.literal("follow").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.doNow(new FollowTask(ctx.getSource().getPlayerOrException()));
                                    return 1;
                                })))
                        .then(Commands.literal("mine").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("block", StringArgumentType.word())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 256)).executes(ctx -> {
                                            Bot bot = bot(ctx);
                                            if (bot == null) return 0;
                                            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(StringArgumentType.getString(ctx, "block")));
                                            if (block == Blocks.AIR) return 0;
                                            Item item = block.asItem() == Items.AIR ? Items.AIR : block.asItem();
                                            bot.doNow(new MineTask(StringArgumentType.getString(ctx, "block"), s -> s.is(block), java.util.Set.of(item),
                                                    IntegerArgumentType.getInteger(ctx, "count")));
                                            return 1;
                                        })))))
                        .then(Commands.literal("get").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 256)).executes(ctx -> {
                                            Bot bot = bot(ctx);
                                            if (bot == null) return 0;
                                            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(StringArgumentType.getString(ctx, "item")));
                                            if (item == Items.AIR) return 0;
                                            bot.doNow(new ObtainTask(Set.of(item), IntegerArgumentType.getInteger(ctx, "count")));
                                            return 1;
                                        })))))
                        .then(Commands.literal("kill").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("entity", StringArgumentType.word())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
                                            Bot bot = bot(ctx);
                                            if (bot == null) return 0;
                                            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(StringArgumentType.getString(ctx, "entity")));
                                            if (type.isEmpty()) return 0;
                                            bot.doNow(new KillTask(Set.of(type.get()), Set.of(), 0, IntegerArgumentType.getInteger(ctx, "count")));
                                            return 1;
                                        })))))
                        .then(Commands.literal("eat").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.doNow(new EatTask());
                                    return 1;
                                })))
                        .then(Commands.literal("lockout").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> lockout(ctx, LockoutBrain.Difficulty.NORMAL))
                                .then(Commands.argument("difficulty", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"easy", "normal", "hard"}, b))
                                        .executes(ctx -> lockout(ctx, LockoutBrain.Difficulty.valueOf(
                                                StringArgumentType.getString(ctx, "difficulty").toUpperCase(java.util.Locale.ROOT)))))))
                        .then(Commands.literal("biome").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.doNow(new net.kasax.challengecraft.bot.task.GoToBiomeTask(Identifier.parse(ns(StringArgumentType.getString(ctx, "id")))));
                                    return 1;
                                }))))
                        .then(Commands.literal("structure").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.doNow(new net.kasax.challengecraft.bot.task.VisitStructureTask(bot.body().level(), ns(StringArgumentType.getString(ctx, "id"))));
                                    return 1;
                                }))))
                        .then(Commands.literal("milk").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.doNow(new net.kasax.challengecraft.bot.task.UseOnMobTask(net.minecraft.world.entity.EntityTypes.COW, Items.BUCKET, e -> !e.isBaby()));
                                    return 1;
                                })))
                        .then(Commands.literal("portal").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.doNow(new net.kasax.challengecraft.bot.task.PortalTask(bot.body().level()));
                                    return 1;
                                })))
                        .then(Commands.literal("bench").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10))
                                        .executes(ctx -> bench(ctx, LockoutBrain.Difficulty.HARD))
                                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                                .executes(ctx -> bench(ctx, LockoutBrain.Difficulty.valueOf(
                                                        StringArgumentType.getString(ctx, "difficulty").toUpperCase(java.util.Locale.ROOT))))))))
                        .then(Commands.literal("estimate").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(ctx -> {
                                            // Test: what the planner thinks an item costs from here, and its first step.
                                            Bot bot = bot(ctx);
                                            if (bot == null) return 0;
                                            var item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(ns(StringArgumentType.getString(ctx, "item"))));
                                            var planner = new net.kasax.challengecraft.bot.plan.ObtainPlanner();
                                            double cost = planner.estimate(bot, java.util.Set.of(item), 1);
                                            var plan = planner.plan(bot, java.util.Set.of(item), 1);
                                            String first = plan instanceof net.kasax.challengecraft.bot.plan.ObtainPlanner.Step st ? st.task().describe() : plan.toString();
                                            ok(ctx, String.format("%s: ~%.0f s, first %s", item, cost, first));
                                            return 1;
                                        }))))
                        .then(Commands.literal("coverage").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    // Test: which goals of the pool the bot has no way for at all (from here), by type.
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    var planner = new net.kasax.challengecraft.bot.plan.ObtainPlanner();
                                    java.util.Map<String, java.util.List<String>> missing = new java.util.TreeMap<>();
                                    int total = 0;
                                    for (var g : net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool.all()) {
                                        total++;
                                        boolean covered;
                                        try {
                                            covered = net.kasax.challengecraft.bot.lockout.LockoutGoals.covers(bot, planner, g);
                                        } catch (RuntimeException e) {
                                            covered = false;
                                        }
                                        if (!covered) missing.computeIfAbsent(g.type().name(), k -> new java.util.ArrayList<>()).add(g.id());
                                    }
                                    int n = missing.values().stream().mapToInt(java.util.List::size).sum();
                                    LOG_COVERAGE.info("[COVERAGE] {} of {} goals without a way: {}", n, total, missing);
                                    ok(ctx, n + " of " + total + " goals without a way (see the log)");
                                    return 1;
                                })))
                        .then(Commands.literal("nether").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    // Test: the way into the Nether the Lockout brain would pick, from scratch.
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    var option = net.kasax.challengecraft.bot.lockout.LockoutGoals.netherForTest(bot, new net.kasax.challengecraft.bot.plan.ObtainPlanner());
                                    if (option == null) {
                                        ok(ctx, "no way to the Nether from here");
                                        return 0;
                                    }
                                    net.kasax.challengecraft.bot.BotTask task = option.task().get();
                                    bot.doNow(task);
                                    ok(ctx, String.format("%s (~%.0f s)", task.describe(), option.cost()));
                                    return 1;
                                })))
                        .then(Commands.literal("cast").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.doNow(new net.kasax.challengecraft.bot.task.CastPortalTask(bot.body().level()));
                                    return 1;
                                })))
                        .then(Commands.literal("pour").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    // Test: pour the first filled bucket into this spot (against a solid neighbour).
                                    BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
                                    Item bucket = BotInventory.slotOf(bot.body(), Items.LAVA_BUCKET) >= 0 ? Items.LAVA_BUCKET : Items.WATER_BUCKET;
                                    ok(ctx, "poured " + bucket + ": " + net.kasax.challengecraft.bot.task.CastPortalTask.pourIntoForTest(bot, bot.body().level(), bucket, pos));
                                    return 1;
                                }))))
                        .then(Commands.literal("surface").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.doNow(new net.kasax.challengecraft.bot.task.SurfaceTask());
                                    return 1;
                                })))
                        .then(Commands.literal("senses").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    bot.senses().refresh();
                                    bot.senses().tick(bot.body());
                                    ok(ctx, bot.name + " sees biomes " + bot.senses().biomes().keySet().stream().map(Identifier::getPath).sorted().toList()
                                            + ", structures " + bot.senses().structures().keySet().stream().map(Identifier::getPath).sorted().toList()
                                            + ", loot chests " + bot.senses().lootables().size()
                                            + "; memory: " + bot.memory().summary(bot.body().level().dimension()));
                                    return 1;
                                })))
                        .then(Commands.literal("status").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    ok(ctx, bot.name + " at " + bot.body().blockPosition().toShortString() + ", nav " + bot.navigator().status() + " (" + bot.navigator().debug() + "):" + bot.status());
                                    return 1;
                                })))
                        .then(Commands.literal("inv").then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                .executes(ctx -> {
                                    Bot bot = bot(ctx);
                                    if (bot == null) return 0;
                                    StringBuilder sb = new StringBuilder(bot.name + ":");
                                    for (var st : bot.body().getInventory().getNonEquipmentItems()) {
                                        if (!st.isEmpty()) sb.append(' ').append(st.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()).append(',');
                                    }
                                    ok(ctx, sb.toString());
                                    return 1;
                                })))
        ));
    }

    /**
     * Lets the bot play Lockout: joins the running game or the lobby, or, when there is none,
     * starts one of the command's sender against the bot.
     */
    private static int lockout(CommandContext<CommandSourceStack> ctx, LockoutBrain.Difficulty difficulty) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Bot bot = bot(ctx);
        if (bot == null) return 0;
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        var server = ctx.getSource().getServer();
        if (!Chal_40_LockoutBingo.isActive() || Chal_40_LockoutBingo.teamOf(server, me.getUUID()) == null
                && !Chal_40_LockoutBingo.isRunning(server)) {
            Chal_40_LockoutBingo.startVersus(me, bot.body(), java.util.List.of());
        }
        bot.setBrain(new LockoutBrain(difficulty));
        ok(ctx, bot.name + " plays Lockout (" + difficulty.name().toLowerCase(java.util.Locale.ROOT) + ")");
        return 1;
    }

    /** A solo Lockout game for the bot alone, measured, with a report at the end (see {@link BotBenchmark}). */
    private static int bench(CommandContext<CommandSourceStack> ctx, LockoutBrain.Difficulty difficulty) {
        Bot bot = bot(ctx);
        if (bot == null) return 0;
        int seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds");
        BotBenchmark.start(bot, seconds * 20, difficulty);
        ok(ctx, bot.name + " benchmark: " + seconds + " s of Lockout (" + difficulty.name().toLowerCase(java.util.Locale.ROOT) + ")");
        return 1;
    }

    private static final org.slf4j.Logger LOG_COVERAGE = org.slf4j.LoggerFactory.getLogger("ChallengeCraft-Bot");

    /** Ids typed without a namespace are Minecraft's. */
    private static String ns(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static Bot bot(CommandContext<CommandSourceStack> ctx) {
        Bot bot = BotManager.byName(StringArgumentType.getString(ctx, "name"));
        if (bot == null) ctx.getSource().sendFailure(Component.literal("No such bot"));
        return bot;
    }

    private static void ok(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }
}
