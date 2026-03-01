package net.kasax.challengecraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;

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
            Codec.BOOL.fieldOf("xpAwarded").forGetter(ChallengeSavedData::isXpAwarded),
            Codec.BOOL.fieldOf("difficultySet").forGetter(ChallengeSavedData::isDifficultySet),
            Codec.list(ItemStack.CODEC).fieldOf("allItemsOrder").forGetter(ChallengeSavedData::getAllItemsOrder),
            Codec.INT.fieldOf("allItemsIndex").forGetter(ChallengeSavedData::getAllItemsIndex),
            Codec.list(EntityType.CODEC).optionalFieldOf("allEntitiesOrder", List.of()).forGetter(ChallengeSavedData::getAllEntitiesOrder),
            Codec.INT.optionalFieldOf("allEntitiesIndex", 0).forGetter(ChallengeSavedData::getAllEntitiesIndex),
            Codec.INT.optionalFieldOf("mobHealthMultiplier", 1).forGetter(ChallengeSavedData::getMobHealthMultiplier),
            Codec.DOUBLE.optionalFieldOf("damageWorldBorderSize", 2.0).forGetter(ChallengeSavedData::getDamageWorldBorderSize)
    ).apply(instance, ChallengeSavedData::new));

    public static final PersistentStateType<ChallengeSavedData> TYPE =
            new PersistentStateType<>(KEY, ChallengeSavedData::new, CODEC, DataFixTypes.LEVEL);

    // ---- your actual state ----

    /** Active challenge IDs (default = [1]) */
    private final List<Integer> active = new ArrayList<>(List.of(1));

    /** Max-health slider in half-heart “ticks” (1…20). Default = 20 (10 hearts). */
    private int maxHeartsTicks;

    private int limitedInventorySlots;

    /** The difficulty rating calculated when world was created. */
    private double initialDifficulty = 0;

    /** If true, player changed challenges mid-game, so difficulty becomes 0. */
    private boolean tainted = false;

    /** To prevent multiple awards for the same achievement in one world. */
    private boolean xpAwarded = false;

    private boolean difficultySet = false;

    private final List<ItemStack> allItemsOrder = new ArrayList<>();
    private int allItemsIndex = 0;

    private final List<EntityType<?>> allEntitiesOrder = new ArrayList<>();
    private int allEntitiesIndex = 0;

    private int mobHealthMultiplier = 1;
    private double damageWorldBorderSize = 2.0;

    private ChallengeSavedData() {}

    public ChallengeSavedData(List<Integer> active, int maxHeartsTicks, int limitedInventorySlots, double initialDifficulty, boolean tainted, boolean xpAwarded, boolean difficultySet, List<ItemStack> allItemsOrder, int allItemsIndex, List<EntityType<?>> allEntitiesOrder, int allEntitiesIndex, int mobHealthMultiplier, double damageWorldBorderSize) {
        this.active.clear();
        this.active.addAll(active);
        this.maxHeartsTicks = maxHeartsTicks;
        this.limitedInventorySlots = limitedInventorySlots;
        this.initialDifficulty = initialDifficulty;
        this.tainted = tainted;
        this.xpAwarded = xpAwarded;
        this.difficultySet = difficultySet;
        this.allItemsOrder.clear();
        this.allItemsOrder.addAll(allItemsOrder);
        this.allItemsIndex = allItemsIndex;
        this.allEntitiesOrder.clear();
        this.allEntitiesOrder.addAll(allEntitiesOrder);
        this.allEntitiesIndex = allEntitiesIndex;
        this.mobHealthMultiplier = mobHealthMultiplier;
        this.damageWorldBorderSize = damageWorldBorderSize;
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

    public boolean isXpAwarded() {
        return xpAwarded;
    }

    public void setXpAwarded(boolean xpAwarded) {
        this.xpAwarded = xpAwarded;
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
}
