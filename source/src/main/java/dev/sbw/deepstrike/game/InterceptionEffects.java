package dev.sbw.deepstrike.game;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public final class InterceptionEffects {
   public static final int PAC_2 = 0;
   public static final int PAC_3 = 1;
   public static final int AVENGER = 2;
   public static final int IRON_DOME = 3;

   private InterceptionEffects() {
   }

   public static void emit(ServerLevel level, Vec3 position, int defense) {
      PatriotDefenseNetwork.broadcastBurst(level, defense, position.x, position.y, position.z);
   }

   public static int defenseOf(Entity projectile, Entity attacker) {
      String direct = id(projectile);
      String owner = id(attacker);
      if (direct.contains("tamir") || owner.contains("iron_dome")) {
         return 3;
      } else if (direct.contains("pac_3")) {
         return 1;
      } else if (direct.contains("pac_2")) {
         return 0;
      } else if (!direct.contains("fim") && !direct.contains("igla") && !owner.contains("avenger")) {
         return projectile != null
               && projectile.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("superbwarfare", "aa_missile")))
            ? 2
            : -1;
      } else {
         return 2;
      }
   }

   public static boolean isManpad(Entity projectile) {
      String name = id(projectile);
      if (projectile == null) {
         return false;
      } else {
         String className = projectile.getClass().getSimpleName().toLowerCase(Locale.ROOT);
         return name.contains("igla")
            || name.contains("fim_92")
            || name.contains("fim92")
            || className.contains("igla")
            || className.contains("fim92")
            || className.contains("fim_92");
      }
   }

   // Automatic gun mounts (SBW AA vehicles and CIWS turrets) get a small per-hit chance to shoot a missile down.
   public static boolean isAutoCannon(Entity attacker) {
      if (!(attacker instanceof VehicleEntity vehicle)) {
         return false;
      } else {
         String name = id(attacker);
         VehicleType type = vehicle.getVehicleType();
         return name.contains("hpj_11") || name.contains("ciws") || type == VehicleType.AA || type == VehicleType.DEFENSE;
      }
   }

   private static String id(Entity entity) {
      return entity == null ? "" : EntityType.getKey(entity.getType()).getPath().toLowerCase();
   }
}
