package dev.sbw.deepstrike.menu;

import dev.sbw.deepstrike.game.FuelHelper;
import dev.sbw.deepstrike.game.MissileAssemblyBlockEntity;
import dev.sbw.deepstrike.game.MissileItem;
import dev.sbw.deepstrike.game.ModularComponentItem;
import dev.sbw.deepstrike.registry.BFMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MissileAssemblyMenu extends AbstractContainerMenu {
   public final MissileAssemblyBlockEntity blockEntity;

   public MissileAssemblyMenu(int id, Inventory inventory, FriendlyByteBuf data) {
      this(id, inventory, (MissileAssemblyBlockEntity)inventory.player.level().getBlockEntity(data.readBlockPos()));
   }

   public MissileAssemblyMenu(int id, Inventory inventory, final MissileAssemblyBlockEntity be) {
      super((MenuType)BFMenus.MISSILE_ASSEMBLY.get(), id);
      this.blockEntity = be;
      this.addSlot(new Slot(be, 0, 24, 42) {
         public boolean mayPlace(ItemStack stack) {
            return be.canPlaceItem(0, stack);
         }
      });
      this.addSlot(new Slot(be, 1, 62, 42) {
         public boolean mayPlace(ItemStack stack) {
            return be.canPlaceItem(1, stack);
         }
      });
      this.addSlot(new Slot(be, 2, 100, 42) {
         public boolean mayPlace(ItemStack stack) {
            return be.canPlaceItem(2, stack);
         }
      });
      this.addSlot(new Slot(be, 3, 24, 84) {
         public boolean mayPlace(ItemStack stack) {
            return be.canPlaceItem(3, stack);
         }
      });
      this.addSlot(new Slot(be, 4, 62, 84) {
         public boolean mayPlace(ItemStack stack) {
            return be.canPlaceItem(4, stack);
         }
      });
      this.addSlot(new Slot(be, 5, 100, 84) {
         public boolean mayPlace(ItemStack stack) {
            return be.canPlaceItem(5, stack);
         }
      });
      this.addSlot(new Slot(be, 6, 164, 60) {
         public boolean mayPlace(ItemStack stack) {
            return false;
         }
      });
      this.addSlot(new Slot(be, 7, 164, 84) {
         public boolean mayPlace(ItemStack stack) {
            return false;
         }
      });

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col + row * 9 + 9, 22 + col * 18, 152 + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(inventory, col, 22 + col * 18, 210));
      }
   }

   public boolean stillValid(Player player) {
      return this.blockEntity != null && this.blockEntity.stillValid(player);
   }

   public ItemStack quickMoveStack(Player player, int index) {
      Slot slot = (Slot)this.slots.get(index);
      if (!slot.hasItem()) {
         return ItemStack.EMPTY;
      } else {
         ItemStack stack = slot.getItem();
         ItemStack copy = stack.copy();
         int containerSlots = 8;
         if (index == 6 || index == 7) {
            if (!this.moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
               return ItemStack.EMPTY;
            }

            slot.onQuickCraft(stack, copy);
         } else if (index < containerSlots) {
            if (!this.moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
               return ItemStack.EMPTY;
            }
         } else if (stack.getItem() instanceof MissileItem) {
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
               return ItemStack.EMPTY;
            }
         } else if (stack.getItem() instanceof ModularComponentItem c) {
            if ("warhead".equals(c.category())) {
               if (!this.moveItemStackTo(stack, 1, 2, false)) {
                  return ItemStack.EMPTY;
               }
            } else if ("engine".equals(c.category())) {
               if (!this.moveItemStackTo(stack, 2, 3, false)) {
                  return ItemStack.EMPTY;
               }
            } else if ("module".equals(c.category()) && !this.moveItemStackTo(stack, 3, 5, false)) {
               return ItemStack.EMPTY;
            }
         } else {
            if (!FuelHelper.isFuelContainer(stack)) {
               return ItemStack.EMPTY;
            }

            if (!this.moveItemStackTo(stack, 5, 6, false)) {
               return ItemStack.EMPTY;
            }
         }

         if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }

         return copy;
      }
   }
}
