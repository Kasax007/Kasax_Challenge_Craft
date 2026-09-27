package net.kasax.challengecraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Challenge Craft's own sound cues: short tone sequences composed from vanilla instruments, played
 * on the client as UI sounds (not positional, not heard by anyone else).
 *
 * <p>Composed rather than recorded on purpose: nothing is shipped, so there is no licence to
 * worry about, and every cue is a few lines here. Each cue is one named sequence, so replacing it
 * with a real recording later is a change in one place.
 *
 * <p>Pitches are frequency ratios: 1.0 is the instrument's root, 1.26 a major third, 1.5 a fifth,
 * 2.0 the octave.
 */
@Environment(EnvType.CLIENT)
public final class SoundCues {
    private record Note(int tick, SoundEvent sound, float pitch, float volume) {
    }

    public enum Cue {
        /** Rising major arpeggio with a crystal sparkle on top. */
        LEVEL_UP(List.of(
                new Note(0, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.7f),
                new Note(3, SoundEvents.NOTE_BLOCK_BELL.value(), 1.26f, 0.7f),
                new Note(6, SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, 0.7f),
                new Note(9, SoundEvents.NOTE_BLOCK_BELL.value(), 2.0f, 0.8f),
                new Note(12, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.0f))),
        /** A small fanfare, then the advancement toast and a twinkle. */
        RUN_COMPLETE(List.of(
                new Note(0, SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.8f),
                new Note(4, SoundEvents.NOTE_BLOCK_PLING.value(), 1.26f, 0.8f),
                new Note(8, SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 0.8f),
                new Note(12, SoundEvents.NOTE_BLOCK_BELL.value(), 2.0f, 0.9f),
                new Note(12, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.6f),
                new Note(20, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 0.5f))),
        /** Red light: two falling low tones, like a traffic signal switching. */
        RED(List.of(
                new Note(0, SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 1.0f),
                new Note(0, SoundEvents.NOTE_BLOCK_BELL.value(), 0.63f, 0.7f),
                new Note(5, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 1.0f),
                new Note(5, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f, 0.7f))),
        /** Yellow: one warning chime; the seconds then tick on their own. */
        YELLOW(List.of(new Note(0, SoundEvents.NOTE_BLOCK_PLING.value(), 1.19f, 0.8f))),
        /** One second of the yellow countdown. */
        TICK(List.of(new Note(0, SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f, 0.8f))),
        /** Green: three quick rising tones. */
        GREEN(List.of(
                new Note(0, SoundEvents.NOTE_BLOCK_BELL.value(), 1.26f, 0.6f),
                new Note(2, SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, 0.6f),
                new Note(4, SoundEvents.NOTE_BLOCK_BELL.value(), 2.0f, 0.6f)));

        private final List<Note> notes;

        Cue(List<Note> notes) {
            this.notes = notes;
        }
    }

    private record Pending(Note note, int dueTick) {
    }

    private static final List<Pending> QUEUE = new ArrayList<>();
    private static int clock;

    private SoundCues() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clock++;
            if (QUEUE.isEmpty()) {
                return;
            }
            for (Iterator<Pending> it = QUEUE.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (p.dueTick() <= clock) {
                    playNow(client, p.note());
                    it.remove();
                }
            }
        });
    }

    public static void play(Cue cue) {
        play(cue, 0);
    }

    /** Plays a cue, optionally after {@code delayTicks}; the first note of an undelayed cue plays at once. */
    public static void play(Cue cue, int delayTicks) {
        Minecraft client = Minecraft.getInstance();
        for (Note note : cue.notes) {
            int due = clock + delayTicks + note.tick();
            if (due <= clock) {
                playNow(client, note);
            } else {
                QUEUE.add(new Pending(note, due));
            }
        }
    }

    private static void playNow(Minecraft client, Note note) {
        client.getSoundManager().play(SimpleSoundInstance.forUI(note.sound(), note.pitch(), note.volume()));
    }
}
