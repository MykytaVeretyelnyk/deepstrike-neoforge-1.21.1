package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFParticles;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.event.EventHooks;

public final class MissileExplosion extends Explosion {
   private final ServerLevel world;
   private final GuidedMissileEntity missile;
   private final MissileKind kind;
   private final Vec3 center;
   private final String warhead;

   private MissileExplosion(ServerLevel world, GuidedMissileEntity missile, String warhead) {
      super(
         world,
         missile,
         missile.impactPosition().x,
         missile.impactPosition().y,
         missile.impactPosition().z,
         "none".equals(warhead)
            ? 2.0F
            : (
               "shaped_charge".equals(warhead)
                  ? missile.kind().blastPower * 0.45F
                  : (
                     "nuclear".equals(warhead)
                        ? missile.kind().blastPower * 4.5F
                        : ("thermobaric".equals(warhead) ? missile.kind().blastPower * 1.5F : missile.kind().blastPower)
                  )
            ),
         false,
         BlockInteraction.KEEP
      );
      this.world = world;
      this.missile = missile;
      this.kind = missile.kind();
      this.warhead = warhead != null ? warhead : "he";
      this.center = missile.impactPosition();
   }

   private net.minecraft.world.damagesource.DamageSource deepstrike$damageSource() {
      return this.world.damageSources().explosion(this.getDirectSourceEntity(), this.getIndirectSourceEntity());
   }

   public static void detonate(ServerLevel world, GuidedMissileEntity missile) {
      detonate(world, missile, missile.warhead());
   }

   public static void detonate(ServerLevel world, GuidedMissileEntity missile, String warhead) {
      MissileExplosion blast = new MissileExplosion(world, missile, warhead);
      if (!EventHooks.onExplosionStart(world, blast)) {
         List<BlockPos> residualFuel = BurningDebris.fuelNear(world, missile.impactPosition());
         DeepStrikeNetwork.flight(missile, false);
         DeepStrikeNetwork.blast(missile);
         blast.explode();
         blast.finalizeExplosion(false);
         if (!"none".equals(warhead) && !"graphite".equals(warhead)) {
            BurningDebris.ignite(world, missile.impactPosition(), residualFuel);
         }

         for (Player player : blast.getHitPlayers().keySet()) {
            if (player instanceof ServerPlayer sp) {
               sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            }
         }
      }
   }

   public void explode() {
      if ("graphite".equals(this.warhead)) {
         this.addGraphiteEmpEffects();
      } else if ("none".equals(this.warhead)) {
         this.addKineticImpact();
      } else if ("shaped_charge".equals(this.warhead)) {
         super.explode();
         this.addShapedChargeJet();
      } else {
         super.explode();
         this.addCircularCrater();
         this.addScatteredStructuralDamage();
         this.addGlassDamage();
         this.addOuterShockwave();
         if ("cluster".equals(this.warhead)) {
            this.addClusterSubmunitions();
         } else if ("thermobaric".equals(this.warhead)) {
            this.addThermobaricFireRing();
         } else if ("nuclear".equals(this.warhead)) {
            this.addNuclearThermalPulse();
         }
      }
   }

   private void addShapedChargeJet() {
      Vec3 direction = this.missile.getDeltaMovement().normalize();
      if (direction.lengthSqr() < 0.1) {
         direction = this.missile.getLookAngle();
      }

      for (int i = 0; i < 12; i++) {
         BlockPos p = BlockPos.containing(this.center.add(direction.scale(i * 0.8)));
         if (!this.world.hasChunkAt(p)) {
            break;
         }

         BlockState state = this.world.getBlockState(p);
         if (state.getDestroySpeed(this.world, p) < 0.0F) {
            break;
         }

         if (!state.isAir()) {
            this.getToBlow().add(p.immutable());
         }
      }

      Vec3 end = this.center.add(direction.scale(12.0));

      for (Entity e : this.world.getEntities(this.missile, new AABB(this.center, end).inflate(2.0), Entity::isAlive)) {
         Vec3 offset = e.getBoundingBox().getCenter().subtract(this.center);
         double along = offset.dot(direction);
         double lateral = offset.subtract(direction.scale(along)).length();
         if (along >= -1.5 && along <= 12.0 && lateral < 2.1) {
            e.hurt(this.deepstrike$damageSource(), 170.0F);
         }
      }
   }

