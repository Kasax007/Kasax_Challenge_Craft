package net.kasax.challengecraft.casino;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events of "The House Always Wins". The files live under
 * {@code assets/challengecraft/sounds/casino/} and are mixed down by
 * {@code scripts/casino/sound_mix.py}: every one is mono (so it attenuates with distance), trimmed,
 * faded and loudness-matched, so no cue jumps out of the mix or sounds louder than vanilla.
 *
 * <p>Card and chip recordings are Kenney's Boardgame Pack (CC0); coins and metal impacts are
 * Kenney RPG/Impact and StarNinjas (CC0). Jingles, reels, the roulette ball and the rocket are
 * synthesised by the script.
 */
public final class CasinoSounds {
    public static final SoundEvent CHIP = register("casino.chip");
    public static final SoundEvent CHIP_STACK = register("casino.chip_stack");
    public static final SoundEvent CARD_SLIDE = register("casino.card_slide");
    public static final SoundEvent CARD_PLACE = register("casino.card_place");
    public static final SoundEvent CARD_FLIP = register("casino.card_flip");
    public static final SoundEvent COINS = register("casino.coins");
    public static final SoundEvent COIN_SHOWER = register("casino.coin_shower");
    public static final SoundEvent LEVER = register("casino.lever");
    public static final SoundEvent REEL_SPIN = register("casino.reel_spin");
    public static final SoundEvent REEL_STOP = register("casino.reel_stop");
    public static final SoundEvent REEL_TENSION = register("casino.reel_tension");
    public static final SoundEvent WIN_SMALL = register("casino.win_small");
    public static final SoundEvent WIN_BIG = register("casino.win_big");
    public static final SoundEvent WIN_EPIC = register("casino.win_epic");
    public static final SoundEvent FREE_SPINS = register("casino.free_spins");
    public static final SoundEvent EXPAND = register("casino.expand");
    public static final SoundEvent SCATTER = register("casino.scatter");
    public static final SoundEvent ROULETTE_SPIN = register("casino.roulette_spin");
    public static final SoundEvent ROULETTE_DROP = register("casino.roulette_drop");
    public static final SoundEvent ROCKET_LAUNCH = register("casino.rocket_launch");
    public static final SoundEvent ROCKET_FLIGHT = register("casino.rocket_flight");
    public static final SoundEvent CASH_OUT = register("casino.cash_out");
    public static final SoundEvent CRASH = register("casino.crash");
    public static final SoundEvent REGISTER = register("casino.register");
    public static final SoundEvent FEE_WARNING = register("casino.fee_warning");
    public static final SoundEvent LOSE = register("casino.lose");
    public static final SoundEvent HOUSE_SENDS = register("casino.house_sends");
    public static final SoundEvent REVIVE = register("casino.revive");
    public static final SoundEvent BUST = register("casino.bust");
    public static final SoundEvent BANKRUPT = register("casino.bankrupt");
    public static final SoundEvent TICK = register("casino.tick");
    public static final SoundEvent UNLOCK = register("casino.unlock");

    private CasinoSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Forces class initialisation, which registers every field above. */
    public static void initialize() {
    }
}
