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
                    .forGetter(ps -> ps.limitedInventorySlots)
    ).apply(instance, (activeList, maxHeartsTicks, limitedInventorySlots) -> {
        var d = new ChallengeSavedData();
        d.active.clear();
        d.active.addAll(activeList);
        d.maxHeartsTicks = maxHeartsTicks;
        d.limitedInventorySlots = limitedInventorySlots;
        return d;
    }));

    public static final PersistentStateType<ChallengeSavedData> TYPE =
            new PersistentStateType<>(KEY, ChallengeSavedData::new, CODEC, DataFixTypes.LEVEL);

    // ---- your actual state ----

    /** Active challenge IDs (default = [1]) */
    private final List<Integer> active = new ArrayList<>(List.of(1));

    /** Max-health slider in half-heart “ticks” (1…20). Default = 20 (10 hearts). */
    private int maxHeartsTicks;

    private int limitedInventorySlots = 36;

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
        tag.putInt("LimitedInventorySlots", limitedInventorySlots);
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
        this.markDirty();
    }
}