   private void addKineticImpact() {
      int radius = 2;
      int cx = Mth.floor(this.center.x);
      int cz = Mth.floor(this.center.z);

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz <= radius * radius) {
               int x = cx + dx;
               int z = cz + dz;
               int surface = this.world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);

               for (int y = surface - 1; y >= Math.max(this.world.getMinBuildHeight(), surface - 2); y--) {
                  BlockPos pos = new BlockPos(x, y, z);
                  BlockState state = this.world.getBlockState(pos);
                  if (!state.isAir() && state.getDestroySpeed(this.world, pos) >= 0.0F) {
                     this.getToBlow().add(pos.immutable());
                  }
               }
            }
         }
      }

      AABB impactBox = new AABB(this.center, this.center).inflate(4.0);

      for (Entity e : this.world.getEntities(this.missile, impactBox, Entity::isAlive)) {
         e.hurt(this.deepstrike$damageSource(), 45.0F);
      }

      this.world.playSound(null, this.center.x, this.center.y, this.center.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2.5F, 0.7F);
      this.world.playSound(null, this.center.x, this.center.y, this.center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2F, 1.3F);
      this.world
         .sendParticles((SimpleParticleType)BFParticles.BLAST_SMOKE.get(), this.center.x, this.center.y, this.center.z, 3, 0.5, 0.5, 0.5, 0.05);
   }

   private void addGraphiteEmpEffects() {
      this.world.playSound(null, this.center.x, this.center.y, this.center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F, 1.8F);
      this.world.playSound(null, this.center.x, this.center.y, this.center.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2.5F, 0.8F);

      for (int i = 0; i < 60; i++) {
         double ox = (this.world.random.nextDouble() - 0.5) * 16.0;
         double oy = this.world.random.nextDouble() * 8.0;
         double oz = (this.world.random.nextDouble() - 0.5) * 16.0;
         this.world
            .sendParticles(
               (SimpleParticleType)BFParticles.SOOT_SMOKE.get(),
               this.center.x + ox,
               this.center.y + oy,
               this.center.z + oz,
               3,
               0.2,
               0.2,
               0.2,
               0.05
            );
         this.world
            .sendParticles(
               (SimpleParticleType)BFParticles.SOOT_SMOKE.get(),
               this.center.x + ox,
               this.center.y + oy,
               this.center.z + oz,
               2,
               0.1,
               0.1,
               0.1,
               0.02
            );
      }

      int radius = 64;
      int cx = Mth.floor(this.center.x);
      int cz = Mth.floor(this.center.z);
      int cy = Mth.floor(this.center.y);

      for (int dx = -radius; dx <= radius; dx += 4) {
         for (int dz = -radius; dz <= radius; dz += 4) {
            int x = cx + dx;
            int z = cz + dz;
            int surface = this.world.getHeight(Types.MOTION_BLOCKING, x, z);
            BlockPos p = new BlockPos(x, surface, z);
            if (this.world.getBlockState(p).is(Blocks.FIRE)) {
               this.world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
         }
      }
   }

   private void addCircularCrater() {
      int radius = this.kind.craterRadius;
      int depthBase = this.kind.craterDepth;
      if ("nuclear".equals(this.warhead)) {
         radius = (int)(radius * 2.1);
         depthBase *= 2;
      } else if ("penetrating".equals(this.warhead)) {
         radius = Math.max(3, (int)(radius * 0.55));
         depthBase = (int)(depthBase * 2.4);
      } else if ("thermobaric".equals(this.warhead)) {
         radius = (int)(radius * 1.35);
         depthBase = (int)(depthBase * 0.85);
      } else if ("cluster".equals(this.warhead)) {
         radius = Math.max(3, (int)(radius * 0.65));
         depthBase = Math.max(2, (int)(depthBase * 0.6));
      }

      int r2 = radius * radius;
      int cx = Mth.floor(this.center.x);
      int cz = Mth.floor(this.center.z);

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            int dist2 = dx * dx + dz * dz;
            if (dist2 <= r2) {
               int x = cx + dx;
               int z = cz + dz;
               if (this.world.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
                  int surface = this.world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                  double edge = 1.0 - Math.sqrt(dist2) / radius;
                  double separation = Math.abs(this.center.y - surface);
                  if (!(separation > radius)) {
                     int depth = (int)Math.ceil(depthBase * Math.pow(Math.max(0.0, edge), 0.72) * Math.max(0.0, 1.0 - separation / radius));

                     for (int y = surface - 1; y >= Math.max(this.world.getMinBuildHeight(), surface - depth); y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = this.world.getBlockState(pos);
                        if (!state.isAir() && !(state.getDestroySpeed(this.world, pos) < 0.0F)) {
                           this.getToBlow().add(pos.immutable());
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void addClusterSubmunitions() {
      boolean peacekeeper = this.kind == MissileKind.PEACEKEEPER;
      int count = peacekeeper ? 8 : 32;
      this.world.playSound(null, this.center.x, this.center.y, this.center.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 4.0F, 0.85F);

      for (int i = 0; i < count; i++) {
         double angle = peacekeeper ? (i + this.world.random.nextDouble() * 0.6) * Math.PI * 2.0 / count : this.world.random.nextDouble() * Math.PI * 2.0;
         double dist = peacekeeper ? 14.0 + this.world.random.nextDouble() * 26.0 : 4.0 + this.world.random.nextDouble() * 30.0;
         int sx = (int)Math.round(this.center.x + Math.cos(angle) * dist);
         int sz = (int)Math.round(this.center.z + Math.sin(angle) * dist);
         if (peacekeeper) {
            this.world.getChunk(sx >> 4, sz >> 4);
         } else if (this.world.getChunkSource().getChunkNow(sx >> 4, sz >> 4) == null) {
            continue;
         }

         int sy = this.world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
         BlockPos subPos = new BlockPos(sx, sy - 1, sz);
         int r = peacekeeper ? 3 : 0;
         int depth = peacekeeper ? 3 : 2;

         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               for (int oy = 0; oy < depth; oy++) {
                  if (dx * dx + dz * dz <= r * r) {
                     BlockPos bp = subPos.offset(dx, -oy, dz);
                     BlockState bs = this.world.getBlockState(bp);
                     if (!bs.isAir() && bs.getDestroySpeed(this.world, bp) >= 0.0F && bs.getDestroySpeed(this.world, bp) < 15.0F) {
                        this.getToBlow().add(bp.immutable());
                     }
                  }
               }
            }
         }

         int damageRadius = peacekeeper ? 4 : 2;
         AABB subBox = new AABB(sx - damageRadius, sy - 1, sz - damageRadius, sx + damageRadius, sy + damageRadius, sz + damageRadius);

         for (Entity e : this.world.getEntities(this.missile, subBox, Entity::isAlive)) {
            e.hurt(this.deepstrike$damageSource(), peacekeeper ? 32.0F : 22.0F);
         }

         this.world.sendParticles((SimpleParticleType)BFParticles.BLAST_SMOKE.get(), sx + 0.5, sy + 0.5, sz + 0.5, 1, 0.1, 0.1, 0.1, 0.05);
      }
   }

   private void addNuclearThermalPulse() {
      AABB bounds = new AABB(this.center, this.center).inflate(this.kind.damageRadius * 1.8);

      for (Entity entity : this.world.getEntities(this.missile, bounds, e -> e.isAlive() && !e.isSpectator())) {
         double distance = entity.getBoundingBox().getCenter().distanceTo(this.center);
         double radius = this.kind.damageRadius * 1.8;
         if (!(distance >= radius) && this.hasLineOfSight(entity)) {
            double falloff = 1.0 - distance / radius;
            entity.hurt(this.deepstrike$damageSource(), (float)(this.kind.peakDamage * 2.0 * falloff));
            Vec3 away = entity.position().subtract(this.center);
            if (away.lengthSqr() > 1.0E-6) {
               entity.setDeltaMovement(entity.getDeltaMovement().add(away.normalize().scale(4.0 * falloff)));
            }
         }
      }

      this.addThermobaricFireRing();
   }

   private void addThermobaricFireRing() {
      int fireRadius = (int)(this.kind.craterRadius * 1.6);
      int cx = Mth.floor(this.center.x);
      int cz = Mth.floor(this.center.z);

      for (int dx = -fireRadius; dx <= fireRadius; dx += 2) {
         for (int dz = -fireRadius; dz <= fireRadius; dz += 2) {
            if (dx * dx + dz * dz <= fireRadius * fireRadius && !(this.world.random.nextFloat() > 0.45F)) {
               int x = cx + dx;
               int z = cz + dz;
               if (this.world.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
                  int surface = this.world.getHeight(Types.MOTION_BLOCKING, x, z);
                  BlockPos firePos = new BlockPos(x, surface, z);
                  BlockState below = this.world.getBlockState(firePos.below());
                  if (this.world.getBlockState(firePos).isAir() && !below.isAir() && below.isSolid()) {
                     this.world.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                  }
               }
            }
         }
      }
   }

   private void addScatteredStructuralDamage() {
      int radius = this.kind.partialDestructionRadius;
      if ("penetrating".equals(this.warhead)) {
         radius = (int)(radius * 0.6);
      } else if ("thermobaric".equals(this.warhead)) {
         radius = (int)(radius * 1.4);
      }

      int cell = 6;

      for (int dx = -radius; dx <= radius; dx += cell) {
         for (int dz = -radius; dz <= radius; dz += cell) {
            if (dx * dx + dz * dz <= radius * radius && !(this.world.random.nextFloat() > 0.15F)) {
               int x = Mth.floor(this.center.x) + dx + this.world.random.nextInt(cell);
               int z = Mth.floor(this.center.z) + dz + this.world.random.nextInt(cell);
               double d2 = (x - this.center.x) * (x - this.center.x) + (z - this.center.z) * (z - this.center.z);
               if (!(d2 <= this.kind.craterRadius * this.kind.craterRadius) && !(d2 > radius * radius) && this.world.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
                  int roof = this.world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                  for (int attempt = 0; attempt < 18; attempt++) {
                     int y = roof + 1 + this.world.random.nextInt(33);
                     if (y < this.world.getMaxBuildHeight()) {
                        BlockPos p = new BlockPos(x, y, z);
                        BlockState state = this.world.getBlockState(p);
                        if (!state.isAir() && !(state.getDestroySpeed(this.world, p) < 0.0F) && !isGlass(state)) {
                           this.getToBlow().add(p.immutable());
                           if (this.world.random.nextBoolean()) {
                              BlockPos q = p.east();
                              BlockState adjacent = this.world.getBlockState(q);
                              if (!adjacent.isAir() && adjacent.getDestroySpeed(this.world, q) >= 0.0F && !isGlass(adjacent)) {
                                 this.getToBlow().add(q.immutable());
                              }
                           }
                           break;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void addGlassDamage() {
      int radius = this.kind.damageRadius;
      if ("thermobaric".equals(this.warhead)) {
         radius = (int)(radius * 1.6);
      }

      int r2 = radius * radius;
      int cx = Mth.floor(this.center.x);
      int cz = Mth.floor(this.center.z);
      int cy = Mth.floor(this.center.y);
      MutableBlockPos pos = new MutableBlockPos();

      for (int chunkX = cx - radius >> 4; chunkX <= cx + radius >> 4; chunkX++) {
         for (int chunkZ = cz - radius >> 4; chunkZ <= cz + radius >> 4; chunkZ++) {
            LevelChunk chunk = this.world.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk != null) {
               for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
                  LevelChunkSection section = chunk.getSections()[sectionIndex];
                  int bottom = this.world.getSectionYFromSectionIndex(sectionIndex) * 16;
                  if (bottom <= cy + radius && bottom + 15 >= cy - radius && !section.hasOnlyAir() && section.maybeHas(MissileExplosion::isGlass)) {
                     for (int localY = 0; localY < 16; localY++) {
                        for (int localZ = 0; localZ < 16; localZ++) {
                           for (int localX = 0; localX < 16; localX++) {
                              BlockState state = section.getBlockState(localX, localY, localZ);
                              if (isGlass(state)) {
                                 int x = (chunkX << 4) + localX;
                                 int y = bottom + localY;
                                 int z = (chunkZ << 4) + localZ;
                                 double dx = x - this.center.x;
                                 double dy = y - this.center.y;
                                 double dz = z - this.center.z;
                                 if (dx * dx + dy * dy + dz * dz <= r2 && this.world.random.nextFloat() < 0.7F) {
                                    this.getToBlow().add(pos.set(x, y, z).immutable());
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void addOuterShockwave() {
      double shockCore = this.kind.craterRadius;
      double shockRadius = "thermobaric".equals(this.warhead) ? this.kind.damageRadius * 1.5 : this.kind.damageRadius;
      AABB bounds = new AABB(this.center, this.center).inflate(shockRadius);
      List<Entity> entities = this.world
         .getEntities(
            this.missile,
            bounds,
            e -> e.isAlive() && !e.isSpectator() && e.distanceToSqr(this.center) > shockCore * shockCore && e.distanceToSqr(this.center) <= shockRadius * shockRadius
         );
      EventHooks.onExplosionDetonate(this.world, this, entities, shockRadius * 2.0);

      for (Entity entity : entities) {
         double dist = entity.position().distanceTo(this.center);
         if (!(dist <= shockCore) && !(dist > shockRadius) && this.hasLineOfSight(entity)) {
            double falloff = Math.max(0.0, 1.0 - dist / shockRadius);
            float damage = (float)(this.kind.peakDamage * Math.pow(falloff, 0.78));
            if ("thermobaric".equals(this.warhead)) {
               damage *= 1.4F;
            }

            if (damage > 0.0F) {
               entity.hurt(this.deepstrike$damageSource(), damage);
            }

            Vec3 away = entity.position().subtract(this.center).add(0.0, 0.25, 0.0);
            if (away.lengthSqr() < 1.0E-8) {
               away = new Vec3(0.0, 1.0, 0.0);
            }

            Vec3 impulse = away.normalize()
               .scale(Math.pow(falloff, 0.72) * (this.kind == MissileKind.FP5 ? 3.8 : (this.kind == MissileKind.STORM_SHADOW ? 2.5 : 1.35)));
            entity.setDeltaMovement(entity.getDeltaMovement().add(impulse));
            if (entity instanceof Player player && !player.isSpectator()) {
               this.getHitPlayers().put(player, impulse);
            }
         }
      }
   }

   private boolean hasLineOfSight(Entity entity) {
      Vec3 eye = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
      return this.world.clip(new ClipContext(this.center, eye, Block.COLLIDER, Fluid.NONE, this.missile)).getType() == Type.MISS;
   }

   private static boolean isGlass(BlockState state) {
      return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass");
   }

   public void finalizeExplosion(boolean particles) {
      for (BlockPos pos : new LinkedHashSet<BlockPos>(this.getToBlow())) {
         BlockState state = this.world.getBlockState(pos);
         if (!state.isAir()) {
            if (state.hasBlockEntity()) {
               if (this.world.getBlockEntity(pos) instanceof LauncherBlockEntity launcher) {
                  LauncherCollision.remove(launcher);
               }

               this.world.removeBlockEntity(pos);
            }

            this.world.setBlock(pos, Blocks.AIR.defaultBlockState(), 35);
         }
      }
   }
}
