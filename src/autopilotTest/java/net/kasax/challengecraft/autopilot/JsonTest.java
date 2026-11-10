package net.kasax.challengecraft.autopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void readsWhatTheModWrites() {
        // A [BOTVS] result as the mod logs it (shortened).
        String line = "{\"seed\":-4512,\"secs\":5400,\"decided\":false,\"winner\":\"BobA\",\"bots\":[{\"name\":\"BobA\","
                + "\"params\":\"copper=1,foodstock=1.35\",\"tiles\":7,\"deaths\":0,\"hearts\":12.5,\"time\":{\"Get \\\"wood\\\"\":90},"
                + "\"claims\":[\"Oak Log@31\"]}]}";
        Map<String, Object> o = Json.map(Json.parse(line));
        assertEquals(-4512L, o.get("seed"));
        assertEquals(false, o.get("decided"));
        Map<String, Object> bot = Json.map(Json.list(o.get("bots")).get(0));
        assertEquals("copper=1,foodstock=1.35", bot.get("params"));
        assertEquals(12.5, bot.get("hearts"));
        assertEquals(90L, Json.map(bot.get("time")).get("Get \"wood\""));
        assertEquals(List.of("Oak Log@31"), bot.get("claims"));
    }

    @Test
    void writesWhatItReads() {
        String text = "{\"a\":[1,2.5,-3e-2,true,null,\"x\\ny\\u0001\"],\"b\":{},\"c\":[]}";
        Object v = Json.parse(text);
        assertEquals(v, Json.parse(Json.write(v)));
        assertEquals(v, Json.parse(Json.pretty(v)));
        assertTrue(Json.write(v).contains("\\u0001"));
    }

    @Test
    void writesNumbersAsPythonDoes() {
        // (No exponent for a time in seconds; whole longs without a fraction.)
        assertEquals("[1792155750.66,30,1.0]", Json.write(List.of(1792155750.66, 30L, 1.0)));
        assertEquals("null", Json.write(Double.NaN));
    }

    @Test
    void prettyIsIndentedByOne() {
        assertEquals("{\n \"a\": [\n  1\n ]\n}\n", Json.pretty(Map.of("a", List.of(1L))));
    }

    @Test
    void refusesWhatIsNotJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} x"));
    }

    @Test
    void helpersFallBack() {
        assertEquals(3.0, Json.num("3", 0));
        assertEquals(7.0, Json.num(null, 7));
        assertNull(Json.str(null, null));
        assertTrue(Json.map("not a map").isEmpty());
        assertTrue(Json.list(5).isEmpty());
    }
}
