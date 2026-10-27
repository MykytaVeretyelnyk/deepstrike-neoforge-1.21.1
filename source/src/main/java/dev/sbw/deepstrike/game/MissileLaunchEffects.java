package dev.sbw.deepstrike.game;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MissileLaunchEffects {
   private MissileLaunchEffects() {
   }

   public static void spawnLaunchPlume(Level level, Vec3 pos, Vec3 direction) {
      spawnLaunchPlume(level, pos, direction, false);
   }

   public static void spawnLaunchPlume(Level level, Vec3 pos, Vec3 launchDirection, boolean peacekeeper) {
      if (level instanceof ServerLevel server) {
         Vec3 back = launchDirection.lengthSqr() > 1.0E-6 ? launchDirection.normalize().scale(-1.0) : new Vec3(0.0, -1.0, 0.0);
         server.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
         server.sendParticles(
            ParticleTypes.LAVA,
            pos.x + back.x * 0.5,
            pos.y + back.y * 0.5,
            pos.z + back.z * 0.5,
            peacekeeper ? 10 : 6,
            0.25,
            0.25,
            0.25,
            0.15
         );
      }
   }
}
