package net.kasax.challengecraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.kasax.challengecraft.ChallengeCraft.LOGGER;

public class ChallengeSavedData extends PersistentState {
    private static final String KEY = "challengecraft_challenges";

    /**
     * Codec that serializes both the list of active IDs and the max-health
     * “ticks” (1–20 = half-hearts).
     */
    private static final Codec<ChallengeSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(Codec.INT).fieldOf("active").forGetter(ChallengeSavedData::getActive),
            Codec.INT.fieldOf("maxHeartsTicks").forGetter(ChallengeSavedData::getMaxHeartsTicks),
            Codec.INT.fieldOf("limitedInventorySlots").forGetter(ChallengeSavedData::getLimitedInventorySlots),
            Codec.DOUBLE.fieldOf("initialDifficulty").forGetter(ChallengeSavedData::getInitialDifficulty),
            Codec.BOOL.fieldOf("tainted").forGetter(ChallengeSavedData::isTainted),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("playerXpAwarded", Map.of()).forGetter(data -> {
                Map<String, Boolean> map = new HashMap<>();
                data.playerXpAwarded.forEach((k, v) -> map.put(k.toString(), v));
                return map;
            }),
            Codec.BOOL.fieldOf("difficultySet").forGetter(ChallengeSavedData::isDifficultySet),
            Codec.INT.optionalFieldOf("mobHealthMultiplier", 1).forGetter(ChallengeSavedData::getMobHealthMultiplier),
            Codec.DOUBLE.optionalFieldOf("damageWorldBorderSize", 2.0).forGetter(ChallengeSavedData::getDamageWorldBorderSize),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("playerXp", Map.of()).forGetter(data -> {
                Map<String, Long> map = new HashMap<>();
                data.playerXp.forEach((k, v) -> map.put(k.toString(), v));
                return map;
            }),
            Codec.list(Codec.INT).optionalFieldOf("activePerks", List.of()).forGetter(ChallengeSavedData::getActivePerks),
            Codec.INT.optionalFieldOf("runIndex", 0).forGetter(ChallengeSavedData::getRunIndex),
            Codec.INT.optionalFieldOf("doubleTroubleMultiplier", 2).forGetter(ChallengeSavedData::getDoubleTroubleMultiplier),
            ChallengeProgress.CODEC.forGetter(data -> new ChallengeProgress(data.allItemsOrder, data.allItemsIndex, data.allEntitiesOrder, data.allEntitiesIndex, data.allAdvancementsOrder, data.allAdvancementsIndex))
    ).apply(instance, (active, maxHeartsTicks, limitedInventorySlots, initialDifficulty, tainted, playerXpAwarded, difficultySet, mobHealthMultiplier, damageWorldBorderSize, playerXp, activePerks, runIndex, doubleTroubleMultiplier, progress) ->
            new ChallengeSavedData(active, maxHeartsTicks, limitedInventorySlots, initialDifficulty, tainted, playerXpAwarded, difficultySet, progress.allItemsOrder, progress.allItemsIndex, progress.allEntitiesOrder, progress.allEntitiesIndex, mobHealthMultiplier, damageWorldBorderSize, playerXp, activePerks, runIndex, doubleTroubleMultiplier, progress.allAdvancementsOrder, progress.allAdvancementsIndex)
    ));

    public static final PersistentStateType<ChallengeSavedData> TYPE =
            new PersistentStateType<>(KEY, ChallengeSavedData::new, CODEC, DataFixTypes.LEVEL);

    // ---- your actual state ----

    /** Active challenge IDs (default = []) */
    private final List<Integer> active = new ArrayList<>();

    /** Max-health slider in half-heart “ticks” (1…20). Default = 20 (10 hearts). */
    private int maxHeartsTicks;

    private int limitedInventorySlots;

    /** The difficulty rating calculated when world was created. */
    private double initialDifficulty = 0;

    /** If true, player changed challenges mid-game, so difficulty becomes 0. */
    private boolean tainted = false;

    /** To prevent multiple awards for the same achievement in one world. */
    private final Map<UUID, Boolean> playerXpAwarded = new HashMap<>();

    private boolean difficultySet = false;

    private final List<ItemStack> allItemsOrder = new ArrayList<>();
    private int allItemsIndex = 0;

    private final List<EntityType<?>> allEntitiesOrder = new ArrayList<>();
    private int allEntitiesIndex = 0;

    private int mobHealthMultiplier = 1;
    private double damageWorldBorderSize = 2.0;

    private final List<Integer> activePerks = new ArrayList<>();

    private final Map<UUID, Long> playerXp = new HashMap<>();

    private int runIndex = 0;
    private int doubleTroubleMultiplier = 2;

    private final List<Identifier> allAdvancementsOrder = new ArrayList<>();
    private int allAdvancementsIndex = 0;

    private ChallengeSavedData() {}

    public ChallengeSavedData(List<Integer> active, int maxHeartsTicks, int limitedInventorySlots, double initialDifficulty, boolean tainted, Map<String, Boolean> playerXpAwarded, boolean difficultySet, List<ItemStack> allItemsOrder, int allItemsIndex, List<EntityType<?>> allEntitiesOrder, int allEntitiesIndex, int mobHealthMultiplier, double damageWorldBorderSize, Map<String, Long> playerXp, List<Integer> activePerks, int runIndex, int doubleTroubleMultiplier, List<Identifier> allAdvancementsOrder, int allAdvancementsIndex) {
        this.active.clear();
        this.active.addAll(active);
        this.maxHeartsTicks = maxHeartsTicks;
        this.limitedInventorySlots = limitedInventorySlots;
        this.initialDifficulty = initialDifficulty;
        this.tainted = tainted;
        this.playerXpAwarded.clear();
        playerXpAwarded.forEach((k, v) -> this.playerXpAwarded.put(UUID.fromString(k), v));
        this.difficultySet = difficultySet;
        this.allItemsOrder.clear();
        this.allItemsOrder.addAll(allItemsOrder);
        this.allItemsIndex = allItemsIndex;
        this.allEntitiesOrder.clear();
        this.allEntitiesOrder.addAll(allEntitiesOrder);
        this.allEntitiesIndex = allEntitiesIndex;
        this.mobHealthMultiplier = mobHealthMultiplier;
        this.damageWorldBorderSize = damageWorldBorderSize;
        this.playerXp.clear();
        playerXp.forEach((k, v) -> this.playerXp.put(UUID.fromString(k), v));
        this.activePerks.clear();
        this.activePerks.addAll(activePerks);
        this.runIndex = runIndex;
        this.doubleTroubleMultiplier = doubleTroubleMultiplier;
        this.allAdvancementsOrder.clear();
        this.allAdvancementsOrder.addAll(allAdvancementsOrder);
        this.allAdvancementsIndex = allAdvancementsIndex;
    }

    /** Retrieve (or create) for this world. */
    public static ChallengeSavedData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(TYPE);
    }

    public NbtCompound writeNbt(NbtCompound tag) {
        // Codec-based PersistentState usually doesn't need manual writeNbt if using the constructor that takes CODEC.
        // But for compatibility or if manual NBT is needed:
        return tag;
    }

    // -------- getters & setters --------

    public List<Integer> getActive() {
        return List.copyOf(active);
    }

    public void setActive(List<Integer> newActive) {
        active.clear();
        active.addAll(newActive);
        LOGGER.info("setActive ChallengeSavedData → " + active);
        markDirty();
    }

    public int getMaxHeartsTicks() {
        return maxHeartsTicks;
    }

    public void setMaxHeartsTicks(int ticks) {
        if (ticks < 1) ticks = 1;
        if (ticks > 20) ticks = 20;
        if (this.maxHeartsTicks != ticks) {
            this.maxHeartsTicks = ticks;
            LOGGER.info("setMaxHeartsTicks ChallengeSavedData → " + ticks);
            markDirty();
        }
    }
    public int getLimitedInventorySlots() {
        return limitedInventorySlots;
    }

    public void setLimitedInventorySlots(int slots) {
        this.limitedInventorySlots = slots;
        markDirty();
    }

    public double getInitialDifficulty() {
        return initialDifficulty;
    }

    public void setInitialDifficulty(double difficulty) {
        this.initialDifficulty = difficulty;
        markDirty();
    }

    public boolean isTainted() {
        return tainted;
    }

    public void setTainted(boolean tainted) {
        this.tainted = tainted;
        markDirty();
    }

    public boolean isXpAwarded(UUID uuid) {
        return playerXpAwarded.getOrDefault(uuid, false);
    }

    public void setXpAwarded(UUID uuid, boolean xpAwarded) {
        this.playerXpAwarded.put(uuid, xpAwarded);
        markDirty();
    }

    public void resetForNewWorld() {
        this.playerXpAwarded.clear();
        this.allItemsIndex = 0;
        this.allEntitiesIndex = 0;
        this.allAdvancementsIndex = 0;
        this.allItemsOrder.clear();
        this.allEntitiesOrder.clear();
        this.allAdvancementsOrder.clear();
        this.tainted = false;
        this.difficultySet = false;
        this.runIndex++;
        markDirty();
    }

    public boolean isDifficultySet() {
        return difficultySet;
    }

    public void setDifficultySet(boolean difficultySet) {
        this.difficultySet = difficultySet;
        markDirty();
    }

    public List<ItemStack> getAllItemsOrder() {
        return List.copyOf(allItemsOrder);
    }

    public void setAllItemsOrder(List<ItemStack> order) {
        this.allItemsOrder.clear();
        this.allItemsOrder.addAll(order);
        markDirty();
    }

    public int getAllItemsIndex() {
        return allItemsIndex;
    }

    public void setAllItemsIndex(int index) {
        this.allItemsIndex = index;
        markDirty();
    }

    public List<EntityType<?>> getAllEntitiesOrder() {
        return allEntitiesOrder;
    }

    public void setAllEntitiesOrder(List<EntityType<?>> order) {
        this.allEntitiesOrder.clear();
        this.allEntitiesOrder.addAll(order);
        markDirty();
    }

    public int getAllEntitiesIndex() {
        return allEntitiesIndex;
    }

    public void setAllEntitiesIndex(int index) {
        this.allEntitiesIndex = index;
        markDirty();
    }

    public int getMobHealthMultiplier() {
        return mobHealthMultiplier;
    }

    public void setMobHealthMultiplier(int multiplier) {
        this.mobHealthMultiplier = multiplier;
        markDirty();
    }

    public double getDamageWorldBorderSize() {
        return damageWorldBorderSize;
    }

    public void setDamageWorldBorderSize(double size) {
        this.damageWorldBorderSize = size;
        markDirty();
    }

    public List<Integer> getActivePerks() {
        return activePerks;
    }

    public void setActivePerks(List<Integer> newPerks) {
        this.activePerks.clear();
        this.activePerks.addAll(newPerks);
        markDirty();
    }

    public long getPlayerXp(UUID uuid) {
        return playerXp.getOrDefault(uuid, 0L);
    }

    public void setPlayerXp(UUID uuid, long xp) {
        playerXp.put(uuid, xp);
        markDirty();
    }

    public int getRunIndex() {
        return runIndex;
    }

    public void setRunIndex(int runIndex) {
        this.runIndex = runIndex;
        markDirty();
    }

    public int getDoubleTroubleMultiplier() {
        return doubleTroubleMultiplier;
    }

    public void setDoubleTroubleMultiplier(int multiplier) {
        this.doubleTroubleMultiplier = multiplier;
        markDirty();
    }

    public List<Identifier> getAllAdvancementsOrder() {
        return List.copyOf(allAdvancementsOrder);
    }

    public void setAllAdvancementsOrder(List<Identifier> order) {
        this.allAdvancementsOrder.clear();
        this.allAdvancementsOrder.addAll(order);
        markDirty();
    }

    public int getAllAdvancementsIndex() {
        return allAdvancementsIndex;
    }

    public void setAllAdvancementsIndex(int index) {
        this.allAdvancementsIndex = index;
        markDirty();
    }

    private record ChallengeProgress(List<ItemStack> allItemsOrder, int allItemsIndex, List<EntityType<?>> allEntitiesOrder, int allEntitiesIndex, List<Identifier> allAdvancementsOrder, int allAdvancementsIndex) {
        public static final MapCodec<ChallengeProgress> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.list(ItemStack.CODEC).fieldOf("allItemsOrder").forGetter(ChallengeProgress::allItemsOrder),
                Codec.INT.fieldOf("allItemsIndex").forGetter(ChallengeProgress::allItemsIndex),
                Codec.list(EntityType.CODEC).optionalFieldOf("allEntitiesOrder", List.of()).forGetter(ChallengeProgress::allEntitiesOrder),
                Codec.INT.optionalFieldOf("allEntitiesIndex", 0).forGetter(ChallengeProgress::allEntitiesIndex),
                Codec.list(Identifier.CODEC).optionalFieldOf("allAdvancementsOrder", List.of()).forGetter(ChallengeProgress::allAdvancementsOrder),
                Codec.INT.optionalFieldOf("allAdvancementsIndex", 0).forGetter(ChallengeProgress::allAdvancementsIndex)
        ).apply(instance, ChallengeProgress::new));
    }
}
