package dev.sbw.deepstrike.registry;

import dev.sbw.deepstrike.game.MissileKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class BFSounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, "deepstrike");
   public static final DeferredHolder<SoundEvent, SoundEvent> EXPLODE_FP5 = SOUNDS.register(
      "entity.fp5.explode", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.fp5.explode"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> EXPLODE_FAR_FP5 = SOUNDS.register(
      "entity.fp5.explode_far", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.fp5.explode_far"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> EXPLODE_STORM_SHADOW = SOUNDS.register(
      "entity.storm_shadow.explode", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.storm_shadow.explode"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> EXPLODE_FAR_STORM_SHADOW = SOUNDS.register(
      "entity.storm_shadow.explode_far", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.storm_shadow.explode_far"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> EXPLODE_JAGM = SOUNDS.register(
      "entity.jagm.explode", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.jagm.explode"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> EXPLODE_FAR_JAGM = SOUNDS.register(
      "entity.jagm.explode_far", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.jagm.explode_far"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_FP5 = SOUNDS.register(
      "entity.fp5.launch", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.fp5.launch"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_STORM_SHADOW = SOUNDS.register(
      "entity.storm_shadow.launch", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.storm_shadow.launch"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_JAGM = SOUNDS.register(
      "entity.jagm.launch", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.jagm.launch"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_ECHO = SOUNDS.register(
      "entity.missile.launch_echo", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.missile.launch_echo"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> FLIGHT_LOOP = SOUNDS.register(
      "entity.missile.flight_loop", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.missile.flight_loop"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE_FLYBY = SOUNDS.register(
      "entity.missile.whistle_flyby", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.missile.whistle_flyby"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> SONIC_BOOM_SINGLE = SOUNDS.register(
      "entity.missile.sonic_boom_single", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.missile.sonic_boom_single"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> SONIC_BOOM_DOUBLE = SOUNDS.register(
      "entity.missile.sonic_boom_double", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.missile.sonic_boom_double"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> EAR_RINGING = SOUNDS.register(
      "entity.missile.ear_ringing", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.missile.ear_ringing"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> INTERCEPT_PAC3 = SOUNDS.register(
      "entity.interceptor.pac3_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.pac3_impact"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> INTERCEPT_PAC2 = SOUNDS.register(
      "entity.interceptor.pac2_burst", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.pac2_burst"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> INTERCEPT_AVENGER = SOUNDS.register(
      "entity.interceptor.avenger", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.avenger"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> INTERCEPT_IRON_DOME = SOUNDS.register(
      "entity.interceptor.iron_dome", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.iron_dome"))
   );
   private static final DeferredHolder<SoundEvent, SoundEvent> RECORDED_NEAR = recorded("near");
   private static final DeferredHolder<SoundEvent, SoundEvent> RECORDED_MID = recorded("mid");
   private static final DeferredHolder<SoundEvent, SoundEvent> RECORDED_FAR = recorded("far");
   public static final DeferredHolder<SoundEvent, SoundEvent> PATRIOT_LAUNCH = SOUNDS.register(
      "entity.interceptor.launch", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.launch"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> SAM_LAUNCH_CLOSE = SOUNDS.register(
      "entity.interceptor.launch_close", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.launch_close"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> SAM_LAUNCH_MID = SOUNDS.register(
      "entity.interceptor.launch_mid", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.launch_mid"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> SAM_LAUNCH_FAR = SOUNDS.register(
      "entity.interceptor.launch_far", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.launch_far"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_LAUNCH_MID = SOUNDS.register(
      "entity.missile.launch_mid", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.missile.launch_mid"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_LAUNCH_FAR = SOUNDS.register(
      "entity.missile.launch_far", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.missile.launch_far"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> SAM_FLIGHT_LOOP = SOUNDS.register(
      "entity.interceptor.flight_loop", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "entity.interceptor.flight_loop"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> GSH6_23_FIRE = SOUNDS.register(
      "vehicle.su24m.gsh6_23_fire", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", "vehicle.su24m.gsh6_23_fire"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent>[][] SIRENS = new DeferredHolder[3][3];

   private static DeferredHolder<SoundEvent, SoundEvent> recorded(String distance) {
      String path = "entity.interceptor.recorded_" + distance;
      return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", path)));
   }

   public static SoundEvent interception(int defense, double distance, boolean occluded) {
      return (SoundEvent)(!occluded && !(distance >= 1000.0) ? (distance >= 250.0 ? RECORDED_MID : RECORDED_NEAR) : RECORDED_FAR).get();
   }

   public static SoundEvent samLaunch(double distance, boolean occluded) {
      return (SoundEvent)(!occluded && !(distance >= 420.0) ? (distance >= 110.0 ? SAM_LAUNCH_MID : SAM_LAUNCH_CLOSE) : SAM_LAUNCH_FAR).get();
   }

   public static SoundEvent missileLaunch(MissileKind kind, double distance, boolean occluded) {
      if (occluded || distance >= 480.0) {
         return (SoundEvent)HEAVY_LAUNCH_FAR.get();
      } else {
         return distance >= 135.0 ? (SoundEvent)HEAVY_LAUNCH_MID.get() : launchForKind(kind);
      }
   }

   public static SoundEvent defenseInterceptLayer(int defense) {
      return switch (defense) {
         case 0 -> (SoundEvent)INTERCEPT_PAC2.get();
         case 1 -> (SoundEvent)INTERCEPT_PAC3.get();
         case 2, 4, 5 -> (SoundEvent)INTERCEPT_AVENGER.get();
         case 3 -> (SoundEvent)INTERCEPT_IRON_DOME.get();
         default -> (SoundEvent)INTERCEPT_PAC2.get();
      };
   }

   public static SoundEvent siren(int tier, int variant) {
      return (SoundEvent)SIRENS[Mth.clamp(tier, 0, 2)][Mth.clamp(variant, 0, 2)].get();
   }

   public static SoundEvent forKind(MissileKind kind) {
      return switch (kind) {
         case FP5, PEACEKEEPER -> (SoundEvent)EXPLODE_FP5.get();
         case STORM_SHADOW -> (SoundEvent)EXPLODE_STORM_SHADOW.get();
         case JAGM, SPIKE_NLOS -> (SoundEvent)EXPLODE_JAGM.get();
      };
   }

   public static SoundEvent farForKind(MissileKind kind) {
      return switch (kind) {
         case FP5, PEACEKEEPER -> (SoundEvent)EXPLODE_FAR_FP5.get();
         case STORM_SHADOW -> (SoundEvent)EXPLODE_FAR_STORM_SHADOW.get();
         case JAGM, SPIKE_NLOS -> (SoundEvent)EXPLODE_FAR_JAGM.get();
      };
   }

   public static SoundEvent launchForKind(MissileKind kind) {
      return switch (kind) {
         case FP5, PEACEKEEPER -> (SoundEvent)LAUNCH_FP5.get();
         case STORM_SHADOW -> (SoundEvent)LAUNCH_STORM_SHADOW.get();
         case JAGM, SPIKE_NLOS -> (SoundEvent)LAUNCH_JAGM.get();
      };
   }

   private BFSounds() {
   }

   static {
      String[] tiers = new String[]{"basic", "advanced", "tower"};

      for (int tier = 0; tier < 3; tier++) {
         for (int variant = 0; variant < 3; variant++) {
            String name = "block.siren." + tiers[tier] + "_" + (variant + 1);
            SIRENS[tier][variant] = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("deepstrike", name)));
         }
      }
   }
}
