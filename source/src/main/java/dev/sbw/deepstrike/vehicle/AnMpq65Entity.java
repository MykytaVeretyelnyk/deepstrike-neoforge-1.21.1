package dev.sbw.deepstrike.vehicle;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.atsuishio.superbwarfare.init.ModTags.Items;
import com.atsuishio.superbwarfare.item.weapon.CrowbarItem;
import dev.sbw.deepstrike.registry.BFItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class AnMpq65Entity extends DeepStrikeVehicleEntity {
   public AnMpq65Entity(EntityType<? extends AnMpq65Entity> type, Level level) {
      super(type, level);
   }

   public InteractionResult interact(Player player, InteractionHand hand) {
      ItemStack held = player.getItemInHand(hand);
      if (held.is(Items.TOOLS_CROWBAR) || held.getItem() instanceof CrowbarItem) {
         return super.interact(player, hand);
      } else if (held.is((Item)BFItems.AIR_DEFENSE_TERMINAL.get())) {
         if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
            CompoundTag tag = NbtCompat.getOrCreateTag(held);
            tag.putUUID("TargetUUID", this.getUUID());
            tag.putInt("TargetEntityId", this.getId());
            tag.putString("TargetName", "AN/MPQ-65");
            tag.putString("TargetDimension", this.level().dimension().location().toString());
            tag.putLong("TargetPosition", this.blockPosition().asLong());
            tag.putInt("TargetChunkX", this.chunkPosition().x);
            tag.putInt("TargetChunkZ", this.chunkPosition().z);
            NbtCompat.setTag(held, tag);
            player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.radar_bound"));
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else {
         if (!this.level().isClientSide) {
            player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.radar_stationary"));
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
   }
}
