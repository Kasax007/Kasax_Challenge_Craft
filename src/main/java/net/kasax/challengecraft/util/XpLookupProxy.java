package net.kasax.challengecraft.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import java.util.UUID;

public class XpLookupProxy {
    private static final boolean IS_CLIENT = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

    public static long getXp(UUID uuid) {
        if (IS_CLIENT) {
            return ClientWrapper.getXp(uuid);
        } else {
            return net.kasax.challengecraft.data.XpManager.getXp(uuid);
        }
    }

    private static class ClientWrapper {
        private static long getXp(UUID uuid) {
            return net.kasax.challengecraft.client.ClientXpLookup.getXp(uuid);
        }
    }
}
