package net.kasax.challengecraft.film;

import net.minecraft.client.resources.DefaultPlayerSkin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Who the film player is. The development client picks "Player" and three random digits at every
 * start, and with the name a random default skin: the same character looked different in every
 * shot. The film fixes it: a plain name, and an id that gives the classic skin.
 */
public final class FilmIdentity {
    public static final String NAME;
    public static final UUID ID;

    static {
        String name = "Steve";
        UUID id = offline(name);
        boolean found = false;
        String[] candidates = {"Steve", "Steve_", "Stevie", "Crafter", "Runner", "Miner", "Builder", "Hero", "Pixel", "Blocky", "Digger",
                "Explorer", "Survivor", "Adventurer", "Wanderer", "Ranger", "Scout", "Rookie", "Gamer", "Cubeman"};
        for (String c : candidates) {
            UUID u = offline(c);
            if (isClassic(u)) {
                name = c;
                id = u;
                found = true;
                break;
            }
        }
        for (int i = 1; !found && i < 500; i++) {
            UUID u = offline("Steve" + i);
            if (isClassic(u)) {
                name = "Steve" + i;
                id = u;
                found = true;
            }
        }
        NAME = name;
        ID = id;
    }

    private FilmIdentity() {
    }

    private static UUID offline(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isClassic(UUID id) {
        try {
            return DefaultPlayerSkin.get(id) == DefaultPlayerSkin.getDefaultSkin();
        } catch (Throwable t) {
            return false;
        }
    }
}
