package net.kasax.challengecraft.block;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class InfiniteChestScreenHandler extends ScreenHandler {
    private final BlockPos pos;
    private final net.minecraft.entity.player.PlayerInventory playerInventory;

    public InfiniteChestScreenHandler(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, PacketData data) {
        this(syncId, playerInventory, data.pos());
    }

    public InfiniteChestScreenHandler(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, BlockPos pos) {
        super(InfiniteChestRegistry.INFINITE_CHEST_SCREEN_HANDLER, syncId);
        this.pos = pos;
        this.playerInventory = playerInventory;

        // Player Inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasStack()) {
            ItemStack itemStack2 = slot.getStack();
            itemStack = itemStack2.copy();
            
            if (player.getWorld().getBlockEntity(pos) instanceof InfiniteChestBlockEntity be) {
                if (!player.getWorld().isClient) {
                    be.getStorage().addStack(itemStack2);
                    be.markDirty();
                    if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                        net.kasax.challengecraft.network.PacketHandler.syncInfiniteChest(serverPlayer, be);
                    }
                }
                itemStack2.setCount(0);
                slot.setStack(ItemStack.EMPTY);
                slot.markDirty();
            } else {
                return ItemStack.EMPTY;
            }
        }
        return itemStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    public BlockPos getPos() {
        return pos;
    }

    public record PacketData(BlockPos pos) implements CustomPayload {
        public static final Id<PacketData> ID = new Id<>(Identifier.of("challengecraft", "infinite_chest_open"));
        public static final PacketCodec<RegistryByteBuf, PacketData> PACKET_CODEC = PacketCodec.of(
                (data, buf) -> buf.writeBlockPos(data.pos()),
                buf -> new PacketData(buf.readBlockPos())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
