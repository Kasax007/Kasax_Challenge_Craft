package net.kasax.challengecraft.bot;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** All bots on the server: joining, leaving, ticking their brains, bringing them back after death. */
public final class BotManager {
    public static final Logger LOG = LoggerFactory.getLogger("ChallengeCraft-Bot");
    private static final Map<UUID, Bot> BOTS = new LinkedHashMap<>();
    private static final int RESPAWN_DELAY = 20; // deathTime stops counting at 20

    private BotManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BotManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Bot bot : new ArrayList<>(BOTS.values())) remove(server, bot);
            BOTS.clear();
        });
        BotCommands.register();
    }

    public static Bot spawn(MinecraftServer server, String name, ServerLevel level, Vec3 pos) {
        Bot existing = byName(name);
        if (existing != null) return existing;
        BotPlayer body = BotPlayer.join(server, name, level, pos);
        Bot bot = new Bot(body);
        BOTS.put(bot.id, bot);
        LOG.info("[Bot] {} joined at {}", name, pos);
        return bot;
    }

    public static void remove(MinecraftServer server, Bot bot) {
        bot.navigator().stop();
        BOTS.remove(bot.id);
        ServerPlayer body = server.getPlayerList().getPlayer(bot.id);
        if (body != null) server.getPlayerList().remove(body);
        LOG.info("[Bot] {} left", bot.name);
    }

    public static Bot byName(String name) {
        for (Bot b : BOTS.values()) if (b.name.equalsIgnoreCase(name)) return b;
        return null;
    }

    public static Collection<Bot> all() {
        return BOTS.values();
    }

    public static boolean isBot(ServerPlayer player) {
        return player instanceof BotPlayer;
    }

    /** A bot's new body after a respawn (called from PlayerList#respawn, see BotRespawnMixin). */
    public static ServerPlayer newBody(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        return new BotPlayer(server, level, profile, info);
    }

    private static void tick(MinecraftServer server) {
        if (BOTS.isEmpty()) return;
        for (Bot bot : new ArrayList<>(BOTS.values())) {
            ServerPlayer now = server.getPlayerList().getPlayer(bot.id);
            if (!(now instanceof BotPlayer body)) {
                BOTS.remove(bot.id); // kicked or removed by something else
                continue;
            }
            if (body != bot.body()) bot.attach(body);
            if (body.isDeadOrDying()) {
                body.stopInputs();
                if (body.deathTime >= RESPAWN_DELAY) {
                    ServerPlayer reborn = server.getPlayerList().respawn(body, false, Entity.RemovalReason.KILLED);
                    if (reborn instanceof BotPlayer b) bot.attach(b);
                    if (bot.brain() != null) bot.brain().respawned(bot);
                }
                continue;
            }
            bot.tick();
        }
        BotBenchmark.tickAll();
    }

    /** A line in chat and log about what a bot is up to (for testing). */
    static void debug(ServerPlayer bot, String text) {
        LOG.info("[Bot] {}: {}", bot.getGameProfile().name(), text);
        MinecraftServer server = bot.level().getServer();
        if (server == null) return;
        Component line = Component.literal("[" + bot.getGameProfile().name() + "] " + text).withStyle(ChatFormatting.DARK_AQUA);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p instanceof BotPlayer) && net.kasax.challengecraft.util.ModPermissions.isOp(p)) p.sendSystemMessage(line);
        }
    }
}
