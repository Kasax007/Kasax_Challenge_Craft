package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The House's books: balances, deposits, purchases, the shared fee and bankruptcy.
 *
 * <h2>The fee</h2>
 * Every 10 minutes of run time the House collects a fee from the <b>team's combined balance</b>.
 * Each player pays in proportion to what they hold — someone with twice the chips pays twice the
 * share — so nobody can hide chips by leaving them with a teammate. If the combined balance cannot
 * cover the fee, the House wins: the run is over.
 *
 * <p>The fee follows an S-curve with a hard ceiling, so it starts gentle, gets serious in the
 * second hour and then stops growing, which keeps a world playable after the dragon:
 * <pre>
 *   fee(k) = CAP / (1 + (HALF / k)^3)     k = 1, 2, 3 … (the k-th collection, 10 min apart)
 *   CAP = 65 536 (8 diamonds), HALF = 12 (two hours), ×(1 + 0.5 per extra player)
 * </pre>
 */
public final class CasinoEconomy {
    public static final int FEE_PERIOD_TICKS = 20 * 60 * 10;
    public static final int FEE_WARNING_TICKS = 20 * 60;
    public static final double FEE_CAP_CHIPS = 65_536.0;
    public static final double FEE_HALF_PERIODS = 12.0;
    /** Share of the balance the House keeps on every death (the user's rule: 25 %). */
    public static final long DEATH_TAX_PERCENT = 25;

    private static int warnedFor = -1;

    private CasinoEconomy() {
    }

    // ---- formatting ---------------------------------------------------------------------------

    /** 950, 12.4K, 3.10M — for centi-chip amounts. */
    public static String format(long centi) {
        long chips = centi / CasinoAccount.CENTI;
        long abs = Math.abs(chips);
        if (abs < 10_000) return Long.toString(chips);
        if (abs < 1_000_000) return String.format(Locale.ROOT, "%.1fK", chips / 1000.0);
        if (abs < 1_000_000_000) return String.format(Locale.ROOT, "%.2fM", chips / 1_000_000.0);
        return String.format(Locale.ROOT, "%.2fB", chips / 1_000_000_000.0);
    }

    /** Full number with thin grouping: 12 480. */
    public static String formatFull(long centi) {
        // Space rather than a locale separator: the string is built on the server for every client.
        return String.format(Locale.ROOT, "%,d", centi / CasinoAccount.CENTI).replace(',', ' ');
    }

    // ---- accounts -----------------------------------------------------------------------------

    public static CasinoAccount account(ServerPlayer player) {
        CasinoSavedData data = CasinoSavedData.get(player.level().getServer());
        CasinoAccount a = data.account(player.getUUID());
        String name = player.getName().getString();
        if (!name.equals(a.name)) {
            a.name = name;
            data.touch();
        }
        return a;
    }

    public static long total(MinecraftServer server) {
        long sum = 0;
        for (CasinoAccount a : CasinoSavedData.get(server).accounts().values()) sum += Math.max(0, a.balance);
        return sum;
    }

    public static void credit(ServerPlayer player, long centi) {
        if (centi == 0) return;
        CasinoAccount a = account(player);
        a.balance += centi;
        CasinoSavedData.get(player.level().getServer()).touch();
    }

    /** Takes {@code centi} if the player has it; false leaves the balance untouched. */
    public static boolean debit(ServerPlayer player, long centi) {
        CasinoAccount a = account(player);
        if (centi <= 0 || a.balance < centi) return false;
        a.balance -= centi;
        CasinoSavedData.get(player.level().getServer()).touch();
        return true;
    }

    // ---- fee ----------------------------------------------------------------------------------

    public static int playerCount(MinecraftServer server) {
        return Math.max(1, CasinoSavedData.get(server).accounts().size());
    }

    /** Fee of the k-th collection in centi-chips. */
    public static long fee(MinecraftServer server, int k) {
        if (k <= 0) return 0;
        CasinoSavedData data = CasinoSavedData.get(server);
        double raw = FEE_CAP_CHIPS / (1.0 + Math.pow(FEE_HALF_PERIODS / k, 3));
        raw *= 1.0 + 0.5 * (playerCount(server) - 1);
        raw *= data.getFeeScale() / 100.0;
        long chips = Math.max(16, Math.round(raw / 16.0) * 16);
        return chips * CasinoAccount.CENTI;
    }

    public static int nextFeeIndex(MinecraftServer server) {
        return CasinoSavedData.get(server).getFeesCharged() + 1;
    }

