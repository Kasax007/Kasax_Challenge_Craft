package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.challenges.Chal_50_HouseAlwaysWins;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Wiring of the casino into the game: who clicked what, the per-tick drivers of every game, and
 * the actions a client may send. The rules of each game live in their own classes.
 */
public final class CasinoGames {
    /** Selectable stakes in chips (the chips on every tray). Stepping past the top goes all-in. */
    public static final long[] BET_LEVELS = DeviceLayouts.STAKES;
    public static final int ALL_IN = -1;

    private static int tickCounter;

    private CasinoGames() {
    }

    /**
     * On the client: whether the local player has chips on a roulette spot (set by the client
     * code, which alone knows the table state there).
     */
    public static java.util.function.BiPredicate<BlockPos, DeviceLayouts.Zone> CLIENT_OWN_BET = (pos, zone) -> false;

    public static boolean active() {
        return Chal_50_HouseAlwaysWins.isActive();
    }

    /** The stake the account's bet level stands for, in centi-chips. All-in rounds down to 10 chips. */
    public static long betAmount(CasinoAccount a) {
        if (a.betLevel == ALL_IN) {
            long chips = Math.max(0, a.balance) / CasinoAccount.CENTI;
            return (chips / 10) * 10 * CasinoAccount.CENTI;
        }
        int i = Math.max(0, Math.min(BET_LEVELS.length - 1, a.betLevel));
        return BET_LEVELS[i] * CasinoAccount.CENTI;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!active()) return InteractionResult.PASS;
            if (BoothProtection.denyUse(player, level, hand, hit)) return InteractionResult.FAIL;
            BlockPos pos = CasinoPartBlock.master(level, hit.getBlockPos());
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CasinoDeviceBlock device)) return InteractionResult.PASS;
            boolean counter = device.getDeviceType() == DeviceType.CASHIER;
            ItemStack held = player.getItemInHand(hand);
            // Building around a device must stay possible (not around the croupier's counter).
            if (!counter && player.isShiftKeyDown() && held.getItem() instanceof BlockItem) return InteractionResult.PASS;
            if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            Direction facing = state.getValue(CasinoDeviceBlock.FACING);
            net.minecraft.world.phys.Vec3 local = DeviceSpace.toDevice(pos, facing, hit.getLocation());
            if (BlackjackRevival.inLimbo(sp.getUUID())) {
                // Mid-hand the only thing a player can do is play it, on their own counter.
                if (counter && BlackjackRevival.seatOf(sp.getUUID()) == CasinoDeviceBlock.counterIndex(level, pos, facing)) {
                    DeviceLayouts.Zone z = DeviceLayouts.counterZone(BlackjackRevival.seatOf(sp.getUUID()), true,
                            local.x, local.y, local.z);
                    if (z.kind() == DeviceLayouts.BJ) BlackjackRevival.act(sp, BlackjackRevival.ACTIONS[z.a()]);
                }
                return InteractionResult.SUCCESS;
            }
            if (CasinoSavedData.get(sp.level().getServer()).isBankrupt()) return InteractionResult.SUCCESS;
            DeviceType type = CasinoDevices.at((ServerLevel) level, pos);
            if (type == null) return InteractionResult.PASS;
            DeviceLayouts.Zone zone = counter
                    ? DeviceLayouts.counterZone(CasinoDeviceBlock.counterIndex(level, pos, facing), false, local.x, local.y, local.z)
                    : DeviceLayouts.zoneAt(type, local);
            if (zone.kind() == DeviceLayouts.CHIP) {
                selectStake(sp, zone.a());
                return InteractionResult.SUCCESS;
            }
            switch (type) {
                case SLOT -> SlotGame.use(sp, pos, held);
                case PLINKO -> PlinkoGame.use(sp, (ServerLevel) level, pos);
                case CRASH -> CrashGame.use(sp, (ServerLevel) level, pos);
                case ROULETTE -> {
                    if (zone.kind() == DeviceLayouts.BET) {
                        RouletteGame.placeBet(sp, pos, zone.a(), zone.b(), betAmount(account(sp)) / CasinoAccount.CENTI);
                    } else if (zone.kind() == DeviceLayouts.TAKE_BACK) {
                        RouletteGame.clearBets(sp, pos);
                    } else {
                        sp.sendOverlayMessage(Component.translatable("challengecraft.casino.roulette.aim").withStyle(ChatFormatting.GRAY));
                    }
                }
                case CASHIER -> {
                    if (zone.kind() == DeviceLayouts.BELL) {
                        CounterDeposit.deal(sp);
                    } else if (zone.kind() == DeviceLayouts.PENDING && CounterDeposit.hasStack(sp, zone.a())) {
                        // Only a slot that holds a stack takes it back; the free rest of the tray
                        // is the counter like everywhere else, so items can be laid there too.
                        CounterDeposit.takeBack(sp, zone.a());
                    } else if (!held.isEmpty() && !CasinoEconomy.isWallet(held.getItem())) {
                        CounterDeposit.place(sp, held);
                    } else {
                        openCashier(sp, TAB_SHOP);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        });

        // Left-click on one's own chips on the roulette felt takes them back instead of breaking the
        // table; anywhere else a device is mined like any block, so a bought device can be picked up
        // and set up elsewhere. Decided on both sides so the client does not start mining either.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!active() || player.isSpectator()) return InteractionResult.PASS;
            if (BoothProtection.denyBreak(player, level, pos)) return InteractionResult.FAIL;
            BlockPos master = CasinoPartBlock.master(level, pos);
            BlockState state = level.getBlockState(master);
            if (!(state.getBlock() instanceof CasinoDeviceBlock block) || block.getDeviceType() != DeviceType.ROULETTE
                    || player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            net.minecraft.world.phys.HitResult pick = player.pick(player.blockInteractionRange(), 1f, false);
            if (!(pick instanceof net.minecraft.world.phys.BlockHitResult bhr)) return InteractionResult.PASS;
            DeviceLayouts.Zone zone = DeviceLayouts.zoneAt(DeviceType.ROULETTE,
                    DeviceSpace.toDevice(master, state.getValue(CasinoDeviceBlock.FACING), bhr.getLocation()));
            if (zone.kind() != DeviceLayouts.BET) return InteractionResult.PASS;
            boolean own = level.isClientSide()
                    ? CLIENT_OWN_BET.test(master, zone)
                    : player instanceof ServerPlayer sp && RouletteGame.hasBet(sp, master, zone.a(), zone.b());
            if (!own) return InteractionResult.PASS;
            if (player instanceof ServerPlayer sp) RouletteGame.removeBet(sp, master, zone.a(), zone.b());
            return InteractionResult.FAIL;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof CroupierEntity)) return InteractionResult.PASS;
            if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer sp) || !active()) return InteractionResult.SUCCESS;
            if (BlackjackRevival.inLimbo(sp.getUUID())) return InteractionResult.SUCCESS;
            ItemStack held = player.getItemInHand(hand);
            if (player.isShiftKeyDown() && !held.isEmpty() && !CasinoEconomy.isWallet(held.getItem())) {
                // Quick deposit: sneak-click with a stack hands the whole stack over, no menu.
                long credited = CasinoEconomy.depositStack(sp, held);
                if (held.isEmpty()) sp.setItemInHand(hand, ItemStack.EMPTY);
                CasinoEconomy.afterDeposit(sp, credited, credited > 0 ? 1 : 0);
            } else if (!held.isEmpty() && !CasinoEconomy.isWallet(held.getItem())) {
                // Handing the croupier a stack lays it on the counter, like clicking the counter.
                CounterDeposit.place(sp, held);
            } else {
                openCashier(sp, TAB_SHOP);
            }
            return InteractionResult.SUCCESS;
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                entity instanceof CroupierEntity ? InteractionResult.FAIL : InteractionResult.PASS);

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!active()) return InteractionResult.PASS;
            if (!CasinoEconomy.isWallet(player.getItemInHand(hand).getItem())) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player instanceof ServerPlayer sp && !BlackjackRevival.inLimbo(sp.getUUID())) openCashier(sp, 2);
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(CasinoGames::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            if (!active()) return;
            ServerPlayer p = handler.player;
            GamblingNotice.send(p);
            if (CasinoSavedData.get(server).isBankrupt()) {
                p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); // the House already won
            }
            CasinoEconomy.account(p);
            CasinoDevices.sync(p);
            CasinoEconomy.sync(p);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!active()) return;
            BlackjackRevival.onLeave(handler.player);
            CounterDeposit.returnAll(handler.player);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CounterDeposit.returnAll(server);
            BlackjackRevival.abortAll(server);
            SlotGame.flush(server);
            PlinkoGame.flush(server);
            CrashGame.refundAll(server);
            RouletteGame.refundAll(server);
        });
    }

    private static void tick(MinecraftServer server) {
        if (!active()) return;
        tickCounter++;
        SlotGame.tick(server);
        CrashGame.tick(server);
        RouletteGame.tick(server);
        PlinkoGame.tick(server);
        BlackjackRevival.tick(server);
        CasinoBooth.tickGestures(server);
        if (tickCounter % 20 == 0) {
            CasinoEconomy.tickFee(server);
            LossWaves.tick(server);
            boolean bankrupt = CasinoSavedData.get(server).isBankrupt();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!bankrupt && !BlackjackRevival.inLimbo(p.getUUID())) CasinoEconomy.ensureWallet(p);
                CasinoEconomy.sync(p);
            }
        }
        if (tickCounter % 100 == 0) {
            CasinoBooth.ensure(server);
        }
        if (tickCounter % 200 == 0) {
            CasinoDevices.validate(server);
        }
        if (tickCounter % 1200 == 0) {
            CasinoDevices.syncAll(server);
        }
    }

    // ---- cashier ------------------------------------------------------------------------------

    public static final int TAB_SHOP = 1;

    /** Tab 0 = deposit, 1 = shop, 2 = account overview. */
    public static void openCashier(ServerPlayer player, int tab) {
        CasinoAccount a = CasinoEconomy.account(player);
        List<Item> items = new ArrayList<>();
        for (String id : a.known) {
            Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
            if (item != null && EmcValues.isForSale(item)) items.add(item);
        }
        items.sort(Comparator.comparingLong(EmcValues::baseValue).reversed());
        List<String> ids = new ArrayList<>(items.size());
        long[] prices = new long[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ids.add(BuiltInRegistries.ITEM.getKey(items.get(i)).toString());
            prices[i] = EmcValues.baseValue(items.get(i));
        }
        CasinoEconomy.sync(player);
        ServerPlayNetworking.send(player, new CasinoNet.Cashier(ids, prices, tab));
        CasinoBooth.gesture(player.level().getServer(), CroupierEntity.GESTURE_WAVE);
    }

    /** Whether the player is standing at the croupier (deposits and purchases need that). */
    public static boolean atCroupier(ServerPlayer player) {
        CroupierEntity c = CasinoBooth.croupier(player.level().getServer());
        if (c == null) return false;
        return c.level() == player.level() && c.distanceToSqr(player) < 8 * 8;
    }

    // ---- client actions -----------------------------------------------------------------------

    public static void handleAction(ServerPlayer player, CasinoNet.Action a) {
        if (!active()) return;
        MinecraftServer server = player.level().getServer();
        boolean bankrupt = CasinoSavedData.get(server).isBankrupt();
        switch (a.action()) {
            case CasinoNet.Action.DEPOSIT_SLOTS, CasinoNet.Action.DEPOSIT_ALL, CasinoNet.Action.BUY_ITEM,
                 CasinoNet.Action.BUY_DEVICE -> {
                if (bankrupt) return;
                if (!atCroupier(player)) {
                    player.sendOverlayMessage(Component.translatable("challengecraft.casino.cashier.too_far").withStyle(ChatFormatting.RED));
                    return;
                }
                switch (a.action()) {
                    case CasinoNet.Action.DEPOSIT_SLOTS -> CasinoEconomy.depositSlots(player, parseSlots(a.text()));
                    case CasinoNet.Action.DEPOSIT_ALL -> CasinoEconomy.depositAll(player);
                    case CasinoNet.Action.BUY_ITEM -> CasinoEconomy.buyItem(player, a.text(), a.a());
                    default -> {
                        DeviceType[] types = DeviceType.values();
                        if (a.a() >= 0 && a.a() < types.length) CasinoEconomy.buyDevice(player, types[a.a()]);
                    }
                }
                openCashier(player, a.b());
            }
            case CasinoNet.Action.BET_UP, CasinoNet.Action.BET_DOWN, CasinoNet.Action.BET_ALL_IN -> changeBet(player, a.action());
            case CasinoNet.Action.ROULETTE_BET -> RouletteGame.placeBet(player, BlockPos.of(a.pos()), a.a(), a.b(), a.amount());
            case CasinoNet.Action.ROULETTE_CLEAR -> RouletteGame.clearBets(player, BlockPos.of(a.pos()));
            case CasinoNet.Action.CRASH_CASH_OUT -> CrashGame.cashOutAnywhere(player);
            case CasinoNet.Action.BJ_HIT -> BlackjackRevival.act(player, BlackjackTable.HIT);
            case CasinoNet.Action.BJ_STAND -> BlackjackRevival.act(player, BlackjackTable.STAND);
            case CasinoNet.Action.BJ_DOUBLE -> BlackjackRevival.act(player, BlackjackTable.DOUBLE);
            case CasinoNet.Action.BJ_SPLIT -> BlackjackRevival.act(player, BlackjackTable.SPLIT);
            case CasinoNet.Action.CASHIER_TAB -> openCashier(player, a.a());
            default -> {
            }
        }
    }

    private static int[] parseSlots(String text) {
        if (text.isEmpty()) return new int[0];
        String[] parts = text.split(",");
        int[] out = new int[Math.min(parts.length, 64)];
        for (int i = 0; i < out.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = -1;
            }
        }
        return out;
    }

    public static CasinoAccount account(ServerPlayer player) {
        return CasinoEconomy.account(player);
    }

    /** A chip of a tray was clicked: that chip's value becomes the player's stake. */
    public static void selectStake(ServerPlayer player, int slot) {
        CasinoAccount a = CasinoEconomy.account(player);
        int level = DeviceLayouts.trayLevel(a.betLevel, slot);
        a.betLevel = level >= DeviceLayouts.ALL_IN_LEVEL ? ALL_IN : level;
        CasinoSavedData.get(player.level().getServer()).touch();
        CasinoEconomy.playTo(player, CasinoSounds.CHIP, 0.7f, a.betLevel == ALL_IN ? 0.7f : 0.9f + 0.03f * a.betLevel);
        player.sendOverlayMessage(Component.translatable(a.betLevel == ALL_IN
                        ? "challengecraft.casino.bet.all_in" : "challengecraft.casino.bet.level",
                CasinoEconomy.formatFull(betAmount(a))).withStyle(ChatFormatting.GOLD));
        CasinoEconomy.sync(player);
    }

    private static void changeBet(ServerPlayer player, int action) {
        CasinoAccount a = CasinoEconomy.account(player);
        if (action == CasinoNet.Action.BET_ALL_IN) {
            a.betLevel = ALL_IN;
        } else if (action == CasinoNet.Action.BET_UP) {
            if (a.betLevel == ALL_IN) return;
            a.betLevel = a.betLevel >= BET_LEVELS.length - 1 ? ALL_IN : a.betLevel + 1;
        } else {
            a.betLevel = a.betLevel == ALL_IN ? BET_LEVELS.length - 1 : Math.max(0, a.betLevel - 1);
        }
        CasinoSavedData.get(player.level().getServer()).touch();
        CasinoEconomy.playTo(player, CasinoSounds.CHIP, 0.6f, a.betLevel == ALL_IN ? 0.7f : 0.9f + 0.03f * a.betLevel);
        player.sendOverlayMessage(Component.translatable(a.betLevel == ALL_IN
                        ? "challengecraft.casino.bet.all_in" : "challengecraft.casino.bet.level",
                CasinoEconomy.formatFull(betAmount(a))).withStyle(ChatFormatting.GOLD));
        CasinoEconomy.sync(player);
    }

    // ---- shared bookkeeping for every game ----------------------------------------------------

    /** Records a settled bet: statistics, and the loss meter that turns into mob waves. */
    public static void settle(ServerPlayer player, long stake, long payout) {
        CasinoAccount a = CasinoEconomy.account(player);
        a.wagered += stake;
        a.won += payout;
        long net = payout - stake;
        if (net > a.biggestWin) a.biggestWin = net;
        long now = player.level().getServer().getTickCount();
        if (net < 0) {
            if (a.lossMeter == 0) a.lossStartTick = now;
            a.lossMeter += -net;
        } else if (net > 0) {
            a.lossMeter = Math.max(0, a.lossMeter - net);
        }
        a.lastBetTick = now;
        CasinoSavedData.get(player.level().getServer()).touch();
    }

    // ---- recipe gate --------------------------------------------------------------------------

    /**
     * Called from {@code MixinScreenHandler} for every slot click: taking a casino device out of a
     * crafting result slot needs that device to have been bought from the croupier once.
     */
    public static boolean blockLockedCraft(Player player, AbstractContainerMenu menu, int slotIndex) {
        if (!(player instanceof ServerPlayer sp) || slotIndex < 0 || slotIndex >= menu.slots.size()) return false;
        Slot slot = menu.slots.get(slotIndex);
        if (!(slot instanceof ResultSlot)) return false;
        DeviceType type = CasinoRegistry.deviceOf(slot.getItem().getItem());
        if (type == null || type == DeviceType.CASHIER) return false;
        if (active() && CasinoEconomy.isUnlocked(sp, type)) return false;
        sp.sendOverlayMessage(Component.translatable("challengecraft.casino.recipe.locked",
                Component.translatable("block.challengecraft." + type.id)).withStyle(ChatFormatting.RED));
        return true;
    }

    /** All players standing within {@code radius} blocks of a position in a level. */
    public static List<ServerPlayer> near(ServerLevel level, BlockPos pos, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius) out.add(p);
        }
        return out;
    }

    public static Map<String, CasinoAccount> accounts(MinecraftServer server) {
        return CasinoSavedData.get(server).accounts();
    }
}
