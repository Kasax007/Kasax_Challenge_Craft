package net.kasax.challengecraft.code;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * A shareable challenge setup — which challenges and perks, the slider values and, optionally, the
 * world seed — packed into a short code a player can paste into chat: {@code CC-0A4K-R8TN-2Q}.
 *
 * <p>A code is a way to SHARE a setup, never a way around progression: whoever loads one must have
 * unlocked every challenge and perk in it (the screens refuse otherwise, and the server's ordinary
 * level check still runs on the result).
 *
 * <p>Plain Java on purpose, with no Minecraft types: the format is the one part of this feature that
 * must never change by accident, and a class that needs no game to run can be round-trip tested on
 * its own. Everything Minecraft-specific (which ids exist, the seed field, the clipboard) lives with
 * the callers.
 *
 * <h2>Format, version 1</h2>
 * Bytes, then Crockford base32 (no I, L, O, U — nothing that reads like another character), grouped
 * in fours behind the {@code CC-} prefix:
 * <ol>
 *   <li>version byte ({@value #VERSION})</li>
 *   <li>VarInt count, then each challenge id as a VarInt, ascending</li>
 *   <li>VarInt count, then each perk id (101+) as a VarInt, ascending</li>
 *   <li>one VarInt per slider, only for tunable challenges that are in the list, in
 *       {@link #TUNABLE_IDS} order — a slider nobody can see is not part of the setup</li>
 *   <li>flag byte: bit 0 = a seed follows</li>
 *   <li>the seed as a zig-zag VarLong, so the short seeds people type stay short</li>
 *   <li>the low 16 bits of a CRC-32 over everything before it, so a typo is caught instead of
 *       silently becoming a different setup</li>
 * </ol>
 * The version byte is 1, so the first character of every v1 code is {@code 0}; that is also why a
 * pasted {@code CC} prefix can be told apart from the data even when the dash is missing.
 *
 * <p><b>Never reorder or reuse anything here.</b> Codes are posted publicly and cannot be recalled;
 * a change in meaning must be a new version byte, with v1 still decodable.
 */
public record ChallengeCode(
        List<Integer> challengeIds,
        List<Integer> perkIds,
        int maxHearts,
        int inventorySlots,
        int mobHealth,
        int doubleTrouble,
        int gameSpeed,
        int fibMinutes,
        Long seed
) {
    public static final String PREFIX = "CC-";
    public static final int VERSION = 1;

    /** The challenges that carry a slider, in the order their values are written. Append only. */
    public static final int[] TUNABLE_IDS = {7, 12, 24, 35, 37, 45};

    public static final int DEFAULT_HEARTS = 20;
    public static final int DEFAULT_SLOTS = 36;
    public static final int DEFAULT_MOB_HEALTH = 1;
    public static final int DEFAULT_DOUBLE_TROUBLE = 2;
    public static final int DEFAULT_GAME_SPEED = 1;
    public static final int DEFAULT_FIB_MINUTES = 60;

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final Pattern IN_TEXT = Pattern.compile("(?i)\\bCC-[0-9A-Z]{2,}(?:-[0-9A-Z]+)*");

    /** Why a string is not a usable code. The caller turns this into a translated message. */
    public enum Problem {
        EMPTY, MALFORMED, CHECKSUM, NEWER_VERSION
    }

    public static final class InvalidCodeException extends Exception {
        private final Problem problem;

        public InvalidCodeException(Problem problem) {
            super(problem.name());
            this.problem = problem;
        }

        public Problem problem() {
            return problem;
        }
    }

    public ChallengeCode {
        challengeIds = List.copyOf(new TreeSet<>(challengeIds));
        perkIds = List.copyOf(new TreeSet<>(perkIds));
    }

    /**
     * Builds a code from a selection, resetting the slider of every tunable challenge that is not
     * selected. The same setup must always give the same code, whatever a hidden slider was left at.
     */
    public static ChallengeCode of(List<Integer> ids, List<Integer> perks, int maxHearts, int inventorySlots,
                                   int mobHealth, int doubleTrouble, int gameSpeed, int fibMinutes, Long seed) {
        return new ChallengeCode(ids, perks,
                ids.contains(7) ? maxHearts : DEFAULT_HEARTS,
                ids.contains(12) ? inventorySlots : DEFAULT_SLOTS,
                ids.contains(24) ? mobHealth : DEFAULT_MOB_HEALTH,
                ids.contains(35) ? doubleTrouble : DEFAULT_DOUBLE_TROUBLE,
                ids.contains(37) ? gameSpeed : DEFAULT_GAME_SPEED,
                ids.contains(45) ? fibMinutes : DEFAULT_FIB_MINUTES,
                seed);
    }

    public boolean hasSeed() {
        return seed != null;
    }

    /** The ids in this code that the given sets do not know — a code from a newer mod version. */
    public List<Integer> unknownIds(Set<Integer> knownChallenges, Set<Integer> knownPerks) {
        List<Integer> unknown = new ArrayList<>();
        for (int id : challengeIds) {
            if (!knownChallenges.contains(id)) {
                unknown.add(id);
            }
        }
        for (int id : perkIds) {
            if (!knownPerks.contains(id)) {
                unknown.add(id);
            }
        }
        return unknown;
    }

    // ---- encoding ----------------------------------------------------------------------------

    public String encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(VERSION);
        writeVarLong(out, challengeIds.size());
        for (int id : challengeIds) {
            writeVarLong(out, id);
        }
        writeVarLong(out, perkIds.size());
        for (int id : perkIds) {
            writeVarLong(out, id);
        }
        for (int id : TUNABLE_IDS) {
            if (challengeIds.contains(id)) {
                writeVarLong(out, sliderValue(id));
            }
        }
        out.write(seed != null ? 1 : 0);
        if (seed != null) {
            writeVarLong(out, (seed << 1) ^ (seed >> 63));
        }
        int check = checksum(out.toByteArray(), out.size());
        out.write((check >>> 8) & 0xFF);
        out.write(check & 0xFF);

        String chars = toBase32(out.toByteArray());
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < chars.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(chars.charAt(i));
        }
        return sb.toString();
    }

    private int sliderValue(int id) {
        return switch (id) {
            case 7 -> maxHearts;
            case 12 -> inventorySlots;
            case 24 -> mobHealth;
            case 35 -> doubleTrouble;
            case 37 -> gameSpeed;
            case 45 -> fibMinutes;
            default -> throw new IllegalArgumentException("not tunable: " + id);
        };
    }

    // ---- decoding ----------------------------------------------------------------------------

    /**
     * Finds the first code inside a longer text — a pasted share post, a chat line — or returns
     * null. Lets a player paste the whole message they were sent instead of fishing the code out.
     */
    public static String find(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = IN_TEXT.matcher(text);
        return m.find() ? m.group() : null;
    }

    /**
     * Parses a code as a person would type it: any case, with or without the {@code CC-} prefix,
     * dashes and spaces anywhere, and O / I / L read as 0 / 1 / 1.
     */
    public static ChallengeCode decode(String input) throws InvalidCodeException {
        if (input == null) {
            throw new InvalidCodeException(Problem.EMPTY);
        }
        String found = find(input);
        String s = (found != null ? found : input).toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (s.startsWith("CC")) {
            s = s.substring(2);
        }
        if (s.isEmpty()) {
            throw new InvalidCodeException(Problem.EMPTY);
        }
        s = s.replace('O', '0').replace('I', '1').replace('L', '1');
        byte[] bytes = fromBase32(s);
        if (bytes.length < 4) {
            throw new InvalidCodeException(Problem.MALFORMED);
        }
        int body = bytes.length - 2;
        int expected = ((bytes[body] & 0xFF) << 8) | (bytes[body + 1] & 0xFF);
        if (checksum(bytes, body) != expected) {
            throw new InvalidCodeException(Problem.CHECKSUM);
        }

        Reader r = new Reader(bytes, body);
        int version = r.readByte();
        if (version > VERSION) {
            throw new InvalidCodeException(Problem.NEWER_VERSION);
        }
        if (version != VERSION) {
            throw new InvalidCodeException(Problem.MALFORMED);
        }
        int count = r.readInt(0, 256);
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(r.readInt(1, 100_000));
        }
        int perkCount = r.readInt(0, 256);
        List<Integer> perks = new ArrayList<>(perkCount);
        for (int i = 0; i < perkCount; i++) {
            perks.add(r.readInt(1, 100_000));
        }
        int hearts = DEFAULT_HEARTS, slots = DEFAULT_SLOTS, mobHealth = DEFAULT_MOB_HEALTH;
        int doubleTrouble = DEFAULT_DOUBLE_TROUBLE, gameSpeed = DEFAULT_GAME_SPEED, fib = DEFAULT_FIB_MINUTES;
        for (int id : TUNABLE_IDS) {
            if (!ids.contains(id)) {
                continue;
            }
            switch (id) {
                case 7 -> hearts = r.readInt(1, 20);
                case 12 -> slots = r.readInt(1, 36);
                case 24 -> mobHealth = r.readInt(1, 100);
                case 35 -> doubleTrouble = r.readInt(2, 10);
                case 37 -> gameSpeed = r.readInt(1, 10);
                case 45 -> fib = r.readInt(15, 180);
                default -> throw new IllegalStateException();
            }
        }
        int flags = r.readByte();
        if ((flags & ~1) != 0) {
            throw new InvalidCodeException(Problem.MALFORMED);
        }
        Long seed = null;
        if ((flags & 1) != 0) {
            long zz = r.readVarLong();
            seed = (zz >>> 1) ^ -(zz & 1);
        }
        if (!r.atEnd()) {
            throw new InvalidCodeException(Problem.MALFORMED);
        }
        return new ChallengeCode(ids, perks, hearts, slots, mobHealth, doubleTrouble, gameSpeed, fib, seed);
    }

    // ---- primitives --------------------------------------------------------------------------

    private static int checksum(byte[] bytes, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, length);
        return (int) (crc.getValue() & 0xFFFF);
    }

    private static void writeVarLong(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static String toBase32(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        if (bits > 0) {
            sb.append(ALPHABET[(buffer << (5 - bits)) & 31]);
        }
        return sb.toString();
    }

    private static byte[] fromBase32(String s) throws InvalidCodeException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = indexOf(s.charAt(i));
            if (v < 0) {
                throw new InvalidCodeException(Problem.MALFORMED);
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
            buffer &= (1 << bits) - 1;
        }
        return out.toByteArray();
    }

    private static int indexOf(char c) {
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private static final class Reader {
        private final byte[] bytes;
        private final int end;
        private int pos;

        Reader(byte[] bytes, int end) {
            this.bytes = bytes;
            this.end = end;
        }

        int readByte() throws InvalidCodeException {
            if (pos >= end) {
                throw new InvalidCodeException(Problem.MALFORMED);
            }
            return bytes[pos++] & 0xFF;
        }

        long readVarLong() throws InvalidCodeException {
            long value = 0;
            for (int shift = 0; shift < 70; shift += 7) {
                int b = readByte();
                value |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw new InvalidCodeException(Problem.MALFORMED);
        }

        int readInt(int min, int max) throws InvalidCodeException {
            long v = readVarLong();
            if (v < min || v > max) {
                throw new InvalidCodeException(Problem.MALFORMED);
            }
            return (int) v;
        }

        boolean atEnd() {
            return pos == end;
        }
    }
}
