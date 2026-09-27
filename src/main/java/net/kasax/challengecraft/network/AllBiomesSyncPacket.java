package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * All Biomes (49): every biome the run needs, which dimension each belongs to, and which have been
 * found. Small enough (well under a hundred ids) to send whole on every new find, so the client
 * never has to merge partial updates — the HUD card and the list screen both read this one copy.
 */
public class AllBiomesSyncPacket implements CustomPacketPayload {
    public static final Type<AllBiomesSyncPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "all_biomes_sync"));

    public static final int OVERWORLD = 0;
    public static final int NETHER = 1;
    public static final int END = 2;

    /** Every biome of the run, grouped by dimension. */
    public final List<String> all;
    /** Parallel to {@link #all}: {@link #OVERWORLD}, {@link #NETHER} or {@link #END}. */
    public final List<Integer> dimensions;
    /** Found so far, in the order they were found. */
    public final List<String> visited;

    public AllBiomesSyncPacket(List<String> all, List<Integer> dimensions, List<String> visited) {
        this.all = List.copyOf(all);
        this.dimensions = List.copyOf(dimensions);
        this.visited = List.copyOf(visited);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AllBiomesSyncPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeVarInt(pkt.all.size());
                for (int i = 0; i < pkt.all.size(); i++) {
                    buf.writeUtf(pkt.all.get(i), 256);
                    buf.writeVarInt(pkt.dimensions.get(i));
                }
                buf.writeVarInt(pkt.visited.size());
                for (String id : pkt.visited) {
                    buf.writeUtf(id, 256);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<String> all = new ArrayList<>(n);
                List<Integer> dims = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    all.add(buf.readUtf(256));
                    dims.add(buf.readVarInt());
                }
                int v = buf.readVarInt();
                List<String> visited = new ArrayList<>(v);
                for (int i = 0; i < v; i++) {
                    visited.add(buf.readUtf(256));
                }
                return new AllBiomesSyncPacket(all, dims, visited);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
