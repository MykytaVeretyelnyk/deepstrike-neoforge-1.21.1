package dev.warmode.game;

import dev.sbw.deepstrike.api.InterceptionEvent;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;

/** DeepStrike hooks: pays the defending team for interceptions. Loaded only when DeepStrike is installed. */
public final class DeepStrikeHooks {
   private static final double OPERATOR_RANGE = 96.0;

   /** The player who launched a DeepStrike missile, when the entity is one. */
   public static UUID ownerOf(Entity entity) {
      return entity instanceof GuidedMissileEntity missile ? missile.ownerId() : null;
   }

   @SubscribeEvent
   public void onInterception(InterceptionEvent event) {
      Entity target = event.target();
      if (!(target.level() instanceof ServerLevel level)) {
         return;
      }

      MinecraftServer server = level.getServer();
      WarData data = WarData.get(server);
      WarTeam defender = event.defenderOwner() == null ? null : data.teamOf(event.defenderOwner());
      if (defender == null && event.defenseUnit() != null) {
         defender = nearestOperatorTeam(server, data, event.defenseUnit());
      }

      WarTeam attacker = event.attackerOwner() == null ? null : data.teamOf(event.attackerOwner());
      if (defender == null || attacker == defender) {
         return;
      }

      String kind;
      Component what;
      if (target instanceof GuidedMissileEntity missile) {
         kind = "intercept_missile";
         what = Component.translatable("msg.warmode.missile", missile.kind().title());
      } else if (AirDefenseTargets.explosive(target)) {
         kind = "intercept_ordnance";
         what = Component.translatable("msg.warmode.ordnance", target.getDisplayName());
      } else {
         kind = "intercept_aircraft";
         what = Component.translatable("msg.warmode.aircraft", target.getDisplayName());
      }

      WarManager.reward(server, defender, kind, what);
   }

   /** Air-defense units have no owner field, so the closest team player to the launcher is treated as its operator. */
   private static WarTeam nearestOperatorTeam(MinecraftServer server, WarData data, Entity unit) {
      WarTeam best = null;
      double bestDistance = OPERATOR_RANGE * OPERATOR_RANGE;

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         WarTeam team = data.teamOf(player.getUUID());
         double distance = player.distanceToSqr(unit);
         if (team != null && player.level() == unit.level() && distance < bestDistance) {
            bestDistance = distance;
            best = team;
         }
      }

      return best;
   }
}
