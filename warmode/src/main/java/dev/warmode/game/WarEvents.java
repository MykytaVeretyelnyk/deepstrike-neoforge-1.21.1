package dev.warmode.game;

import dev.warmode.WarMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Vanilla-side hooks of the war mode. Optional mod hooks live in SbwHooks and DeepStrikeHooks. */
public final class WarEvents {
   private static Explosion lastExplosion;

   @SubscribeEvent
   public void onServerTick(ServerTickEvent.Post event) {
      WarManager.tick(event.getServer());
   }

   @SubscribeEvent
   public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         WarManager.onLogin(player);
      }
   }

   @SubscribeEvent
   public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
         return;
      }

      MinecraftServer server = player.getServer();
      WarData data = WarData.get(server);
      WarTeam team = data.teamOf(player.getUUID());
      if (team == null || !data.running || player.getRespawnPosition() != null) {
         return;
      }

      BlockPos hq = data.hqPos[team.ordinal()];
      ServerLevel level = server.getLevel(data.hqLevel(team));
      if (hq != null && level != null) {
         player.teleportTo(level, hq.getX() + 0.5, hq.getY() + 1.0, hq.getZ() + 0.5, player.getYRot(), player.getXRot());
      }
   }

   @SubscribeEvent
   public void onDeath(LivingDeathEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer victim)) {
         return;
      }

      MinecraftServer server = victim.getServer();
      WarData data = WarData.get(server);
      WarTeam victimTeam = data.teamOf(victim.getUUID());
      WarTeam killer = attackerTeam(server, event.getSource());
      if (victimTeam != null && killer != null && killer != victimTeam) {
         WarManager.reward(server, killer, "kill_player", victim.getDisplayName());
      }
   }

   /** Team of whoever caused the damage, following projectiles to their owner and DeepStrike missiles to their launcher. */
   public static WarTeam attackerTeam(MinecraftServer server, DamageSource source) {
      WarData data = WarData.get(server);
      Entity cause = source.getEntity();
      if (cause != null) {
         WarTeam team = data.teamOf(cause.getUUID());
         if (team != null) {
            return team;
         }
      }

      Entity direct = source.getDirectEntity();
      return direct == null ? null : ownerTeam(server, data, direct);
   }

   public static WarTeam ownerTeam(MinecraftServer server, WarData data, Entity entity) {
      WarTeam team = data.teamOf(entity.getUUID());
      if (team != null) {
         return team;
      }

      if (WarMode.DEEPSTRIKE) {
         java.util.UUID owner = DeepStrikeHooks.ownerOf(entity);
         if (owner != null) {
            return data.teamOf(owner);
         }
      }

      return null;
   }

   @SubscribeEvent
   public void onDetonate(ExplosionEvent.Detonate event) {
      if (!(event.getLevel() instanceof ServerLevel level) || event.getExplosion() == lastExplosion) {
         return;
      }

      MinecraftServer server = level.getServer();
      WarData data = WarData.get(server);
      if (!data.running || data.mode != GameMode.HQ) {
         return;
      }

      Explosion explosion = event.getExplosion();
      Vec3 center = explosion.center();
      float radius = explosion.radius();
      double reach = radius * 2.0 + 3.0;
      WarTeam attacker = null;
      Entity direct = explosion.getDirectSourceEntity();
      if (explosion.getIndirectSourceEntity() != null) {
         attacker = data.teamOf(explosion.getIndirectSourceEntity().getUUID());
      }

      if (attacker == null && direct != null) {
         attacker = ownerTeam(server, data, direct);
      }

      for (WarTeam team : WarTeam.values()) {
         BlockPos hq = data.hqPos[team.ordinal()];
         if (hq == null || !level.dimension().equals(data.hqLevel(team))) {
            continue;
         }

         double distance = center.distanceTo(Vec3.atCenterOf(hq));
         if (distance < reach) {
            lastExplosion = explosion;
            double damage = radius * radius * 3.0 * (1.0 - distance / reach);
            WarManager.damageHq(server, team, damage, attacker);
         }
      }
   }

   public static Component describe(Entity entity) {
      return entity.getDisplayName();
   }
}
