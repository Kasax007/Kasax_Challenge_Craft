package net.kasax.challengecraft.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kasax.challengecraft.bot.task.FollowTask;
import net.kasax.challengecraft.bot.task.GoToTask;
import net.kasax.challengecraft.bot.task.EatTask;
import net.kasax.challengecraft.bot.task.KillTask;
import net.kasax.challengecraft.bot.task.MineTask;
import net.kasax.challengecraft.bot.task.ObtainTask;
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
                                    ServerPlayer me = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    Bot bot = BotManager.spawn(ctx.getSource().getServer(), name, me.level(), me.position());
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

    private static Bot bot(CommandContext<CommandSourceStack> ctx) {
        Bot bot = BotManager.byName(StringArgumentType.getString(ctx, "name"));
        if (bot == null) ctx.getSource().sendFailure(Component.literal("No such bot"));
        return bot;
    }

    private static void ok(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }
}
