package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import dev.sbw.deepstrike.client.smoke.SmokeFlow;
import dev.sbw.deepstrike.client.smoke.SmokePolygonClip;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.InterceptionEffects;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.game.SmokeVisibility;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.ManpadGeometry;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelGeometry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

public final class ContrailRenderer {
   private static final int MAX_NODES = 40000;
   private static final int MAX_TRAIL_NODES = 12000;
   private static final double ROCKET_WAKE_SPIRAL_PITCH = 5.0;
   private static final double ROCKET_WAKE_SPIRAL_RADIUS = 0.14;
   private static final Map<Integer, ContrailRenderer.Trail> TRAILS = new HashMap<>();
   private static final ArrayList<ContrailRenderer.JetPuff> JET_PLUME = new ArrayList<>();
   private static final ArrayList<ContrailRenderer.ColumnSource> COLUMNS = new ArrayList<>();
   private static final ArrayList<ContrailRenderer.ColumnPuff> COLUMN_PUFFS = new ArrayList<>();
   private static ClientLevel world;
   private static long tick;
   private static int nodeCount;
   private static long costPeriodStart;
   private static long costTotal;
   private static long costPeak;
   private static int costFrames;
   private static boolean reviewSmokeTiming;
   private static long reviewJetFlowNanos;
   private static long reviewJetDrawNanos;
   private static int reviewJetFlowCalls;
   private static int reviewJetDrawCalls;
   private static long jetAuditTick = -1L;

   private static Vec3 perpendicular(Vec3 tangent, Vec3 previous) {
      if (previous != null) {
         Vec3 projected = previous.subtract(tangent.scale(previous.dot(tangent)));
         if (projected.lengthSqr() > 1.0E-8) {
            return projected.normalize();
         }
      }

      return tangent.cross(Math.abs(tangent.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0)).normalize();
   }

   private static boolean ensureWorld() {
      ClientLevel current = Minecraft.getInstance().level;
      if (world != current) {
         TRAILS.clear();
         JET_PLUME.clear();
         COLUMNS.clear();
         COLUMN_PUFFS.clear();
         nodeCount = 0;
         tick = 0L;
         world = current;
         SmokeFlow.clear();
      }

      return current != null;
   }

   public static void record(GuidedMissileEntity missile) {
      if (missile != null && missile.isAlive() && missile.engineRunning()) {
         float width = switch (missile.kind()) {
            case PEACEKEEPER -> 1.38F;
            case STORM_SHADOW -> missile.launchBoostBurning() ? 3.6F : 0.308F;
            case FP5 -> missile.launchBoostBurning() ? 3.6F : 0.33F;
            case SPIKE_NLOS -> 0.187F;
            default -> 0.22F;
         };
         ContrailRenderer.Trail trail = prepare(
            missile.getId(),
            width,
            missile.kind() == MissileKind.PEACEKEEPER ? 360.0F : 280.0F,
            missile.kind().hasRocketMotor(),
            missile.kind() == MissileKind.PEACEKEEPER ? 1.68F : (missile.launchBoostBurning() ? 3.3F : 1.4F),
            0.97F,
            0.975F,
            0.98F
         );
         if (trail != null) {
            trail.source = missile;
            trail.damaged = false;
            if (missile.launchBoostBurning()) {
               trail.boosterObserved = true;
            }

            if (missile.kind() == MissileKind.STORM_SHADOW) {
               trail.expansion = missile.launchBoostBurning() ? 3.3F : 1.4F;
            }

            if (missile.kind() == MissileKind.FP5 && !missile.launchBoostBurning()) {
               trail.expansion = 0.35F;
            }
         }
      }
   }

   public static void recordDamaged(GuidedMissileEntity missile) {
      if (missile != null && missile.isAlive()) {
         ContrailRenderer.Trail trail = prepare(missile.getId() ^ Integer.MIN_VALUE, 0.3F, 220.0F, true, 1.4F, 0.97F, 0.975F, 0.98F);
         if (trail != null) {
            trail.source = missile;
            trail.damaged = true;
         }
      }
   }

   public static void recordInterceptor(PatriotInterceptorEntity entity) {
      float width = entity.warhead() == 3 ? 0.22F : (entity.warhead() == 0 ? 0.32F : 0.26F);
      ContrailRenderer.Trail trail = prepare(entity.getId(), width * 1.1F, 280.0F, true, 1.4F, 0.97F, 0.975F, 0.98F);
      if (trail != null) {
         trail.spiral = true;
         trail.interceptor = entity;
         trail.samWarhead = entity.warhead();
      }
   }

   public static void recordInterceptorFrame(int id, Vec3 position, float width, float partial) {
      if (Double.isFinite(position.lengthSqr())) {
         ContrailRenderer.Trail trail = prepare(id, width, 280.0F, true, 1.4F, 0.97F, 0.975F, 0.98F);
         if (trail != null) {
            trail.spiral = true;
            trail.emit(position, (float)tick + partial);
         }
      }
   }

   public static void recordInterceptorFrame(int id, Vec3 position, Vec3 direction, int warhead, float ageTicks, float width, float partial) {
      if (Double.isFinite(position.lengthSqr())) {
         ContrailRenderer.Trail trail = prepare(id, width, 280.0F, true, 1.4F, 0.97F, 0.975F, 0.98F);
         if (trail != null) {
            trail.spiral = true;
            trail.samWarhead = warhead;
            trail.lightUpdatedTick = tick;
            trail.lightPosition = position;
            trail.lightDirection = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : new Vec3(0.0, 1.0, 0.0);
            float flash = ExhaustStyles.launchFlashFromAge(ageTicks, warhead == 3 ? 50.0F : 65.0F);
            trail.lightScale = ExhaustStyles.samMotorScale(warhead) * (1.0F + 0.25F * flash);
            trail.lightLength = ExhaustStyles.samLength(warhead) * (1.0F + 0.28F * flash);
            trail.emit(position, (float)tick + partial);
         }
      }
   }

   static void reviewSpiralGeometry() {
      for (boolean rocket : new boolean[]{true, false}) {
         ContrailRenderer.Trail trail = new ContrailRenderer.Trail(-999, rocket, 1.0F, 0.97F, 0.975F, 0.98F);
         trail.sourceWidth = 0.2F;
         trail.emit(new Vec3(0.0, 100.0, 0.0), 0.0);
         trail.emit(new Vec3(20.0, 100.0, 0.0), 1.0);
         double maxRadius = 0.0;
         double minRadius = 100.0;
         List<Double> crossings = new ArrayList<>();
         ContrailRenderer.Sample previous = null;

         for (ContrailRenderer.Sample sample : trail.samples) {
            double y = sample.origin.y - 100.0;
            double z = sample.origin.z;
            double radius = Math.hypot(y, z);
            maxRadius = Math.max(maxRadius, radius);
            minRadius = Math.min(minRadius, radius);
            if (previous != null) {
               double before = previous.origin.y - 100.0;
               if (before * y < 0.0) {
                  crossings.add(previous.distance + (sample.distance - previous.distance) * Math.abs(before) / (Math.abs(before) + Math.abs(y)));
               }
            }

            previous = sample;
         }

         nodeCount = nodeCount - trail.samples.size();
         double pitch = 2.0 * (crossings.get(crossings.size() - 1) - crossings.get(0)) / (crossings.size() - 1);
         if (pitch < 4.0 || pitch > 6.0 || maxRadius > 0.2 || minRadius < 0.08 || maxRadius - minRadius > 0.001) {
            throw new IllegalStateException("Smoke spiral geometry out of bounds");
         }

         LoggerFactory.getLogger(ContrailRenderer.class)
            .info(
               "AIR_DEFENSE_SPIRAL_VERIFIED rocket={} pitchMetres={} radiusMetres={} samples={}", new Object[]{rocket, pitch, maxRadius, trail.samples.size()}
            );
      }
   }

   private static ContrailRenderer.Trail prepare(int id, float width, float lifetime, boolean rocket, float expansion, float red, float green, float blue) {
      if (ensureWorld() && !Minecraft.getInstance().isPaused()) {
         ContrailRenderer.Trail trail = TRAILS.computeIfAbsent(id, key -> new ContrailRenderer.Trail(key, rocket, expansion, red, green, blue));
         trail.receivedTick = tick;
         trail.sourceWidth = Math.max(0.05F, width);
         return trail;
      } else {
         return null;
      }
   }

   public static void record(int id, Vec3 position, float width, float lifetime, boolean rocket, float expansion, float red, float green, float blue) {
      record(id, null, position, width, lifetime, rocket, expansion, red, green, blue);
   }

   public static void record(
      int id, MissileKind kind, Vec3 position, float width, float lifetime, boolean rocket, float expansion, float red, float green, float blue
   ) {
      recordFrame(id, position, width, lifetime, rocket, expansion, red, green, blue, 0.0F);
   }

   public static void recordFrame(
      int id, Vec3 position, float width, float lifetime, boolean rocket, float expansion, float red, float green, float blue, float partial
   ) {
      if (Double.isFinite(position.lengthSqr())) {
         ContrailRenderer.Trail trail = prepare(id, width, lifetime, rocket, expansion, red, green, blue);
         if (trail != null) {
            trail.emit(position, (float)tick + partial);
         }
      }
   }

   public static void emitSmoke(Vec3 position, Vec3 velocity, float scale, int seed) {
      emitSmoke(position, velocity, scale, seed, false);
   }

   public static void emitSmoke(Vec3 position, Vec3 velocity, float scale, int seed, boolean soot) {
      if (soot) {
         smokeColumn(position, 0.55F, seed, false);
      } else {
         emitDetachedSmoke(position, velocity, scale, seed, 400);
      }
   }

   public static void emitBlastWave(Vec3 position, Vec3 velocity, float scale, int seed) {
      emitDetachedSmoke(position, velocity, scale, seed, 65);
   }

   public static void emitBlastDust(Vec3 position, Vec3 velocity, float scale, int seed) {
      emitDetachedSmoke(position, velocity, scale, seed, 65, 0.66F, 0.59F, 0.49F);
   }

   private static void emitDetachedSmoke(Vec3 position, Vec3 velocity, float scale, int seed, int lifetime) {
      emitDetachedSmoke(position, velocity, scale, seed, lifetime, 0.97F, 0.975F, 0.98F);
   }

