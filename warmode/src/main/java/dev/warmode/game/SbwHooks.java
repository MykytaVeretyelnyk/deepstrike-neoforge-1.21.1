package dev.warmode.game;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/** Superb Warfare hooks: remembers which team rides a vehicle and pays for destroyed enemy vehicles. */
public final class SbwHooks {
   private static final String TEAM_TAG = "WarTeam";

   @SubscribeEvent
   public void onMount(EntityMountEvent event) {
      if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer player && event.getEntityBeingMounted() instanceof VehicleEntity vehicle) {
         WarTeam team = WarData.get(player.getServer()).teamOf(player.getUUID());
         if (team != null) {
            vehicle.getPersistentData().putString(TEAM_TAG, team.id);
         }
      }
   }

   @SubscribeEvent
   public void onLeave(EntityLeaveLevelEvent event) {
      if (!(event.getEntity() instanceof VehicleEntity vehicle) || !(event.getLevel() instanceof ServerLevel level) || vehicle.getHealth() > 0.0F) {
         return;
      }

      WarTeam owner = WarTeam.byId(vehicle.getPersistentData().getString(TEAM_TAG));
      Entity attacker = vehicle.getLastAttacker();
      if (owner == null || attacker == null) {
         return;
      }

      MinecraftServer server = level.getServer();
      WarTeam team = WarData.get(server).teamOf(attacker.getUUID());
      if (team != null && team != owner) {
         WarManager.reward(server, team, "kill_vehicle", Component.translatable("msg.warmode.vehicle", vehicle.getDisplayName()));
      }
   }
}
