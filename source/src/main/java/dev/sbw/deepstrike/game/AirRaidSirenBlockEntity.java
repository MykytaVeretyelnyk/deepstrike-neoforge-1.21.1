package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class AirRaidSirenBlockEntity extends BlockEntity {
   private int variant;
   private int audibleRadius = 128;
   private boolean zoneOnly;
   private double zoneX;
   private double zoneZ;
   private int zoneRadius = 500;
   private boolean active;
   private long lastSoundTick = -1000L;

   public AirRaidSirenBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)BFBlocks.SIREN_BE.get(), pos, state);
      this.zoneX = pos.getX() + 0.5;
      this.zoneZ = pos.getZ() + 0.5;
   }

   public AirRaidSirenBlock.Tier tier() {
      return ((AirRaidSirenBlock)this.getBlockState().getBlock()).tier();
   }

   public int variant() {
      return this.variant;
   }

   public int audibleRadius() {
      return this.audibleRadius;
   }

   public boolean zoneOnly() {
      return this.zoneOnly;
   }

   public double zoneX() {
      return this.zoneX;
   }

   public double zoneZ() {
      return this.zoneZ;
   }

   public int zoneRadius() {
      return this.zoneRadius;
   }

   public boolean active() {
      return this.active;
   }

   public int detectionRadius() {
      return switch (this.tier()) {
         case BASIC -> 2000;
         case ADVANCED -> 5000;
         case TOWER -> 10000;
      };
   }

   public int maxAudibleRadius() {
      return this.tier() == AirRaidSirenBlock.Tier.TOWER ? 5000 : 300;
   }

   public void configure(int variant, int radius, boolean zoneOnly, double x, double z, int zoneRadius) {
      this.variant = Mth.clamp(variant, 0, 2);
      this.audibleRadius = Mth.clamp(radius, 16, this.maxAudibleRadius());
      this.zoneOnly = this.tier() == AirRaidSirenBlock.Tier.TOWER && zoneOnly;
      if (Double.isFinite(x) && Double.isFinite(z)) {
         this.zoneX = Mth.clamp(x, -3.0E7, 3.0E7);
         this.zoneZ = Mth.clamp(z, -3.0E7, 3.0E7);
      }

      this.zoneRadius = Mth.clamp(zoneRadius, 64, 5000);
      this.sync();
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, AirRaidSirenBlockEntity siren) {
      if (level instanceof ServerLevel server) {
         long now = server.getGameTime();
         if (now % 20L == Math.floorMod(pos.asLong(), 20)) {
            boolean danger = false;

            for (Entity entity : server.getAllEntities()) {
               if (entity instanceof GuidedMissileEntity missile && missile.isAlive() && !missile.isIntercepted()) {
                  double distanceSqr = siren.tier() == AirRaidSirenBlock.Tier.TOWER
                     ? (missile.getX() - siren.zoneX) * (missile.getX() - siren.zoneX)
                        + (missile.getZ() - siren.zoneZ) * (missile.getZ() - siren.zoneZ)
                     : missile.distanceToSqr(Vec3.atCenterOf(pos));
                  if (!(distanceSqr > (double)siren.detectionRadius() * siren.detectionRadius()) && siren.threatens(missile)) {
                     danger = true;
                     break;
                  }
               }
            }

            if (siren.active != danger) {
               siren.active = danger;
               siren.sync();
            }
         }

         if (siren.active && now - siren.lastSoundTick >= 70L) {
            siren.lastSoundTick = now;

            for (ServerPlayer player : server.players()) {
               if (siren.isAudibleTo(player)) {
                  player.playNotifySound(BFSounds.siren(siren.tier().ordinal(), siren.variant), SoundSource.BLOCKS, 1.0F, 1.0F);
               }
            }
         }
      }
   }

   private boolean threatens(GuidedMissileEntity missile) {
      Vec3 center = this.tier() == AirRaidSirenBlock.Tier.TOWER
         ? new Vec3(this.zoneX, 0.0, this.zoneZ)
         : new Vec3(this.worldPosition.getX() + 0.5, 0.0, this.worldPosition.getZ() + 0.5);
      double threshold = this.tier() == AirRaidSirenBlock.Tier.TOWER ? this.zoneRadius : (this.tier() == AirRaidSirenBlock.Tier.BASIC ? 320.0 : 600.0);
      Vec3 current = missile.position();
      List<RoutePoint> route = missile.getRoute();
      int start = missile.getRouteIndex();

      for (int i = start; i < route.size(); i++) {
         RoutePoint point = route.get(i);
         Vec3 next = new Vec3(point.x(), point.y(), point.z());
         if (segmentDistanceSqr(current, next, center) <= threshold * threshold) {
            return true;
         }

         current = next;
      }

      if (!route.isEmpty()) {
         return false;
      } else {
         Vec3 velocity = missile.getDeltaMovement();
         if (velocity.horizontalDistanceSqr() < 1.0E-4) {
            return false;
         } else {
            double dx = center.x - missile.getX();
            double dz = center.z - missile.getZ();
            double t = Mth.clamp((dx * velocity.x + dz * velocity.z) / velocity.horizontalDistanceSqr(), 0.0, 600.0);
            double px = missile.getX() + velocity.x * t;
            double pz = missile.getZ() + velocity.z * t;
            return (px - center.x) * (px - center.x) + (pz - center.z) * (pz - center.z) <= threshold * threshold;
         }
      }
   }

   private static double segmentDistanceSqr(Vec3 a, Vec3 b, Vec3 center) {
      double dx = b.x - a.x;
      double dz = b.z - a.z;
      double len = dx * dx + dz * dz;
      double t = len < 1.0E-4 ? 0.0 : Mth.clamp(((center.x - a.x) * dx + (center.z - a.z) * dz) / len, 0.0, 1.0);
      double x = a.x + t * dx - center.x;
      double z = a.z + t * dz - center.z;
      return x * x + z * z;
   }

   private boolean isAudibleTo(ServerPlayer player) {
      double dx = player.getX() - this.worldPosition.getX() - 0.5;
      double dz = player.getZ() - this.worldPosition.getZ() - 0.5;
      if (this.zoneOnly) {
         dx = player.getX() - this.zoneX;
         dz = player.getZ() - this.zoneZ;
         return dx * dx + dz * dz <= (double)this.zoneRadius * this.zoneRadius;
      } else {
         return dx * dx + dz * dz <= (double)this.audibleRadius * this.audibleRadius;
      }
   }

   private void sync() {
      this.setChanged();
      if (this.level instanceof ServerLevel server) {
         server.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      super.saveAdditional(tag, registries);
      tag.putInt("Variant", this.variant);
      tag.putInt("AudibleRadius", this.audibleRadius);
      tag.putBoolean("ZoneOnly", this.zoneOnly);
      tag.putDouble("ZoneX", this.zoneX);
      tag.putDouble("ZoneZ", this.zoneZ);
      tag.putInt("ZoneRadius", this.zoneRadius);
      tag.putBoolean("Active", this.active);
   }

   public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      super.loadAdditional(tag, registries);
      this.variant = Mth.clamp(tag.getInt("Variant"), 0, 2);
      this.audibleRadius = tag.contains("AudibleRadius") ? Mth.clamp(tag.getInt("AudibleRadius"), 16, this.maxAudibleRadius()) : 128;
      this.zoneOnly = tag.getBoolean("ZoneOnly");
      this.zoneX = tag.contains("ZoneX") ? tag.getDouble("ZoneX") : this.worldPosition.getX() + 0.5;
      this.zoneZ = tag.contains("ZoneZ") ? tag.getDouble("ZoneZ") : this.worldPosition.getZ() + 0.5;
      this.zoneRadius = tag.contains("ZoneRadius") ? Mth.clamp(tag.getInt("ZoneRadius"), 64, 5000) : 500;
      this.active = tag.getBoolean("Active");
   }

   public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
      return NbtCompat.saveBE(this);
   }

   public ClientboundBlockEntityDataPacket getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, net.minecraft.core.HolderLookup.Provider registries) {
      if (packet.getTag() != null) {
         this.loadAdditional(packet.getTag(), registries);
      }
   }
}
