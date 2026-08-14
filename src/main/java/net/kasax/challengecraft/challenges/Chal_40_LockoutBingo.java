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
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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
            if (!active || server.getTickCount() % 20 != 0) {
                return;
            }

            LockoutBingoSavedData data = getData(server);
            ensureCurrentRun(server, data);
            ensureMaps(server, server.getTickCount());

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
                return InteractionResult.PASS;
            }

            ItemStack heldStack = player.getItemInHand(hand);
            if (!heldStack.is(ModItems.LOCKOUT_BINGO_MAP)) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                openAppropriateScreen(serverPlayer);
                return InteractionResult.SUCCESS_SERVER;
            }

            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!active || world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            handleBlockInteraction(serverPlayer, player.getItemInHand(hand), world.getBlockState(hit.getBlockPos()));
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!active || world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            handleEntityInteraction(serverPlayer, player.getItemInHand(hand), entity);
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damageSource, baseDamageTaken, damageTaken, blocked) -> {
            if (!active || !(entity instanceof ServerPlayer player) || damageTaken <= 0.0f || player.isDeadOrDying()) {
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
            data.updatePlayerName(handler.player.getUUID(), handler.player.getGameProfile().name());
            ensureMap(handler.player, server.getTickCount());
            syncToPlayer(handler.player);
        }));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            if (!active) {
                return;
            }

            LockoutBingoSavedData data = getData(server);
            ensureCurrentRun(server, data);
            if (!data.isStarted()) {
                data.setReady(handler.player.getUUID(), false);
                syncToAll(server);
            }
        }));
    }

    public static void handleAction(ServerPlayer player, LockoutBingoActionPacket packet) {
        if (!active) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        data.updatePlayerName(player.getUUID(), player.getGameProfile().name());

        switch (packet.action()) {
            case REQUEST_SYNC -> {
                syncToPlayer(player);
                return;
            }
            case JOIN_TEAM -> {
                if (data.isStarted()) {
                    player.sendOverlayMessage(Component.translatable("challengecraft.lockout.error.started").withStyle(ChatFormatting.RED));
                    syncToPlayer(player);
                    return;
                }

                LockoutBingoTeam team = LockoutBingoTeam.fromOrdinal(packet.teamId());
                if (team == null) {
                    syncToPlayer(player);
                    return;
                }

                data.setTeam(player.getUUID(), player.getGameProfile().name(), team);
                data.setReady(player.getUUID(), false);
            }
            case LEAVE_TEAM -> {
                if (data.isStarted()) {
                    player.sendOverlayMessage(Component.translatable("challengecraft.lockout.error.started").withStyle(ChatFormatting.RED));
                    syncToPlayer(player);
                    return;
                }

                data.removeTeam(player.getUUID());
            }
            case READY -> {
                if (data.isStarted()) {
                    player.sendOverlayMessage(Component.translatable("challengecraft.lockout.error.started").withStyle(ChatFormatting.RED));
                    syncToPlayer(player);
                    return;
                }

                if (data.getTeam(player.getUUID()) == null) {
                    player.sendOverlayMessage(Component.translatable("challengecraft.lockout.error.join_team_first").withStyle(ChatFormatting.RED));
                    syncToPlayer(player);
                    return;
                }

                data.setReady(player.getUUID(), true);
            }
            case UNREADY -> {
                if (!data.isStarted()) {
                    data.setReady(player.getUUID(), false);
                }
            }
        }

        maybeStartGame(server, data);
        if (!data.isStarted()) {
            syncToAll(server);
        }
    }

    public static void handleScreenSlotClick(Player player, AbstractContainerMenu handler, int slotIndex) {
        if (!active || !(player instanceof ServerPlayer serverPlayer) || slotIndex < 0 || slotIndex >= handler.slots.size()) {
            return;
        }

        MinecraftServer server = serverPlayer.level().getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded() || data.getTeam(serverPlayer.getUUID()) == null) {
            return;
        }

        Slot slot = handler.slots.get(slotIndex);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return;
        }

        if (handler instanceof BrewingStandMenu && slot.getContainerSlot() >= 0 && slot.getContainerSlot() <= 2 && isPotionStack(stack)) {
            claimFirstMatchingGoal(server, data, serverPlayer, goal -> matchesBrewGoal(goal, stack));
            return;
        }

        if (handler instanceof MerchantMenu merchantHandler && slot instanceof MerchantResultSlot) {
            Merchant merchant = ((MerchantScreenHandlerAccessor) merchantHandler).challengecraft$getMerchant();
            claimFirstMatchingGoal(server, data, serverPlayer, goal -> matchesTradeGoal(goal, stack, merchant));
        }
    }

    public static void onActivated(ServerLevel world) {
        if (world == null) {
            return;
        }

        MinecraftServer server = world.getServer();
        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        ensureMaps(server, server.getTickCount());
        syncToAll(server);
    }

    public static void syncToAll(MinecraftServer server) {
        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        LockoutBingoSyncPacket packet = buildSyncPacket(server, data);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
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

    public static void startSoloDebugRun(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        active = true;
        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        data.resetForRun(data.getRunId());
        data.setTeam(player.getUUID(), player.getGameProfile().name(), LockoutBingoTeam.RED);
        data.retainPlayers(Set.of(player.getUUID()));

        long boardSeed = server.overworld().getSeed()
                ^ server.overworld().getGameTime()
                ^ player.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits();
        List<LockoutBingoGoal> goals = LockoutBingoGoalPool.pickBoard(boardSeed);
        data.setBoard(goals.stream().map(LockoutBingoGoal::id).toList(), boardSeed, server.overworld().getGameTime());
        captureGoalStatBaselines(data, List.of(player), goals);
        data.setStarted(true);
        data.setEnded(false);
        data.setWinnerTeam(null);
        data.clearReady();

        ensureMap(player, server.getTickCount());
        syncToAll(server);
        ServerPlayNetworking.send(player, new LockoutBingoOpenScreenPacket(true));
    }

    public static int debugClaimTiles(ServerPlayer player, int amount) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !active) {
            return 0;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded()) {
            return 0;
        }

        LockoutBingoTeam team = data.getTeam(player.getUUID());
        if (team == null) {
            team = LockoutBingoTeam.RED;
            data.setTeam(player.getUUID(), player.getGameProfile().name(), team);
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

    private static void openAppropriateScreen(ServerPlayer player) {
        syncToPlayer(player);
        LockoutBingoSavedData data = getData(player.level().getServer());
        ServerPlayNetworking.send(player, new LockoutBingoOpenScreenPacket(data.isStarted()));
    }

    private static void handleBlockInteraction(ServerPlayer player, ItemStack heldStack, BlockState state) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded() || data.getTeam(player.getUUID()) == null) {
            return;
        }

        claimFirstMatchingGoal(server, data, player, goal -> matchesBlockInteraction(goal, player, heldStack, state));
    }

    private static void handleEntityInteraction(ServerPlayer player, ItemStack heldStack, Entity entity) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded() || data.getTeam(player.getUUID()) == null) {
            return;
        }

        claimFirstMatchingGoal(server, data, player, goal -> matchesEntityInteraction(goal, heldStack, entity));
    }

    private static void handlePlayerDamage(ServerPlayer player, DamageSource damageSource, float damageTaken) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded() || data.getTeam(player.getUUID()) == null) {
            return;
        }

        claimFirstMatchingGoal(server, data, player, goal -> matchesDamageGoal(goal, player, damageSource, damageTaken));
    }

    private static boolean matchesBlockInteraction(LockoutBingoGoal goal, ServerPlayer player, ItemStack heldStack, BlockState state) {
        if (goal.type() != LockoutBingoGoalType.INTERACT && goal.type() != LockoutBingoGoalType.CONSUME) {
            return false;
        }

        return switch (goal.id()) {
            case "eat_cake_slice" -> goal.type() == LockoutBingoGoalType.CONSUME
                    && state.is(Blocks.CAKE)
                    && !heldStack.is(ItemTags.CANDLES)
                    && player.canEat(false)
                    && !Chal_39_NoFood.isActive();
            case "use_lectern", "use_grindstone", "use_stonecutter", "use_cartography_table", "use_smithing_table", "use_loom", "ring_bell" ->
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(goal.primaryTarget());
            case "light_campfire" -> state.getBlock() instanceof CampfireBlock
                    && !state.getValue(CampfireBlock.LIT)
                    && (heldStack.is(Items.FLINT_AND_STEEL) || heldStack.is(Items.FIRE_CHARGE));
            default -> false;
        };
    }

    private static boolean matchesEntityInteraction(LockoutBingoGoal goal, ItemStack heldStack, Entity entity) {
        if (goal.type() != LockoutBingoGoalType.INTERACT) {
            return false;
        }

        return switch (goal.id()) {
            case "milk_cow" -> entity instanceof Cow && heldStack.is(Items.BUCKET);
            case "shear_sheep" -> entity instanceof Sheep sheep && heldStack.is(Items.SHEARS) && !sheep.isSheared();
            case "barter_with_piglin" -> entity instanceof Piglin piglin && !piglin.isBaby() && heldStack.is(Items.GOLD_INGOT);
            default -> false;
        };
    }

    private static boolean matchesDamageGoal(LockoutBingoGoal goal, ServerPlayer player, DamageSource damageSource, float damageTaken) {
        if (goal.type() != LockoutBingoGoalType.DAMAGE_EVENT) {
            return false;
        }

        return switch (goal.id()) {
            case "survive_explosion" -> damageSource.is(DamageTypes.EXPLOSION) || damageSource.is(DamageTypes.PLAYER_EXPLOSION);
            case "take_fall_damage" -> damageSource.is(DamageTypes.FALL);
            case "fall_20_blocks_and_survive" -> damageSource.is(DamageTypes.FALL) && player.fallDistance >= 20.0f;
            case "burn_and_survive" -> damageSource.is(DamageTypes.IN_FIRE) || damageSource.is(DamageTypes.ON_FIRE) || damageSource.is(DamageTypes.CAMPFIRE);
            case "freeze_in_powder_snow" -> damageSource.is(DamageTypes.FREEZE);
            case "get_shot_by_skeleton" -> damageSource.is(DamageTypes.ARROW) && damageSource.getEntity() instanceof Skeleton;
            default -> false;
        };
    }

    private static void claimFirstMatchingGoal(
            MinecraftServer server,
            LockoutBingoSavedData data,
            ServerPlayer player,
            Predicate<LockoutBingoGoal> matcher
    ) {
        LockoutBingoTeam team = data.getTeam(player.getUUID());
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

    private static void handleEntityDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource damageSource) {
        if (!active || !(damageSource.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        LockoutBingoSavedData data = getData(server);
        ensureCurrentRun(server, data);
        if (!data.isStarted() || data.isEnded()) {
            return;
        }

        LockoutBingoTeam team = data.getTeam(player.getUUID());
        if (team == null) {
            return;
        }

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
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
        int currentRunId = ChallengeSavedData.get(server.overworld()).getRunIndex();
        if (data.getRunId() != currentRunId) {
            data.resetForRun(currentRunId);
        }
    }

    private static LockoutBingoSavedData getData(MinecraftServer server) {
        return LockoutBingoSavedData.get(server.overworld());
    }

    private static void ensureMaps(MinecraftServer server, long currentTick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ensureMap(player, currentTick);
        }
    }

    private static void ensureMap(ServerPlayer player, long currentTick) {
        Inventory inventory = player.getInventory();
        int firstSlot = -1;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.LOCKOUT_BINGO_MAP)) {
                continue;
            }

            if (firstSlot == -1) {
                firstSlot = i;
            } else {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }

        if (firstSlot != -1) {
            LAST_MAP_DROP_TICK.remove(player.getUUID());
            return;
        }

        ItemStack mapStack = new ItemStack(ModItems.LOCKOUT_BINGO_MAP);
        if (inventory.add(mapStack)) {
            LAST_MAP_DROP_TICK.remove(player.getUUID());
            return;
        }

        long lastDropTick = LAST_MAP_DROP_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (currentTick - lastDropTick >= MAP_DROP_RETRY_TICKS) {
            player.drop(new ItemStack(ModItems.LOCKOUT_BINGO_MAP), false);
            LAST_MAP_DROP_TICK.put(player.getUUID(), currentTick);
        }
    }

    private static void maybeStartGame(MinecraftServer server, LockoutBingoSavedData data) {
        if (data.isStarted() || data.isEnded()) {
            return;
        }

        List<ServerPlayer> participants = getOnlineParticipants(server, data);
        if (participants.size() < 2) {
            return;
        }

        long distinctTeams = participants.stream()
                .map(player -> data.getTeam(player.getUUID()))
                .filter(team -> team != null)
                .distinct()
                .count();
        if (distinctTeams < 2) {
            return;
        }

        boolean everyoneReady = participants.stream().allMatch(player -> data.isReady(player.getUUID()));
        if (!everyoneReady) {
            return;
        }

        data.retainPlayers(participants.stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet()));
        long boardSeed = server.overworld().getSeed() ^ server.overworld().getGameTime() ^ participants.size();
        List<LockoutBingoGoal> goals = LockoutBingoGoalPool.pickBoard(boardSeed);
        data.setBoard(goals.stream().map(LockoutBingoGoal::id).toList(), boardSeed, server.overworld().getGameTime());
        captureGoalStatBaselines(data, participants, goals);
        data.setStarted(true);
        data.setEnded(false);
        data.setWinnerTeam(null);
        data.clearReady();

        server.getPlayerList().broadcastSystemMessage(Component.translatable("challengecraft.lockout.start.broadcast").withStyle(ChatFormatting.GOLD), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_PLING, SoundSource.MASTER, 1.0f, 1.2f);
        }

        scanPassiveGoals(server, data);
        syncToAll(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new LockoutBingoOpenScreenPacket(true));
        }
    }

    private static void captureGoalStatBaselines(
            LockoutBingoSavedData data,
            List<ServerPlayer> participants,
            List<LockoutBingoGoal> goals
    ) {
        for (ServerPlayer player : participants) {
            for (LockoutBingoGoal goal : goals) {
                if (!usesStatBaseline(goal)) {
                    continue;
                }
                data.setGoalStatBaseline(player.getUUID(), goal.id(), readProgressStat(player, goal));
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

    private static List<ServerPlayer> getOnlineParticipants(MinecraftServer server, LockoutBingoSavedData data) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> data.getTeam(player.getUUID()) != null)
                .toList();
    }

    private static void scanPassiveGoals(MinecraftServer server, LockoutBingoSavedData data) {
        if (!data.isStarted() || data.isEnded()) {
            return;
        }

        List<ServerPlayer> participants = getOnlineParticipants(server, data);
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

            for (ServerPlayer player : participants) {
                LockoutBingoTeam team = data.getTeam(player.getUUID());
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

    private static boolean matchesPassiveGoal(LockoutBingoSavedData data, ServerPlayer player, LockoutBingoGoal goal) {
        return switch (goal.type()) {
            case ITEM, ITEM_TAG -> inventoryHasGoal(player, goal);
            case ITEM_AMOUNT -> countMatchingItems(player, goal) >= goal.amount();
            case CRAFT, TRADE -> hasAdvancedStat(data, player, goal);
            case CONSUME -> "eat_cake_slice".equals(goal.id()) ? false : hasAdvancedStat(data, player, goal);
            case DIMENSION -> player.level().dimension().identifier().toString().equals(goal.primaryTarget());
            case BIOME -> player.level().getBiome(player.blockPosition())
                    .unwrapKey()
                    .map(key -> key.identifier().toString())
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

    private static boolean inventoryHasGoal(ServerPlayer player, LockoutBingoGoal goal) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (goal.matchesItem(player.getInventory().getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static int countMatchingItems(ServerPlayer player, LockoutBingoGoal goal) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (goal.matchesItem(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean hasAdvancedStat(LockoutBingoSavedData data, ServerPlayer player, LockoutBingoGoal goal) {
        return readProgressStat(player, goal) > data.getGoalStatBaseline(player.getUUID(), goal.id());
    }

    private static int readProgressStat(ServerPlayer player, LockoutBingoGoal goal) {
        return switch (goal.type()) {
            case CRAFT -> sumItemStats(player, goal.targets(), StatKind.CRAFTED);
            case CONSUME -> sumItemStats(player, goal.targets(), StatKind.USED);
            case TRADE -> "trade_with_villager".equals(goal.id())
                    ? player.getStats().getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER))
                    : 0;
            case INTERACT -> readInteractStat(player, goal);
            case FISHING -> player.getStats().getValue(Stats.CUSTOM.get(Stats.FISH_CAUGHT));
            case ENCHANT -> "enchant_item".equals(goal.id())
                    ? player.getStats().getValue(Stats.CUSTOM.get(Stats.ENCHANT_ITEM))
                    : countEnchantedTargetItems(player, goal);
            case ACTION -> readActionStat(player, goal);
            default -> 0;
        };
    }

    private static int readActionStat(ServerPlayer player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "block_damage_with_shield" -> player.getStats().getValue(Stats.CUSTOM.get(Stats.DAMAGE_BLOCKED_BY_SHIELD));
            case "place_tnt" -> sumItemStats(player, List.of("minecraft:tnt"), StatKind.USED);
            case "ignite_tnt" -> sumItemStats(player, List.of("minecraft:flint_and_steel", "minecraft:fire_charge"), StatKind.USED);
            case "shoot_crossbow" -> sumItemStats(player, List.of("minecraft:crossbow"), StatKind.USED);
            case "obtain_firework_crossbow" -> countFireworkCrossbows(player);
            case "hit_target_block" -> player.getStats().getValue(Stats.CUSTOM.get(Stats.TARGET_HIT));
            case "throw_ender_pearl" -> sumItemStats(player, List.of("minecraft:ender_pearl"), StatKind.USED);
            case "throw_trident" -> sumItemStats(player, List.of("minecraft:trident"), StatKind.USED);
            case "use_totem" -> sumItemStats(player, List.of("minecraft:totem_of_undying"), StatKind.USED);
            case "splash_potion" -> sumItemStats(player, List.of("minecraft:splash_potion"), StatKind.USED);
            default -> 0;
        };
    }

    private static int readInteractStat(ServerPlayer player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "breed_animals" -> player.getStats().getValue(Stats.CUSTOM.get(Stats.ANIMALS_BRED));
            case "breed_cows" -> hasAdvancementCriterion(player, "minecraft:husbandry/bred_all_animals", "minecraft:cow") ? 1 : 0;
            case "breed_sheep" -> hasAdvancementCriterion(player, "minecraft:husbandry/bred_all_animals", "minecraft:sheep") ? 1 : 0;
            case "breed_pigs" -> hasAdvancementCriterion(player, "minecraft:husbandry/bred_all_animals", "minecraft:pig") ? 1 : 0;
            case "breed_chickens" -> hasAdvancementCriterion(player, "minecraft:husbandry/bred_all_animals", "minecraft:chicken") ? 1 : 0;
            default -> 0;
        };
    }

    private static int sumItemStats(ServerPlayer player, List<String> itemIds, StatKind kind) {
        int total = 0;
        for (String itemId : itemIds) {
            Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId));
            total += switch (kind) {
                case CRAFTED -> player.getStats().getValue(Stats.ITEM_CRAFTED.get(item));
                case USED -> player.getStats().getValue(Stats.ITEM_USED.get(item));
            };
        }
        return total;
    }

    private static int countEnchantedTargetItems(ServerPlayer player, LockoutBingoGoal goal) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matchesEnchantedItemGoal(stack, goal)) {
                count++;
            }
        }
        return count;
    }

    private static boolean matchesEnchantedItemGoal(ItemStack stack, LockoutBingoGoal goal) {
        if (!stack.isEnchanted()) {
            return false;
        }

        return switch (goal.id()) {
            case "enchant_sword" -> stack.is(ItemTags.SWORDS);
            case "enchant_pickaxe" -> stack.is(ItemTags.PICKAXES);
            default -> goal.matchesItem(stack);
        };
    }

    private static int countFireworkCrossbows(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(Items.CROSSBOW)) {
                continue;
            }

            ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (charged != null && charged.contains(Items.FIREWORK_ROCKET)) {
                count++;
            }
        }
        return count;
    }

    private static boolean matchesEquipGoal(ServerPlayer player, LockoutBingoGoal goal) {
        if (!goal.contextTarget().isBlank() && !player.level().dimension().identifier().toString().equals(goal.contextTarget())) {
            return false;
        }

        int matched = 0;
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = player.getItemBySlot(slot);
            if (goal.matchesItem(stack)) {
                matched++;
            }
        }
        return matched >= goal.amount();
    }

    private static boolean matchesInventorySetGoal(ServerPlayer player, LockoutBingoGoal goal) {
        Set<String> matchedTargets = new HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (matchesInventorySetTarget(stack, goal.targets())) {
                matchedTargets.add(itemId);
            }
        }
        return matchedTargets.size() >= goal.amount();
    }

    private static boolean matchesInventorySetTarget(ItemStack stack, List<String> targets) {
        for (String target : targets) {
            if (target.startsWith("#")) {
                if (stack.is(TagKey.create(Registries.ITEM, Identifier.parse(target.substring(1))))) {
                    return true;
                }
                continue;
            }

            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPassiveInteractGoal(ServerPlayer player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "sleep_in_bed" -> player.isSleeping();
            case "breed_animals", "breed_cows", "breed_sheep", "breed_pigs", "breed_chickens" -> hasAdvancedStat(getData(player.level().getServer()), player, goal);
            case "ride_horse" -> player.getVehicle() instanceof Horse;
            case "ride_pig" -> player.getVehicle() instanceof Pig;
            case "ride_strider" -> player.getVehicle() instanceof Strider;
            case "activate_pressure_plate" -> hasPoweredPressurePlateNear(player);
            default -> false;
        };
    }

    private static boolean isPotionStack(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }

    private static boolean matchesBrewGoal(LockoutBingoGoal goal, ItemStack stack) {
        if (goal.type() != LockoutBingoGoalType.BREW || !isPotionStack(stack)) {
            return false;
        }

        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) {
            return false;
        }

        String potionId = contents.potion()
                .flatMap(entry -> entry.unwrapKey().map(key -> key.identifier().toString()))
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

        String targetPath = Identifier.parse(targetId).getPath();
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
                    merchant instanceof Villager villager && villagerMatchesProfession(villager, goal.primaryTarget());
            default -> false;
        };
    }

    private static boolean itemStackMatchesTarget(ItemStack stack, String targetId) {
        return stack.is(BuiltInRegistries.ITEM.getValue(Identifier.parse(targetId)));
    }

    private static boolean villagerMatchesProfession(Villager villager, String professionId) {
        return villager.getVillagerData().profession().is(switch (professionId) {
            case "minecraft:librarian" -> VillagerProfession.LIBRARIAN;
            case "minecraft:armorer" -> VillagerProfession.ARMORER;
            case "minecraft:farmer" -> VillagerProfession.FARMER;
            case "minecraft:cleric" -> VillagerProfession.CLERIC;
            case "minecraft:toolsmith" -> VillagerProfession.TOOLSMITH;
            case "minecraft:fletcher" -> VillagerProfession.FLETCHER;
            default -> VillagerProfession.NONE;
        });
    }

    private static boolean hasAdvancement(ServerPlayer player, String advancementId) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }

        AdvancementHolder advancement = server.getAdvancements().get(Identifier.parse(advancementId));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static boolean hasAdvancementCriterion(ServerPlayer player, String advancementId, String criterionId) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }

        AdvancementHolder advancement = server.getAdvancements().get(Identifier.parse(advancementId));
        if (advancement == null) {
            return false;
        }

        for (String obtainedCriterion : player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria()) {
            if (criterionId.equals(obtainedCriterion)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStatusGoal(ServerPlayer player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "get_poisoned" -> player.hasEffect(MobEffects.POISON);
            case "get_withered" -> player.hasEffect(MobEffects.WITHER);
            case "get_levitation" -> player.hasEffect(MobEffects.LEVITATION);
            default -> false;
        };
    }

    private static boolean matchesLocationGoal(ServerPlayer player, LockoutBingoGoal goal) {
        return switch (goal.id()) {
            case "reach_y_minus_50" -> player.getBlockY() <= -50;
            case "reach_build_limit" -> player.getBlockY() >= player.level().dimensionType().minY() + player.level().dimensionType().height() - 5;
            case "enter_end_gateway" -> isBlockNear(player, state -> state.is(Blocks.END_GATEWAY), 3, 3);
            case "stand_on_bedrock" -> {
                BlockPos below = player.blockPosition().below();
                yield player.level().getBlockState(below).is(Blocks.BEDROCK);
            }
            default -> false;
        };
    }

    private static boolean matchesStructureGoal(ServerPlayer player, LockoutBingoGoal goal) {
        Identifier structureId = normalizeStructureId(goal.primaryTarget());
        if (!(player.level() instanceof ServerLevel world)) {
            return false;
        }
        BlockPos pos = player.blockPosition();
        StructureStart start;

        if (isStructureTag(structureId)) {
            start = world.structureManager().getStructureWithPieceAt(pos, TagKey.create(Registries.STRUCTURE, structureId));
        } else {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structureId);
            Structure structure = world.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .get(key)
                    .map(entry -> entry.value())
                    .orElse(null);
            if (structure == null) {
                return false;
            }
            start = world.structureManager().getStructureWithPieceAt(pos, structure);
        }

        return start != null && start != StructureStart.INVALID_START && start.isValid();
    }

    private static Identifier normalizeStructureId(String targetId) {
        return switch (targetId) {
            case "minecraft:jungle_temple" -> Identifier.parse("minecraft:jungle_pyramid");
            case "minecraft:ocean_monument" -> Identifier.parse("minecraft:monument");
            case "minecraft:woodland_mansion" -> Identifier.parse("minecraft:mansion");
            default -> Identifier.parse(targetId);
        };
    }

    private static boolean isStructureTag(Identifier structureId) {
        return switch (structureId.toString()) {
            case "minecraft:village", "minecraft:mineshaft", "minecraft:shipwreck", "minecraft:ocean_ruin", "minecraft:ruined_portal" -> true;
            default -> false;
        };
    }

    private static boolean hasPoweredPressurePlateNear(ServerPlayer player) {
        return isBlockNear(player, state -> {
            if (state.hasProperty(PressurePlateBlock.POWERED) && state.getValue(PressurePlateBlock.POWERED)) {
                return true;
            }
            return state.hasProperty(WeightedPressurePlateBlock.POWER) && state.getValue(WeightedPressurePlateBlock.POWER) > 0;
        }, 1, 1);
    }

    private static boolean isBlockNear(ServerPlayer player, Predicate<BlockState> predicate, int horizontalRadius, int verticalRadius) {
        BlockPos center = player.blockPosition();
        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    if (predicate.test(player.level().getBlockState(center.offset(x, y, z)))) {
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
            ServerPlayer player,
            LockoutBingoTeam team
    ) {
        if (!data.isStarted() || data.isEnded() || data.getClaimedTeam(index) != null) {
            return;
        }

        data.claimTile(index, team, player.getUUID(), player.getGameProfile().name());

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("challengecraft.lockout.claim.broadcast", team.displayName(), goal.title()),
                false
        );
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            onlinePlayer.level().playSound(null, onlinePlayer.getX(), onlinePlayer.getY(), onlinePlayer.getZ(), SoundEvents.NOTE_BLOCK_CHIME, SoundSource.MASTER, 1.0f, 1.1f);
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

        long elapsedTicks = Math.max(0L, server.overworld().getGameTime() - Math.max(0L, data.getStartedAtWorldTicks()));
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
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
            if (onlinePlayer != null) {
                LevelManager.XpResult result = LevelManager.addXp(onlinePlayer, xpAmount);
                ServerPlayNetworking.send(onlinePlayer, new ChallengeRewardPacket(result.oldXp, result.newXp, result.actualAmount, false));
            } else {
                XpManager.addXp(uuid, xpAmount);
            }

            StatsManager.recordCompletion(uuid.toString(), CHALLENGE_ID, elapsedTicksInt);
            data.setRewarded(uuid);
        }

        Component chatMessage = winner != null
                ? Component.translatable("challengecraft.lockout.win.broadcast", winner.displayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                : Component.translatable("challengecraft.lockout.draw.broadcast").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        server.getPlayerList().broadcastSystemMessage(chatMessage, false);

        Component title = winner != null
                ? Component.translatable("challengecraft.lockout.win.title", winner.displayName())
                : Component.translatable("challengecraft.lockout.draw.title");
        Component subtitle = winner != null
                ? Component.translatable("challengecraft.lockout.win.subtitle")
                : Component.translatable("challengecraft.lockout.draw.subtitle");
        server.getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        server.getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(title));
        server.getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(subtitle));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
        }

        syncToAll(server);
    }

    private static LockoutBingoSyncPacket buildSyncPacket(MinecraftServer server, LockoutBingoSavedData data) {
        List<LockoutBingoSyncPacket.PlayerState> players = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : data.getTeamAssignments().entrySet()) {
            UUID uuid = entry.getKey();
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
            String name = data.getPlayerNames().getOrDefault(uuid, onlinePlayer != null ? onlinePlayer.getGameProfile().name() : uuid.toString());
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
            elapsedTicks = Math.max(0L, server.overworld().getGameTime() - data.getStartedAtWorldTicks());
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