   private static void emitDetachedSmoke(Vec3 position, Vec3 velocity, float scale, int seed, int lifetime, float red, float green, float blue) {
      if (ensureWorld() && Double.isFinite(position.lengthSqr())) {
         ContrailRenderer.Trail trail = new ContrailRenderer.Trail(seed, true, 0.2F, red, green, blue);
         Vec3 direction = velocity.lengthSqr() > 1.0E-8 ? velocity.normalize() : new Vec3(0.0, 1.0, 0.0);
         ContrailRenderer.Sample puff = new ContrailRenderer.Sample(
            position, direction, perpendicular(direction, null), scale * 1.45F, lifetime, seed * 0.79F, tick, 0
         );
         puff.hotJet = true;
         JET_PLUME.add(new ContrailRenderer.JetPuff(trail, puff, velocity, seed, Math.max(0.025F, scale), new SmokeFlow.Parcel(position, velocity), 0.84F));
      }
   }

   public static void emitLaunchBackblast(Vec3 position, Vec3 launchDirection, float scale, int seed) {
      if (ensureWorld() && Double.isFinite(position.lengthSqr())) {
         if (!Boolean.getBoolean("deepstrike.fp5Review") && !Boolean.getBoolean("deepstrike.installedSmokeReview")) {
            Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (camPos == null || !(position.distanceTo(camPos) >= SmokeVisibility.MAX_SMOKE_DISTANCE)) {
               Vec3 dir = launchDirection != null && launchDirection.lengthSqr() > 1.0E-6 ? launchDirection.normalize() : new Vec3(0.0, 1.0, 0.0);
               Vec3 back = dir.scale(-1.0);
               Vec3 right = perpendicular(dir, null);
               Vec3 up = dir.cross(right).normalize();
               Random rand = new Random(seed * 31L + tick);
               int jetCount = Math.max(8, Math.min(18, Math.round(11.0F * scale)));

               for (int i = 0; i < jetCount; i++) {
                  double angle = (double)i / jetCount * Math.PI * 2.0 + rand.nextDouble() * 0.35;
                  Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                  double axialSpeed = (10.0 + rand.nextDouble() * 16.0) * Math.sqrt(scale);
                  double radialSpeed = (2.2 + rand.nextDouble() * 3.8) * scale;
                  Vec3 vel = back.scale(axialSpeed).add(radial.scale(radialSpeed));
                  Vec3 spawn = SmokeFlow.constrainDrift(
                     world, position, position.add(back.scale(0.35 + rand.nextDouble() * 0.65)).add(radial.scale(0.25 * scale))
                  );
                  emitDetachedSmoke(spawn, vel, (0.36F + rand.nextFloat() * 0.28F) * scale, seed + i * 19, 180 + rand.nextInt(90));
               }
            }
         }
      }
   }

   public static void smokeColumn(Vec3 position, float scale, int seed, boolean main) {
      if (ensureWorld() && Double.isFinite(position.lengthSqr())) {
         Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         if (camPos == null || !(position.distanceTo(camPos) >= SmokeVisibility.MAX_SMOKE_DISTANCE)) {
            for (ContrailRenderer.ColumnSource source : COLUMNS) {
               if (source.main == main && source.position.distanceToSqr(position) < (main ? 64 : 9)) {
                  if (!main) {
                     source.expires = tick + 24L;
                  }

                  return;
               }
            }

            if (COLUMNS.size() < 48) {
               COLUMNS.add(new ContrailRenderer.ColumnSource(position, scale, seed, main));
            }
         }
      }
   }

   private static void tickColumns() {
      COLUMNS.removeIf(sourcex -> tick >= sourcex.expires);
      COLUMN_PUFFS.removeIf(puffx -> tick - puffx.sample.born >= puffx.sample.lifetime);

      for (ContrailRenderer.ColumnSource source : COLUMNS) {
         if (tick >= source.next) {
            source.next = tick + (source.main ? 24 : 32);
            if (COLUMN_PUFFS.size() < 768) {
               int seed = source.seed + (int)(tick - source.born) * 31;
               float progress = Mth.clamp((float)(tick - source.born) / 1800.0F, 0.0F, 1.0F);
               float gray = 0.29F + 0.24F * progress + (float)(SpringSmokeMotion.unit(seed, 9) - 0.5) * 0.07F;
               ContrailRenderer.Trail trail = new ContrailRenderer.Trail(seed, true, 0.2F, gray * 0.97F, gray, gray * 1.025F);
               ContrailRenderer.Sample puff = new ContrailRenderer.Sample(
                  source.position,
                  new Vec3(0.0, 1.0, 0.0),
                  new Vec3(1.0, 0.0, 0.0),
                  source.scale,
                  1800.0F,
                  seed * 0.79F,
                  tick,
                  (int)((tick - source.born) / (source.main ? 24 : 32))
               );
               puff.hotJet = true;
               puff.blastSmoke = true;
               puff.motionSeed = seed;
               puff.motionInitialized = true;
               double clouds = world.effects().getCloudHeight();
               if (!Double.isFinite(clouds)) {
                  clouds = source.position.y + 180.0;
               }

               double ceiling = Math.max(source.position.y + 24.0, clouds - 8.0 - source.scale * 10.0F);
               BlockHitResult roof = world.clip(
                  new ClipContext(
                     source.position,
                     new Vec3(source.position.x, ceiling, source.position.z),
                     Block.COLLIDER,
                     Fluid.NONE,
                     Minecraft.getInstance().player
                  )
               );
               if (roof.getType() != Type.MISS) {
                  ceiling = Math.max(source.position.y, roof.getLocation().y - 0.1);
               }

               COLUMN_PUFFS.add(new ContrailRenderer.ColumnPuff(trail, puff, source.scale, ceiling, source.main));
            }
         }
      }
   }

   private static void drawColumns(RenderLevelStageEvent event, VertexConsumer out, Vec3 camera) {
      double now = (float)tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);

