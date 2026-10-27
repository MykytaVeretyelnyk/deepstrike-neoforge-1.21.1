package dev.sbw.deepstrike.menu;

import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.registry.BFMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class LauncherMenu extends AbstractContainerMenu {
   public final LauncherBlockEntity launcher;

   public LauncherMenu(int id, Inventory inventory, FriendlyByteBuf data) {
      this(id, inventory, (LauncherBlockEntity)inventory.player.level().getBlockEntity(data.readBlockPos()));
   }

   public LauncherMenu(int id, Inventory inventory, final LauncherBlockEntity launcher) {
      super((MenuType)BFMenus.LAUNCHER.get(), id);
      this.launcher = launcher;

      for (int i = 0; i < launcher.capacity(); i++) {
         int index = i;
         this.addSlot(new Slot(launcher, i, launcher.capacity() == 1 ? 80 : 69 + i % 2 * 22, 24 + (launcher.capacity() == 1 ? 0 : 2 - i / 2) * 22) {
            public boolean mayPlace(ItemStack stack) {
               return launcher.canPlaceItem(this.index, stack);
            }

            public boolean mayPickup(Player player) {
               return !launcher.slotLocked(this.index);
            }

            public int getMaxStackSize() {
               return 1;
            }
         });
      }

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(inventory, col, 8 + col * 18, 162));
      }
   }

   public boolean stillValid(Player player) {
      return this.launcher.getLevel() != null
         && this.launcher.getLevel().getBlockEntity(this.launcher.getBlockPos()) == this.launcher
         && player.distanceToSqr(this.launcher.getBlockPos().getX() + 0.5, this.launcher.getBlockPos().getY() + 0.5, this.launcher.getBlockPos().getZ() + 0.5)
            < 100.0;
   }

   public ItemStack quickMoveStack(Player player, int index) {
      Slot slot = (Slot)this.slots.get(index);
      if (slot.hasItem() && slot.mayPickup(player)) {
         ItemStack stack = slot.getItem();
         ItemStack copy = stack.copy();
         int count = this.launcher.capacity();
         if (index < count) {
            if (!this.moveItemStackTo(stack, count, this.slots.size(), true)) {
               return ItemStack.EMPTY;
            }
         } else if (!this.moveItemStackTo(stack, 0, count, false)) {
            return ItemStack.EMPTY;
         }

         if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }

         return copy;
      } else {
         return ItemStack.EMPTY;
      }
   }
}
