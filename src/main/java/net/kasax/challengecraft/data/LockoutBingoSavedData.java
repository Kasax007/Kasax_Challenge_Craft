package net.kasax.challengecraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stores one lockout board, its team state, and per-run bookkeeping for the current world. */
public class LockoutBingoSavedData extends SavedData {
    private static final String KEY = "challengecraft_lockout_bingo";

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

    private static final Codec<Set<UUID>> UUID_SET_CODEC = Codec.list(Codec.STRING).xmap(
            list -> {
                Set<UUID> converted = new HashSet<>();
                list.forEach(uuid -> converted.add(UUID.fromString(uuid)));
                return converted;
            },
            set -> set.stream().map(UUID::toString).toList()
    );

    private static final Codec<Map<UUID, Map<String, Integer>>> UUID_NESTED_INT_MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.unboundedMap(Codec.STRING, Codec.INT)
    ).xmap(
            map -> {
                Map<UUID, Map<String, Integer>> converted = new HashMap<>();
                map.forEach((uuid, values) -> converted.put(UUID.fromString(uuid), new HashMap<>(values)));
                return converted;
            },
            map -> {
                Map<String, Map<String, Integer>> converted = new HashMap<>();
                map.forEach((uuid, values) -> converted.put(uuid.toString(), new HashMap<>(values)));
                return converted;
            }
    );

    private static final Codec<LockoutBingoSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(Codec.STRING).optionalFieldOf("boardGoalIds", List.of()).forGetter(LockoutBingoSavedData::getBoardGoalIds),
            Codec.list(Codec.INT).optionalFieldOf("claimedTeams", List.of()).forGetter(LockoutBingoSavedData::getClaimedTeams),
            Codec.list(Codec.STRING).optionalFieldOf("claimedByUuids", List.of()).forGetter(LockoutBingoSavedData::getClaimedByUuidStrings),
            Codec.list(Codec.STRING).optionalFieldOf("claimedByNames", List.of()).forGetter(LockoutBingoSavedData::getClaimedByNames),
            UUID_INT_MAP_CODEC.optionalFieldOf("teamAssignments", Map.of()).forGetter(LockoutBingoSavedData::getTeamAssignments),
            UUID_STRING_MAP_CODEC.optionalFieldOf("playerNames", Map.of()).forGetter(LockoutBingoSavedData::getPlayerNames),
            UUID_SET_CODEC.optionalFieldOf("readyPlayers", Set.of()).forGetter(LockoutBingoSavedData::getReadyPlayers),
            UUID_SET_CODEC.optionalFieldOf("rewardedPlayers", Set.of()).forGetter(LockoutBingoSavedData::getRewardedPlayers),
            UUID_NESTED_INT_MAP_CODEC.optionalFieldOf("goalStatBaselines", Map.of()).forGetter(LockoutBingoSavedData::getGoalStatBaselines),
            Codec.BOOL.optionalFieldOf("started", false).forGetter(LockoutBingoSavedData::isStarted),
            Codec.BOOL.optionalFieldOf("ended", false).forGetter(LockoutBingoSavedData::isEnded),
            Codec.INT.optionalFieldOf("winnerTeam", -1).forGetter(LockoutBingoSavedData::getWinnerTeamOrdinal),
            Codec.LONG.optionalFieldOf("generatedSeed", 0L).forGetter(LockoutBingoSavedData::getGeneratedSeed),
            Codec.INT.optionalFieldOf("runId", 0).forGetter(LockoutBingoSavedData::getRunId),
            Codec.LONG.optionalFieldOf("startedAtWorldTicks", -1L).forGetter(LockoutBingoSavedData::getStartedAtWorldTicks)
    ).apply(instance, LockoutBingoSavedData::new));

    public static final SavedDataType<LockoutBingoSavedData> TYPE =
            new SavedDataType<>(Identifier.fromNamespaceAndPath("challengecraft", KEY), LockoutBingoSavedData::new, CODEC, DataFixTypes.LEVEL);

    private final List<String> boardGoalIds = new ArrayList<>();
    private final List<Integer> claimedTeams = new ArrayList<>();
    private final List<String> claimedByUuids = new ArrayList<>();
    private final List<String> claimedByNames = new ArrayList<>();
    private final Map<UUID, Integer> teamAssignments = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> rewardedPlayers = new HashSet<>();
    private final Map<UUID, Map<String, Integer>> goalStatBaselines = new HashMap<>();
    private boolean started;
    private boolean ended;
    private int winnerTeamOrdinal = -1;
    private long generatedSeed;
    private int runId;
    private long startedAtWorldTicks = -1L;

    private LockoutBingoSavedData() {
    }

    private LockoutBingoSavedData(
            List<String> boardGoalIds,
            List<Integer> claimedTeams,
            List<String> claimedByUuids,
            List<String> claimedByNames,
            Map<UUID, Integer> teamAssignments,
            Map<UUID, String> playerNames,
            Set<UUID> readyPlayers,
            Set<UUID> rewardedPlayers,
            Map<UUID, Map<String, Integer>> goalStatBaselines,
            boolean started,
            boolean ended,
            int winnerTeamOrdinal,
            long generatedSeed,
            int runId,
            long startedAtWorldTicks
    ) {
        this.boardGoalIds.addAll(boardGoalIds);
        this.claimedTeams.addAll(claimedTeams);
        this.claimedByUuids.addAll(claimedByUuids);
        this.claimedByNames.addAll(claimedByNames);
        this.teamAssignments.putAll(teamAssignments);
        this.playerNames.putAll(playerNames);
        this.readyPlayers.addAll(readyPlayers);
        this.rewardedPlayers.addAll(rewardedPlayers);
        goalStatBaselines.forEach((uuid, values) -> this.goalStatBaselines.put(uuid, new HashMap<>(values)));
        this.started = started;
        this.ended = ended;
        this.winnerTeamOrdinal = winnerTeamOrdinal;
        this.generatedSeed = generatedSeed;
        this.runId = runId;
        this.startedAtWorldTicks = startedAtWorldTicks;
        normalizeClaimLists();
    }

    public static LockoutBingoSavedData get(ServerLevel world) {
        SavedDataStorage manager = world.getDataStorage();
        return manager.computeIfAbsent(TYPE);
    }

    public List<String> getBoardGoalIds() {
        return List.copyOf(boardGoalIds);
    }

    public List<Integer> getClaimedTeams() {
        normalizeClaimLists();
        return List.copyOf(claimedTeams);
    }

    public List<String> getClaimedByUuidStrings() {
        normalizeClaimLists();
        return List.copyOf(claimedByUuids);
    }

    public List<String> getClaimedByNames() {
        normalizeClaimLists();
        return List.copyOf(claimedByNames);
    }

    public Map<UUID, Integer> getTeamAssignments() {
        return Map.copyOf(teamAssignments);
    }

    public Map<UUID, String> getPlayerNames() {
        return Map.copyOf(playerNames);
    }

    public Set<UUID> getReadyPlayers() {
        return Set.copyOf(readyPlayers);
    }

    public Set<UUID> getRewardedPlayers() {
        return Set.copyOf(rewardedPlayers);
    }

    public Map<UUID, Map<String, Integer>> getGoalStatBaselines() {
        Map<UUID, Map<String, Integer>> copy = new HashMap<>();
        goalStatBaselines.forEach((uuid, values) -> copy.put(uuid, Map.copyOf(values)));
        return Map.copyOf(copy);
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isEnded() {
        return ended;
    }

    public int getWinnerTeamOrdinal() {
        return winnerTeamOrdinal;
    }

    public LockoutBingoTeam getWinnerTeam() {
        return LockoutBingoTeam.fromOrdinal(winnerTeamOrdinal);
    }

    public long getGeneratedSeed() {
        return generatedSeed;
    }

    public int getRunId() {
        return runId;
    }

    public long getStartedAtWorldTicks() {
        return startedAtWorldTicks;
    }

    public void resetForRun(int newRunId) {
        boardGoalIds.clear();
        claimedTeams.clear();
        claimedByUuids.clear();
        claimedByNames.clear();
        teamAssignments.clear();
        playerNames.clear();
        readyPlayers.clear();
        rewardedPlayers.clear();
        goalStatBaselines.clear();
        started = false;
        ended = false;
        winnerTeamOrdinal = -1;
        generatedSeed = 0L;
        runId = newRunId;
        startedAtWorldTicks = -1L;
        setDirty();
    }

    public void clearBoard() {
        boardGoalIds.clear();
        claimedTeams.clear();
        claimedByUuids.clear();
        claimedByNames.clear();
        started = false;
        ended = false;
        winnerTeamOrdinal = -1;
        generatedSeed = 0L;
        startedAtWorldTicks = -1L;
        rewardedPlayers.clear();
        readyPlayers.clear();
        goalStatBaselines.clear();
        setDirty();
    }

    public void setBoard(List<String> goalIds, long seed, long startedAtTicks) {
        boardGoalIds.clear();
        boardGoalIds.addAll(goalIds);
        claimedTeams.clear();
        claimedByUuids.clear();
        claimedByNames.clear();
        for (int i = 0; i < goalIds.size(); i++) {
            claimedTeams.add(-1);
            claimedByUuids.add("");
            claimedByNames.add("");
        }
        generatedSeed = seed;
        startedAtWorldTicks = startedAtTicks;
        winnerTeamOrdinal = -1;
        ended = false;
        rewardedPlayers.clear();
        goalStatBaselines.clear();
        setDirty();
    }

    public void setTeam(UUID uuid, String playerName, LockoutBingoTeam team) {
        teamAssignments.put(uuid, team.ordinal());
        playerNames.put(uuid, playerName);
        setDirty();
    }

    public void removeTeam(UUID uuid) {
        teamAssignments.remove(uuid);
        readyPlayers.remove(uuid);
        playerNames.remove(uuid);
        setDirty();
    }

    public void updatePlayerName(UUID uuid, String playerName) {
        if (!playerName.equals(playerNames.get(uuid))) {
            playerNames.put(uuid, playerName);
            setDirty();
        }
    }

    public LockoutBingoTeam getTeam(UUID uuid) {
        Integer ordinal = teamAssignments.get(uuid);
        return ordinal == null ? null : LockoutBingoTeam.fromOrdinal(ordinal);
    }

    public void setReady(UUID uuid, boolean ready) {
        if (ready) {
            readyPlayers.add(uuid);
        } else {
            readyPlayers.remove(uuid);
        }
        setDirty();
    }

    public void clearReady() {
        readyPlayers.clear();
        setDirty();
    }

    public void retainPlayers(Set<UUID> uuids) {
        teamAssignments.keySet().retainAll(uuids);
        playerNames.keySet().retainAll(uuids);
        readyPlayers.retainAll(uuids);
        rewardedPlayers.retainAll(uuids);
        goalStatBaselines.keySet().retainAll(uuids);
        setDirty();
    }

    public int getGoalStatBaseline(UUID uuid, String goalId) {
        return goalStatBaselines.getOrDefault(uuid, Map.of()).getOrDefault(goalId, 0);
    }

    public void setGoalStatBaseline(UUID uuid, String goalId, int value) {
        goalStatBaselines.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(goalId, value);
        setDirty();
    }

    public boolean isReady(UUID uuid) {
        return readyPlayers.contains(uuid);
    }

    public void setStarted(boolean started) {
        this.started = started;
        setDirty();
    }

    public void setEnded(boolean ended) {
        this.ended = ended;
        setDirty();
    }

    public void setWinnerTeam(LockoutBingoTeam team) {
        winnerTeamOrdinal = team == null ? -1 : team.ordinal();
        setDirty();
    }

    public void claimTile(int index, LockoutBingoTeam team, UUID byUuid, String byName) {
        normalizeClaimLists();
        claimedTeams.set(index, team.ordinal());
        claimedByUuids.set(index, byUuid.toString());
        claimedByNames.set(index, byName);
        playerNames.put(byUuid, byName);
        setDirty();
    }

    public LockoutBingoTeam getClaimedTeam(int index) {
        normalizeClaimLists();
        if (index < 0 || index >= claimedTeams.size()) {
            return null;
        }
        return LockoutBingoTeam.fromOrdinal(claimedTeams.get(index));
    }

    public String getClaimedByName(int index) {
        normalizeClaimLists();
        if (index < 0 || index >= claimedByNames.size()) {
            return "";
        }
        return claimedByNames.get(index);
    }

    public UUID getClaimedByUuid(int index) {
        normalizeClaimLists();
        if (index < 0 || index >= claimedByUuids.size()) {
            return null;
        }
        String value = claimedByUuids.get(index);
        return value.isBlank() ? null : UUID.fromString(value);
    }

    public boolean isRewarded(UUID uuid) {
        return rewardedPlayers.contains(uuid);
    }

    public void setRewarded(UUID uuid) {
        rewardedPlayers.add(uuid);
        setDirty();
    }

    private void normalizeClaimLists() {
        while (claimedTeams.size() < boardGoalIds.size()) {
            claimedTeams.add(-1);
        }
        while (claimedByUuids.size() < boardGoalIds.size()) {
            claimedByUuids.add("");
        }
        while (claimedByNames.size() < boardGoalIds.size()) {
            claimedByNames.add("");
        }

        if (claimedTeams.size() > boardGoalIds.size()) {
            claimedTeams.subList(boardGoalIds.size(), claimedTeams.size()).clear();
        }
        if (claimedByUuids.size() > boardGoalIds.size()) {
            claimedByUuids.subList(boardGoalIds.size(), claimedByUuids.size()).clear();
        }
        if (claimedByNames.size() > boardGoalIds.size()) {
            claimedByNames.subList(boardGoalIds.size(), claimedByNames.size()).clear();
        }
    }
}
