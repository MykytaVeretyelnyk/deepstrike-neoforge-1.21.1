package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.menu.MissileAssemblyMenu;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class MissileAssemblyBlockEntity extends BlockEntity implements Container, MenuProvider {
   public static final int SLOT_AIRFRAME = 0;
   public static final int SLOT_WARHEAD = 1;
   public static final int SLOT_ENGINE = 2;
   public static final int SLOT_MOD1 = 3;
   public static final int SLOT_MOD2 = 4;
   public static final int SLOT_FUEL = 5;
   public static final int SLOT_OUTPUT = 6;
   public static final int SLOT_EMPTY_CONTAINER = 7;
   public static final int TOTAL_SLOTS = 8;
   private final NonNullList<ItemStack> items = NonNullList.withSize(8, ItemStack.EMPTY);
   private String selectedCamo = "graphite";
   private final IItemHandler itemHandler = new MissileAssemblyBlockEntity.AutomationHandler();

   public static void serverTick(Level level, BlockPos pos, BlockState state, MissileAssemblyBlockEntity be) {
      if (level.getGameTime() % 10L == 0L && level.hasNeighborSignal(pos)) {
         be.assembleInternal(be.selectedCamo, null, true);
      }
   }

   public MissileAssemblyBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)BFBlocks.MISSILE_ASSEMBLY_BE.get(), pos, state);
   }

   public String getSelectedCamo() {
      return this.selectedCamo;
   }

   public void setSelectedCamo(String camo) {
      this.selectedCamo = camo;
      this.setChanged();
      if (this.level != null && !this.level.isClientSide) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   public int getContainerSize() {
      return 8;
   }

   public boolean isEmpty() {
      for (ItemStack stack : this.items) {
         if (!stack.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public ItemStack getItem(int slot) {
      return (ItemStack)this.items.get(slot);
   }

   public ItemStack removeItem(int slot, int count) {
      ItemStack result = ContainerHelper.removeItem(this.items, slot, count);
      if (!result.isEmpty()) {
         this.setChanged();
      }

      return result;
   }

   public ItemStack removeItemNoUpdate(int slot) {
      return ContainerHelper.takeItem(this.items, slot);
   }

   public void setItem(int slot, ItemStack stack) {
      this.items.set(slot, stack);
      if (stack.getCount() > this.getMaxStackSize()) {
         stack.setCount(this.getMaxStackSize());
      }

      this.setChanged();
   }

   public boolean stillValid(Player player) {
      return this.level != null
         && this.level.getBlockEntity(this.worldPosition) == this
         && player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
   }

   public void clearContent() {
      this.items.clear();
      this.setChanged();
   }

   public boolean canPlaceItem(int slot, ItemStack stack) {
      if (slot == 6 || slot == 7) {
         return false;
      } else if (slot == 0) {
         return stack.getItem() instanceof AirframeItem || stack.getItem() instanceof MissileItem;
      } else if (slot == 1) {
         return stack.getItem() instanceof ModularComponentItem c && "warhead".equals(c.category());
      } else if (slot == 2) {
         return stack.getItem() instanceof ModularComponentItem c && "engine".equals(c.category());
      } else if (slot == 3 || slot == 4) {
         return stack.getItem() instanceof ModularComponentItem c && "module".equals(c.category());
      } else {
         return slot == 5 ? FuelHelper.isFuelContainer(stack) : true;
      }
   }

   public boolean assemble(String camoToApply, ServerPlayer player) {
      return this.assembleInternal(camoToApply, player, false);
   }

   private boolean assembleInternal(String camoToApply, @Nullable ServerPlayer player, boolean automated) {
      ItemStack airframeStack = (ItemStack)this.items.get(0);
      if (!airframeStack.isEmpty() && airframeStack.getItem() instanceof MissileItem missileItem) {
         if (!((ItemStack)this.items.get(6)).isEmpty()) {
            if (player != null) {
               player.displayClientMessage(Component.translatable("message.deepstrike.assembly.take_output"), true);
            }

            return false;
         } else {
            MissileKind kind = missileItem.kind();
            MissileData prevData = MissileData.fromItemStack(airframeStack);
            ItemStack warheadStack = (ItemStack)this.items.get(1);
            String warhead = prevData != null ? prevData.warhead() : "none";
            if (!warheadStack.isEmpty() && warheadStack.getItem() instanceof ModularComponentItem c && "warhead".equals(c.category())) {
               warhead = c.componentId();
            }

            ItemStack engineStack = (ItemStack)this.items.get(2);
            String engine = prevData != null ? prevData.engine() : "rocket";
            if (!engineStack.isEmpty() && engineStack.getItem() instanceof ModularComponentItem c && "engine".equals(c.category())) {
               engine = c.componentId();
            }

            List<String> modules = new ArrayList<>();
            ItemStack mod1 = (ItemStack)this.items.get(3);
            if (!mod1.isEmpty() && mod1.getItem() instanceof ModularComponentItem c && "module".equals(c.category()) && !modules.contains(c.componentId())) {
               modules.add(c.componentId());
            }

            ItemStack mod2 = (ItemStack)this.items.get(4);
            if (!mod2.isEmpty() && mod2.getItem() instanceof ModularComponentItem c && "module".equals(c.category()) && !modules.contains(c.componentId())) {
               modules.add(c.componentId());
            }

            String camo = camoToApply != null && !camoToApply.isEmpty() ? camoToApply : this.selectedCamo;
            ItemStack fuelStack = (ItemStack)this.items.get(5);
            if (!automated || !warheadStack.isEmpty() && !engineStack.isEmpty() && FuelHelper.isFuelContainer(fuelStack)) {
               ItemStack emptyContainer = FuelHelper.getEmptyContainer(fuelStack);
               ItemStack returned = (ItemStack)this.items.get(7);
               if (!automated
                  || emptyContainer.isEmpty()
                  || returned.isEmpty()
                  || ItemStack.isSameItemSameComponents(returned, emptyContainer) && returned.getCount() < returned.getMaxStackSize()) {
                  int currentFuel = prevData != null ? prevData.fuel() : 0;
                  int maxFuel = 1000;
                  if (kind == MissileKind.FP5) {
                     maxFuel = 1200;
                  } else if (kind == MissileKind.STORM_SHADOW) {
                     maxFuel = 1500;
                  } else if (kind == MissileKind.JAGM) {
                     maxFuel = 800;
                  } else if (kind == MissileKind.PEACEKEEPER) {
                     maxFuel = 3000;
                  }

                  if (modules.contains("range")) {
                     maxFuel = (int)(maxFuel * 1.4);
                  }

                  MissileData proposed = new MissileData(kind, warhead, engine, modules, camo, currentFuel, maxFuel);
                  if (proposed.supportsWarhead() && (kind != MissileKind.PEACEKEEPER && kind != MissileKind.SPIKE_NLOS || "rocket".equals(engine))) {
                     boolean fueledThisCycle = false;
                     if (!fuelStack.isEmpty() && FuelHelper.isFuelContainer(fuelStack)) {
                        currentFuel = maxFuel;
                        fueledThisCycle = true;
                        fuelStack.shrink(1);
                        if (!emptyContainer.isEmpty()) {
                           if (returned.isEmpty()) {
                              this.items.set(7, emptyContainer);
                           } else if (ItemStack.isSameItemSameComponents(returned, emptyContainer) && returned.getCount() < returned.getMaxStackSize()) {
                              returned.grow(1);
                           } else if (player != null && !player.getInventory().add(emptyContainer)) {
                              player.drop(emptyContainer, false);
                           }
                        }
                     }

                     ItemStack outputStack;
                     if ("none".equals(warhead)) {
                        outputStack = switch (kind) {
                           case FP5 -> new ItemStack((ItemLike)BFItems.FP5_AIRFRAME.get());
                           case STORM_SHADOW -> new ItemStack((ItemLike)BFItems.STORM_SHADOW_AIRFRAME.get());
                           case JAGM -> new ItemStack((ItemLike)BFItems.JAGM_AIRFRAME.get());
                           case PEACEKEEPER -> new ItemStack((ItemLike)BFItems.PEACEKEEPER_AIRFRAME.get());
                           case SPIKE_NLOS -> new ItemStack((ItemLike)BFItems.SPIKE_NLOS_AIRFRAME.get());
                        };
                     } else if (kind == MissileKind.FP5 && "graphite".equals(warhead)) {
                        outputStack = new ItemStack((ItemLike)BFItems.FP5_GRAPHITE.get());
                     } else if (kind == MissileKind.FP5) {
                        outputStack = new ItemStack((ItemLike)BFItems.FP5.get());
                     } else if (kind == MissileKind.STORM_SHADOW) {
                        outputStack = new ItemStack((ItemLike)BFItems.STORM_SHADOW.get());
                     } else if (kind == MissileKind.PEACEKEEPER) {
                        outputStack = new ItemStack((ItemLike)BFItems.PEACEKEEPER.get());
                     } else if (kind == MissileKind.SPIKE_NLOS) {
                        outputStack = new ItemStack((ItemLike)BFItems.SPIKE_NLOS_MISSILE.get());
                     } else {
                        outputStack = new ItemStack((ItemLike)BFItems.JAGM.get());
                     }

                     MissileData finalData = new MissileData(kind, warhead, engine, modules, camo, currentFuel, maxFuel);
                     finalData.writeToItemStack(outputStack);
                     airframeStack.shrink(1);
                     if (!warheadStack.isEmpty()) {
                        warheadStack.shrink(1);
                     }

                     if (!engineStack.isEmpty()) {
                        engineStack.shrink(1);
                     }

                     if (!mod1.isEmpty()) {
                        mod1.shrink(1);
                     }

                     if (!mod2.isEmpty()) {
                        mod2.shrink(1);
                     }

                     this.items.set(6, outputStack);
                     this.setChanged();
                     if (this.level != null) {
                        this.level.playSound(null, this.worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9F, 1.15F);
                        if (fueledThisCycle) {
                           this.level.playSound(null, this.worldPosition, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.8F, 1.0F);
                        }
                     }

                     Component fuelStatus = currentFuel > 0
                        ? Component.translatable("message.deepstrike.assembly.fueled", new Object[]{currentFuel})
                        : Component.translatable("message.deepstrike.assembly.empty");
                     if (player != null) {
                        player.displayClientMessage(Component.translatable("message.deepstrike.assembly.complete", new Object[]{fuelStatus}), true);
                     }

                     return true;
                  } else {
                     if (player != null) {
                        player.displayClientMessage(Component.translatable("message.deepstrike.assembly.incompatible"), true);
                     }

                     return false;
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      } else {
         if (player != null) {
            player.displayClientMessage(Component.translatable("message.deepstrike.assembly.insert_airframe"), true);
         }

         return false;
      }
   }

   public IItemHandler itemHandler() {
      return this.itemHandler;
   }

   public Component getDisplayName() {
      return Component.translatable("message.deepstrike.assembly.title");
   }

   @Nullable
   public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
      return new MissileAssemblyMenu(id, inventory, this);
   }

   protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      super.saveAdditional(tag, registries);
      ContainerHelper.saveAllItems(tag, this.items, registries);
      tag.putString("Camo", this.selectedCamo);
   }

   public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      super.loadAdditional(tag, registries);
      this.items.clear();
      ContainerHelper.loadAllItems(tag, this.items, registries);
      this.selectedCamo = tag.getString("Camo");
      if (this.selectedCamo.isEmpty()) {
         this.selectedCamo = "graphite";
      }
   }

   public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
      CompoundTag tag = super.getUpdateTag(registries);
      this.saveAdditional(tag, registries);
      return tag;
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   private final class AutomationHandler implements IItemHandler {
      public int getSlots() {
         return 8;
      }

      public ItemStack getStackInSlot(int slot) {
         return slot >= 0 && slot < 8 ? (ItemStack)MissileAssemblyBlockEntity.this.items.get(slot) : ItemStack.EMPTY;
      }

      public int getSlotLimit(int slot) {
         return MissileAssemblyBlockEntity.this.getMaxStackSize();
      }

      public boolean isItemValid(int slot, ItemStack stack) {
         return slot >= 0 && slot < 8 && MissileAssemblyBlockEntity.this.canPlaceItem(slot, stack);
      }

      public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
         if (!stack.isEmpty() && this.isItemValid(slot, stack)) {
            ItemStack stored = (ItemStack)MissileAssemblyBlockEntity.this.items.get(slot);
            if (!stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, stack)) {
               return stack;
            } else {
               int accepted = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), this.getSlotLimit(slot)) - stored.getCount());
               if (accepted <= 0) {
                  return stack;
               } else {
                  if (!simulate) {
                     if (stored.isEmpty()) {
                        MissileAssemblyBlockEntity.this.items.set(slot, stack.copyWithCount(accepted));
                     } else {
                        stored.grow(accepted);
                     }

                     MissileAssemblyBlockEntity.this.setChanged();
                  }

                  return stack.copyWithCount(stack.getCount() - accepted);
               }
            }
         } else {
            return stack;
         }
      }

      public ItemStack extractItem(int slot, int amount, boolean simulate) {
         if ((slot == 6 || slot == 7) && amount > 0) {
            ItemStack stored = (ItemStack)MissileAssemblyBlockEntity.this.items.get(slot);
            if (stored.isEmpty()) {
               return ItemStack.EMPTY;
            } else {
               int removed = Math.min(amount, stored.getCount());
               ItemStack result = stored.copyWithCount(removed);
               if (!simulate) {
                  stored.shrink(removed);
                  if (stored.isEmpty()) {
                     MissileAssemblyBlockEntity.this.items.set(slot, ItemStack.EMPTY);
                  }

                  MissileAssemblyBlockEntity.this.setChanged();
               }

               return result;
            }
         } else {
            return ItemStack.EMPTY;
         }
      }
   }
}
