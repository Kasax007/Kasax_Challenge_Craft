package net.kasax.challengecraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.code.ChallengeCode;
import net.kasax.challengecraft.daily.DailyEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.kasax.challengecraft.LevelManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client glue for {@link ChallengeCode}: where the world seed comes from, which ids exist, what a
 * code's problems read like, the clipboard, and the code armed on the title screen.
 */
@Environment(EnvType.CLIENT)
public final class ChallengeCodeClient {
    /**
     * The overworld seed of the connected world, from the challenge sync. Needed on a dedicated
     * server, where the client has no other way to learn it; cleared on disconnect.
     */
    private static Long syncedWorldSeed = null;

    /** A code picked on the title screen, waiting for the world-creation screen to apply it. */
    private static ChallengeCode pending = null;

    private ChallengeCodeClient() {
    }

    public static void setSyncedWorldSeed(Long seed) {
        syncedWorldSeed = seed;
    }

    /** The seed of the world the player is in, or null outside a world. */
    public static Long currentWorldSeed() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            return server.overworld().getSeed();
        }
        return syncedWorldSeed;
    }

    public static void arm(ChallengeCode code) {
        pending = code;
    }

    public static ChallengeCode pending() {
        return pending;
    }

    public static void clear() {
        pending = null;
    }

    /** The challenge ids this build knows, so a code from a newer version is refused, not half-applied. */
    public static Set<Integer> knownChallengeIds() {
        return Set.copyOf(net.kasax.challengecraft.client.screen.ChallengeTab.challengeIds());
    }

    /** A daily as a code, so a daily's card and share text can carry one too. */
    public static ChallengeCode ofDaily(DailyEntry e) {
        int unset = DailyEntry.UNSET;
        return ChallengeCode.of(e.challengeIds(), List.of(),
                e.maxHearts() == unset ? ChallengeCode.DEFAULT_HEARTS : e.maxHearts(),
                e.inventorySlots() == unset ? ChallengeCode.DEFAULT_SLOTS : e.inventorySlots(),
                e.mobHealthMultiplier() == unset ? ChallengeCode.DEFAULT_MOB_HEALTH : e.mobHealthMultiplier(),
                e.doubleTroubleMultiplier() == unset ? ChallengeCode.DEFAULT_DOUBLE_TROUBLE : e.doubleTroubleMultiplier(),
                e.gameSpeedMultiplier() == unset ? ChallengeCode.DEFAULT_GAME_SPEED : e.gameSpeedMultiplier(),
                ChallengeCode.DEFAULT_FIB_MINUTES,
                e.hasFixedSeed() ? e.seed() : null);
    }

    /** The running world's setup as last synced by the server: challenges, perks, sliders and seed. */
    public static ChallengeCode ofSyncedWorld(List<Integer> ids) {
        return ChallengeCode.of(ids, ChallengeCraftClient.SELECTED_PERKS,
                ChallengeCraftClient.SELECTED_MAX_HEARTS,
                ChallengeCraftClient.SELECTED_LIMITED_INVENTORY,
                ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER,
                ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER,
                ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER,
                ChallengeCraftClient.SELECTED_FIB_MINUTES,
                currentWorldSeed());
    }

    /**
     * Why a decoded code still cannot be used here, or null if it can. A code never unlocks anything:
     * every challenge and perk in it must already be unlocked for this player.
     */
    public static Component usabilityProblem(ChallengeCode code) {
        if (!code.unknownIds(knownChallengeIds(), Set.copyOf(LevelManager.ALL_PERKS)).isEmpty()) {
            return Component.translatable("challengecraft.code.error.unknown_challenge");
        }
        int[] clash = ChallengeManager.findConflict(code.challengeIds(), code.perkIds());
        if (clash != null) {
            return Component.translatable("challengecraft.conflict.pair", name(clash[0]), name(clash[1]));
        }
        long xp = ChallengeCraftClient.refreshLocalPlayerXp();
        int level = LevelManager.getLevelForXp(xp);
        List<Integer> all = new ArrayList<>(code.challengeIds());
        all.addAll(code.perkIds());
        List<Component> locked = new ArrayList<>();
        for (int id : all) {
            boolean open = id == LevelManager.PERK_INFINITY_WEAPON
                    ? LevelManager.getStars(xp) >= 20
                    : LevelManager.isChallengeUnlocked(id, level);
            if (!open) {
                locked.add(Component.translatable("challengecraft.code.locked_entry", name(id),
                        LevelManager.getRequiredLevel(id)));
            }
        }
        if (locked.isEmpty()) {
            return null;
        }
        MutableComponent list = Component.empty();
        int shown = Math.min(3, locked.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                list.append(", ");
            }
            list.append(locked.get(i));
        }
        if (locked.size() > shown) {
            list.append(" +" + (locked.size() - shown));
        }
        return Component.translatable("challengecraft.code.error.locked", list);
    }

    /** A challenge's or perk's display name. */
    public static Component name(int id) {
        return Component.translatable(id > 100 ? "challengecraft.perk." + id : "challengecraft.worldcreate.challenge" + id);
    }

    public static Component describe(ChallengeCode.Problem problem) {
        return Component.translatable(switch (problem) {
            case EMPTY -> "challengecraft.code.error.empty";
            case MALFORMED -> "challengecraft.code.error.malformed";
            case CHECKSUM -> "challengecraft.code.error.checksum";
            case NEWER_VERSION -> "challengecraft.code.error.newer_version";
        });
    }

    /** The first code on the clipboard, or null — also finds one inside a pasted share post. */
    public static String clipboardCode() {
        try {
            return ChallengeCode.find(Minecraft.getInstance().keyboardHandler.getClipboard());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void copy(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }
}
