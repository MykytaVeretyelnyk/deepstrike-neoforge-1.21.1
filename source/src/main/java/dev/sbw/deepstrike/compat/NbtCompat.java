package dev.sbw.deepstrike.compat;

import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Item NBT access on top of the 1.21 custom_data component. Reads return copies. */
public final class NbtCompat {
   private NbtCompat() {
   }

   public static boolean hasTag(ItemStack stack) {
      return stack.has(DataComponents.CUSTOM_DATA);
   }

   public static CompoundTag getTag(ItemStack stack) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      return data == null ? null : data.copyTag();
   }

   public static CompoundTag getOrCreateTag(ItemStack stack) {
      CompoundTag tag = getTag(stack);
      return tag == null ? new CompoundTag() : tag;
   }

   public static CompoundTag getTagElement(ItemStack stack, String key) {
      CompoundTag tag = getTag(stack);
      return tag != null && tag.contains(key, 10) ? tag.getCompound(key) : null;
   }

   public static void setTag(ItemStack stack, CompoundTag tag) {
      if (tag == null) {
         stack.remove(DataComponents.CUSTOM_DATA);
      } else {
         stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      }
   }

   public static void update(ItemStack stack, Consumer<CompoundTag> updater) {
      CustomData.update(DataComponents.CUSTOM_DATA, stack, updater);
   }

   public static HolderLookup.Provider registries(BlockEntity be) {
      if (be.getLevel() != null) {
         return be.getLevel().registryAccess();
      }
      return ServerLifecycleHooks.getCurrentServer() != null ? ServerLifecycleHooks.getCurrentServer().registryAccess() : RegistryAccess.EMPTY;
   }

   public static CompoundTag saveBE(BlockEntity be) {
      return be.saveWithoutMetadata(registries(be));
   }

   public static CompoundTag saveFullBE(BlockEntity be) {
      return be.saveWithFullMetadata(registries(be));
   }

   public static void loadBE(BlockEntity be, CompoundTag tag) {
      be.loadWithComponents(tag, registries(be));
   }
}
