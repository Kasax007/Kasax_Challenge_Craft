package net.kasax.challengecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server/client screen handler bridge for the infinite chest UI. */
public class InfiniteChestScreenHandler extends AbstractContainerMenu {
    private final BlockPos pos;
    private final net.minecraft.world.entity.player.Inventory playerInventory;

    public InfiniteChestScreenHandler(int syncId, net.minecraft.world.entity.player.Inventory playerInventory, PacketData data) {
        this(syncId, playerInventory, data.pos());
    }

    public InfiniteChestScreenHandler(int syncId, net.minecraft.world.entity.player.Inventory playerInventory, BlockPos pos) {
        super(InfiniteChestRegistry.INFINITE_CHEST_SCREEN_HANDLER, syncId);
        this.pos = pos;
        this.playerInventory = playerInventory;

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();
            
            if (player.level().getBlockEntity(pos) instanceof InfiniteChestBlockEntity be) {
                if (!player.level().isClientSide()) {
                    be.getStorage().addStack(itemStack2);
                    be.setChanged();
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        net.kasax.challengecraft.network.PacketHandler.syncInfiniteChest(serverPlayer, be);
                    }
                }
                itemStack2.setCount(0);
                slot.setByPlayer(ItemStack.EMPTY);
                slot.setChanged();
            } else {
                return ItemStack.EMPTY;
            }
        }
        return itemStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public BlockPos getPos() {
        return pos;
    }

    public record PacketData(BlockPos pos) implements CustomPacketPayload {
        public static final Type<PacketData> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "infinite_chest_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PacketData> PACKET_CODEC = StreamCodec.ofMember(
                (data, buf) -> buf.writeBlockPos(data.pos()),
                buf -> new PacketData(buf.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
