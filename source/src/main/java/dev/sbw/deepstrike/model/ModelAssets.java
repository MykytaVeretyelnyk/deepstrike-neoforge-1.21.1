package dev.sbw.deepstrike.model;

import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;

public final class ModelAssets {
   public static String missile(MissileKind kind) {
      return switch (kind) {
         case FP5 -> "fp5_flamingo";
         case STORM_SHADOW -> "storm_shadow";
         case JAGM -> "agm179_jagm";
         case PEACEKEEPER -> "lgm118_peacekeeper";
         case SPIKE_NLOS -> "spike_nlos_missile";
      };
   }

   public static String texture(MissileKind kind, boolean graphite) {
      return texture(kind, MissileData.defaultCamo(kind), graphite);
   }

   public static String texture(MissileKind kind, String camo, boolean graphite) {
      if (graphite && kind == MissileKind.FP5) {
         return "fp5_missile_graphite";
      } else {
         String c = camo == null ? "" : camo.trim().toLowerCase();

         return switch (kind) {
            case FP5 -> c.isEmpty() || c.equals("default")
               ? "fp5_missile_graphite"
               : (
                  c.equals("olive")
                     ? "fp5_missile_olive"
                     : (
                        c.equals("desert")
                           ? "fp5_missile_desert"
                           : (
                              c.equals("gray")
                                 ? "fp5_missile_gray"
                                 : (
                                    !c.equals("stealth") && !c.equals("black")
                                       ? (
                                          !c.equals("sky") && !c.equals("sea")
                                             ? (
                                                c.equals("pink")
                                                   ? "fp5_missile_pink"
                                                   : (c.equals("graphite") ? "fp5_missile_graphite" : "fp5_missile_graphite")
                                             )
                                             : "fp5_missile_sky"
                                       )
                                       : "fp5_missile_stealth"
                                 )
                           )
                     )
               );
            case STORM_SHADOW -> c.equals("desert")
               ? "storm_shadow_desert"
               : (
                  !c.equals("sky") && !c.equals("sea")
                     ? (!c.equals("stealth") && !c.equals("black") ? "storm_shadow" : "storm_shadow_stealth")
                     : "storm_shadow_sky"
               );
            case JAGM -> c.equals("desert")
               ? "jagm_atlas_desert"
               : (!c.equals("sea") && !c.equals("sky") ? (!c.equals("stealth") && !c.equals("black") ? "jagm_atlas" : "jagm_atlas_stealth") : "jagm_atlas_sea");
            case PEACEKEEPER -> "lgm118_peacekeeper_graphite";
            case SPIKE_NLOS -> "spike_nlos_missile";
         };
      }
   }

   public static String launcher(LauncherBlock.Family f) {
      return switch (f) {
         case JAGM -> "jagm_launcher";
         case FP5 -> "fp5_launcher";
         case PEACEKEEPER -> "lgm118_holder";
      };
   }

   public static String launcherTexture(LauncherBlock.Family f, boolean graphite) {
      return switch (f) {
         case JAGM -> "jagm_atlas";
         case FP5 -> "fp5_graphite";
         case PEACEKEEPER -> "lgm118_holder_graphite";
      };
   }
}
