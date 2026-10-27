package dev.sbw.deepstrike.game;

import net.minecraft.network.chat.Component;

public enum MissileKind {
   FP5("fp5", 0.013, 15.0F, 1, 30, 50, 90, 12, 100.0F, 200, 3000, 350, 1650, 850),
   STORM_SHADOW("storm_shadow", 0.011, 5.0F, 2, 10, 20, 30, 5, 55.0F, 50, 950, 850, 1850, 1200),
   JAGM("jagm", 0.017, 3.0F, 3, 6, 10, 10, 3, 28.0F, 50, 350, 900, 2200, 1600),
   PEACEKEEPER("peacekeeper", 0.013, 20.0F, 4, 38, 65, 110, 16, 150.0F, 100, 6000, 2000, 5500, 2400),
   SPIKE_NLOS("spike_nlos", 0.017, 3.8F, 5, 7, 12, 18, 4, 38.0F, 25, 3000, 80, 650, 450);

   public final String id;
   public final double speedFactor;
   public final float blastPower;
   public final int launcherFamily;
   public final int craterRadius;
   public final int partialDestructionRadius;
   public final int damageRadius;
   public final int craterDepth;
   public final float peakDamage;
   public final int minRange;
   public final int maxRange;
   public final int minSpeed;
   public final int maxSpeed;
   public final int defaultSpeed;

   private MissileKind(
      String id,
      double speedFactor,
      float blastPower,
      int launcherFamily,
      int craterRadius,
      int partialDestructionRadius,
      int damageRadius,
      int craterDepth,
      float peakDamage,
      int minRange,
      int maxRange,
      int minSpeed,
      int maxSpeed,
      int defaultSpeed
   ) {
      this.id = id;
      this.speedFactor = speedFactor;
      this.blastPower = blastPower;
      this.launcherFamily = launcherFamily;
      this.craterRadius = craterRadius;
      this.partialDestructionRadius = partialDestructionRadius;
      this.damageRadius = damageRadius;
      this.craterDepth = craterDepth;
      this.peakDamage = peakDamage;
      this.minRange = minRange;
      this.maxRange = maxRange;
      this.minSpeed = minSpeed;
      this.maxSpeed = maxSpeed;
      this.defaultSpeed = defaultSpeed;
   }

   public Component title() {
      return Component.translatable("item.deepstrike." + this.id);
   }

   public boolean hasRocketMotor() {
      return this == PEACEKEEPER || this == JAGM || this == SPIKE_NLOS;
   }

   public static MissileKind byId(int id) {
      return values()[Math.max(0, Math.min(values().length - 1, id))];
   }
}
