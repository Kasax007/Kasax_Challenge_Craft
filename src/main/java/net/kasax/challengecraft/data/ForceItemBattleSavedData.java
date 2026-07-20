package net.kasax.challengecraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent state for one Force Item Battle: per-player target item, score, and jokers, plus
 * team assignments and the battle timer. World-scoped (overworld-owned) like the other saved
 * data; lifetime XP prizes go through the usual XpManager path instead.
 */
public class ForceItemBattleSavedData extends PersistentState {
    private static final String KEY = "challengecraft_force_item_battle";

    public static final int STATE_IDLE = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_ENDED = 2;

    public static final int DEFAULT_JOKERS = 5;

    private static final Codec<Map<UUID, Integer>> UUID_INT_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
            map -> {
                Map<UUID, Integer> converted = new HashMap<>();
                map.forEach((uuid, value) -> converted.put(UUID.fromString(uuid), value));
                return converted;
            },
            map -> {
                Map<String, Integer> converted = new HashMap<>();
                map.forEach((uuid, value) -> converted.put(uuid.toString(), value));
                return converted;
            }
    );

    private static final Codec<Map<UUID, String>> UUID_STRING_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(
            map -> {
                Map<UUID, String> converted = new HashMap<>();
                map.forEach((uuid, value) -> converted.put(UUID.fromString(uuid), value));
                return converted;
            },
            map -> {
                Map<String, String> converted = new HashMap<>();
                map.forEach((uuid, value) -> converted.put(uuid.toString(), value));
                return converted;
            }
    );

    private static final Codec<Map<UUID, List<String>>> UUID_STRING_LIST_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.list(Codec.STRING)).xmap(
            map -> {
                Map<UUID, List<String>> converted = new HashMap<>();
                map.forEach((uuid, value) -> converted.put(UUID.fromString(uuid), new ArrayList<>(value)));
                return converted;
            },
            map -> {
                Map<String, List<String>> converted = new HashMap<>();
                map.forEach((uuid, value) -> converted.put(uuid.toString(), List.copyOf(value)));
                return converted;
            }
    );

    private static final Codec<ForceItemBattleSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("state", STATE_IDLE).forGetter(ForceItemBattleSavedData::getState),
            Codec.LONG.optionalFieldOf("endGameTime", -1L).forGetter(ForceItemBattleSavedData::getEndGameTime),
            UUID_STRING_MAP_CODEC.optionalFieldOf("currentItems", Map.of()).forGetter(ForceItemBattleSavedData::getCurrentItems),
            UUID_INT_MAP_CODEC.optionalFieldOf("scores", Map.of()).forGetter(ForceItemBattleSavedData::getScores),
            UUID_INT_MAP_CODEC.optionalFieldOf("jokers", Map.of()).forGetter(ForceItemBattleSavedData::getJokers),
            UUID_INT_MAP_CODEC.optionalFieldOf("teamAssignments", Map.of()).forGetter(ForceItemBattleSavedData::getTeamAssignments),
            UUID_STRING_MAP_CODEC.optionalFieldOf("playerNames", Map.of()).forGetter(ForceItemBattleSavedData::getPlayerNames),
            Codec.BOOL.optionalFieldOf("resultsAwarded", false).forGetter(ForceItemBattleSavedData::isResultsAwarded),
            UUID_STRING_LIST_MAP_CODEC.optionalFieldOf("collectedItems", Map.of()).forGetter(ForceItemBattleSavedData::getCollectedItems),
            Codec.BOOL.optionalFieldOf("debugRun", false).forGetter(ForceItemBattleSavedData::isDebugRun)
    ).apply(instance, ForceItemBattleSavedData::new));

    public static final PersistentStateType<ForceItemBattleSavedData> TYPE =
            new PersistentStateType<>(KEY, ForceItemBattleSavedData::new, CODEC, DataFixTypes.LEVEL);

    private int state = STATE_IDLE;
    private long endGameTime = -1L;
    private final Map<UUID, String> currentItems = new HashMap<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, Integer> jokers = new HashMap<>();
    private final Map<UUID, Integer> teamAssignments = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private boolean resultsAwarded = false;
    private final Map<UUID, List<String>> collectedItems = new HashMap<>();
    private boolean debugRun = false;

    private ForceItemBattleSavedData() {
    }

    private ForceItemBattleSavedData(int state, long endGameTime, Map<UUID, String> currentItems,
                                     Map<UUID, Integer> scores, Map<UUID, Integer> jokers,
                                     Map<UUID, Integer> teamAssignments, Map<UUID, String> playerNames,
                                     boolean resultsAwarded, Map<UUID, List<String>> collectedItems,
                                     boolean debugRun) {
        this.state = state;
        this.endGameTime = endGameTime;
        this.currentItems.putAll(currentItems);
        this.scores.putAll(scores);
        this.jokers.putAll(jokers);
        this.teamAssignments.putAll(teamAssignments);
        this.playerNames.putAll(playerNames);
        this.resultsAwarded = resultsAwarded;
        collectedItems.forEach((uuid, list) -> this.collectedItems.put(uuid, new ArrayList<>(list)));
        this.debugRun = debugRun;
    }

    public static ForceItemBattleSavedData get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE);
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
        markDirty();
    }

    public long getEndGameTime() {
        return endGameTime;
    }

    public void setEndGameTime(long endGameTime) {
        this.endGameTime = endGameTime;
        markDirty();
    }

    public Map<UUID, String> getCurrentItems() {
        return Map.copyOf(currentItems);
    }

    public String getCurrentItem(UUID uuid) {
        return currentItems.getOrDefault(uuid, "");
    }

    public void setCurrentItem(UUID uuid, String itemId) {
        currentItems.put(uuid, itemId);
        markDirty();
    }

    public Map<UUID, Integer> getScores() {
        return Map.copyOf(scores);
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    public void addScore(UUID uuid) {
        scores.merge(uuid, 1, Integer::sum);
        markDirty();
    }

    public Map<UUID, Integer> getJokers() {
        return Map.copyOf(jokers);
    }

    public int getJokersLeft(UUID uuid) {
        return jokers.getOrDefault(uuid, 0);
    }

    public void setJokersLeft(UUID uuid, int amount) {
        jokers.put(uuid, amount);
        markDirty();
    }

    public Map<UUID, Integer> getTeamAssignments() {
        return Map.copyOf(teamAssignments);
    }

    public Integer getTeamOrdinal(UUID uuid) {
        return teamAssignments.get(uuid);
    }

    public void setTeam(UUID uuid, String playerName, int teamOrdinal) {
        teamAssignments.put(uuid, teamOrdinal);
        playerNames.put(uuid, playerName);
        markDirty();
    }

    public void removeTeam(UUID uuid) {
        teamAssignments.remove(uuid);
        markDirty();
    }

    public Map<UUID, String> getPlayerNames() {
        return Map.copyOf(playerNames);
    }

    public void updatePlayerName(UUID uuid, String name) {
        if (!name.equals(playerNames.get(uuid))) {
            playerNames.put(uuid, name);
            markDirty();
        }
    }

    /** Registers a participant on first contact: name, joker budget, empty score. */
    public void ensurePlayer(UUID uuid, String name) {
        playerNames.putIfAbsent(uuid, name);
        jokers.putIfAbsent(uuid, DEFAULT_JOKERS);
        scores.putIfAbsent(uuid, 0);
        updatePlayerName(uuid, name);
        markDirty();
    }

    public boolean isResultsAwarded() {
        return resultsAwarded;
    }

    public void setResultsAwarded(boolean awarded) {
        this.resultsAwarded = awarded;
        markDirty();
    }

    public Map<UUID, List<String>> getCollectedItems() {
        Map<UUID, List<String>> copy = new HashMap<>();
        collectedItems.forEach((uuid, list) -> copy.put(uuid, List.copyOf(list)));
        return copy;
    }

    /** Ordered history of scored items (normal collects and jokers alike). */
    public List<String> getCollectedItems(UUID uuid) {
        return List.copyOf(collectedItems.getOrDefault(uuid, List.of()));
    }

    public void addCollectedItem(UUID uuid, String itemId) {
        collectedItems.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(itemId);
        markDirty();
    }

    public boolean isDebugRun() {
        return debugRun;
    }

    public void setDebugRun(boolean debug) {
        this.debugRun = debug;
        markDirty();
    }

    public void resetForNewBattle() {
        state = STATE_IDLE;
        endGameTime = -1L;
        currentItems.clear();
        scores.clear();
        jokers.clear();
        collectedItems.clear();
        resultsAwarded = false;
        debugRun = false;
        markDirty();
    }
}
