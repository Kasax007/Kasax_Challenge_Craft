package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Where the casino devices stand. Clients need the positions to draw the moving parts, the server
 * needs them to run the shared crash and roulette rounds. Kept in {@link CasinoSavedData} as
 * {@code "dimension|x|y|z|type"} strings.
 */
public final class CasinoDevices {
    private CasinoDevices() {
    }

    public record Device(String dimension, BlockPos pos, DeviceType type) {
    }

    private static String key(ServerLevel level, BlockPos pos, DeviceType type) {
        return level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ() + "|" + type.id;
    }

    private static Device parse(String s) {
        String[] p = s.split("\\|");
        if (p.length != 5) return null;
        try {
            DeviceType type = DeviceType.byId(p[4]);
            if (type == null) return null;
            return new Device(p[0], new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])), type);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void register(ServerLevel level, BlockPos pos, DeviceType type) {
        CasinoSavedData data = CasinoSavedData.get(level.getServer());
        if (data.devices().add(key(level, pos, type))) {
            data.touch();
            syncAll(level.getServer());
        }
    }

    public static List<Device> all(MinecraftServer server) {
        List<Device> out = new ArrayList<>();
        for (String s : CasinoSavedData.get(server).devices()) {
            Device d = parse(s);
            if (d != null) out.add(d);
        }
        return out;
    }

    public static List<Device> in(ServerLevel level, DeviceType type) {
        String dim = level.dimension().identifier().toString();
        List<Device> out = new ArrayList<>();
        for (Device d : all(level.getServer())) {
            if (d.dimension().equals(dim) && (type == null || d.type() == type)) out.add(d);
        }
        return out;
    }

    /** The device at a position, registering it on the fly if it was placed some other way. */
    public static DeviceType at(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CasinoDeviceBlock block)) return null;
        DeviceType type = block.getDeviceType();
        register(level, pos, type);
        return type;
    }

    /**
     * Drops devices whose block is gone. Only checks loaded chunks — an unloaded device is assumed
     * to still stand, which is the only safe assumption.
     */
    public static void validate(MinecraftServer server) {
        CasinoSavedData data = CasinoSavedData.get(server);
        boolean changed = false;
        for (Iterator<String> it = data.devices().iterator(); it.hasNext(); ) {
            Device d = parse(it.next());
            if (d == null) {
                it.remove();
                changed = true;
                continue;
            }
            ServerLevel level = levelOf(server, d.dimension());
            if (level == null || !level.isLoaded(d.pos())) continue;
            BlockState state = level.getBlockState(d.pos());
            if (!(state.getBlock() instanceof CasinoDeviceBlock block) || block.getDeviceType() != d.type()) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            data.touch();
            syncAll(server);
        }
    }

    /** Whether the device of this type at {@code pos} has been picked up (only judged where loaded). */
    public static boolean gone(ServerLevel level, BlockPos pos, DeviceType type) {
        if (!level.isLoaded(pos)) return false;
        return !(level.getBlockState(pos).getBlock() instanceof CasinoDeviceBlock d && d.getDeviceType() == type);
    }

    /** Gives a stake back to its owner, with a note if they are online. */
    public static void refund(MinecraftServer server, java.util.UUID owner, long centi) {
        CasinoAccount a = CasinoSavedData.get(server).existing(owner);
        if (a == null || centi <= 0) return;
        a.balance += centi;
        CasinoSavedData.get(server).touch();
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) {
            CasinoEconomy.sync(p);
            p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("challengecraft.casino.device_gone",
                    CasinoEconomy.formatFull(centi)).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    public static ServerLevel levelOf(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) return level;
        }
        return null;
    }

    public static void sync(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<Device> list = in(level, null);
        int[] data = new int[list.size() * 4];
        for (int i = 0; i < list.size(); i++) {
            Device d = list.get(i);
            data[i * 4] = d.pos().getX();
            data[i * 4 + 1] = d.pos().getY();
            data[i * 4 + 2] = d.pos().getZ();
            data[i * 4 + 3] = d.type().ordinal();
        }
        ServerPlayNetworking.send(player, new CasinoNet.Devices(data));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }
}
