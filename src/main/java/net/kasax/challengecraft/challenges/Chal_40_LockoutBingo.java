package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.LockoutBingoSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.item.ModItems;
import net.kasax.challengecraft.mixin.MerchantScreenHandlerAccessor;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.kasax.challengecraft.network.LockoutBingoActionPacket;
import net.kasax.challengecraft.network.LockoutBingoOpenScreenPacket;
import net.kasax.challengecraft.network.LockoutBingoSyncPacket;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.WeightedPressurePlateBlock;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Merchant;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.structure.StructureStart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server-side controller for lockout bingo.
 *
 * The board itself lives in persistent state; this class wires player actions and passive goal scans
 * into claims, score changes, rewards, and client synchronization.
 */
public final class Chal_40_LockoutBingo {
    public static final int CHALLENGE_ID = 40;
    private static final int BOARD_SIZE = 25;
    private static final int MAP_DROP_RETRY_TICKS = 100;

    private static boolean active = false;
    private static final Map<UUID, Long> LAST_MAP_DROP_TICK = new LinkedHashMap<>();

    private Chal_40_LockoutBingo() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active || server.getTicks() % 20 != 0) {
                return;
            }

            LockoutBingoSavedData data = getData(server);
            ensureCurrentRun(server, data);
            ensureMaps(server, server.getTicks());

            if (!data.isStarted()) {
                maybeStartGame(server, data);
                return;
            }

            if (!data.isEnded()) {
                scanPassiveGoals(server, data);
            }
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!active) {
                return ActionResult.PASS;
            }

            ItemStack heldStack = player.getStackInHand(hand);
            if (!heldStack.isOf(ModItems.LOCKOUT_BINGO_MAP)) {
                return ActionResult.PASS;
            }

            if (world.isClient()) {
                return ActionResult.SUCCESS;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
                openAppropriateScreen(serverPlayer);
                return ActionResult.SUCCESS_SERVER;
            }

            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!active || world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            handleBlockInteraction(serverPlayer, player.getStackInHand(hand), world.getBlockState(hit.getBlockPos()));
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!active || world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            handleEntityInteraction(serverPlayer, player.getStackInHand(hand), entity);
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damageSource, baseDamageTaken, damageTaken, blocked) -> {
            if (!active || !(entity instanceof ServerPlayerEntity player) || damageTaken <= 0.0f || player.isDead()) {
                return;
            }

            handlePlayerDamage(player, damageSource, damageTaken);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register(Chal_40_LockoutBingo::handleEntityDeath);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            if (!active) {
                return;
            }

            LockoutBingoSavedData data = getData(server);
            ensureCurrentRun(server, data);
            data.updatePlayerName(handler.player.getUuid(), handler.player.getGameProfile().getName());
            ensureMap(handler.player, server.getTicks());
            syncToPlayer(handler.player);
        }));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            if (!active) {
                return;
            }

            LockoutBingoSavedData data = getData(server);
            ensureCurrentRun(server, data);
            if (!data.isStarted()) {
                data.setReady(handler.player.getUuid(), false);
                syncToAll(server);
            }
        }));
    }

    public static void handleAction(ServerPlayerEntity player, LockoutBingoActionPacket packet) {
        if (!active) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        data.updatePlayerName(player.getUuid(), player.getGameProfile().getName());

        switch (packet.action()) {
            case REQUEST_SYNC -> {
                syncToPlayer(player);
                return;
            }
            case JOIN_TEAM -> {
                if (data.isStarted()) {
                    player.sendMessage(Text.translatable("challengecraft.lockout.error.started").formatted(Formatting.RED), true);
                    syncToPlayer(player);
                    return;
                }

                LockoutBingoTeam team = LockoutBingoTeam.fromOrdinal(packet.teamId());
                if (team == null) {
                    syncToPlayer(player);
                    return;
                }

                data.setTeam(player.getUuid(), player.getGameProfile().getName(), team);
                data.setReady(player.getUuid(), false);
            }
            case LEAVE_TEAM -> {
                if (data.isStarted()) {
                    player.sendMessage(Text.translatable("challengecraft.lockout.error.started").formatted(Formatting.RED), true);
                    syncToPlayer(player);
                    return;
                }

                data.removeTeam(player.getUuid());
            }
            case READY -> {
                if (data.isStarted()) {
                    player.sendMessage(Text.translatable("challengecraft.lockout.error.started").formatted(Formatting.RED), true);
                    syncToPlayer(player);
                    return;
                }

                if (data.getTeam(player.getUuid()) == null) {
                    player.sendMessage(Text.translatable("challengecraft.lockout.error.join_team_first").formatted(Formatting.RED), true);
                    syncToPlayer(player);
                    return;
                }

                data.setReady(player.getUuid(), true);
            }
            case UNREADY -> {
                if (!data.isStarted()) {
                    data.setReady(player.getUuid(), false);
                }
            }
        }

        maybeStartGame(server, data);
        if (!data.isStarted()) {
            syncToAll(server);
        }
    }

    public static void handleScreenSlotClick(PlayerEntity player, ScreenHandler handler, int slotIndex) {
        if (!active || !(player instanceof ServerPlayerEntity serverPlayer) || slotIndex < 0 || slotIndex >= handler.slots.size()) {
            return;
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded() || data.getTeam(serverPlayer.getUuid()) == null) {
            return;
        }

        Slot slot = handler.slots.get(slotIndex);
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) {
            return;
        }

        if (handler instanceof BrewingStandScreenHandler && slot.getIndex() >= 0 && slot.getIndex() <= 2 && isPotionStack(stack)) {
            claimFirstMatchingGoal(server, data, serverPlayer, goal -> matchesBrewGoal(goal, stack));
            return;
        }

        if (handler instanceof MerchantScreenHandler merchantHandler && slot instanceof TradeOutputSlot) {
            Merchant merchant = ((MerchantScreenHandlerAccessor) merchantHandler).challengecraft$getMerchant();
            claimFirstMatchingGoal(server, data, serverPlayer, goal -> matchesTradeGoal(goal, stack, merchant));
        }
    }

    public static void onActivated(ServerWorld world) {
        if (world == null) {
            return;
        }

        MinecraftServer server = world.getServer();
        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        ensureMaps(server, server.getTicks());
        syncToAll(server);
    }

    public static void syncToAll(MinecraftServer server) {
        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        LockoutBingoSyncPacket packet = buildSyncPacket(server, data);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    public static void syncToPlayer(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        ServerPlayNetworking.send(player, buildSyncPacket(server, data));
    }

    public static void setActive(boolean value) {
        active = value;
        if (!active) {
            LAST_MAP_DROP_TICK.clear();
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void startSoloDebugRun(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        active = true;
        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        data.resetForRun(data.getRunId());
        data.setTeam(player.getUuid(), player.getGameProfile().getName(), LockoutBingoTeam.RED);
        data.retainPlayers(Set.of(player.getUuid()));

        long boardSeed = server.getOverworld().getSeed()
                ^ server.getOverworld().getTime()
                ^ player.getUuid().getMostSignificantBits()
                ^ player.getUuid().getLeastSignificantBits();
        List<LockoutBingoGoal> goals = LockoutBingoGoalPool.pickBoard(boardSeed);
        data.setBoard(goals.stream().map(LockoutBingoGoal::id).toList(), boardSeed, server.getOverworld().getTime());
        captureGoalStatBaselines(data, List.of(player), goals);
        data.setStarted(true);
        data.setEnded(false);
        data.setWinnerTeam(null);
        data.clearReady();

        ensureMap(player, server.getTicks());
        syncToAll(server);
        ServerPlayNetworking.send(player, new LockoutBingoOpenScreenPacket(true));
    }

    public static int debugClaimTiles(ServerPlayerEntity player, int amount) {
        MinecraftServer server = player.getServer();
        if (server == null || !active) {
            return 0;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded()) {
            return 0;
        }

        LockoutBingoTeam team = data.getTeam(player.getUuid());
        if (team == null) {
            team = LockoutBingoTeam.RED;
            data.setTeam(player.getUuid(), player.getGameProfile().getName(), team);
        }

        int claimed = 0;
        for (int claim = 0; claim < amount && !data.isEnded(); claim++) {
            boolean found = false;
            List<String> board = data.getBoardGoalIds();
            for (int i = 0; i < board.size(); i++) {
                if (data.getClaimedTeam(i) != null) {
                    continue;
                }

                LockoutBingoGoal goal = LockoutBingoGoalPool.byId(board.get(i));
                if (goal == null) {
                    continue;
                }

                claimGoal(server, data, i, goal, player, team);
                claimed++;
                found = true;
                break;
            }

            if (!found) {
                break;
            }
        }
        return claimed;
    }

    public static int getClinchTarget(LockoutBingoSavedData data, LockoutBingoTeam team) {
        if (team == null) {
            return BOARD_SIZE + 1;
        }

        int maxOtherScore = 0;
        for (LockoutBingoTeam otherTeam : LockoutBingoTeam.values()) {
            if (otherTeam == team) {
                continue;
            }
            maxOtherScore = Math.max(maxOtherScore, getScore(data, otherTeam));
        }

        return maxOtherScore + getRemainingTiles(data) + 1;
    }

    private static void openAppropriateScreen(ServerPlayerEntity player) {
        syncToPlayer(player);
        LockoutBingoSavedData data = getData(player.getServer());
        ServerPlayNetworking.send(player, new LockoutBingoOpenScreenPacket(data.isStarted()));
    }

    private static void handleBlockInteraction(ServerPlayerEntity player, ItemStack heldStack, BlockState state) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded() || data.getTeam(player.getUuid()) == null) {
            return;
        }

        claimFirstMatchingGoal(server, data, player, goal -> matchesBlockInteraction(goal, player, heldStack, state));
    }

    private static void handleEntityInteraction(ServerPlayerEntity player, ItemStack heldStack, Entity entity) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded() || data.getTeam(player.getUuid()) == null) {
            return;
        }

        claimFirstMatchingGoal(server, data, player, goal -> matchesEntityInteraction(goal, heldStack, entity));
    }

    private static void handlePlayerDamage(ServerPlayerEntity player, DamageSource damageSource, float damageTaken) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded() || data.getTeam(player.getUuid()) == null) {
            return;
        }

        claimFirstMatchingGoal(server, data, player, goal -> matchesDamageGoal(goal, player, damageSource, damageTaken));
    }

    private static boolean matchesBlockInteraction(LockoutBingoGoal goal, ServerPlayerEntity player, ItemStack heldStack, BlockState state) {
        if (goal.type() != LockoutBingoGoalType.INTERACT && goal.type() != LockoutBingoGoalType.CONSUME) {
            return false;
        }

        return switch (goal.id()) {
            case "eat_cake_slice" -> goal.type() == LockoutBingoGoalType.CONSUME
                    && state.isOf(Blocks.CAKE)
                    && !heldStack.isIn(ItemTags.CANDLES)
                    && player.canConsume(false)
                    && !Chal_39_NoFood.isActive();
            case "use_lectern", "use_grindstone", "use_stonecutter", "use_cartography_table", "use_smithing_table", "use_loom", "ring_bell" ->
                    Registries.BLOCK.getId(state.getBlock()).toString().equals(goal.primaryTarget());
            case "light_campfire" -> state.getBlock() instanceof CampfireBlock
                    && !state.get(CampfireBlock.LIT)
                    && (heldStack.isOf(Items.FLINT_AND_STEEL) || heldStack.isOf(Items.FIRE_CHARGE));
            default -> false;
        };
    }

    private static boolean matchesEntityInteraction(LockoutBingoGoal goal, ItemStack heldStack, Entity entity) {
        if (goal.type() != LockoutBingoGoalType.INTERACT) {
            return false;
        }

        return switch (goal.id()) {
            case "milk_cow" -> entity instanceof CowEntity && heldStack.isOf(Items.BUCKET);
            case "shear_sheep" -> entity instanceof SheepEntity sheep && heldStack.isOf(Items.SHEARS) && !sheep.isSheared();
            case "barter_with_piglin" -> entity instanceof PiglinEntity piglin && !piglin.isBaby() && heldStack.isOf(Items.GOLD_INGOT);
            default -> false;
        };
    }

    private static boolean matchesDamageGoal(LockoutBingoGoal goal, ServerPlayerEntity player, DamageSource damageSource, float damageTaken) {
        if (goal.type() != LockoutBingoGoalType.DAMAGE_EVENT) {
            return false;
        }

        return switch (goal.id()) {
            case "survive_explosion" -> damageSource.isOf(DamageTypes.EXPLOSION) || damageSource.isOf(DamageTypes.PLAYER_EXPLOSION);
            case "take_fall_damage" -> damageSource.isOf(DamageTypes.FALL);
            case "fall_20_blocks_and_survive" -> damageSource.isOf(DamageTypes.FALL) && player.fallDistance >= 20.0f;
            case "burn_and_survive" -> damageSource.isOf(DamageTypes.IN_FIRE) || damageSource.isOf(DamageTypes.ON_FIRE) || damageSource.isOf(DamageTypes.CAMPFIRE);
            case "freeze_in_powder_snow" -> damageSource.isOf(DamageTypes.FREEZE);
            case "get_shot_by_skeleton" -> damageSource.isOf(DamageTypes.ARROW) && damageSource.getAttacker() instanceof SkeletonEntity;
            default -> false;
        };
    }

    private static void claimFirstMatchingGoal(
            MinecraftServer server,
            LockoutBingoSavedData data,
            ServerPlayerEntity player,
            Predicate<LockoutBingoGoal> matcher
    ) {
        LockoutBingoTeam team = data.getTeam(player.getUuid());
        if (team == null) {
            return;
        }

        List<String> board = data.getBoardGoalIds();
        for (int i = 0; i < board.size(); i++) {
            if (data.getClaimedTeam(i) != null) {
                continue;
            }

            LockoutBingoGoal goal = LockoutBingoGoalPool.byId(board.get(i));
            if (goal != null && matcher.test(goal)) {
                claimGoal(server, data, i, goal, player, team);
                return;
            }
        }
    }

    private static void handleEntityDeath(LivingEntity entity, net.minecraft.entity.damage.DamageSource damageSource) {
        if (!active || !(damageSource.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded()) {
            return;
        }

        LockoutBingoTeam team = data.getTeam(player.getUuid());
        if (team == null) {
            return;
        }

        String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        List<String> board = data.getBoardGoalIds();
        for (int i = 0; i < board.size(); i++) {
            if (data.getClaimedTeam(i) != null) {
                continue;
            }

            LockoutBingoGoal goal = LockoutBingoGoalPool.byId(board.get(i));
            if (goal != null && goal.type() == LockoutBingoGoalType.KILL && goal.primaryTarget().equals(entityId)) {
                claimGoal(server, data, i, goal, player, team);
                break;
            }
        }
    }

    private static void ensureCurrentRun(MinecraftServer server, LockoutBingoSavedData data) {
        int currentRunId = ChallengeSavedData.get(server.getOverworld()).getRunIndex();
        if (data.getRunId() != currentRunId) {
            data.resetForRun(currentRunId);
        }
    }

    private static LockoutBingoSavedData getData(MinecraftServer server) {
        return LockoutBingoSavedData.get(server.getOverworld());
    }

    private static void ensureMaps(MinecraftServer server, long currentTick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ensureMap(player, currentTick);
        }
    }

    private static void ensureMap(ServerPlayerEntity player, long currentTick) {
        PlayerInventory inventory = player.getInventory();
        int firstSlot = -1;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isOf(ModItems.LOCKOUT_BINGO_MAP)) {
                continue;
            }

            if (firstSlot == -1) {
                firstSlot = i;
            } else {
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }

        if (firstSlot != -1) {
            LAST_MAP_DROP_TICK.remove(player.getUuid());
            return;
        }

        ItemStack mapStack = new ItemStack(ModItems.LOCKOUT_BINGO_MAP);
        if (inventory.insertStack(mapStack)) {
            LAST_MAP_DROP_TICK.remove(player.getUuid());
            return;
        }

        long lastDropTick = LAST_MAP_DROP_TICK.getOrDefault(player.getUuid(), Long.MIN_VALUE);
        if (currentTick - lastDropTick >= MAP_DROP_RETRY_TICKS) {
            player.dropItem(new ItemStack(ModItems.LOCKOUT_BINGO_MAP), false);
            LAST_MAP_DROP_TICK.put(player.getUuid(), currentTick);
        }
    }

    private static void maybeStartGame(MinecraftServer server, LockoutBingoSavedData data) {
        if (data.isStarted() || data.isEnded()) {
            return;
        }

        List<ServerPlayerEntity> participants = getOnlineParticipants(server, data);
        if (participants.size() < 2) {
            return;
        }

        long distinctTeams = participants.stream()
                .map(player -> data.getTeam(player.getUuid()))
                .filter(team -> team != null)
                .distinct()
                .count();
        if (distinctTeams < 2) {
            return;
        }

        boolean everyoneReady = participants.stream().allMatch(player -> data.isReady(player.getUuid()));
        if (!everyoneReady) {
            return;
        }

        data.retainPlayers(participants.stream().map(ServerPlayerEntity::getUuid).collect(java.util.stream.Collectors.toSet()));
        long boardSeed = server.getOverworld().getSeed() ^ server.getOverworld().getTime() ^ participants.size();
        List<LockoutBingoGoal> goals = LockoutBingoGoalPool.pickBoard(boardSeed);
        data.setBoard(goals.stream().map(LockoutBingoGoal::id).toList(), boardSeed, server.getOverworld().getTime());
        captureGoalStatBaselines(data, participants, goals);
        data.setStarted(true);
        data.setEnded(false);
        data.setWinnerTeam(null);
        data.clearReady();

        server.getPlayerManager().broadcast(Text.translatable("challengecraft.lockout.start.broadcast").formatted(Formatting.GOLD), false);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 1.2f);
        }

        scanPassiveGoals(server, data);
        syncToAll(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new LockoutBingoOpenScreenPacket(true));
        }
    }

    private static void captureGoalStatBaselines(
            LockoutBingoSavedData data,
            List<ServerPlayerEntity> participants,
            List<LockoutBingoGoal> goals
    ) {
        for (ServerPlayerEntity player : participants) {
            for (LockoutBingoGoal goal : goals) {
                if (!usesStatBaseline(goal)) {
                    continue;
                }
                data.setGoalStatBaseline(player.getUuid(), goal.id(), readProgressStat(player, goal));
            }
        }
    }

    private static boolean usesStatBaseline(LockoutBingoGoal goal) {
        return switch (goal.type()) {
            case CRAFT, TRADE, FISHING, ACTION, ENCHANT -> true;
            case INTERACT -> goal.id().startsWith("breed_");
            case CONSUME -> !"eat_cake_slice".equals(goal.id());
            default -> false;
        };
    }

    private static List<ServerPlayerEntity> getOnlineParticipants(MinecraftServer server, LockoutBingoSavedData data) {
        return server.getPlayerManager().getPlayerList().stream()
                .filter(player -> data.getTeam(player.getUuid()) != null)
                .toList();
    }

    private static void scanPassiveGoals(MinecraftServer server, LockoutBingoSavedData data) {
        if (!data.isStarted() || data.isEnded()) {
            return;
        }

        List<ServerPlayerEntity> participants = getOnlineParticipants(server, data);
        if (participants.isEmpty()) {
            return;
        }

        List<String> board = data.getBoardGoalIds();
        for (int i = 0; i < board.size(); i++) {
            if (data.getClaimedTeam(i) != null) {
                continue;
            }

            LockoutBingoGoal goal = LockoutBingoGoalPool.byId(board.get(i));
            if (goal == null || goal.type() == LockoutBingoGoalType.KILL) {
                continue;
            }

            for (ServerPlayerEntity player : participants) {
                LockoutBingoTeam team = data.getTeam(player.getUuid());
                if (team == null) {
                    continue;
                }

                if (matchesPassiveGoal(data, player, goal)) {
                    claimGoal(server, data, i, goal, player, team);
                    if (data.isEnded()) {
                        return;
                    }
                    break;
                }
            }
        }
    }

    private static boolean matchesPassiveGoal(LockoutBingoSavedData data, ServerPlayerEntity player, LockoutBingoGoal goal) {
        return switch (goal.type()) {
            case ITEM, ITEM_TAG -> inventoryHasGoal(player, goal);
            case ITEM_AMOUNT -> countMatchingItems(player, goal) >= goal.amount();
            case CRAFT, TRADE -> hasAdvancedStat(data, player, goal);
            case CONSUME -> "eat_cake_slice".equals(goal.id()) ? false : hasAdvancedStat(data, player, goal);
            case DIMENSION -> player.getWorld().getRegistryKey().getValue().toString().equals(goal.primaryTarget());
            case BIOME -> player.getWorld().getBiome(player.getBlockPos())
                    .getKey()
                    .map(key -> key.getValue().toString())
                    .filter(goal.primaryTarget()::equals)
                    .isPresent();
            case EQUIP -> matchesEquipGoal(player, goal);
            case INTERACT -> matchesPassiveInteractGoal(player, goal);
            case INVENTORY_SET -> matchesInventorySetGoal(player, goal);
            case FISHING, ACTION, ENCHANT -> hasAdvancedStat(data, player, goal);
            case ADVANCEMENT -> hasAdvancement(player, goal.primaryTarget());
            case STRUCTURE -> matchesStructureGoal(player, goal);
            case STATUS_EFFECT -> matchesStatusGoal(player, goal);
            case HEALTH_CHECK -> player.getMaxHealth() - player.getHealth() >= 20.0f;
            case LOCATION -> matchesLocationGoal(player, goal);
            default -> false;
        };
    }

    private static boolean inventoryHasGoal(ServerPlayerEntity player, LockoutBingoGoal goal) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (goal.matchesItem(player.getInventory().getStack(i))) {
                return true;
            }
        }
        return false;
    }

    private static int countMatchingItems(ServerPlayerEntity player, LockoutBingoGoal goal) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (goal.matchesItem(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean hasAdvancedStat(LockoutBingoSavedData data, ServerPlayerEntity player, LockoutBingoGoal goal) {
        return readProgressStat(player, goal) > data.getGoalStatBaseline(player.getUuid(), goal.id());
    }

    private static int readProgressStat(ServerPlayerEntity player, LockoutBingoGoal goal) {
        return switch (goal.type()) {
            case CRAFT -> sumItemStats(player, goal.targets(), StatKind.CRAFTED);
            case CONSUME -> sumItemStats(player, goal.targets(), StatKind.USED);
            case TRADE -> "trade_with_villager".equals(goal.id())
                    ? player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TRADED_WITH_VILLAGER))
                    : 0;
            case INTERACT -> readInteractStat(player, goal);
            case FISHING -> player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.FISH_CAUGHT));
            case ENCHANT -> "enchant_item".equals(goal.id())
                    ? player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.ENCHANT_ITEM))
                    : countEnchantedTargetItems(player, goal);
            case ACTION -> readActionStat(player, goal);
            default -> 0;
        };
    }

    private static int readActionStat(ServerPlayerEntity player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "block_damage_with_shield" -> player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_BLOCKED_BY_SHIELD));
            case "place_tnt" -> sumItemStats(player, List.of("minecraft:tnt"), StatKind.USED);
            case "ignite_tnt" -> sumItemStats(player, List.of("minecraft:flint_and_steel", "minecraft:fire_charge"), StatKind.USED);
            case "shoot_crossbow" -> sumItemStats(player, List.of("minecraft:crossbow"), StatKind.USED);
            case "obtain_firework_crossbow" -> countFireworkCrossbows(player);
            case "hit_target_block" -> player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TARGET_HIT));
            case "throw_ender_pearl" -> sumItemStats(player, List.of("minecraft:ender_pearl"), StatKind.USED);
            case "throw_trident" -> sumItemStats(player, List.of("minecraft:trident"), StatKind.USED);
            case "use_totem" -> sumItemStats(player, List.of("minecraft:totem_of_undying"), StatKind.USED);
            case "splash_potion" -> sumItemStats(player, List.of("minecraft:splash_potion"), StatKind.USED);
            default -> 0;
        };
    }

    private static int readInteractStat(ServerPlayerEntity player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "breed_animals" -> player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.ANIMALS_BRED));
            case "breed_cows" -> hasAdvancementCriterion(player, "minecraft:husbandry/bred_all_animals", "minecraft:cow") ? 1 : 0;
            case "breed_sheep" -> hasAdvancementCriterion(player, "minecraft:husbandry/bred_all_animals", "minecraft:sheep") ? 1 : 0;
            case "breed_pigs" -> hasAdvancementCriterion(player, "minecraft:husbandry/bred_all_animals", "minecraft:pig") ? 1 : 0;
            case "breed_chickens" -> hasAdvancementCriterion(player, "minecraft:husbandry/bred_all_animals", "minecraft:chicken") ? 1 : 0;
            default -> 0;
        };
    }

    private static int sumItemStats(ServerPlayerEntity player, List<String> itemIds, StatKind kind) {
        int total = 0;
        for (String itemId : itemIds) {
            Item item = Registries.ITEM.get(net.minecraft.util.Identifier.of(itemId));
            total += switch (kind) {
                case CRAFTED -> player.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(item));
                case USED -> player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));
            };
        }
        return total;
    }

    private static int countEnchantedTargetItems(ServerPlayerEntity player, LockoutBingoGoal goal) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (matchesEnchantedItemGoal(stack, goal)) {
                count++;
            }
        }
        return count;
    }

    private static boolean matchesEnchantedItemGoal(ItemStack stack, LockoutBingoGoal goal) {
        if (!stack.hasEnchantments()) {
            return false;
        }

        return switch (goal.id()) {
            case "enchant_sword" -> stack.isIn(ItemTags.SWORDS);
            case "enchant_pickaxe" -> stack.isIn(ItemTags.PICKAXES);
            default -> goal.matchesItem(stack);
        };
    }

    private static int countFireworkCrossbows(ServerPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(Items.CROSSBOW)) {
                continue;
            }

            ChargedProjectilesComponent charged = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
            if (charged != null && charged.contains(Items.FIREWORK_ROCKET)) {
                count++;
            }
        }
        return count;
    }

    private static boolean matchesEquipGoal(ServerPlayerEntity player, LockoutBingoGoal goal) {
        if (!goal.contextTarget().isBlank() && !player.getWorld().getRegistryKey().getValue().toString().equals(goal.contextTarget())) {
            return false;
        }

        int matched = 0;
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = player.getEquippedStack(slot);
            if (goal.matchesItem(stack)) {
                matched++;
            }
        }
        return matched >= goal.amount();
    }

    private static boolean matchesInventorySetGoal(ServerPlayerEntity player, LockoutBingoGoal goal) {
        Set<String> matchedTargets = new HashSet<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                continue;
            }

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            if (matchesInventorySetTarget(stack, goal.targets())) {
                matchedTargets.add(itemId);
            }
        }
        return matchedTargets.size() >= goal.amount();
    }

    private static boolean matchesInventorySetTarget(ItemStack stack, List<String> targets) {
        for (String target : targets) {
            if (target.startsWith("#")) {
                if (stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of(target.substring(1))))) {
                    return true;
                }
                continue;
            }

            if (Registries.ITEM.getId(stack.getItem()).toString().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPassiveInteractGoal(ServerPlayerEntity player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "sleep_in_bed" -> player.isSleeping();
            case "breed_animals", "breed_cows", "breed_sheep", "breed_pigs", "breed_chickens" -> hasAdvancedStat(getData(player.getServer()), player, goal);
            case "ride_horse" -> player.getVehicle() instanceof HorseEntity;
            case "ride_pig" -> player.getVehicle() instanceof PigEntity;
            case "ride_strider" -> player.getVehicle() instanceof StriderEntity;
            case "activate_pressure_plate" -> hasPoweredPressurePlateNear(player);
            default -> false;
        };
    }

    private static boolean isPotionStack(ItemStack stack) {
        return stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION);
    }

    private static boolean matchesBrewGoal(LockoutBingoGoal goal, ItemStack stack) {
        if (goal.type() != LockoutBingoGoalType.BREW || !isPotionStack(stack)) {
            return false;
        }

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) {
            return false;
        }

        String potionId = contents.potion()
                .flatMap(entry -> entry.getKey().map(key -> key.getValue().toString()))
                .orElse("");
        if ("brew_any_potion".equals(goal.id())) {
            return contents.hasEffects()
                    && !potionId.equals("minecraft:water")
                    && !potionId.equals("minecraft:mundane")
                    && !potionId.equals("minecraft:thick")
                    && !potionId.equals("minecraft:awkward");
        }

        return matchesPotionTarget(potionId, goal.primaryTarget());
    }

    private static boolean matchesPotionTarget(String potionId, String targetId) {
        if (potionId.equals(targetId)) {
            return true;
        }

        String targetPath = Identifier.of(targetId).getPath();
        return potionId.endsWith(":long_" + targetPath) || potionId.endsWith(":strong_" + targetPath);
    }

    private static boolean matchesTradeGoal(LockoutBingoGoal goal, ItemStack stack, Merchant merchant) {
        if (goal.type() != LockoutBingoGoalType.TRADE) {
            return false;
        }

        return switch (goal.id()) {
            case "trade_with_villager" -> true;
            case "obtain_emerald_by_trade", "buy_bread", "buy_arrows", "buy_lapis" -> itemStackMatchesTarget(stack, goal.primaryTarget());
            case "trade_with_librarian", "trade_with_armorer", "trade_with_farmer", "trade_with_cleric", "trade_with_toolsmith", "trade_with_fletcher" ->
                    merchant instanceof VillagerEntity villager && villagerMatchesProfession(villager, goal.primaryTarget());
            default -> false;
        };
    }

    private static boolean itemStackMatchesTarget(ItemStack stack, String targetId) {
        return stack.isOf(Registries.ITEM.get(Identifier.of(targetId)));
    }

    private static boolean villagerMatchesProfession(VillagerEntity villager, String professionId) {
        return villager.getVillagerData().profession().matchesKey(switch (professionId) {
            case "minecraft:librarian" -> VillagerProfession.LIBRARIAN;
            case "minecraft:armorer" -> VillagerProfession.ARMORER;
            case "minecraft:farmer" -> VillagerProfession.FARMER;
            case "minecraft:cleric" -> VillagerProfession.CLERIC;
            case "minecraft:toolsmith" -> VillagerProfession.TOOLSMITH;
            case "minecraft:fletcher" -> VillagerProfession.FLETCHER;
            default -> VillagerProfession.NONE;
        });
    }

    private static boolean hasAdvancement(ServerPlayerEntity player, String advancementId) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        AdvancementEntry advancement = server.getAdvancementLoader().get(Identifier.of(advancementId));
        return advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone();
    }

    private static boolean hasAdvancementCriterion(ServerPlayerEntity player, String advancementId, String criterionId) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        AdvancementEntry advancement = server.getAdvancementLoader().get(Identifier.of(advancementId));
        if (advancement == null) {
            return false;
        }

        for (String obtainedCriterion : player.getAdvancementTracker().getProgress(advancement).getObtainedCriteria()) {
            if (criterionId.equals(obtainedCriterion)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStatusGoal(ServerPlayerEntity player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "get_poisoned" -> player.hasStatusEffect(StatusEffects.POISON);
            case "get_withered" -> player.hasStatusEffect(StatusEffects.WITHER);
            case "get_levitation" -> player.hasStatusEffect(StatusEffects.LEVITATION);
            default -> false;
        };
    }

    private static boolean matchesLocationGoal(ServerPlayerEntity player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "reach_y_minus_50" -> player.getBlockY() <= -50;
            case "reach_build_limit" -> player.getBlockY() >= player.getWorld().getDimension().minY() + player.getWorld().getDimension().height() - 5;
            case "enter_end_gateway" -> isBlockNear(player, state -> state.isOf(Blocks.END_GATEWAY), 3, 3);
            case "stand_on_bedrock" -> {
                BlockPos below = player.getBlockPos().down();
                yield player.getWorld().getBlockState(below).isOf(Blocks.BEDROCK);
            }
            default -> false;
        };
    }

    private static boolean matchesStructureGoal(ServerPlayerEntity player, LockoutBingoGoal goal) {
        Identifier structureId = normalizeStructureId(goal.primaryTarget());
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        BlockPos pos = player.getBlockPos();
        StructureStart start;

        if (isStructureTag(structureId)) {
            start = world.getStructureAccessor().getStructureContaining(pos, TagKey.of(RegistryKeys.STRUCTURE, structureId));
        } else {
            RegistryKey<Structure> key = RegistryKey.of(RegistryKeys.STRUCTURE, structureId);
            Structure structure = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE)
                    .getOptional(key)
                    .map(entry -> entry.value())
                    .orElse(null);
            if (structure == null) {
                return false;
            }
            start = world.getStructureAccessor().getStructureContaining(pos, structure);
        }

        return start != null && start != StructureStart.DEFAULT && start.hasChildren();
    }

    private static Identifier normalizeStructureId(String targetId) {
        return switch (targetId) {
            case "minecraft:jungle_temple" -> Identifier.of("minecraft:jungle_pyramid");
            case "minecraft:ocean_monument" -> Identifier.of("minecraft:monument");
            case "minecraft:woodland_mansion" -> Identifier.of("minecraft:mansion");
            default -> Identifier.of(targetId);
        };
    }

    private static boolean isStructureTag(Identifier structureId) {
        return switch (structureId.toString()) {
            case "minecraft:village", "minecraft:mineshaft", "minecraft:shipwreck", "minecraft:ocean_ruin", "minecraft:ruined_portal" -> true;
            default -> false;
        };
    }

    private static boolean hasPoweredPressurePlateNear(ServerPlayerEntity player) {
        return isBlockNear(player, state -> {
            if (state.contains(PressurePlateBlock.POWERED) && state.get(PressurePlateBlock.POWERED)) {
                return true;
            }
            return state.contains(WeightedPressurePlateBlock.POWER) && state.get(WeightedPressurePlateBlock.POWER) > 0;
        }, 1, 1);
    }

    private static boolean isBlockNear(ServerPlayerEntity player, Predicate<BlockState> predicate, int horizontalRadius, int verticalRadius) {
        BlockPos center = player.getBlockPos();
        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    if (predicate.test(player.getWorld().getBlockState(center.add(x, y, z)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void claimGoal(
            MinecraftServer server,
            LockoutBingoSavedData data,
            int index,
            LockoutBingoGoal goal,
            ServerPlayerEntity player,
            LockoutBingoTeam team
    ) {
        if (!data.isStarted() || data.isEnded() || data.getClaimedTeam(index) != null) {
            return;
        }

        data.claimTile(index, team, player.getUuid(), player.getGameProfile().getName());

        server.getPlayerManager().broadcast(
                Text.translatable("challengecraft.lockout.claim.broadcast", team.displayName(), goal.title()),
                false
        );
        for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            onlinePlayer.getWorld().playSound(null, onlinePlayer.getX(), onlinePlayer.getY(), onlinePlayer.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 1.0f, 1.1f);
        }

        LockoutBingoTeam winner = getWinner(data);
        if (winner != null) {
            finishGame(server, data, winner);
            return;
        }

        if (getRemainingTiles(data) == 0) {
            finishGame(server, data, null);
            return;
        }

        syncToAll(server);
    }

    private static LockoutBingoTeam getWinner(LockoutBingoSavedData data) {
        int remainingTiles = getRemainingTiles(data);
        for (LockoutBingoTeam team : LockoutBingoTeam.values()) {
            int teamScore = getScore(data, team);
            if (teamScore <= 0) {
                continue;
            }

            int maxOtherScore = 0;
            for (LockoutBingoTeam otherTeam : LockoutBingoTeam.values()) {
                if (otherTeam == team) {
                    continue;
                }
                maxOtherScore = Math.max(maxOtherScore, getScore(data, otherTeam));
            }

            if (teamScore > maxOtherScore + remainingTiles) {
                return team;
            }
        }
        return null;
    }

    private static int getScore(LockoutBingoSavedData data, LockoutBingoTeam team) {
        int score = 0;
        for (Integer claimedTeam : data.getClaimedTeams()) {
            if (claimedTeam == team.ordinal()) {
                score++;
            }
        }
        return score;
    }

    private static int getRemainingTiles(LockoutBingoSavedData data) {
        return Math.max(0, BOARD_SIZE - (int) data.getClaimedTeams().stream().filter(teamId -> teamId >= 0).count());
    }

    private static void finishGame(MinecraftServer server, LockoutBingoSavedData data, LockoutBingoTeam winner) {
        data.setEnded(true);
        data.setWinnerTeam(winner);

        long elapsedTicks = Math.max(0L, server.getOverworld().getTime() - Math.max(0L, data.getStartedAtWorldTicks()));
        int elapsedTicksInt = (int) Math.min(Integer.MAX_VALUE, elapsedTicks);

        for (Map.Entry<UUID, Integer> entry : data.getTeamAssignments().entrySet()) {
            UUID uuid = entry.getKey();
            if (data.isRewarded(uuid)) {
                continue;
            }

            LockoutBingoTeam team = LockoutBingoTeam.fromOrdinal(entry.getValue());
            if (team == null) {
                continue;
            }

            long xpAmount = team == winner ? 100L : 50L;
            ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(uuid);
            if (onlinePlayer != null) {
                LevelManager.XpResult result = LevelManager.addXp(onlinePlayer, xpAmount);
                ServerPlayNetworking.send(onlinePlayer, new ChallengeRewardPacket(result.oldXp, result.newXp, result.actualAmount, false));
            } else {
                XpManager.addXp(uuid, xpAmount);
            }

            StatsManager.recordCompletion(uuid.toString(), CHALLENGE_ID, elapsedTicksInt);
            data.setRewarded(uuid);
        }

        Text chatMessage = winner != null
                ? Text.translatable("challengecraft.lockout.win.broadcast", winner.displayName()).formatted(Formatting.GOLD, Formatting.BOLD)
                : Text.translatable("challengecraft.lockout.draw.broadcast").formatted(Formatting.GOLD, Formatting.BOLD);
        server.getPlayerManager().broadcast(chatMessage, false);

        Text title = winner != null
                ? Text.translatable("challengecraft.lockout.win.title", winner.displayName())
                : Text.translatable("challengecraft.lockout.draw.title");
        Text subtitle = winner != null
                ? Text.translatable("challengecraft.lockout.win.subtitle")
                : Text.translatable("challengecraft.lockout.draw.subtitle");
        server.getPlayerManager().sendToAll(new TitleFadeS2CPacket(10, 70, 20));
        server.getPlayerManager().sendToAll(new TitleS2CPacket(title));
        server.getPlayerManager().sendToAll(new SubtitleS2CPacket(subtitle));

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        }

        syncToAll(server);
    }

    private static LockoutBingoSyncPacket buildSyncPacket(MinecraftServer server, LockoutBingoSavedData data) {
        List<LockoutBingoSyncPacket.PlayerState> players = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : data.getTeamAssignments().entrySet()) {
            UUID uuid = entry.getKey();
            ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(uuid);
            String name = data.getPlayerNames().getOrDefault(uuid, onlinePlayer != null ? onlinePlayer.getGameProfile().getName() : uuid.toString());
            players.add(new LockoutBingoSyncPacket.PlayerState(
                    uuid,
                    name,
                    entry.getValue(),
                    data.isReady(uuid),
                    onlinePlayer != null
            ));
        }
        players.sort(Comparator.comparingInt(LockoutBingoSyncPacket.PlayerState::teamId).thenComparing(LockoutBingoSyncPacket.PlayerState::name, String.CASE_INSENSITIVE_ORDER));

        long elapsedTicks = 0L;
        if (data.isStarted() && data.getStartedAtWorldTicks() >= 0L) {
            elapsedTicks = Math.max(0L, server.getOverworld().getTime() - data.getStartedAtWorldTicks());
        }

        return new LockoutBingoSyncPacket(
                data.getBoardGoalIds(),
                data.getClaimedTeams(),
                data.getClaimedByNames(),
                players,
                data.isStarted(),
                data.isEnded(),
                data.getWinnerTeamOrdinal(),
                elapsedTicks,
                data.getRunId()
        );
    }

    private enum StatKind {
        CRAFTED,
        USED
    }
}
