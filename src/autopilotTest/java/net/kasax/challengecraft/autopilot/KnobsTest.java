package net.kasax.challengecraft.autopilot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class KnobsTest {
    @Test
    void textIsSortedAndShortAsTheTrainerWritesIt() {
        Map<String, Double> p = new TreeMap<>(Map.of("quick", 30.0, "foodstock", 1.0, "copper", 1.0));
        assertEquals("copper=1,foodstock=1,quick=30", Knobs.text(p));
        p.put("foodstock", 1.35);
        p.put("foodcap", -1.0);
        assertEquals("copper=1,foodcap=-1,foodstock=1.35,quick=30", Knobs.text(p));
        // The shipped champion (written by the laptop trainer) reads back to the same text.
        String shipped = "copper=1,cover=1,descent=1,foodcap=-1,foodstock=1,goalmodel=1,guardian=1,nightarmour=0,quick=30,shield=0,worldsense=1";
        assertEquals(shipped, Knobs.text(Knobs.parse(shipped)));
    }

    @Test
    void sameKnobsHoweverWritten() {
        // (The mod writes the knobs back in the order given, whole numbers without ".0".)
        assertTrue(Knobs.same("quick=30,copper=1", "copper=1.0,quick=30"));
        assertFalse(Knobs.same("quick=30,copper=1", "copper=0,quick=30"));
    }

    @Test
    void aChallengerDiffersStaysInRangeAndIsNotOneTriedBefore() {
        Random rng = new Random(7);
        Map<String, Double> champion = Knobs.defaults();
        Set<String> tried = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            Map<String, Double> ch = Knobs.mutate(champion, rng, tried);
            assertNotNull(ch);
            String t = Knobs.text(ch);
            assertNotEquals(Knobs.text(champion), t);
            assertFalse(tried.contains(t), "drawn again: " + t);
            int changed = 0;
            for (Knobs.Knob k : Knobs.ALL.values()) {
                double v = ch.get(k.name());
                if (v != champion.get(k.name())) changed++;
                switch (k.kind()) {
                    case CHOICE -> assertTrue(contains(k.values(), v), k.name() + "=" + v);
                    case INT -> assertTrue(v >= k.lo() && v <= k.hi() && v == Math.rint(v), k.name() + "=" + v);
                    case FLOAT -> assertTrue(v >= k.lo() && v <= k.hi() && Math.abs(v * 100 - Math.rint(v * 100)) < 1e-9, k.name() + "=" + v);
                }
            }
            assertTrue(changed >= 1 && changed <= 2, t);
            tried.add(t);
        }
    }

    private static boolean contains(double[] values, double v) {
        for (double x : values) if (x == v) return true;
        return false;
    }

    /** The same knobs, ranges and defaults as the laptop trainer's league (scripts/train/bobleague.py). */
    @Test
    void matchesTheLaptopTrainer() throws IOException {
        String py = Files.readString(repo().resolve("scripts/train/bobleague.py"));
        Pattern knob = Pattern.compile("\"(\\w+)\":\\s*\\(\"(choice|int|float)\",\\s*([\\[(][^\\])]*[\\])]),\\s*(-?[0-9.]+)\\)");
        Matcher m = knob.matcher(py.substring(py.indexOf("KNOBS = {"), py.indexOf("}", py.indexOf("KNOBS = {"))));
        Map<String, String> seen = new TreeMap<>();
        while (m.find()) {
            String name = m.group(1);
            Knobs.Knob k = Knobs.ALL.get(name);
            assertNotNull(k, "the trainer has a knob the autopilot does not: " + name);
            assertEquals(m.group(2), k.kind().name().toLowerCase(java.util.Locale.ROOT), name);
            double[] values = numbers(m.group(3));
            if (k.kind() == Knobs.Kind.CHOICE) assertArrayEquals(values, k.values(), name);
            else assertArrayEquals(values, new double[]{k.lo(), k.hi()}, name);
            assertEquals(Double.parseDouble(m.group(4)), k.def(), name);
            seen.put(name, m.group(0));
        }
        assertEquals(Knobs.ALL.keySet(), seen.keySet());
    }

    private static double[] numbers(String list) {
        String[] parts = list.replaceAll("[\\[\\]() ]", "").split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        return out;
    }

    static Path repo() {
        String p = System.getProperty("bob.repo");
        Path dir = p != null ? Path.of(p) : Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("scripts/train/bobleague.py"))) dir = dir.getParent();
        assertNotNull(dir, "the project folder (with scripts/train) was not found");
        return dir;
    }
}
