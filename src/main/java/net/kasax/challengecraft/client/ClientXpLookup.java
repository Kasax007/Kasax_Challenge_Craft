package net.kasax.challengecraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraftClient;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class ClientXpLookup {
    public static long getXp(UUID uuid) {
        return ChallengeCraftClient.PLAYER_XP_MAP.getOrDefault(uuid, 0L);
    }
}