    public static int ticksToFee(MinecraftServer server) {
        int due = nextFeeIndex(server) * FEE_PERIOD_TICKS;
        return Math.max(0, due - ChallengeTimeUtil.getDisplayRunTicks(server));
    }

    /** How the next fee would be split right now: centi-chips per account, in account order. */
    public static Map<String, Long> shares(MinecraftServer server, long fee) {
        CasinoSavedData data = CasinoSavedData.get(server);
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        long total = total(server);
        if (total <= 0) {
            data.accounts().keySet().forEach(k -> out.put(k, 0L));
            return out;
        }
        long assigned = 0;
        String richest = null;
        long richestBalance = -1;
        for (Map.Entry<String, CasinoAccount> e : data.accounts().entrySet()) {
            long bal = Math.max(0, e.getValue().balance);
            long share = (long) Math.floor((double) fee * bal / total);
            share = Math.min(share, bal);
            out.put(e.getKey(), share);
            assigned += share;
            if (bal > richestBalance) {
                richestBalance = bal;
                richest = e.getKey();
            }
        }
        // Rounding leftovers go to whoever can carry them, richest first.
        long rest = fee - assigned;
        if (rest > 0) {
            List<Map.Entry<String, CasinoAccount>> order = new ArrayList<>(data.accounts().entrySet());
            order.sort((x, y) -> Long.compare(y.getValue().balance, x.getValue().balance));
            for (Map.Entry<String, CasinoAccount> e : order) {
                long room = Math.max(0, e.getValue().balance) - out.get(e.getKey());
                long take = Math.min(room, rest);
                out.put(e.getKey(), out.get(e.getKey()) + take);
                rest -= take;
                if (rest <= 0) break;
            }
        }
        return out;
    }

