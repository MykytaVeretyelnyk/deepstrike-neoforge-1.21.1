package dev.sbw.deepstrike.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class FlightMotion {
   public static void save(Entity entity, CompoundTag tag) {
      Vec3 velocity = entity.getDeltaMovement().scale(20.0);
      ListTag values = new ListTag();
      values.add(DoubleTag.valueOf(velocity.x));
      values.add(DoubleTag.valueOf(velocity.y));
      values.add(DoubleTag.valueOf(velocity.z));
      tag.put("FlightVelocityMps", values);
   }

   public static void restore(Entity entity, CompoundTag tag, double maximumKmh) {
      boolean si = tag.contains("FlightVelocityMps", 9);
      ListTag values = tag.getList(si ? "FlightVelocityMps" : "Motion", 6);
      if (values.size() == 3) {
         double scale = si ? 0.05 : 1.0;
         Vec3 velocity = new Vec3(values.getDouble(0), values.getDouble(1), values.getDouble(2)).scale(scale);
         double limit = FlightUnits.blocksPerTick(maximumKmh) * 1.01;
         if (Double.isFinite(velocity.lengthSqr()) && velocity.lengthSqr() <= limit * limit) {
            entity.setDeltaMovement(velocity);
         }
      }
   }

   private FlightMotion() {
   }
}
