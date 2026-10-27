package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import net.minecraft.util.Mth;

final class ExhaustStyles {
   static int previewVariant;
   static MissileKind previewKind;

   static int selected(MissileKind kind) {
      return previewKind == kind && previewVariant > 0 ? previewVariant : 2;
   }

   static float modelCode(MissileKind kind) {
      return kind == MissileKind.SPIKE_NLOS ? 0.0F : (kind == MissileKind.JAGM ? 0.33333334F : 0.6666667F);
   }

   static float radius(MissileKind kind, int variant) {
      float base = switch (kind) {
         case SPIKE_NLOS -> 0.135F;
         case JAGM -> 0.195F;
         case STORM_SHADOW -> 0.15F;
         case FP5 -> 0.175F;
         case PEACEKEEPER -> 1.75F;
      };
      return base * new float[]{1.0F, 0.76F, 1.08F, 0.9F, 1.17F, 0.98F}[variant];
   }

   static float length(MissileKind kind, int variant) {
      float base = switch (kind) {
         case SPIKE_NLOS -> 2.8F;
         case JAGM -> 4.3F;
         case STORM_SHADOW -> 2.2F;
         case FP5 -> 2.7F;
         case PEACEKEEPER -> 24.0F;
      };
      return base * new float[]{1.0F, 1.12F, 0.82F, 1.18F, 1.05F, 0.92F}[variant];
   }

   static float samMotorScale(int warhead) {
      return switch (warhead) {
         case 0 -> 1.0F;
         case 1 -> 0.78F;
         case 2 -> 0.88F;
         case 3 -> 0.58F;
         default -> 0.38F;
      };
   }

   static float samRadius(int warhead) {
      return switch (warhead) {
         case 0 -> 0.44F;
         case 1 -> 0.33F;
         case 2 -> 0.38F;
         case 3 -> 0.25F;
         default -> 0.17F;
      };
   }

   static float samLength(int warhead) {
      return switch (warhead) {
         case 0 -> 9.2F;
         case 1 -> 7.0F;
         case 2 -> 8.1F;
         case 3 -> 5.2F;
         default -> 3.2F;
      };
   }

   static float samLightRadius(int warhead) {
      return switch (warhead) {
         case 0 -> 23.0F;
         case 1 -> 18.5F;
         case 2 -> 20.5F;
         case 3 -> 16.0F;
         default -> 12.5F;
      };
   }

   static float samHaloRadius(int warhead) {
      return switch (warhead) {
         case 0 -> 6.4F;
         case 1 -> 5.1F;
         case 2 -> 5.7F;
         case 3 -> 4.3F;
         default -> 3.2F;
      };
   }

   static float samSmokeReach(int warhead) {
      return switch (warhead) {
         case 0 -> 8.2F;
         case 1 -> 6.4F;
         case 2 -> 7.2F;
         case 3 -> 5.1F;
         default -> 3.6F;
      };
   }

   static float launchFlashFromAge(float ageTicks, float flashTicks) {
      if (!(ageTicks < 0.0F) && !(ageTicks >= flashTicks)) {
         float t = 1.0F - ageTicks / flashTicks;
         return t * t;
      } else {
         return 0.0F;
      }
   }

   static float samBurnStage(float ageTicks) {
      if (ageTicks <= 18.0F) {
         return 0.04F;
      } else {
         return ageTicks <= 75.0F
            ? Mth.lerp((ageTicks - 18.0F) / 57.0F, 0.04F, 0.24F)
            : Mth.lerp(Mth.clamp((ageTicks - 75.0F) / 140.0F, 0.0F, 1.0F), 0.24F, 0.42F);
      }
   }

   static float burnStage(GuidedMissileEntity missile) {
      if (missile.launchBoostBurning()) {
         return 0.0F;
      } else {
         String var1 = missile.flightPhase();

         return switch (var1) {
            case "LAUNCHING" -> 0.08F;
            case "BOOST" -> 0.12F;
            case "CRUISE" -> 0.52F;
            case "TERMINAL" -> 0.7F;
            case "COAST" -> 0.92F;
            default -> 0.38F;
         };
      }
   }

   static float motorAge(MissileKind kind, float flightAge) {
      return Math.max(0.0F, flightAge - (kind == MissileKind.STORM_SHADOW ? 12 : 0));
   }

   static float ignition(float ageTicks) {
      return (float)(1.0 - Math.exp(-Math.max(0.0F, ageTicks) / 2.4));
   }

   static float motorIgnition(MissileKind kind, float ageTicks) {
      return (float)(1.0 - Math.exp(-Math.max(0.0F, ageTicks) / (kind == MissileKind.PEACEKEEPER ? 18.0F : 2.4F)));
   }

   static float motorRadius(MissileKind kind, float ageTicks) {
      return radius(kind, 2) * (0.22F + 0.78F * motorIgnition(kind, ageTicks));
   }

   static float motorLength(MissileKind kind, float ageTicks) {
      return length(kind, 2) * (0.08F + 0.92F * motorIgnition(kind, ageTicks));
   }

   static float samMotorRadius(int warhead, float ageTicks) {
      return samRadius(warhead) * (0.22F + 0.78F * ignition(ageTicks));
   }

   static float samMotorLength(int warhead, float ageTicks) {
      return samLength(warhead) * (0.08F + 0.92F * ignition(ageTicks));
   }

   static float motorBrightness(float ageTicks) {
      return 0.7F + 0.3F * ignition(ageTicks);
   }

   static float motorBrightness(MissileKind kind, float ageTicks) {
      return 0.7F + 0.3F * motorIgnition(kind, ageTicks);
   }

   private ExhaustStyles() {
   }
}
