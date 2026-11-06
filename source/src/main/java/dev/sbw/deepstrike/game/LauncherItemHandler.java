package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.registry.BFBlocks;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Item handler capability for launcher blocks so other mods' automation can load missiles. Insert only: loaded missiles can only be
 * taken out by hand (unload), never drained by hoppers or conveyors. Every insert goes through LauncherBlockEntity.canPlaceItem and
 * setItem, so the launcher family, the 1-item slot limit, slotLocked (firing slot, command network) and terminal linking all apply.
 */
@EventBusSubscriber(
   modid = "deepstrike"
)
public final class LauncherItemHandler implements IItemHandler {
   private final LauncherBlockEntity launcher;

   private LauncherItemHandler(LauncherBlockEntity launcher) {
      this.launcher = launcher;
   }

   @SubscribeEvent
   public static void register(RegisterCapabilitiesEvent event) {
      event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BFBlocks.LAUNCHER_BE.get(), (be, side) -> new LauncherItemHandler(be));
   }

   public int getSlots() {
      return this.launcher.getContainerSize();
   }

   public ItemStack getStackInSlot(int slot) {
      return slot >= 0 && slot < this.getSlots() ? this.launcher.getItem(slot).copy() : ItemStack.EMPTY;
   }

   public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      if (!stack.isEmpty() && slot >= 0 && slot < this.getSlots() && this.launcher.getItem(slot).isEmpty() && this.launcher.canPlaceItem(slot, stack)) {
         if (!simulate) {
            this.launcher.setItem(slot, stack.copyWithCount(1));
         }

         return stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
      } else {
         return stack;
      }
   }

   public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return ItemStack.EMPTY;
   }

   public int getSlotLimit(int slot) {
      return 1;
   }

   public boolean isItemValid(int slot, ItemStack stack) {
      return slot >= 0 && slot < this.getSlots() && this.launcher.canPlaceItem(slot, stack);
   }
}
