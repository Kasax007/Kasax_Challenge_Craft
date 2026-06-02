package net.kasax.challengecraft.block;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.kasax.challengecraft.storage.InfiniteChestStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** Block entity backing the infinite chest GUI and Fabric transfer API view. */
public class InfiniteChestBlockEntity extends BlockEntity implements ExtendedMenuProvider<InfiniteChestScreenHandler.PacketData> {
    private final InfiniteChestStorage storage = new InfiniteChestStorage();

    public InfiniteChestBlockEntity(BlockPos pos, BlockState state) {
        super(InfiniteChestRegistry.INFINITE_CHEST_BLOCK_ENTITY, pos, state);
    }

    public InfiniteChestStorage getStorage() {
        return storage;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Storage").ifPresent(storage::read);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        storage.write(output.child("Storage"));
    }

    @Override
    public InfiniteChestScreenHandler.PacketData getScreenOpeningData(ServerPlayer player) {
        return new InfiniteChestScreenHandler.PacketData(this.worldPosition);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.challengecraft.infinite_chest");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new InfiniteChestScreenHandler(syncId, playerInventory, this.worldPosition);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveCustomOnly(registryLookup);
    }
    public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> getItemStorage() {
        return new net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>() {
            @Override
            public long insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant resource, long maxAmount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
                if (maxAmount <= 0) return 0;
                transaction.addCloseCallback((t, result) -> {
                    if (result.wasCommitted()) {
                        storage.addItems(InfiniteChestStorage.ItemStackKey.fromStack(resource.toStack()), maxAmount);
                        setChanged();
                    }
                });
                return maxAmount;
            }

            @Override
            public long extract(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant resource, long maxAmount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
                long available = storage.getStoredItems().getOrDefault(InfiniteChestStorage.ItemStackKey.fromStack(resource.toStack()), 0L);
                long toExtract = Math.min(available, maxAmount);
                if (toExtract > 0) {
                    transaction.addCloseCallback((t, result) -> {
                        if (result.wasCommitted()) {
                            storage.removeItems(InfiniteChestStorage.ItemStackKey.fromStack(resource.toStack()), toExtract);
                            setChanged();
                        }
                    });
                }
                return toExtract;
            }

            @Override
            public java.util.Iterator<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>> iterator() {
                return storage.getStoredItems().entrySet().stream().map(e -> (net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>) new net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>() {
                    @Override
                    public long extract(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant resource, long maxAmount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
                        return 0; 
                    }
                    @Override
                    public boolean isResourceBlank() { return false; }
                    @Override
                    public net.fabricmc.fabric.api.transfer.v1.item.ItemVariant getResource() { 
                        return net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(e.getKey().toStack(1)); 
                    }
                    @Override
                    public long getAmount() { return e.getValue(); }
                    @Override
                    public long getCapacity() { return Long.MAX_VALUE; }
                }).iterator();
            }
        };
    }
}
