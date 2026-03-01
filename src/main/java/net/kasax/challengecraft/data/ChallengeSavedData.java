package net.kasax.challengecraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
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
            Codec.list(Codec.INT)
                    .fieldOf("active")
                    .forGetter(ps -> List.copyOf(ps.active)),
            Codec.INT
                    .fieldOf("maxHeartsTicks")
                    .forGetter(ps -> ps.maxHeartsTicks),
            Codec.INT
                    .fieldOf("limitedInventorySlots")
                    .forGetter(ps -> ps.limitedInventorySlots),
            Codec.DOUBLE
                    .fieldOf("initialDifficulty")
                    .forGetter(ps -> ps.initialDifficulty),
            Codec.BOOL
                    .fieldOf("tainted")
                    .forGetter(ps -> ps.tainted),
            Codec.BOOL
                    .fieldOf("xpAwarded")
                    .forGetter(ps -> ps.xpAwarded),
            Codec.BOOL
                    .fieldOf("difficultySet")
                    .forGetter(ps -> ps.difficultySet)
    ).apply(instance, (activeList, maxHeartsTicks, limitedInventorySlots, initialDifficulty, tainted, xpAwarded, difficultySet) -> {
        var d = new ChallengeSavedData();
        d.active.clear();
        d.active.addAll(activeList);
        d.maxHeartsTicks = maxHeartsTicks;
        d.limitedInventorySlots = limitedInventorySlots;
        d.initialDifficulty = initialDifficulty;
        d.tainted = tainted;
        d.xpAwarded = xpAwarded;
        d.difficultySet = difficultySet;
        return d;
    }));

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

    private ChallengeSavedData() {}

    /** Retrieve (or create) for this world. */
    public static ChallengeSavedData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(TYPE);
    }

    public NbtCompound writeNbt(NbtCompound tag) {
        // Codec will write both “active” and “maxHeartsTicks” fields for you.
        tag.putIntArray("active", active.stream().mapToInt(i -> i).toArray());
        tag.putInt("maxHeartsTicks", maxHeartsTicks);
        tag.putInt("limitedInventorySlots", limitedInventorySlots);
        tag.putDouble("initialDifficulty", initialDifficulty);
        tag.putBoolean("tainted", tainted);
        tag.putBoolean("xpAwarded", xpAwarded);
        tag.putBoolean("difficultySet", difficultySet);
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
}
