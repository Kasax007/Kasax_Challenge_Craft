package net.kasax.challengecraft.casino;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kasax.challengecraft.util.ModPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator commands for testing and tuning "The House Always Wins":
 * <pre>
 *   /casino chips &lt;amount&gt;     add chips to your own account (negative takes them away)
 *   /casino fee                  collect the next fee right now
 *   /casino feescale &lt;percent&gt;  scale the whole fee curve (100 = as designed)
 *   /casino unlock               unlock all three devices and their recipes for yourself
 *   /casino booth                rebuild the croupier's booth at world spawn
 *   /casino status               balances, fee and totals in chat
 * </pre>
 */
public final class CasinoCommands {
    private CasinoCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("casino")
                        .requires(source -> ModPermissions.isOp(source))
                        .then(Commands.literal("chips").then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CasinoAccount a = CasinoEconomy.account(p);
                                    a.balance = Math.max(0, a.balance + (long) amount * CasinoAccount.CENTI);
                                    CasinoSavedData.get(ctx.getSource().getServer()).touch();
                                    CasinoEconomy.sync(p);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Balance: "
                                            + CasinoEconomy.formatFull(a.balance)).withStyle(ChatFormatting.GOLD), false);
                                    return 1;
                                })))
                        .then(Commands.literal("fee").executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            CasinoSavedData data = CasinoSavedData.get(server);
                            // Pretend the run clock just reached the next collection.
                            int index = data.getFeesCharged() + 1;
                            long fee = CasinoEconomy.fee(server, index);
                            long total = CasinoEconomy.total(server);
                            if (total < fee) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Fee " + CasinoEconomy.formatFull(fee)
                                        + " > team total " + CasinoEconomy.formatFull(total) + " - that would bankrupt the run. "
                                        + "Give chips first.").withStyle(ChatFormatting.RED), false);
                                return 0;
                            }
                            java.util.Map<String, Long> shares = CasinoEconomy.shares(server, fee);
                            shares.forEach((k, v) -> {
                                CasinoAccount a = data.accounts().get(k);
                                a.balance -= v;
                                a.feesPaid += v;
                            });
                            data.setFeesCharged(index);
                            data.addFeesTotal(fee);
                            CasinoEconomy.syncAll(server);
                            ctx.getSource().sendSuccess(() -> Component.literal("Collected fee #" + index + ": "
                                    + CasinoEconomy.formatFull(fee)).withStyle(ChatFormatting.GOLD), true);
                            return 1;
                        }))
                        .then(Commands.literal("feescale").then(Commands.argument("percent", IntegerArgumentType.integer(1, 10000))
                                .executes(ctx -> {
                                    int pct = IntegerArgumentType.getInteger(ctx, "percent");
                                    CasinoSavedData.get(ctx.getSource().getServer()).setFeeScale(pct);
                                    CasinoEconomy.syncAll(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Fee scale: " + pct + " %")
                                            .withStyle(ChatFormatting.GOLD), true);
                                    return 1;
                                })))
                        .then(Commands.literal("unlock").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            CasinoAccount a = CasinoEconomy.account(p);
                            for (DeviceType t : DeviceType.values()) {
                                if (t.purchasable) {
                                    a.unlocked.add(t.id);
                                    CasinoEconomy.give(p, CasinoRegistry.item(t), 1);
                                }
                            }
                            CasinoSavedData.get(ctx.getSource().getServer()).touch();
                            CasinoEconomy.sync(p);
                            ctx.getSource().sendSuccess(() -> Component.literal("All devices unlocked")
                                    .withStyle(ChatFormatting.GOLD), false);
                            return 1;
                        }))
                        .then(Commands.literal("slotforce").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(SlotGame.FORCE_KINDS, b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind");
                                    if (!SlotGame.FORCE_KINDS.contains(kind)) return 0;
                                    SlotGame.force(p, kind);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Next spin: " + kind)
                                            .withStyle(ChatFormatting.GOLD), false);
                                    return 1;
                                })))
                        .then(Commands.literal("revive").executes(ctx -> {
                            CasinoEconomy.revive(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("The House lets you play on")
                                    .withStyle(ChatFormatting.GOLD), true);
                            return 1;
                        }))
                        .then(Commands.literal("booth").executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            CasinoSavedData.get(server).setBoothBuilt(false, 0L);
                            CasinoBooth.ensure(server);
                            ctx.getSource().sendSuccess(() -> Component.literal("Booth rebuilt")
                                    .withStyle(ChatFormatting.GOLD), true);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            CasinoSavedData data = CasinoSavedData.get(server);
                            int index = CasinoEconomy.nextFeeIndex(server);
                            StringBuilder sb = new StringBuilder("Next fee #" + index + ": "
                                    + CasinoEconomy.formatFull(CasinoEconomy.fee(server, index)) + " in "
                                    + CasinoEconomy.ticksToFee(server) / 20 + " s, scale " + data.getFeeScale()
                                    + " %, collected so far " + CasinoEconomy.formatFull(data.getFeesTotal()));
                            data.accounts().values().forEach(a -> sb.append("\n  ").append(a.name).append(": ")
                                    .append(CasinoEconomy.formatFull(a.balance)).append(" (wagered ")
                                    .append(CasinoEconomy.formatFull(a.wagered)).append(", won ")
                                    .append(CasinoEconomy.formatFull(a.won)).append(")"));
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        }))));
    }
}
