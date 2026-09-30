package net.kasax.challengecraft.casino;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent state of "The House Always Wins", stored with the overworld like
 * {@code ForceItemBattleSavedData}. Every field is optional with a default, so worlds that never
 * ran the casino (or ran an older version of it) load cleanly.
 */
public class CasinoSavedData extends SavedData {
    private static final net.minecraft.resources.Identifier KEY = net.minecraft.resources.Identifier
            .fromNamespaceAndPath(net.kasax.challengecraft.ChallengeCraft.MOD_ID, "challengecraft_casino");

    private static final Codec<CasinoSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, CasinoAccount.CODEC).optionalFieldOf("accounts", Map.of()).forGetter(d -> d.accounts),
            Codec.INT.optionalFieldOf("feesCharged", 0).forGetter(d -> d.feesCharged),
            Codec.BOOL.optionalFieldOf("bankrupt", false).forGetter(d -> d.bankrupt),
            Codec.STRING.optionalFieldOf("croupier", "").forGetter(d -> d.croupier),
            Codec.BOOL.optionalFieldOf("boothBuilt", false).forGetter(d -> d.boothBuilt),
            Codec.LONG.optionalFieldOf("boothPos", 0L).forGetter(d -> d.boothPos),
            Codec.STRING.listOf().optionalFieldOf("devices", List.of()).forGetter(d -> new ArrayList<>(d.devices)),
            Codec.LONG.optionalFieldOf("feesTotal", 0L).forGetter(d -> d.feesTotal),
            Codec.INT.optionalFieldOf("feeScale", 100).forGetter(d -> d.feeScale)
    ).apply(i, CasinoSavedData::new));

    public static final SavedDataType<CasinoSavedData> TYPE =
            new SavedDataType<>(KEY, CasinoSavedData::new, CODEC, DataFixTypes.LEVEL);

    /** Keyed by player UUID string. */
    private final Map<String, CasinoAccount> accounts = new LinkedHashMap<>();
    /** How many 10-minute fees have been collected so far. */
    private int feesCharged;
    private boolean bankrupt;
    private String croupier = "";
    private boolean boothBuilt;
    private long boothPos;
    /** "dimension|x|y|z|type" per placed device. */
    private final Set<String> devices = new LinkedHashSet<>();
    private long feesTotal;
    /** Percent multiplier on the fee curve, settable by an operator for testing and tuning. */
    private int feeScale = 100;

    private CasinoSavedData() {
    }

    private CasinoSavedData(Map<String, CasinoAccount> accounts, int feesCharged, boolean bankrupt, String croupier,
                            boolean boothBuilt, long boothPos, List<String> devices, long feesTotal, int feeScale) {
        this.accounts.putAll(accounts);
        this.feesCharged = feesCharged;
        this.bankrupt = bankrupt;
        this.croupier = croupier;
        this.boothBuilt = boothBuilt;
        this.boothPos = boothPos;
        this.devices.addAll(devices);
        this.feesTotal = feesTotal;
        this.feeScale = feeScale;
    }

    public static CasinoSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public CasinoAccount account(UUID uuid) {
        CasinoAccount a = accounts.computeIfAbsent(uuid.toString(), k -> new CasinoAccount());
        return a;
    }

    public CasinoAccount existing(UUID uuid) {
        return accounts.get(uuid.toString());
    }

    public Map<String, CasinoAccount> accounts() {
        return accounts;
    }

    public int getFeesCharged() {
        return feesCharged;
    }

    public void setFeesCharged(int v) {
        feesCharged = v;
        setDirty();
    }

    public boolean isBankrupt() {
        return bankrupt;
    }

    public void setBankrupt(boolean v) {
        bankrupt = v;
        setDirty();
    }

    public String getCroupier() {
        return croupier;
    }

    public void setCroupier(String v) {
        croupier = v;
        setDirty();
    }

    public boolean isBoothBuilt() {
        return boothBuilt;
    }

    public void setBoothBuilt(boolean v, long pos) {
        boothBuilt = v;
        boothPos = pos;
        setDirty();
    }

    public long getBoothPos() {
        return boothPos;
    }

    public Set<String> devices() {
        return devices;
    }

    public long getFeesTotal() {
        return feesTotal;
    }

    public void addFeesTotal(long v) {
        feesTotal += v;
        setDirty();
    }

    public int getFeeScale() {
        return feeScale;
    }

    public void setFeeScale(int v) {
        feeScale = Math.max(1, v);
        setDirty();
    }

    /** Wipes everything; called when the challenge is switched on for a fresh run. */
    public void reset() {
        accounts.clear();
        feesCharged = 0;
        bankrupt = false;
        croupier = "";
        boothBuilt = false;
        boothPos = 0L;
        devices.clear();
        feesTotal = 0L;
        setDirty();
    }

    public void touch() {
        setDirty();
    }
}
