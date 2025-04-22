package net.kasax.challengecraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.ListCodec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;

public class ChallengeSavedData extends PersistentState {
    private static final String KEY = "challengecraft_challenges";

    //
    // 1) a Codec that maps between List<Integer> and your PS object
    //
    private static final Codec<ChallengeSavedData> CODEC = Codec
            // first build a list‐of‐int codec...
            .list(Codec.INT)
            // then xmap it into/from your PS class
            .xmap(
                    list -> {
                        var d = new ChallengeSavedData();
                        d.active.clear();
                        d.active.addAll(list);
                        return d;
                    },
                    ps -> List.copyOf(ps.active)
            );

    //
    // 2) the single PersistentStateType instance, exactly like you had before.
    //
    public static final PersistentStateType<ChallengeSavedData> TYPE =
            new PersistentStateType<>(
                    KEY,
                    ChallengeSavedData::new,  // supplier: for fresh worlds
                    CODEC,                    // our new list‐codec
                    DataFixTypes.LEVEL        // keep using the level datafix pipeline
            );

    // ---- your actual state ----

    /** The set of *all* currently active challenge‐IDs. */
    private final List<Integer> active = new ArrayList<>(List.of(1));
    // default to challenge #1 if nothing saved yet

    /** no‑arg ctor used by your TYPE supplier */
    private ChallengeSavedData() {}

    /** retrieve or create */
    public static ChallengeSavedData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(TYPE);
    }

    /** writeNbt may stay empty, the Codec does all the work under "data" */
    public NbtCompound writeNbt(NbtCompound tag) {
        return tag;
    }

    // --------- your getters/setters ---------

    /** Immutable snapshot of the active list. */
    public List<Integer> getActive() {
        return List.copyOf(active);
    }

    /** Replace the entire active‐IDs list. */
    public void setActive(List<Integer> newActive) {
        active.clear();
        active.addAll(newActive);
        markDirty();
    }

    /** Convenience: add one challenge ID. */
    public void add(int challengeId) {
        if (!active.contains(challengeId)) {
            active.add(challengeId);
            markDirty();
        }
    }

    /** Convenience: remove one challenge ID. */
    public void remove(int challengeId) {
        if (active.remove((Integer)challengeId)) {
            markDirty();
        }
    }
}