    public static void tickFee(MinecraftServer server) {
        CasinoSavedData data = CasinoSavedData.get(server);
        if (data.isBankrupt()) return;
        int runTicks = ChallengeTimeUtil.getDisplayRunTicks(server);
        int index = nextFeeIndex(server);
        int due = index * FEE_PERIOD_TICKS;

        if (due - runTicks <= FEE_WARNING_TICKS && warnedFor != index && due - runTicks > 0) {
            warnedFor = index;
            long fee = fee(server, index);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, new CasinoNet.Fx(CasinoNet.Fx.FEE_WARNING, fee, "", 0L));
            }
        }

        if (runTicks < due) return;

        long fee = fee(server, index);
        long total = total(server);
        if (total < fee) {
            bankrupt(server, fee, total);
            return;
        }
        Map<String, Long> shares = shares(server, fee);
        for (Map.Entry<String, Long> e : shares.entrySet()) {
            CasinoAccount a = data.accounts().get(e.getKey());
            a.balance -= e.getValue();
            a.feesPaid += e.getValue();
        }
        data.setFeesCharged(index);
        data.addFeesTotal(fee);
        ChallengeCraft.LOGGER.info("[Casino] fee #{} collected: {} chips from {} in total", index, fee / 100, total / 100);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Long share = shares.get(p.getStringUUID());
            ServerPlayNetworking.send(p, new CasinoNet.Fx(CasinoNet.Fx.FEE_PAID, share == null ? 0 : share,
                    Long.toString(fee), 0L));
        }
        syncAll(server);
    }

    private static void bankrupt(MinecraftServer server, long fee, long total) {
        CasinoSavedData data = CasinoSavedData.get(server);
        data.setBankrupt(true);
        ChallengeSavedData run = ChallengeSavedData.get(server.overworld());
        run.setInitialDifficulty(0);
        run.setTainted(true);
        ChallengeCraft.LOGGER.info("[Casino] bankrupt: fee {} > total {}", fee / 100, total / 100);

        Component title = Component.translatable("challengecraft.casino.bankrupt.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("challengecraft.casino.bankrupt.subtitle", formatFull(fee), formatFull(total))
                .withStyle(ChatFormatting.GRAY);
        server.getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 100, 30));
        server.getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(title));
        server.getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(subtitle));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable("challengecraft.casino.bankrupt.chat", formatFull(fee), formatFull(total))
                    .withStyle(ChatFormatting.RED));
            ServerPlayNetworking.send(p, new CasinoNet.Fx(CasinoNet.Fx.BANKRUPT, fee, "", 0L));
            playTo(p, CasinoSounds.BANKRUPT, 1.0f, 1.0f);
            p.setGameMode(GameType.SPECTATOR);
        }
        syncAll(server);
    }

    // ---- deposits -----------------------------------------------------------------------------

    /** Deposits whole inventory slots; returns centi-chips credited. */
    public static long depositSlots(ServerPlayer player, int[] slots) {
        Inventory inv = player.getInventory();
        long credited = 0;
        int stacks = 0;
        for (int slot : slots) {
            if (slot < 0 || slot >= inv.getContainerSize()) continue;
            ItemStack stack = inv.getItem(slot);
            credited += depositStack(player, stack);
            if (stack.isEmpty()) {
                inv.setItem(slot, ItemStack.EMPTY);
                stacks++;
            }
        }
        inv.setChanged();
        afterDeposit(player, credited, stacks);
        return credited;
    }

    /** Deposits everything valuable in the main inventory (not the hotbar, not armour). */
    public static long depositAll(ServerPlayer player) {
        int[] slots = new int[27];
        for (int i = 0; i < 27; i++) slots[i] = 9 + i;
        return depositSlots(player, slots);
    }

    /** Takes the whole stack if it has a value; returns centi-chips credited. */
    public static long depositStack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || isWallet(stack.getItem())) return 0;
        long unit = EmcValues.unitValue(stack);
        if (unit <= 0) return 0;
        long centi = unit * stack.getCount() * CasinoAccount.CENTI;
        CasinoAccount a = account(player);
        a.balance += centi;
        a.known.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        stack.setCount(0);
        CasinoSavedData.get(player.level().getServer()).touch();
        return centi;
    }

    public static void afterDeposit(ServerPlayer player, long credited, int stacks) {
        if (credited <= 0) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.deposit.nothing").withStyle(ChatFormatting.GRAY));
            return;
        }
        ServerPlayNetworking.send(player, new CasinoNet.Fx(CasinoNet.Fx.DEPOSIT, credited, Integer.toString(stacks), 0L));
        playAt(player, CasinoSounds.REGISTER, 0.9f, 1.0f);
        CasinoBooth.gesture(player.level().getServer(), CroupierEntity.GESTURE_TAKE);
        sync(player);
    }

    // ---- withdrawals --------------------------------------------------------------------------

    public static void buyItem(ServerPlayer player, String itemId, int count) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        CasinoAccount a = account(player);
        if (item == null || item == Items.AIR || !a.known.contains(itemId) || !EmcValues.isForSale(item)) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.buy.unknown").withStyle(ChatFormatting.RED));
            return;
        }
        long unit = EmcValues.baseValue(item) * CasinoAccount.CENTI;
        int max = (int) Math.min(item.getDefaultMaxStackSize() * 36L, a.balance / unit);
        int n = count <= 0 ? max : Math.min(count, max);
        if (n <= 0) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.buy.poor").withStyle(ChatFormatting.RED));
            playTo(player, CasinoSounds.LOSE, 0.5f, 1.4f);
            return;
        }
        a.balance -= unit * n;
        CasinoSavedData.get(player.level().getServer()).touch();
        give(player, item, n);
        playAt(player, CasinoSounds.CHIP_STACK, 0.8f, 1.0f);
        CasinoBooth.gesture(player.level().getServer(), CroupierEntity.GESTURE_PAY);
        sync(player);
    }

    /** Hands items to the player; whatever does not fit lands at their feet. */
    public static void give(ServerPlayer player, Item item, int count) {
        int max = Math.max(1, item.getDefaultMaxStackSize());
        while (count > 0) {
            int n = Math.min(max, count);
            count -= n;
            ItemStack stack = new ItemStack(item, n);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                ServerLevel level = (ServerLevel) player.level();
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack));
            }
        }
    }

    public static void buyDevice(ServerPlayer player, DeviceType type) {
        if (type == null || !type.purchasable) return;
        Inventory inv = player.getInventory();
        for (DeviceType.Cost cost : type.price) {
            if (countInMain(inv, cost.item()) < cost.count()) {
                player.sendOverlayMessage(Component.translatable("challengecraft.casino.device.missing",
                        cost.count(), new ItemStack(cost.item()).getHoverName()).withStyle(ChatFormatting.RED));
                playTo(player, CasinoSounds.LOSE, 0.5f, 1.4f);
                return;
            }
        }
        for (DeviceType.Cost cost : type.price) removeFromMain(inv, cost.item(), cost.count());
        inv.setChanged();
        CasinoAccount a = account(player);
        boolean first = a.unlocked.add(type.id);
        CasinoSavedData.get(player.level().getServer()).touch();
        give(player, CasinoRegistry.item(type), 1);
        ServerPlayNetworking.send(player, new CasinoNet.Fx(first ? CasinoNet.Fx.UNLOCK : CasinoNet.Fx.PURCHASE, 0, type.id, 0L));
        playAt(player, CasinoSounds.UNLOCK, 1.0f, 1.0f);
        CasinoBooth.gesture(player.level().getServer(), CroupierEntity.GESTURE_TIP_HAT);
        sync(player);
    }

    private static int countInMain(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.isDamaged()) n += s.getCount();
        }
        return n;
    }

    private static void removeFromMain(Inventory inv, Item item, int count) {
        for (int i = 0; i < 36 && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item) || s.isDamaged()) continue;
            int take = Math.min(count, s.getCount());
            s.shrink(take);
            count -= take;
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    public static boolean isUnlocked(ServerPlayer player, DeviceType type) {
        return account(player).unlocked.contains(type.id);
    }

    // ---- death --------------------------------------------------------------------------------

    /** The House keeps a quarter of everything on every death. Returns centi-chips taken. */
    public static long deathTax(ServerPlayer player) {
        CasinoAccount a = account(player);
        long tax = Math.max(0, a.balance) * DEATH_TAX_PERCENT / 100;
        a.balance -= tax;
        a.deaths++;
        CasinoSavedData.get(player.level().getServer()).touch();
        ServerPlayNetworking.send(player, new CasinoNet.Fx(CasinoNet.Fx.DEATH_TAX, tax, "", 0L));
        sync(player);
        return tax;
    }

    // ---- wallet -------------------------------------------------------------------------------

    public static boolean isWallet(Item item) {
        return item == CasinoRegistry.CHIP_WALLET;
    }

    /**
     * Keeps exactly one chip wallet in every player's inventory while the House is open, and
     * keeps its name showing the balance. Like the die of challenge 46 it is handed back if lost,
     * and duplicates are removed so it can never be farmed.
     */
    public static void ensureWallet(ServerPlayer player) {
        if (player.isSpectator()) return;
        Inventory inv = player.getInventory();
        int first = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!isWallet(inv.getItem(i).getItem())) continue;
            if (first == -1) first = i;
            else inv.setItem(i, ItemStack.EMPTY);
        }
        CasinoAccount a = account(player);
        Component name = Component.translatable("item.challengecraft.chip_wallet.balance", formatFull(a.balance))
                .withStyle(ChatFormatting.GOLD);
        if (first == -1) {
            ItemStack wallet = new ItemStack(CasinoRegistry.CHIP_WALLET);
            wallet.set(DataComponents.CUSTOM_NAME, name);
            inv.add(wallet);
            return;
        }
        ItemStack wallet = inv.getItem(first);
        Component current = wallet.get(DataComponents.CUSTOM_NAME);
        if (current == null || !current.getString().equals(name.getString())) {
            wallet.set(DataComponents.CUSTOM_NAME, name);
        }
    }

    public static void removeWallets(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (isWallet(inv.getItem(i).getItem())) inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // ---- sync ---------------------------------------------------------------------------------

    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        CasinoSavedData data = CasinoSavedData.get(server);
        CasinoAccount me = account(player);
        int index = nextFeeIndex(server);
        long fee = fee(server, index);
        Map<String, Long> shares = shares(server, fee);
        List<CasinoNet.Member> members = new ArrayList<>();
        for (Map.Entry<String, CasinoAccount> e : data.accounts().entrySet()) {
            members.add(new CasinoNet.Member(e.getValue().name, e.getValue().balance,
                    shares.getOrDefault(e.getKey(), 0L)));
        }
        ServerPlayNetworking.send(player, new CasinoNet.State(me.balance, total(server), fee, ticksToFee(server),
                index, data.isBankrupt(), me.betLevel, CasinoGames.betAmount(me), members,
                new ArrayList<>(me.unlocked), me.known.size(), BlackjackRevival.inLimbo(player.getUUID())));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    // ---- sounds -------------------------------------------------------------------------------

    /** A positional sound everyone nearby hears. */
    public static void playAt(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * A quiet sound at the player. Deliberately positional rather than private: the server-side
     * {@code Player.playSound} skips the player itself (their client is expected to predict it).
     */
    public static void playTo(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume * 0.6f, pitch);
    }

    public static UUID uuidOf(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