      for (ContrailRenderer.ColumnPuff column : COLUMN_PUFFS) {
         ContrailRenderer.Sample p = column.sample;
         if (!(p.position().distanceTo(camera) >= SmokeVisibility.MAX_SMOKE_DISTANCE)) {
            float seconds = (float)((now - p.born) * 0.05);
            double riseSpeed = column.main ? 2.6 : 2.0;
            double height = Math.max(0.0, column.ceiling - p.origin.y);
            double travel = seconds * riseSpeed;
            double bend = Math.min(8.0, height);
            double rise = travel <= height - bend ? travel : height - bend * Math.exp(-(travel - height + bend) / Math.max(0.001, bend));
            double atTop = Math.max(0.0, seconds - (column.ceiling - p.origin.y) / riseSpeed);
            double sway = Math.sin(seconds * 0.12 + p.motionSeed) * Math.min(2.0, rise * 0.025);
            Vec3 destination = p.origin
               .add(-seconds * 0.04 - atTop * 0.45 + sway, rise, Math.cos(seconds * 0.1 + p.motionSeed) * Math.min(1.5, rise * 0.018));
            if (rise < 12.0) {
               destination = SmokeFlow.constrainDrift(world, p.origin, destination);
            }

            p.x = destination.x;
            p.y = destination.y;
            p.z = destination.z;
            p.ageSeconds = seconds;
            p.radius = column.scale * Math.min(10.0F, 2.4F + 1.7F * (float)Math.sqrt(seconds) + 0.25F * (float)atTop);
            p.alpha = 0.65F * Math.min(1.0F, seconds * 3.0F) * SmokeAppearance.dissipation((float)((now - p.born) / p.lifetime));
            float merged = column.main ? Mth.clamp((seconds - 4.0F) / 4.0F, 0.0F, 1.0F) : 0.0F;
            float weight = (p.strand & 1) == 0 ? 1.0F + 0.55F * merged : 1.0F - merged;
            if (weight > 0.001F) {
               drawWisp(event, out, column.trail, p, camera, event.getPoseStack().last(), weight, false);
            }
         }
      }
   }

   public static void clientTick(ClientTickEvent event) {
      if (event.phase == Phase.END && ensureWorld() && !Minecraft.getInstance().isPaused()) {
         tick++;
         tickColumns();
         if (Boolean.getBoolean("deepstrike.smokeColumnReview") && tick % 100L == 0L) {
            LoggerFactory.getLogger(ContrailRenderer.class)
               .info("SMOKE_COLUMNS tick={} sources={} puffs={} jetPuffs={}", new Object[]{tick, COLUMNS.size(), COLUMN_PUFFS.size(), JET_PLUME.size()});
         }

         JET_PLUME.removeIf(p -> tick - p.sample.born >= p.sample.lifetime);
         Iterator<ContrailRenderer.Trail> trails = TRAILS.values().iterator();

         while (trails.hasNext()) {
            ContrailRenderer.Trail trail;
            for (trail = trails.next(); !trail.samples.isEmpty() && tick - trail.samples.peekFirst().born >= trail.samples.peekFirst().lifetime; nodeCount--) {
               trail.samples.removeFirst();
            }

            ContrailRenderer.Sample retained = null;
            Iterator<ContrailRenderer.Sample> points = trail.samples.iterator();

            while (points.hasNext()) {
               ContrailRenderer.Sample point = points.next();
               float spacing = Math.min(2.4F, 0.24F + (float)Math.sqrt(Math.max(0.0, tick - point.born)) * 0.09F);
               if (tick - point.born > 20.0
                  && retained != null
                  && point != trail.samples.peekLast()
                  && retained.strand == point.strand
                  && point.distance - retained.distance < spacing
                  && point.direction.dot(retained.direction) > 0.998) {
                  points.remove();
                  nodeCount--;
               } else {
                  retained = point;
               }
            }

            if (trail.head != null && tick - trail.head.born >= trail.head.lifetime) {
               trail.head = null;
            }

            if (trail.samples.isEmpty() && trail.head == null && tick - trail.receivedTick > 4L) {
               trails.remove();
            }
         }

         while (nodeCount > 40000) {
            ContrailRenderer.Trail oldest = null;

            for (ContrailRenderer.Trail trailx : TRAILS.values()) {
               if (!trailx.samples.isEmpty() && (oldest == null || trailx.samples.peekFirst().born < oldest.samples.peekFirst().born)) {
                  oldest = trailx;
               }
            }

            if (oldest == null) {
               break;
            }

            oldest.samples.removeFirst();
            nodeCount--;
         }
      }
   }

   private static void renderNativeFlames(RenderLevelStageEvent event) {
      if (ensureWorld()) {
         BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
         Vec3 camera = event.getCamera().getPosition();

         for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof MissileProjectile missile
               && InterceptionEffects.isManpad(missile)
               && missile.isAlive()
               && !PatriotRemoteEffects.hasFlight(missile.getId())
               && !PatriotRemoteEffects.hasFlight(missile.getId())
               && !missile.isLost()
               && !missile.isLostTarget()) {
               Vec3 direction = missile.getDeltaMovement();
               if (!(direction.lengthSqr() < 1.0E-8)) {
                  float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                  Vec3 position = new Vec3(
                     Mth.lerp(partial, missile.xo, missile.getX()),
                     Mth.lerp(partial, missile.yo, missile.getY()),
                     Mth.lerp(partial, missile.zo, missile.getZ())
                  );
                  Vec3 nozzle = ManpadGeometry.nozzle(missile, position, direction).subtract(camera);
                  FlightOrientation.Angles angles = FlightOrientation.along(direction, missile.getYRot(), missile.getXRot());
                  PoseStack pose = event.getPoseStack();
                  pose.pushPose();

                  try {
                     pose.translate(nozzle.x, nozzle.y, nozzle.z);
                     pose.mulPose(ModelGeometry.flight(angles.yaw(), angles.pitch()));
                     ExhaustPlume.renderSam(pose, buffers, missile.tickCount + partial, 0.0F, 4, missile.tickCount + partial);
                  } finally {
                     pose.popPose();
                  }
               }
            }
         }

         buffers.endBatch();
      }
   }

   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         renderNativeFlames(LateWorldEffects.worldView(event));
      }

      if (event.getStage() == Stage.AFTER_LEVEL && ensureWorld()) {
         recordNativeAirDefense(event.getPartialTick().getGameTimeDeltaPartialTick(false));
         if ((!TRAILS.isEmpty() || !JET_PLUME.isEmpty() || !COLUMN_PUFFS.isEmpty())
            && MissileEffectsRenderTypes.smokeReady()
            && (!Boolean.getBoolean("deepstrike.installedSmokeReview") || !"fire".equals(System.getProperty("deepstrike.reviewEffectIsolation")))) {
            long costStart = Boolean.getBoolean("deepstrike.installedSmokeReview") ? System.nanoTime() : 0L;
            long preparedAt = 0L;
            long blastAt = 0L;
            long cloudAt = 0L;
            long detailAt = 0L;
            reviewSmokeTiming = costStart != 0L;
            if (reviewSmokeTiming) {
               reviewJetDrawNanos = 0L;
               reviewJetFlowNanos = 0L;
               reviewJetDrawCalls = 0;
               reviewJetFlowCalls = 0;
            }

            event = LateWorldEffects.worldView(event);
            Minecraft mc = Minecraft.getInstance();
            double now = (float)tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 camera = event.getCamera().getPosition();
            MissileEffectsRenderTypes.updateSmokeLighting(world.getSkyDarken(event.getPartialTick().getGameTimeDeltaPartialTick(false)), camera, event.getPartialTick().getGameTimeDeltaPartialTick(false));
            BufferSource buffers = mc.renderBuffers().bufferSource();
            RenderType material = MissileEffectsRenderTypes.RIBBON;
            VertexConsumer out = buffers.getBuffer(material);

            for (ContrailRenderer.Trail trail : TRAILS.values()) {
               GuidedMissileEntity source = trail.source;
               trail.lightActive = false;
               if (!mc.isPaused()
                  && source != null
                  && source.isAlive()
                  && tick - trail.receivedTick <= 3L
                  && (trail.damaged ? source.isIntercepted() : source.engineRunning())) {
                  Vec3 position = source.position();
                  if (RemoteFlights.get(source.getId()) != source) {
                     position = new Vec3(
                        Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(false), source.xo, source.getX()),
                        Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(false), source.yo, source.getY()),
                        Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(false), source.zo, source.getZ())
                     );
                  }

                  Vec3 direction = source.getDeltaMovement().lengthSqr() > 1.0E-6 ? source.getDeltaMovement().normalize() : source.getLookAngle();
                  float narrow = plumeTransition(trail, now);
                  Vec3 emission = position.subtract(direction.scale(MissileGeometry.tailDistance(source.kind())));
                  if (source.kind() == MissileKind.FP5) {
                     Matrix4f flightMat = MissileGeometry.flight(source.kind(), source.getYRot(), source.getXRot());
                     Vector3f axis = flightMat.transformDirection(new Vector3f(-1.0F, 0.0F, 0.0F));
                     direction = new Vec3(axis).normalize();
                     Vector3f dorsalOffset = flightMat.transformPosition(new Vector3f(3.063125F, 0.75625F, 0.0F));
                     Vec3 dorsalEmission = position.add(dorsalOffset.x, dorsalOffset.y, dorsalOffset.z);
                     if (source.fp5BoosterBurning()) {
                        Vector3f boosterOffset = flightMat.transformPosition(new Vector3f(0.75F, -0.478125F, 0.0F));
                        Vec3 boosterEmission = position.add(boosterOffset.x, boosterOffset.y, boosterOffset.z);
                        emission = boosterEmission;
                     } else {
                        emission = dorsalEmission;
                     }
                  }

                  trail.lightActive = !trail.damaged && source.engineRunning() && (source.kind().hasRocketMotor() || source.launchBoostBurning());
                  trail.lightPosition = emission;
                  trail.lightDirection = direction;
                  trail.lightScale = source.kind() == MissileKind.PEACEKEEPER
                     ? Mth.lerp(narrow, 3.25F, 1.45F)
                     : (
                        source.launchBoostBurning()
                           ? 1.0F
                           : (source.kind() == MissileKind.JAGM ? 0.44F : (source.kind() == MissileKind.SPIKE_NLOS ? 0.34F : 0.26F))
                     );
                  trail.lightLength = source.kind() == MissileKind.PEACEKEEPER
                     ? Mth.lerp(narrow, 36.0F, 18.0F)
                     : (source.launchBoostBurning() ? 8.5F : ExhaustStyles.length(source.kind(), 2) * 1.55F);
                  Vec3 lightEnd = emission.subtract(direction.scale(trail.lightLength));
                  if (trail.lightActive) {
                     BlockHitResult hit = world.clip(new ClipContext(emission, lightEnd, Block.COLLIDER, Fluid.NONE, mc.player));
                     if (hit.getType() != Type.MISS) {
                        lightEnd = hit.getLocation().add(direction.scale(0.02));
                     }
                  }

                  trail.lightGlow = trail.lightActive
                     ? new FlightLighting.Glow(
                        source.getId(), emission, source.kind() == MissileKind.PEACEKEEPER ? 64.0F : 15.0F, 1.0F, 1.0F, false, direction, lightEnd, null
                     )
                     : null;
                  if (source.kind() == MissileKind.PEACEKEEPER && narrow >= 0.999F) {
                     AABB body = MissileGeometry.canonicalBounds(source.kind());
                     trail.sourceWidth = (float)Math.max(body.getYsize(), body.getZsize()) * 0.9F;
                     trail.emit(emission, now);
                  } else if ((source.kind() != MissileKind.FP5 && source.kind() != MissileKind.STORM_SHADOW || !(narrow < 1.0F))
                     && source.kind() != MissileKind.PEACEKEEPER) {
                     trail.emit(emission, now);
                  } else {
                     emitBoosterJet(trail, emission, direction, now, narrow);
                  }
               }

               PatriotInterceptorEntity interceptor = trail.interceptor;
               if (!mc.isPaused()
                  && interceptor != null
                  && interceptor.isAlive()
                  && tick - trail.receivedTick <= 3L
                  && !PatriotRemoteEffects.hidesTracked(interceptor)) {
                  float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                  Vec3 center = new Vec3(
                     Mth.lerp(partial, interceptor.xo, interceptor.getX()),
                     Mth.lerp(partial, interceptor.yo, interceptor.getY()),
                     Mth.lerp(partial, interceptor.zo, interceptor.getZ())
                  );
                  Vec3 directionx = FlightOrientation.direction(
                     Mth.rotLerp(partial, interceptor.previousYaw, interceptor.yaw()), Mth.lerp(partial, interceptor.previousPitch, interceptor.pitch())
                  );
                  String model = InterceptorGeometry.model(interceptor.warhead());
                  double tail = ModelGeometry.get(model).tailX * InterceptorGeometry.axialScale(interceptor.warhead());
                  Vec3 emissionx = center.subtract(directionx.scale(tail));
                  int wh = interceptor.warhead();
                  float age = interceptor.tickCount + partial;
                  float stage = ExhaustStyles.samBurnStage(age);
                  float flash = ExhaustStyles.launchFlashFromAge(interceptor.tickCount, partial);
                  trail.samWarhead = wh;
                  trail.lightUpdatedTick = tick;
                  trail.lightPosition = emissionx;
                  trail.lightDirection = directionx;
                  trail.lightScale = ExhaustStyles.samSmokeReach(wh) / 4.5F * stage * (1.0F + 0.25F * flash);
                  trail.lightLength = ExhaustStyles.samLength(wh) * stage * (1.55F + 0.35F * flash);
                  trail.emit(emissionx, now);
               }

               if (!mc.isPaused() && trail.source == null && tick - trail.lightUpdatedTick <= 3L && trail.lightPosition != null && trail.lightDirection != null
                  )
                {
                  trail.lightActive = true;
                  Vec3 lightEndx = trail.lightPosition.subtract(trail.lightDirection.scale(trail.lightLength));
                  BlockHitResult hit = world.clip(new ClipContext(trail.lightPosition, lightEndx, Block.COLLIDER, Fluid.NONE, mc.player));
                  if (hit.getType() != Type.MISS) {
                     lightEndx = hit.getLocation().add(trail.lightDirection.scale(0.02));
                  }

                  trail.lightGlow = new FlightLighting.Glow(
                     trail.interceptor != null ? trail.interceptor.getId() : trail.motionSeed,
                     trail.lightPosition,
                     ExhaustStyles.samLightRadius(trail.samWarhead),
                     1.0F,
                     1.0F,
                     false,
                     trail.lightDirection,
                     lightEndx,
                     null
                  );
               }

               ArrayList<ContrailRenderer.Sample> list = trail.renderSamples;
               list.clear();
               list.addAll(trail.samples);
               if (trail.head != null && (list.isEmpty() || trail.head.distance > list.get(list.size() - 1).distance + 1.0E-5)) {
                  list.add(trail.head);
               }

               if (list.size() >= 2) {
                  for (ContrailRenderer.Sample sample : list) {
                     sample.advect(trail, now);
                  }

                  int start = 0;
                  if (trail.source == null || trail.source.kind() != MissileKind.FP5 || trail.head != null && trail.head.position().distanceToSqr(camera) > 262144.0
                     )
                   {
                     for (int end = 1; end <= list.size(); end++) {
                        if (end == list.size() || list.get(end).strand != list.get(start).strand) {
                           if (end - start >= 2) {
                              drawRibbon(event, out, trail, list, start, end, camera);
                           }

                           start = end;
                        }
                     }
                  }
               }
            }

            buffers.endBatch(material);
            preparedAt = costStart == 0L ? 0L : System.nanoTime();
            VertexConsumer denseBlast = buffers.getBuffer(MissileEffectsRenderTypes.BLAST_CORE);
            drawJetPlume(event, denseBlast, camera, true);
            buffers.endBatch(MissileEffectsRenderTypes.BLAST_CORE);
            blastAt = costStart == 0L ? 0L : System.nanoTime();
            VertexConsumer clouds = buffers.getBuffer(MissileEffectsRenderTypes.CLOUD);

            for (ContrailRenderer.Trail trail : TRAILS.values()) {
               drawWisps(event, clouds, trail, camera, false);
               if (Boolean.getBoolean("deepstrike.fp5Review")
                  && trail.source != null
                  && trail.source.kind() == MissileKind.FP5
                  && trail.source.tickCount >= 100
                  && trail.source.tickCount - trail.lastDebugAge >= 20) {
                  trail.lastDebugAge = trail.source.tickCount;
                  LoggerFactory.getLogger(ContrailRenderer.class)
                     .info(
                        "POST_BOOST_SMOKE age={} samples={} visible={} nozzle={} camera={} head={}",
                        new Object[]{
                           trail.source.tickCount,
                           trail.renderSamples.size(),
                           trail.visibleWisps.size(),
                           trail.lightPosition,
                           camera,
                           trail.head == null ? null : trail.head.position()
                        }
                     );
               }
            }

            drawJetPlume(event, clouds, camera, false);
            drawColumns(event, clouds, camera);
            buffers.endBatch(MissileEffectsRenderTypes.CLOUD);
            cloudAt = costStart == 0L ? 0L : System.nanoTime();
            VertexConsumer detail = buffers.getBuffer(MissileEffectsRenderTypes.DETAIL);

            for (ContrailRenderer.Trail trailx : TRAILS.values()) {
               drawWisps(event, detail, trailx, camera, true);
            }

            buffers.endBatch(MissileEffectsRenderTypes.DETAIL);
            detailAt = costStart == 0L ? 0L : System.nanoTime();
            if (costStart != 0L) {
               long elapsed = System.nanoTime() - costStart;
               if (elapsed >= 25000000L) {
                  LoggerFactory.getLogger(ContrailRenderer.class)
                     .info(
                        "SMOKE_PHASE tick={} puffs={} totalMs={} prepareMs={} blastMs={} cloudMs={} detailMs={} flowMs={} drawMs={} flowCalls={} drawCalls={}",
                        new Object[]{
                           tick,
                           JET_PLUME.size(),
                           elapsed / 1000000.0,
                           (preparedAt - costStart) / 1000000.0,
                           (blastAt - preparedAt) / 1000000.0,
                           (cloudAt - blastAt) / 1000000.0,
                           (detailAt - cloudAt) / 1000000.0,
                           reviewJetFlowNanos / 1000000.0,
                           reviewJetDrawNanos / 1000000.0,
                           reviewJetFlowCalls,
                           reviewJetDrawCalls
                        }
                     );
               }

               costTotal += elapsed;
               costPeak = Math.max(costPeak, elapsed);
               costFrames++;
               if (costPeriodStart == 0L) {
                  costPeriodStart = costStart;
               }

               if (costStart - costPeriodStart >= 1000000000L) {
                  LoggerFactory.getLogger(ContrailRenderer.class)
                     .info(
                        "SMOKE_RENDER_COST tick={} frames={} meanMs={} peakMs={}",
                        new Object[]{tick, costFrames, costTotal / (costFrames * 1000000.0), costPeak / 1000000.0}
                     );
                  costPeriodStart = costStart;
                  costPeak = 0L;
                  costTotal = 0L;
                  costFrames = 0;
               }
            }
         }
      }
   }

   private static void recordNativeAirDefense(float partial) {
      for (Entity entity : world.entitiesForRendering()) {
         if (entity instanceof MissileProjectile missile
            && missile.isAlive()
            && InterceptionEffects.isManpad(missile)
            && !PatriotRemoteEffects.hasFlight(missile.getId())
            && !missile.isLost()
            && !missile.isLostTarget()) {
            Vec3 movement = missile.getDeltaMovement();
            if (missile.getSyncedTick() > 0 || !(movement.lengthSqr() < 1.0E-8)) {
               Vec3 center = new Vec3(
                  Mth.lerp(partial, missile.xo, missile.getX()),
                  Mth.lerp(partial, missile.yo, missile.getY()),
                  Mth.lerp(partial, missile.zo, missile.getZ())
               );
               boolean first = !TRAILS.containsKey(missile.getId());
               Vec3 nozzle = ManpadGeometry.nozzle(missile, center, movement);
               Vec3 dir = movement.lengthSqr() > 1.0E-6 ? movement.normalize() : missile.getLookAngle();
               recordInterceptorFrame(missile.getId(), nozzle, dir, 4, missile.tickCount + partial, 0.16F, partial);
               if (first && Boolean.getBoolean("deepstrike.airDefenseReview")) {
                  LoggerFactory.getLogger(ContrailRenderer.class)
                     .info("AIR_DEFENSE_NATIVE_TRAIL kind={} id={} nozzle={}", new Object[]{missile.getClass().getSimpleName(), missile.getId(), nozzle});
               }
            }
         }
      }
   }

   static boolean reviewHasWake(int id) {
      ContrailRenderer.Trail trail = TRAILS.get(id);
      return trail != null && trail.samples.size() > 5;
   }

   static void reviewClearPreviousScene() {
      if (Boolean.getBoolean("deepstrike.reviewNativeContact")) {
         TRAILS.clear();
         JET_PLUME.clear();
         nodeCount = 0;
      }
   }

   private static void drawRibbon(
      RenderLevelStageEvent event, VertexConsumer out, ContrailRenderer.Trail trail, ArrayList<ContrailRenderer.Sample> points, int start, int end, Vec3 camera
   ) {
      Pose pose = event.getPoseStack().last();

      for (int i = start + 1; i < end; i++) {
         ContrailRenderer.Sample a = points.get(i - 1);
         ContrailRenderer.Sample b = points.get(i);
         double distA = a.position().distanceTo(camera);
         double distB = b.position().distanceTo(camera);
         if (!(distA >= SmokeVisibility.MAX_SMOKE_DISTANCE) || !(distB >= SmokeVisibility.MAX_SMOKE_DISTANCE)) {
            double radius = Math.max(a.radius, b.radius) * 1.3;
            float olderAlpha = i == start + 1 ? 0.0F : ribbonAlpha(a) * trailVisibility(trail, a, camera);
            float newerAlpha = ribbonAlpha(b) * trailVisibility(trail, b, camera);
            if (!(Math.max(olderAlpha, newerAlpha) < 0.003F)
               && visibleSmoke(
                  event,
                  camera,
                  new AABB(
                     Math.min(a.x, b.x) - radius,
                     Math.min(a.y, b.y) - radius,
                     Math.min(a.z, b.z) - radius,
                     Math.max(a.x, b.x) + radius,
                     Math.max(a.y, b.y) + radius,
                     Math.max(a.z, b.z) + radius
                  )
               )) {
               Vec3 middle = a.position().lerp(b.position(), 0.5);
               float envelope = (float)(radius + a.position().distanceTo(b.position()) * 0.5);
               if (!(SmokeFlow.safeRadius(world, middle, envelope) < envelope - 0.001F)) {
                  float olderHeat = smokeIllumination(trail, a);
                  float newerHeat = smokeIllumination(trail, b);
                  double scaleA = distA > 250.0 ? Math.max(0.3, 1.0 - 0.7 * Math.min(1.0, (distA - 250.0) / 1250.0)) : 1.0;
                  double scaleB = distB > 250.0 ? Math.max(0.3, 1.0 - 0.7 * Math.min(1.0, (distB - 250.0) / 1250.0)) : 1.0;
                  SpringSmokeTrail.Point older = new SpringSmokeTrail.Point(
                     a.position(), a.direction, (float)(a.radius * 1.1F * scaleA), a.ageSeconds, olderAlpha, a.distance * 0.56F, a.light, olderHeat
                  );
                  SpringSmokeTrail.Point newer = new SpringSmokeTrail.Point(
                     b.position(), b.direction, (float)(b.radius * 1.1F * scaleB), b.ageSeconds, newerAlpha, b.distance * 0.56F, b.light, newerHeat
                  );
                  SpringSmokeTrail.draw(out, pose, camera, older, newer, trail.red, trail.green, trail.blue, (int)(trail.phase * 100.0F));
               }
            }
         }
      }
   }

   private static float ribbonAlpha(ContrailRenderer.Sample p) {
      return p.alpha * 0.28F * SmokeAppearance.fade(p.ageSeconds / 2.0F, 0.25F);
   }

   private static void drawWisps(RenderLevelStageEvent event, VertexConsumer out, ContrailRenderer.Trail trail, Vec3 camera, boolean detail) {
      Pose pose = event.getPoseStack().last();
      if (detail) {
         for (ContrailRenderer.Sample p : trail.visibleWisps) {
            if (p.frameVertices != null) {
               for (ContrailRenderer.WispVertex vertex : p.frameVertices) {
                  emitSmokeVertex(out, pose, trail, p, vertex.position, vertex.u, vertex.v, p.frameOpacity, p.frameSeed);
               }
            }
         }
      } else {
         trail.visibleWisps.clear();
         ContrailRenderer.Sample previous = null;

         for (ContrailRenderer.Sample current : trail.renderSamples) {
            if (previous == null || previous.strand != current.strand) {
               previous = current;
            } else if (trailVisibility(trail, previous, camera) <= 0.0F && trailVisibility(trail, current, camera) <= 0.0F) {
               previous = current;
            } else {
               double span = current.distance - previous.distance;
               if (span < 1.0E-6) {
                  previous = current;
               } else {
                  double radius = Math.max(previous.radius, current.radius) * 2.8;
                  if (!visibleSmoke(
                     event,
                     camera,
                     new AABB(
                        Math.min(previous.x, current.x) - radius,
                        Math.min(previous.y, current.y) - radius,
                        Math.min(previous.z, current.z) - radius,
                        Math.max(previous.x, current.x) + radius,
                        Math.max(previous.y, current.y) + radius,
                        Math.max(previous.z, current.z) + radius
                     )
                  )) {
                     previous = current;
                  } else {
                     double baseStep = trail.source != null && trail.source.kind() == MissileKind.FP5
                        ? (trail.source.launchBoostBurning() ? 0.11 : current.width * 0.5)
                        : Math.max(0.1, current.width * 0.55);
                     double desired = Math.max(baseStep, Math.min(previous.densityRadius, current.densityRadius) * 0.55);
                     int level = Math.max(0, (int)Math.floor(Math.log(desired / baseStep) / Math.log(2.0)));
                     double step = baseStep * Math.scalb(1.0, level);
                     long first = (long)Math.floor(previous.distance / step) + 1L;
                     long last = (long)Math.floor(current.distance / step);

                     for (long index = first; index <= last; index++) {
                        double distance = index * step;
                        float fraction = Mth.clamp((float)((distance - previous.distance) / span), 0.0F, 1.0F);
                        Vec3 direction = previous.direction.lerp(current.direction, fraction).normalize();
                        ContrailRenderer.Sample px = new ContrailRenderer.Sample(
                           previous.origin.lerp(current.origin, fraction),
                           direction,
                           previous.across,
                           Mth.lerp(fraction, previous.width, current.width),
                           current.lifetime,
                           (float)distance,
                           Mth.lerp(fraction, previous.born, current.born),
                           current.strand,
                           current.light
                        );
                        px.straightWake = previous.straightWake && current.straightWake;
                        px.advect(trail, (float)tick + event.getPartialTick().getGameTimeDeltaPartialTick(false));
                        double ratio = Mth.clamp(Math.max(baseStep, px.densityRadius * 0.55) / step, 1.0, 2.0);
                        px.renderWeight = (float)((index & 1L) == 0L ? 1.0 : (2.0 - ratio) / ratio);
                        if (!(px.renderWeight < 0.002F)) {
                           px.frameVertices = new ArrayList<>(4);
                           trail.visibleWisps.add(px);
                           drawWisp(event, out, trail, px, camera, pose, px.renderWeight, false);
                        }
                     }

                     previous = current;
                  }
               }
            }
         }
      }
   }

   private static float trailVisibility(ContrailRenderer.Trail trail, ContrailRenderer.Sample p, Vec3 camera) {
      if (Double.isNaN(trail.terrainReference)) {
         ContrailRenderer.Sample first = trail.samples.peekFirst();
         trail.terrainReference = trail.source != null && trail.source.tickCount > 60
            ? world.getSeaLevel()
            : (first == null ? p.origin.y : first.origin.y);
      }

      double dist = p.position().distanceTo(camera);
      return dist >= SmokeVisibility.MAX_SMOKE_DISTANCE ? 0.0F : MissileVisibility.trailOpacity(p.position(), dist, trail.terrainReference);
   }

   private static void drawWisp(
      RenderLevelStageEvent event,
      VertexConsumer out,
      ContrailRenderer.Trail trail,
      ContrailRenderer.Sample p,
      Vec3 camera,
      Pose pose,
      float weight,
      boolean detail
   ) {
      boolean freshCore = p.straightWake
         || trail.source != null
            && (trail.source.kind() == MissileKind.FP5 || trail.source.kind() == MissileKind.STORM_SHADOW)
            && !trail.source.launchBoostBurning();
      if (!(p.alpha < 1.0E-4) && (p.hotJet || freshCore || !(p.ageSeconds < 0.025F))) {
         Vec3 center = p.position();
         double viewingDistance = center.distanceTo(camera);
         if (!(viewingDistance >= SmokeVisibility.MAX_SMOKE_DISTANCE)) {
            float visibility = trailVisibility(trail, p, camera);
            if (!(visibility <= 0.0F)) {
               double distScale = SmokeVisibility.distanceScale(viewingDistance);

               float renderRadius = (float)(p.radius * distScale);
               if (!(renderRadius < 0.001F)) {
                  if (visibleSmoke(event, camera, new AABB(center, center).inflate(renderRadius * 2.8))) {
                     p.lightScatter = smokeIllumination(trail, p);
                     Vec3 view = camera.subtract(center).normalize();
                     Vec3 axis = p.direction.subtract(view.scale(p.direction.dot(view)));
                     if (axis.lengthSqr() < 1.0E-5) {
                        axis = perpendicular(view, p.up);
                     }

                     axis = axis.normalize();
                     Vec3 side = axis.cross(view).normalize();
                     double turn = SpringSmokeMotion.angle(p.motionSeed, p.ageSeconds);
                     Vec3 along = axis.scale(Math.cos(turn)).add(side.scale(Math.sin(turn)));
                     Vec3 across = along.cross(view).normalize();
                     boolean pkActive = trail.source != null && trail.source.kind() == MissileKind.PEACEKEEPER && trail.lightActive;
                     float ageRamp = pkActive
                        ? Math.min(1.0F, 0.26F + p.ageSeconds * 3.2F)
                        : Math.min(1.0F, p.ageSeconds * (!p.hotJet && !freshCore ? 12 : 160));
                     float opacity = p.alpha * 0.81F * weight * (p.straightWake ? 1.0F : ageRamp);
                     if (p.hotJet || trail.source != null && trail.source.kind() == MissileKind.FP5 && p.width >= 1.0F) {
                        opacity = Math.min(pkActive ? 0.88F : 0.97F, opacity * (pkActive ? 1.25F : 1.55F));
                     }

                     opacity *= visibility;
                     double lobeScale = 0.7 + 0.44 * SpringSmokeMotion.unit(p.motionSeed, 5);
                     Vec3 horizontal = along.scale(renderRadius * lobeScale * 1.15);
                     Vec3 vertical = across.scale(renderRadius * lobeScale);
                     Vec3 extent = new Vec3(
                        Math.abs(horizontal.x) + Math.abs(vertical.x),
                        Math.abs(horizontal.y) + Math.abs(vertical.y),
                        Math.abs(horizontal.z) + Math.abs(vertical.z)
                     );
                     double collisionRadius = p.hotJet && p.lifetime >= 180.0F && renderRadius > 3.25 ? 3.25 : renderRadius;
                     Vec3 physicalExtent = extent.scale(Math.min(1.0, collisionRadius / renderRadius));
                     AABB requiredBounds = new AABB(center.subtract(physicalExtent), center.add(physicalExtent)).inflate(0.00390625);
                     if (p.obstacles == null
                        || tick - p.obstacleTick >= 4L
                        || !p.obstacleBounds.contains(requiredBounds.minX, requiredBounds.minY, requiredBounds.minZ)
                        || !p.obstacleBounds.contains(requiredBounds.maxX, requiredBounds.maxY, requiredBounds.maxZ)) {
                        p.obstacleBounds = requiredBounds.inflate(2.0);
                        p.obstacles = SmokeFlow.obstacles(world, p.obstacleBounds);
                        p.clippedObstacles = p.obstacles.size() < 2 ? p.obstacles : SmokeFlow.mergeFullBoxes(p.obstacles);
                        p.cachedLaunchFragments = null;
                        p.obstacleTick = tick;
                     }

                     List<AABB> solids = p.obstacles;

                     for (AABB solid : solids) {
                        if (solid.contains(center)) {
                           return;
                        }
                     }

                     if (solids.isEmpty()) {
                        int seed = (int)(p.distance * 37.0F + trail.phase * 100.0F) & 32767;
                        Vec3 relative = center.subtract(camera);

                        for (int k = 0; k < 4; k++) {
                           float u = k < 2 ? -1.0F : 1.0F;
                           float v = k != 0 && k != 3 ? 1.0F : -1.0F;
                           smokeVertex(out, pose, trail, p, relative.add(horizontal.scale(u)).add(vertical.scale(v)), u, v, opacity, seed);
                        }
                     } else {
                        drawPlanarWisp(out, pose, trail, p, center, camera, horizontal, vertical, opacity, p.clippedObstacles, p.hotJet && p.lifetime >= 180.0F);
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean drawPlanarWisp(
      VertexConsumer out,
      Pose pose,
      ContrailRenderer.Trail trail,
      ContrailRenderer.Sample p,
      Vec3 center,
      Vec3 camera,
      Vec3 horizontal,
      Vec3 vertical,
      float opacity,
      List<AABB> solids,
      boolean denseLaunchSmoke
   ) {
      List<Vec3> quad = new ArrayList<>(4);

      for (int k = 0; k < 4; k++) {
         quad.add(center.add(horizontal.scale(k < 2 ? -1.0 : 1.0)).add(vertical.scale(k != 0 && k != 3 ? 1.0 : -1.0)));
      }

      if (denseLaunchSmoke
         && p.cachedLaunchFragments != null
         && p.cachedLaunchObstacleTick == p.obstacleTick
         && p.ageSeconds - p.cachedLaunchFragmentsAge < 0.033333335F) {
         for (List<ContrailRenderer.WispVertex> polygon : p.cachedLaunchFragments) {
            drawFreeWispPolygon(out, pose, trail, p, polygon, camera, opacity);
         }

         return true;
      } else {
         List<List<Vec3>> pieces = SmokePolygonClip.freeAir(quad, center, solids, true, !denseLaunchSmoke);
         List<List<ContrailRenderer.WispVertex>> cached = denseLaunchSmoke ? new ArrayList<>(pieces.size()) : null;

         for (List<Vec3> points : pieces) {
            List<ContrailRenderer.WispVertex> polygon = new ArrayList<>(points.size());

            for (Vec3 point : points) {
               Vec3 delta = point.subtract(center);
               float edge = 1.0F;
               boolean original = false;

               for (Vec3 corner : quad) {
                  if (corner.distanceToSqr(point) < 1.0E-12) {
                     original = true;
                     break;
                  }
               }

               if (!original) {
                  edge = 0.0F;
               }

               polygon.add(
                  new ContrailRenderer.WispVertex(
                     point, (float)(delta.dot(horizontal) / horizontal.lengthSqr()), (float)(delta.dot(vertical) / vertical.lengthSqr()), edge
                  )
               );
            }

            if (Boolean.getBoolean("deepstrike.fp5Review") && !Boolean.getBoolean("deepstrike.reviewSkipSmokeAudit")) {
               AABB renderedBounds = wispBounds(polygon).inflate(1.0E-5);

               for (AABB box : solids) {
                  if (box.intersects(renderedBounds) && SmokePolygonClip.intersects(points, box)) {
                     throw new IllegalStateException("Smoke fragment intersects terrain: " + renderedBounds + " / " + box);
                  }
               }
            }

            drawFreeWispPolygon(out, pose, trail, p, polygon, camera, opacity);
            if (cached != null) {
               cached.add(List.copyOf(polygon));
            }
         }

         if (cached != null) {
            p.cachedLaunchFragments = cached;
            p.cachedLaunchFragmentsAge = p.ageSeconds;
            p.cachedLaunchObstacleTick = p.obstacleTick;
         }

         return true;
      }
   }

   private static void drawFreeWispPolygon(
      VertexConsumer out,
      Pose pose,
      ContrailRenderer.Trail trail,
      ContrailRenderer.Sample p,
      List<ContrailRenderer.WispVertex> polygon,
      Vec3 camera,
      float opacity
   ) {
      int seed = (int)(p.distance * 37.0F + trail.phase * 100.0F) & 32767;
      emitWispPolygon(out, pose, trail, p, polygon, camera, opacity, seed);
   }

   private static void emitWispPolygon(
      VertexConsumer out,
      Pose pose,
      ContrailRenderer.Trail trail,
      ContrailRenderer.Sample p,
      List<ContrailRenderer.WispVertex> polygon,
      Vec3 camera,
      float opacity,
      int seed
   ) {
      if (polygon.size() == 4) {
         for (ContrailRenderer.WispVertex vertex : polygon) {
            smokeVertex(out, pose, trail, p, vertex.position.subtract(camera), vertex.u, vertex.v, opacity, seed);
         }
      } else {
         for (int i = 1; i < polygon.size() - 1; i++) {
            for (int index : new int[]{0, i, i + 1, i + 1}) {
               ContrailRenderer.WispVertex vertex = polygon.get(index);
               smokeVertex(out, pose, trail, p, vertex.position.subtract(camera), vertex.u, vertex.v, opacity, seed);
            }
         }
      }
   }

   private static AABB wispBounds(List<ContrailRenderer.WispVertex> vertices) {
      Vec3 first = vertices.get(0).position;
      double x0 = first.x;
      double y0 = first.y;
      double z0 = first.z;
      double x1 = x0;
      double y1 = y0;
      double z1 = z0;

      for (ContrailRenderer.WispVertex vertex : vertices) {
         Vec3 v = vertex.position;
         x0 = Math.min(x0, v.x);
         y0 = Math.min(y0, v.y);
         z0 = Math.min(z0, v.z);
         x1 = Math.max(x1, v.x);
         y1 = Math.max(y1, v.y);
         z1 = Math.max(z1, v.z);
      }

      return new AABB(x0, y0, z0, x1, y1, z1);
   }

   private static double coordinate(Vec3 p, int axis) {
      return axis == 0 ? p.x : (axis == 1 ? p.y : p.z);
   }

   private static double minimum(AABB b, int axis) {
      return axis == 0 ? b.minX : (axis == 1 ? b.minY : b.minZ);
   }

   private static double maximum(AABB b, int axis) {
      return axis == 0 ? b.maxX : (axis == 1 ? b.maxY : b.maxZ);
   }

   private static float plumeTransition(ContrailRenderer.Trail trail, double now) {
      if (trail.source.kind() == MissileKind.FP5 && !trail.source.fp5BoosterBurning()) {
         return 1.0F;
      } else if (trail.source.kind() == MissileKind.PEACEKEEPER) {
         return trail.source.peacekeeperNarrowPlume((float)(now - tick));
      } else if (trail.source.launchBoostBurning()) {
         trail.boosterObserved = true;
         trail.boosterFadeStart = Double.NaN;
         return 0.0F;
      } else if (!trail.boosterObserved) {
         return 1.0F;
      } else {
         if (Double.isNaN(trail.boosterFadeStart)) {
            trail.boosterFadeStart = now;
         }

         float t = Mth.clamp((float)(now - trail.boosterFadeStart) / 30.0F, 0.0F, 1.0F);
         float blend = t * t * (3.0F - 2.0F * t);
         int bucket = (int)(t * 10.0F);
         if (Boolean.getBoolean("deepstrike.fp5Review") && bucket != trail.lastBlendBucket) {
            trail.lastBlendBucket = bucket;
            LoggerFactory.getLogger(ContrailRenderer.class)
               .info(
                  "SMOKE_TRANSITION kind={} elapsedTicks={} blend={} narrowWidth={}",
                  new Object[]{trail.source.kind(), now - trail.boosterFadeStart, blend, trail.sourceWidth}
               );
         }

         return blend;
      }
   }

   private static void emitBoosterJet(ContrailRenderer.Trail trail, Vec3 nozzle, Vec3 direction, double now, float narrow) {
      if (!trail.source.exhaustSmokeReady()) {
         trail.jetPosition = nozzle;
         trail.jetDirection = direction;
         trail.jetTime = now;
         trail.nextJetTime = now;
      } else {
         if (trail.jetPosition == null || trail.jetPosition.distanceToSqr(nozzle) > 65536.0) {
            trail.jetPosition = nozzle;
            trail.jetDirection = direction;
            trail.jetTime = now;
            trail.nextJetTime = now;
         }

         float narrowWidth = trail.sourceWidth;
         if (trail.source.kind() == MissileKind.PEACEKEEPER) {
            AABB body = MissileGeometry.canonicalBounds(trail.source.kind());
            narrowWidth = (float)Math.max(body.getYsize(), body.getZsize()) * 0.9F;
         }

         float scale = Mth.lerp(narrow, trail.source.kind() == MissileKind.PEACEKEEPER ? 3.4F : 1.0F, narrowWidth * 0.594F / 1.9F);
         float opacity = 0.84F * Mth.lerp(narrow, 1.0F, 0.72196627F);
         double elapsed = now - trail.jetTime;
         Vec3 carrier = trail.source.getDeltaMovement().scale(20.0);
         double stepSpeed = Math.max(carrier.length() / 20.0, elapsed > 1.0E-6 ? nozzle.distanceTo(trail.jetPosition) / elapsed : 0.0);
         double interval = Math.max(0.04, Math.min(0.6, 1.3 * scale / (stepSpeed + 1.9 * scale)));
         if (trail.source.kind() == MissileKind.PEACEKEEPER) {
            interval *= 1.12;
         }

         for (int emitted = 0; trail.nextJetTime <= now && emitted++ < 1024; trail.nextJetTime += interval) {
            double fraction = elapsed > 1.0E-6 ? Mth.clamp((trail.nextJetTime - trail.jetTime) / elapsed, 0.0, 1.0) : 1.0;
            Vec3 center = trail.jetPosition.lerp(nozzle, fraction);
            Vec3 forward = trail.jetDirection.lerp(direction, fraction).normalize();
            Vec3 across = perpendicular(forward, null);
            Vec3 up = forward.cross(across).normalize();
            int sequence = trail.jetSequence++;
            int seed = SpringSmokeMotion.seed(sequence * 0.79F, trail.motionSeed, 17);
            double rotation = SpringSmokeMotion.unit(seed, 3) * Math.PI * 2.0;
            boolean buriedSource = false;

            for (AABB solid : SmokeFlow.obstacles(world, new AABB(center, center).inflate(0.00390625))) {
               if (solid.contains(center)) {
                  if (trail.source.launchBoostBurning() && trail.source.tickCount < 60) {
                     Vec3[] exits = new Vec3[]{
                        new Vec3(solid.minX - 0.00390625, center.y, center.z),
                        new Vec3(solid.maxX + 0.00390625, center.y, center.z),
                        new Vec3(center.x, solid.maxY + 0.00390625, center.z),
                        new Vec3(center.x, center.y, solid.minZ - 0.00390625),
                        new Vec3(center.x, center.y, solid.maxZ + 0.00390625)
                     };
                     Vec3 closest = null;
                     double distance = Double.POSITIVE_INFINITY;

                     for (Vec3 exit : exits) {
                        boolean free = true;

                        for (AABB other : SmokeFlow.obstacles(world, new AABB(exit, exit).inflate(0.00390625))) {
                           if (other.contains(exit)) {
                              free = false;
                              break;
                           }
                        }

                        if (free && exit.distanceToSqr(center) < distance) {
                           closest = exit;
                           distance = exit.distanceToSqr(center);
                        }
                     }

                     if (closest != null && distance <= 9.0) {
                        center = closest;
                        continue;
                     }
                  }

                  buriedSource = true;
                  break;
               }
            }

            for (int lobe = 0; !buriedSource && lobe < 2; lobe++) {
               double angle = rotation + lobe * Math.PI;
               Vec3 radial = across.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
               Vec3 origin = SmokeFlow.constrainDrift(world, center, center.add(radial.scale(0.6 * scale)));
               Vec3 velocity = carrier.scale(0.12)
                  .subtract(forward.scale((34.0 + 6.0 * SpringSmokeMotion.unit(seed, lobe + 5)) * Math.sqrt(scale)))
                  .add(radial.scale((1.6 + 1.3 * SpringSmokeMotion.unit(seed, lobe + 9)) * scale))
                  .scale(1.0F - narrow);
               ContrailRenderer.Sample puff = new ContrailRenderer.Sample(
                  origin, forward, across, 1.45F * scale, trail.sourceLifetime, sequence * 0.79F + lobe * 47.1F, trail.nextJetTime, 0
               );
               puff.hotJet = true;
               JET_PLUME.add(new ContrailRenderer.JetPuff(trail, puff, velocity, sequence, scale, new SmokeFlow.Parcel(origin, velocity), opacity));
               if (!trail.onsetLogged && Boolean.getBoolean("deepstrike.fp5Review")) {
                  trail.onsetLogged = true;
                  LoggerFactory.getLogger(ContrailRenderer.class)
                     .info("SMOKE_ONSET kind={} flightAge={} position={}", new Object[]{trail.source.kind(), trail.source.tickCount, origin});
               }
            }
         }

         trail.jetPosition = nozzle;
         trail.jetDirection = direction;
         trail.jetTime = now;
      }
   }

   private static void drawJetPlume(RenderLevelStageEvent event, VertexConsumer out, Vec3 camera, boolean dense) {
      double now = (float)tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);

      for (ContrailRenderer.JetPuff jet : JET_PLUME) {
         ContrailRenderer.Sample p = jet.sample;
         if ((!dense || p.lifetime == 65.0F) && (!(p.origin.distanceTo(camera) >= SmokeVisibility.MAX_SMOKE_DISTANCE) || !(p.position().distanceTo(camera) >= SmokeVisibility.MAX_SMOKE_DISTANCE))) {
            float ageSeconds = Math.max(0.0F, (float)(now - p.born) * 0.05F);
            float consolidation = Mth.clamp((ageSeconds - 0.8F) / 1.25F, 0.0F, 1.0F);
            consolidation = consolidation * consolidation * (3.0F - 2.0F * consolidation);
            float weight = p.lifetime == 65.0F ? 1.0F : (jet.sequence % 3 == 0 ? 1.0F + 1.1F * consolidation : 0.55F * (1.0F - consolidation));
            if (!(weight < 1.0E-4F)) {
               double maximumRadius = jet.scale * (1.9 + 2.2 * Math.sqrt(p.lifetime * 0.05));
               AABB sweptEnvelope = new AABB(p.origin, p.origin.add(jet.velocity.scale(0.5555555555555556))).inflate(80.0 + maximumRadius * 1.75);
               if (visibleSmoke(event, camera, sweptEnvelope)) {
                  p.advect(jet.trail, now);
                  float seconds = p.ageSeconds;
                  float life = (float)(now - p.born) / p.lifetime;
                  float fade = p.lifetime == 65.0F ? SmokeAppearance.fade(life, 0.4F) : SmokeAppearance.dissipation(life);
                  if (!(jet.opacity * fade < 1.0E-4F)) {
                     Vec3 center;
                     if (p.lifetime == 65.0F) {
                        center = p.origin.add(jet.velocity.scale(seconds * 0.65)).add(0.0, seconds * 0.25, 0.0);
                     } else {
                        if (seconds - jet.flow.seconds >= 0.03333333333333333) {
                           if (reviewSmokeTiming) {
                              long started = System.nanoTime();
                              SmokeFlow.advance(world, jet.flow, seconds, p.motionSeed);
                              reviewJetFlowNanos = reviewJetFlowNanos + (System.nanoTime() - started);
                              reviewJetFlowCalls++;
                           } else {
                              SmokeFlow.advance(world, jet.flow, seconds, p.motionSeed);
                           }
                        }

                        center = jet.flow.position;
                     }

                     p.x = center.x;
                     p.y = center.y;
                     p.z = center.z;
                     p.radius = jet.scale * (1.9F + (p.lifetime == 65.0F ? 0.65F : 2.2F) * (float)Math.sqrt(seconds));
                     p.alpha = jet.opacity * fade;
                     if (weight > 0.002F) {
                        if (reviewSmokeTiming) {
                           long started = System.nanoTime();
                           drawWisp(event, out, jet.trail, p, camera, event.getPoseStack().last(), weight, false);
                           reviewJetDrawNanos = reviewJetDrawNanos + (System.nanoTime() - started);
                           reviewJetDrawCalls++;
                        } else {
                           drawWisp(event, out, jet.trail, p, camera, event.getPoseStack().last(), weight, false);
                        }
                     }

                     if (Boolean.getBoolean("deepstrike.fp5FlowReview") && jet.sequence == 30 && p.distance < 24.7F) {
                        auditJet(jet, now);
                     }
                  }
               }
            }
         }
      }
   }

   static SmokeFlow.Parcel reviewApertureSmoke(Vec3 origin, Vec3 velocity, int sequence) {
      if (Boolean.getBoolean("deepstrike.apertureReview") && ensureWorld()) {
         ContrailRenderer.Trail trail = TRAILS.computeIfAbsent(-777, id -> new ContrailRenderer.Trail(id, true, 0.2F, 0.97F, 0.975F, 0.98F));
         trail.receivedTick = tick;
         ContrailRenderer.Sample puff = new ContrailRenderer.Sample(
            origin, new Vec3(0.0, 0.0, 1.0), new Vec3(1.0, 0.0, 0.0), 0.2F, 200.0F, sequence * 0.79F, tick, 0
         );
         puff.hotJet = true;
         SmokeFlow.Parcel flow = new SmokeFlow.Parcel(origin, velocity);
         JET_PLUME.add(new ContrailRenderer.JetPuff(trail, puff, velocity, sequence, 0.15F, flow, 0.84F));
         return flow;
      } else {
         return null;
      }
   }

   static Vec3 flowReviewAnchor(int missileId) {
      ContrailRenderer.Trail trail = TRAILS.get(missileId);
      return trail != null && trail.jetPosition != null ? trail.jetPosition.subtract(trail.jetDirection.scale(18.0)) : null;
   }

   private static void auditJet(ContrailRenderer.JetPuff jet, double now) {
      if (tick % 10L == 0L && jetAuditTick != tick) {
         jetAuditTick = tick;
         ContrailRenderer.Sample p = jet.sample;
         Vec3 rocket = jet.trail.source.position();
         LoggerFactory.getLogger(ContrailRenderer.class)
            .info(
               "FP5_FLOW_PUFF born={} age={} origin={} position={} radius={} missile={}",
               new Object[]{p.born, p.ageSeconds, p.origin, p.position(), p.radius, rocket}
            );
      }
   }

   private static float smokeIllumination(ContrailRenderer.Trail trail, ContrailRenderer.Sample p) {
      float illumination = 0.0F;
      if (trail.lightActive && trail.lightPosition != null) {
         double rawDx = p.x - trail.lightPosition.x;
         double rawDy = p.y - trail.lightPosition.y;
         double rawDz = p.z - trail.lightPosition.z;
         double behind = -(rawDx * trail.lightDirection.x + rawDy * trail.lightDirection.y + rawDz * trail.lightDirection.z);
         double axial = Math.max(0.0, Math.min((double)trail.lightLength, behind));
         double dx = rawDx + trail.lightDirection.x * axial;
         double dy = rawDy + trail.lightDirection.y * axial;
         double dz = rawDz + trail.lightDirection.z * axial;
         double reach = (trail.source != null && trail.source.kind() == MissileKind.PEACEKEEPER ? 6.8 : 4.8) * trail.lightScale;
         double distance2 = dx * dx + dy * dy + dz * dz;
         if (trail.lightGlow != null && distance2 > trail.lightGlow.radius() * trail.lightGlow.radius()) {
            return 0.0F;
         }

         float base = (float)(1.0 / (1.0 + distance2 / (reach * reach)));
         double amcwReach = Math.max(0.85, reach * 1.28);
         double invQuartic = 1.0 / (1.0 + distance2 / (amcwReach * amcwReach));
         float amcwFalloff = (float)(invQuartic * invQuartic);
         float jetFrac = trail.lightLength > 0.001F ? (float)Mth.clamp(behind / (trail.lightLength * 1.15), 0.0, 1.0) : 1.0F;
         double jetCone = Math.max(0.32, 0.75 * trail.lightScale * (0.55 + 0.95 * jetFrac));
         float jetCore = behind >= -0.35 && behind <= trail.lightLength * 1.18
            ? (float)(Math.exp(-distance2 / (jetCone * jetCone)) * (1.0 - 0.55 * jetFrac))
            : 0.0F;
         illumination = Mth.clamp(Math.max(base, amcwFalloff * 0.95F) + jetCore * 0.52F, 0.0F, 1.0F);
      }

      if (illumination > 0.005F && trail.lightGlow != null) {
         illumination *= FlightLighting.smokeTransmission(trail.lightGlow, p.position());
      }

      return illumination;
   }

   private static Vec3 smokeProjection(Vec3 relative) {
      double far = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.72;
      double distance = relative.length();
      return distance > far ? relative.scale(far / distance) : relative;
   }

   private static boolean visibleSmoke(RenderLevelStageEvent event, Vec3 camera, AABB bounds) {
      double far = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.72;
      if (bounds.getCenter().distanceToSqr(camera) <= far * far) {
         return event.getFrustum().isVisible(bounds);
      } else {
         Vec3 centre = bounds.getCenter();
         double distance = centre.distanceTo(camera);
         Vec3 projected = camera.add(smokeProjection(centre.subtract(camera)));
         double radius = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) * 0.6 * far / distance;
         return event.getFrustum().isVisible(new AABB(projected, projected).inflate(radius));
      }
   }

   private static void smokeVertex(
      VertexConsumer out, Pose pose, ContrailRenderer.Trail trail, ContrailRenderer.Sample p, Vec3 pos, float u, float v, float alpha, int seed
   ) {
      if (p.frameVertices != null) {
         p.frameVertices.add(new ContrailRenderer.WispVertex(pos, u, v));
         p.frameOpacity = alpha;
         p.frameSeed = seed;
      }

      emitSmokeVertex(out, pose, trail, p, pos, u, v, alpha, seed);
   }

   private static void emitSmokeVertex(
      VertexConsumer out, Pose pose, ContrailRenderer.Trail trail, ContrailRenderer.Sample p, Vec3 pos, float u, float v, float alpha, int seed
   ) {
      float illumination = p.lightScatter;
      pos = smokeProjection(pos);
      out.addVertex(pose.pose(), (float)pos.x, (float)pos.y, (float)pos.z)
         .setColor(trail.red, trail.green, trail.blue, alpha)
         .setUv(u, v)
         .setUv1(
            illumination > 0.005F ? -Math.max(1, (int)(32767.0F * illumination)) : seed,
            p.blastSmoke
               ? -24000
               : (p.hotJet && p.lifetime == 65.0F ? -16384 - Math.min(1000, (int)(p.ageSeconds * 20.0F)) : Math.min(32767, (int)(p.ageSeconds * 20.0F)))
         )
         .setLight(p.light)
         .setNormal(0.0F, 1.0F, 0.0F)
         ;
   }

   private ContrailRenderer() {
   }

   private record ColumnPuff(ContrailRenderer.Trail trail, ContrailRenderer.Sample sample, float scale, double ceiling, boolean main) {
   }

   private static final class ColumnSource {
      final Vec3 position;
      final float scale;
      final int seed;
      final boolean main;
      final long born;
      long expires;
      long next;

      ColumnSource(Vec3 p, float scale, int seed, boolean main) {
         this.position = p;
         this.scale = scale;
         this.seed = seed;
         this.main = main;
         this.born = ContrailRenderer.tick;
         this.next = ContrailRenderer.tick;
         this.expires = ContrailRenderer.tick + (main ? 1800 : 24);
      }
   }

   private record JetPuff(
      ContrailRenderer.Trail trail, ContrailRenderer.Sample sample, Vec3 velocity, int sequence, float scale, SmokeFlow.Parcel flow, float opacity
   ) {
   }

   private static final class Sample {
      final Vec3 origin;
      final Vec3 direction;
      final Vec3 across;
      final Vec3 up;
      final float width;
      final float lifetime;
      final float distance;
      final double born;
      final int strand;
      final int light;
      double x;
      double y;
      double z;
      float radius;
      float densityRadius;
      float alpha;
      float ageSeconds;
      float renderWeight;
      float lightScatter;
      boolean hotJet;
      boolean straightWake;
      boolean motionInitialized;
      boolean blastSmoke;
      long obstacleTick = Long.MIN_VALUE;
      AABB obstacleBounds;
      List<AABB> obstacles;
      List<AABB> clippedObstacles;
      SpringSmokeMotion.Emission emission;
      int motionSeed;
      ArrayList<ContrailRenderer.WispVertex> frameVertices;
      List<List<ContrailRenderer.WispVertex>> cachedLaunchFragments;
      float cachedLaunchFragmentsAge = Float.NEGATIVE_INFINITY;
      long cachedLaunchObstacleTick = Long.MIN_VALUE;
      float frameOpacity;
      int frameSeed;

      Sample(Vec3 origin, Vec3 direction, Vec3 across, float width, float lifetime, float distance, double born, int strand) {
         this(origin, direction, across, width, lifetime, distance, born, strand, SmokeAppearance.light(ContrailRenderer.world, origin));
      }

      Sample(Vec3 origin, Vec3 direction, Vec3 across, float width, float lifetime, float distance, double born, int strand, int light) {
         this.origin = origin;
         this.direction = direction;
         this.across = across;
         this.up = direction.cross(across).normalize();
         this.width = width;
         this.lifetime = lifetime;
         this.distance = distance;
         this.born = born;
         this.strand = strand;
         this.light = light;
      }

      void advect(ContrailRenderer.Trail trail, double now) {
         float age = Math.max(0.0F, (float)(now - this.born));
         float seconds = age * 0.05F;
         this.ageSeconds = seconds;
         if (this.hotJet) {
            if (!this.motionInitialized) {
               this.motionSeed = SpringSmokeMotion.seed(this.distance, trail.motionSeed, this.strand);
               this.motionInitialized = true;
            }
         } else {
            float growth = 2.7F * (1.0F - (float)Math.exp(-seconds * 0.16F));
            this.radius = Math.max(this.width * 0.45F, (this.width + growth * trail.expansion * (trail.rocket ? 0.92F : 0.48F)) * 0.594F);
            if (this.emission == null) {
               this.motionSeed = SpringSmokeMotion.seed(this.distance, trail.motionSeed, this.strand);
               this.emission = SpringSmokeMotion.emit(this.width * 0.12, this.motionSeed);
            }

            double displacement = SpringSmokeMotion.velocityDistance(seconds * 30.0, 0.92);
            double u = this.emission.across() * displacement;
            double v = this.emission.up() * displacement;
            double exhaust = this.emission.axial() * displacement;
            double wind = seconds - (1.0 - Math.exp(-seconds * 0.7)) / 0.7;
            this.x = this.origin.x + this.direction.x * exhaust + this.across.x * u + this.up.x * v + wind * 0.16;
            this.y = this.origin.y + this.direction.y * exhaust + this.across.y * u + this.up.y * v + wind * 0.09;
            this.z = this.origin.z + this.direction.z * exhaust + this.across.z * u + this.up.z * v + wind * 0.055;
            if (this.straightWake) {
               this.radius = this.width * 0.594F;
               this.x = this.origin.x + wind * 0.02;
               this.y = this.origin.y;
               this.z = this.origin.z + wind * 0.01;
            }

            if (!this.hotJet) {
               Vec3 constrained = SmokeFlow.constrainDrift(ContrailRenderer.world, this.origin, new Vec3(this.x, this.y, this.z));
               this.x = constrained.x;
               this.y = constrained.y;
               this.z = constrained.z;
            }

            float fade = SmokeAppearance.dissipation(age / this.lifetime);
            this.densityRadius = this.radius;
            this.alpha = 0.94F * fade;
         }
      }

      Vec3 position() {
         return new Vec3(this.x, this.y, this.z);
      }
   }

   private static final class Trail {
      final boolean rocket;
      final int motionSeed;
      final float red;
      final float green;
      final float blue;
      final float phase;
      float expansion;
      final ArrayDeque<ContrailRenderer.Sample> samples = new ArrayDeque<>();
      final ArrayList<ContrailRenderer.Sample> renderSamples = new ArrayList<>();
      final ArrayList<ContrailRenderer.Sample> visibleWisps = new ArrayList<>();
      Vec3 lastPosition;
      Vec3 emissionAcross;
      ContrailRenderer.Sample head;
      double lastTime;
      float distance;
      float carried;
      float sourceWidth;
      float sourceLifetime;
      int strand;
      long lastTick;
      long receivedTick;
      GuidedMissileEntity source;
      PatriotInterceptorEntity interceptor;
      boolean damaged;
      boolean spiral;
      Vec3 jetPosition;
      Vec3 jetDirection;
      Vec3 lightPosition;
      Vec3 lightDirection;
      float lightScale;
      float lightLength;
      double terrainReference = Double.NaN;
      boolean lightActive;
      FlightLighting.Glow lightGlow;
      int samWarhead = -1;
      long lightUpdatedTick = -100L;
      double jetTime;
      double nextJetTime;
      double boosterFadeStart = Double.NaN;
      boolean boosterObserved;
      boolean onsetLogged;
      int jetSequence;
      int lastDebugAge = -1000;
      int lastBlendBucket = -1;

      Trail(int id, boolean rocket, float expansion, float red, float green, float blue) {
         this.rocket = rocket;
         this.expansion = expansion;
         this.spiral = true;
         this.motionSeed = id;
         this.red = red;
         this.green = green;
         this.blue = blue;
         this.phase = (id * -1640531527 & 65535) / 65535.0F * 31.0F;
         this.sourceLifetime = 400.0F;
         if (Boolean.getBoolean("deepstrike.smokeVisual")) {
            LoggerFactory.getLogger(ContrailRenderer.class).info("SMOKE_REVIEW_LIFETIME trail={} seconds={}", this.motionSeed, this.sourceLifetime * 0.05F);
         }
      }

      void append(ContrailRenderer.Sample point) {
         if (this.source != null && this.source.kind() == MissileKind.PEACEKEEPER && this.source.peacekeeperNarrowPlume(0.0F) >= 0.999F) {
            point.straightWake = true;
         }

         this.samples.addLast(point);
         ContrailRenderer.nodeCount++;
         if (this.samples.size() > 12000) {
            this.samples.removeFirst();
            ContrailRenderer.nodeCount--;
         }
      }

      Vec3 pathPosition(Vec3 center, Vec3 direction, double atDistance) {
         if (!this.spiral) {
            return center;
         } else {
            Vec3 across = ContrailRenderer.perpendicular(direction, this.emissionAcross);
            Vec3 up = direction.cross(across).normalize();
            double phase = atDistance * (Math.PI * 2.0 / 5.0);
            double amplitude = 0.14;
            return center.add(across.scale(Math.cos(phase) * amplitude)).add(up.scale(Math.sin(phase) * amplitude));
         }
      }

      void emit(Vec3 position, double now) {
         if (this.lastPosition == null && this.jetPosition != null) {
            this.lastPosition = this.jetPosition;
            this.lastTime = this.jetTime;
            this.lastTick = (long)this.jetTime;
         }

         double maxJump = this.source == null ? 256.0 : Math.max(256.0, this.source.getDeltaMovement().length() * Math.max(1.0, now - this.lastTime) * 2.0);
         if (this.lastPosition != null && ContrailRenderer.tick - this.lastTick <= 40L && !(this.lastPosition.distanceToSqr(position) > maxJump * maxJump)) {
            Vec3 delta = position.subtract(this.lastPosition);
            double length = delta.length();
            if (length < 1.0E-6) {
               this.lastTick = ContrailRenderer.tick;
            } else {
               Vec3 direction = delta.scale(1.0 / length);
               this.emissionAcross = ContrailRenderer.perpendicular(direction, this.emissionAcross);
               if (this.samples.isEmpty() || this.samples.peekLast().strand != this.strand) {
                  this.append(
                     new ContrailRenderer.Sample(
                        this.pathPosition(this.lastPosition, direction, this.distance),
                        direction,
                        this.emissionAcross,
                        this.sourceWidth,
                        this.sourceLifetime,
                        this.distance,
                        this.lastTime,
                        this.strand
                     )
                  );
               }

               float step = this.source != null && this.source.kind() == MissileKind.FP5
                  ? (this.source.launchBoostBurning() ? 0.11F : 0.15F)
                  : Math.max(0.1F, Math.min(0.4F, this.sourceWidth * 0.55F));

               double offset;
               for (offset = step - this.carried; offset <= length; offset += step) {
                  double fraction = offset / length;
                  this.append(
                     new ContrailRenderer.Sample(
                        this.pathPosition(this.lastPosition.add(direction.scale(offset)), direction, this.distance + offset),
                        direction,
                        this.emissionAcross,
                        this.sourceWidth,
                        this.sourceLifetime,
                        this.distance + (float)offset,
                        Mth.lerp(fraction, this.lastTime, now),
                        this.strand
                     )
                  );
               }

               this.carried = (float)(length - (offset - step));
               this.distance += (float)length;
               this.head = new ContrailRenderer.Sample(
                  this.pathPosition(position, direction, this.distance),
                  direction,
                  this.emissionAcross,
                  this.sourceWidth,
                  this.sourceLifetime,
                  this.distance,
                  now,
                  this.strand
               );
               if (this.source != null && this.source.kind() == MissileKind.PEACEKEEPER && this.source.peacekeeperNarrowPlume(0.0F) >= 0.999F) {
                  this.head.straightWake = true;
               }

               this.lastPosition = position;
               this.lastTime = now;
               this.lastTick = ContrailRenderer.tick;
            }
         } else {
            if (this.head != null && (this.samples.isEmpty() || this.head.distance > this.samples.peekLast().distance + 1.0E-5)) {
               this.append(this.head);
            }

            this.strand++;
            this.lastPosition = position;
            this.lastTime = now;
            this.carried = 0.0F;
            this.lastTick = ContrailRenderer.tick;
            this.head = null;
            this.emissionAcross = null;
         }
      }
   }

   private record WispVertex(Vec3 position, float u, float v, float edgeOpacity) {
      WispVertex(Vec3 position, float u, float v) {
         this(position, u, v, 1.0F);
      }
   }
}
