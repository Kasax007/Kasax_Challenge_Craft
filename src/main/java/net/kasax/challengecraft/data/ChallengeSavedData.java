package net.kasax.challengecraft.data;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.nbt.NbtCompound;

public class ChallengeSavedData extends PersistentState {
    private static final String KEY = "challengecraft_challenge";

    // 1) Codec mapping a single int ↔ ChallengeSavedData:
    private static final Codec<ChallengeSavedData> CODEC = Codec.INT.xmap(
            sel -> {
                ChallengeSavedData d = new ChallengeSavedData();
                d.selectedChallenge = sel;
                return d;
            },
            ChallengeSavedData::getSelectedChallenge
    );

    // 2) Use DataFixTypes.LEVEL (the correct enum in 1.21.5):
    public static final PersistentStateType<ChallengeSavedData> TYPE =
            new PersistentStateType<>(
                    KEY,
                    ChallengeSavedData::new,    // Supplier<T>
                    CODEC,                      // Codec<T>
                    DataFixTypes.LEVEL          // <-- fixed constant
            );

    private int selectedChallenge = 1;

    /** Retrieve (or create) the world’s saved data. */
    public static ChallengeSavedData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(TYPE);
    }

    private ChallengeSavedData() { }

    public NbtCompound writeNbt(NbtCompound tag) {
        // since the codec writes under "data", this can remain empty
        return tag;
    }

    public int getSelectedChallenge() {
        return selectedChallenge;
    }

    public void setSelectedChallenge(int value) {
        this.selectedChallenge = value;
        this.markDirty();
    }
}
