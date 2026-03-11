package net.kasax.challengecraft.block;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.kasax.challengecraft.storage.InfiniteChestStorage;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.Nullable;

public class InfiniteChestBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<InfiniteChestScreenHandler.PacketData> {
    private final InfiniteChestStorage storage = new InfiniteChestStorage();

    public InfiniteChestBlockEntity(BlockPos pos, BlockState state) {
        super(InfiniteChestRegistry.INFINITE_CHEST_BLOCK_ENTITY, pos, state);
    }

    public InfiniteChestStorage getStorage() {
        return storage;
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        nbt.getCompound("Storage").ifPresent(storageNbt -> {
            storage.readNbt(storageNbt, registryLookup);
        });
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        NbtCompound storageNbt = new NbtCompound();
        storage.writeNbt(storageNbt, registryLookup);
        nbt.put("Storage", storageNbt);
    }

    @Override
    public InfiniteChestScreenHandler.PacketData getScreenOpeningData(ServerPlayerEntity player) {
        return new InfiniteChestScreenHandler.PacketData(this.pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.challengecraft.infinite_chest");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new InfiniteChestScreenHandler(syncId, playerInventory, this.pos);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt, registryLookup);
        return nbt;
    }
    public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> getItemStorage() {
        return new net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>() {
            @Override
            public long insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant resource, long maxAmount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
                if (maxAmount <= 0) return 0;
                transaction.addCloseCallback((t, result) -> {
                    if (result.wasCommitted()) {
                        storage.addItems(InfiniteChestStorage.ItemStackKey.fromStack(resource.toStack()), maxAmount);
                        markDirty();
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
                            markDirty();
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
