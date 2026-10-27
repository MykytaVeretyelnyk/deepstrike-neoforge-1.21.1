package dev.sbw.deepstrike.vehicle;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
import dev.sbw.deepstrike.game.DeepStrikeData;
import dev.sbw.deepstrike.game.DefenseRuntime;
import dev.sbw.deepstrike.game.LaunchJournal;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.registries.DeferredHolder;

public abstract class DeepStrikeVehicleEntity extends GeoVehicleEntity {
   protected DeepStrikeVehicleEntity(EntityType<? extends DeepStrikeVehicleEntity> type, Level level) {
      super(type, level);
   }

   public ItemStack snapshotItem() {
      java.util.function.Supplier<Item> item = this instanceof PatriotTruckEntity
         ? BFItems.PATRIOT_TRUCK
         : (this instanceof IronDomeEntity ? BFItems.IRON_DOME : (this instanceof AnMpq65Entity ? BFItems.AN_MPQ_65 : BFItems.SPIKE_NLOS));
      ItemStack stack = new ItemStack((ItemLike)item.get());
      CompoundTag snapshot = this.saveWithoutId(new CompoundTag());
      DeepStrikeData.stamp(snapshot);
      snapshot.remove("Passengers");
      snapshot.remove("PendingLaunch");
      NbtCompat.update(stack, t -> t.put("DeepStrikeVehicle", snapshot));
      return stack;
   }

   public ItemStack getPickResult() {
      return this.snapshotItem();
   }

   public List<ItemStack> getRetrieveItems() {
      return List.of(this.snapshotItem());
   }

   public InteractionResult onCrowbarInteract(ItemStack crowbar, Player player, InteractionHand hand) {
      if (!player.isShiftKeyDown() || !this.getPassengers().isEmpty()) {
         return null;
      } else if (this.isWreck()) {
         return InteractionResult.PASS;
      } else {
         if (this.level() instanceof ServerLevel server) {
            if (this instanceof SpikeNlosEntity spike && spike.hasPendingLaunch()) {
               player.displayClientMessage(Component.translatable("message.deepstrike.launch.wait"), true);
               return InteractionResult.FAIL;
            }

            ItemStack packed = this.snapshotItem();
            NbtCompat.update(packed, t -> t.putUUID("DeepStrikeTransfer", DefenseRuntime.get(server).pack(this)));
            ItemHandlerHelper.giveItemToPlayer(player, packed);
            this.discard();
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
   }

   public void tick() {
      super.tick();
      if (this.level() instanceof ServerLevel server && this.tickCount % 20 == 0) {
         DefenseRuntime.get(server).remember(this);
      }
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      LaunchJournal.id(this);
      super.addAdditionalSaveData(tag);
      DeepStrikeData.stamp(tag);
      tag.put("ForgeData", this.getPersistentData().copy());
   }

   public void remove(RemovalReason reason) {
      if (this.level() instanceof ServerLevel server) {
         DefenseRuntime.get(server).departed(this, reason);
      }

      super.remove(reason);
   }
}
