package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.InterceptionEffects;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.ManpadGeometry;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelGeometry;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

public final class FlightLighting {
   private static final List<FlightLighting.Flash> flashes = new ArrayList<>();
   private static final Map<Long, FlightLighting.Surface> surfaces = new HashMap<>();
   private static final Map<Long, FlightLighting.EngineAfterglow> engineAfterglows = new HashMap<>();
   private static final int ENGINE_AFTERGLOW_TICKS = 12;
   private static ClientLevel level;
   private static final Map<Long, FlightLighting.ShadowField> shadowFields = new HashMap<>();
   private static final Map<Long, FlightLighting.ShadowField> smokeShadowFields = new HashMap<>();
   private static final Map<Integer, Integer> reviewAges = new HashMap<>();
   private static final List<FlightLighting.Glow> cachedEngines = new ArrayList<>();

   public static void flash(Vec3 pos, float radius) {
      flash(pos, radius, Math.max(50, (int)(radius * 2.2F)));
   }

   public static void flash(Vec3 pos, float radius, int duration) {
      sync();
      if (level != null) {
         float energy = Math.max(1.0F, radius * 1.5F);
         flashes.add(new FlightLighting.Flash(pos, level.getGameTime(), radius, level.random.nextLong(), duration, energy));
         if (flashes.size() > 64) {
            flashes.remove(0);
         }
      }
   }

   static float transmission(FlightLighting.Glow glow, Vec3 target) {
      return transmission(glow, target, false);
   }

   static float smokeTransmission(FlightLighting.Glow glow, Vec3 target) {
      return transmission(glow, target, true);
   }

   private static float transmission(FlightLighting.Glow glow, Vec3 target, boolean smoke) {
      Map<Long, FlightLighting.ShadowField> fields = smoke ? smokeShadowFields : shadowFields;
      if (level == null) {
         return 0.0F;
      } else if (glow.position().y >= level.getMaxBuildHeight() && target.y >= level.getMaxBuildHeight()) {
         return 1.0F;
      } else {
         long now = level.getGameTime();
         Vec3 end = glow.end() == null ? glow.position() : glow.end();
         FlightLighting.ShadowField field = fields.get(glow.id());
         if (field == null || now - field.tick() > 2L || field.source().distanceToSqr(glow.position()) > 0.25 || field.end().distanceToSqr(end) > 0.25) {
            Long2FloatOpenHashMap values = new Long2FloatOpenHashMap();
            values.defaultReturnValue(Float.NaN);
            field = new FlightLighting.ShadowField(glow.position(), end, now, values);
            fields.put(glow.id(), field);
         }

         long key = BlockPos.asLong((int)Math.floor(target.x * 2.0), (int)Math.floor(target.y * 2.0), (int)Math.floor(target.z * 2.0));
         float value = field.values().get(key);
         if (!Float.isNaN(value)) {
            return value;
         } else {
            int clear = 0;

            for (int i = 0; i < 3; i++) {
               Vec3 source = glow.position().lerp(end, i * 0.35);
               BlockHitResult hit = level.clip(new ClipContext(source, target, Block.COLLIDER, Fluid.NONE, Minecraft.getInstance().player));
               if (hit.getType() == Type.MISS || hit.getLocation().distanceToSqr(target) < 4.0E-4) {
                  clear++;
               }
            }

            float result = clear / 3.0F;
            if (field.values().size() < 131072) {
               field.values().put(key, result);
            }

            return result;
         }
      }
   }

   private static void sync() {
      ClientLevel next = Minecraft.getInstance().level;
      if (level != next) {
         level = next;
         flashes.clear();
         surfaces.clear();
         cachedEngines.clear();
         engineAfterglows.clear();
         shadowFields.clear();
         smokeShadowFields.clear();
      }
   }

   private static boolean engine(GuidedMissileEntity e) {
      return e.isAlive() && e.exhaustVisible() && e.kind() != MissileKind.STORM_SHADOW && (e.kind() != MissileKind.FP5 || e.fp5BoosterBurning());
   }

   public static void kelvinToRgb(float kelvin, float[] outRgb) {
      float t = kelvin / 100.0F;
      float r = t <= 66.0F ? 1.0F : clamp01(1.293407F * (float)Math.pow(t - 60.0F, -0.13320476F));
      float g = t <= 66.0F
         ? clamp01(0.39008158F * (float)Math.log(Math.max(1.0F, t)) - 0.6318414F)
         : clamp01(1.1298909F * (float)Math.pow(Math.max(1.0F, t - 60.0F), -0.07551485F));
      float b = t >= 66.0F ? 1.0F : (t <= 19.0F ? 0.0F : clamp01(0.5432068F * (float)Math.log(Math.max(1.0F, t - 10.0F)) - 1.1962541F));
      outRgb[0] = r * r;
      outRgb[1] = g * g;
      outRgb[2] = b * b;
   }

   private static float clamp01(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   public static void collectEngineGlows(List<FlightLighting.Glow> lights, float ambient, float partialTick) {
      if (level != null) {
         float[] engineRgb = new float[3];
         kelvinToRgb(2350.0F, engineRgb);
         Set<Integer> seen = new HashSet<>();

         for (GuidedMissileEntity missile : RemoteFlights.all()) {
            if (seen.add(missile.getId())) {
               addEngine(lights, missile, ambient, partialTick);
            }
         }

         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof GuidedMissileEntity missilex && seen.add(missilex.getId())) {
               addEngine(lights, missilex, ambient, partialTick);
            }
         }

         float night = Mth.clamp((level.getSkyDarken() - 2) / 8.0F, 0.0F, 1.0F);
         PatriotRemoteEffects.engineLights(lights, night, partialTick);

         for (Entity entityx : level.entitiesForRendering()) {
            if (entityx instanceof PatriotInterceptorEntity interceptor
               && interceptor.isAlive()
               && !PatriotRemoteEffects.hidesTracked(interceptor)
               && !PatriotRemoteEffects.hasFlight(interceptor.getId())) {
               int warhead = interceptor.warhead();
               float age = interceptor.tickCount + partialTick;
               float flash = ExhaustStyles.launchFlashFromAge(age, warhead == 3 ? 50.0F : 65.0F);
               float burnStage = ExhaustStyles.samBurnStage(age);
               Vec3 center = new Vec3(
                  Mth.lerp(partialTick, interceptor.xo, interceptor.getX()),
                  Mth.lerp(partialTick, interceptor.yo, interceptor.getY()),
                  Mth.lerp(partialTick, interceptor.zo, interceptor.getZ())
               );
               Vec3 dir = FlightOrientation.direction(
                  Mth.rotLerp(partialTick, interceptor.previousYaw, interceptor.yaw()),
                  Mth.lerp(partialTick, interceptor.previousPitch, interceptor.pitch())
               );
               String model = InterceptorGeometry.model(warhead);
               double tail = ModelGeometry.get(model).tailX * InterceptorGeometry.axialScale(warhead);
               Vec3 nozzle = center.subtract(dir.scale(tail));
               float jetLen = ExhaustStyles.samMotorLength(warhead, age);
               Vec3 end = nozzle.subtract(dir.scale(jetLen));
               BlockHitResult hit = level.clip(new ClipContext(nozzle, end, Block.COLLIDER, Fluid.NONE, Minecraft.getInstance().player));
               if (hit.getType() != Type.MISS) {
                  end = hit.getLocation().add(dir.scale(0.02));
               }

               float[] rgb = new float[3];
               kelvinToRgb(Mth.lerp(burnStage, 3150.0F, 2250.0F), rgb);
               float motorScale = ExhaustStyles.samMotorScale(warhead);
               lights.add(
                  new FlightLighting.Glow(
                     interceptor.getId(),
                     nozzle,
                     ExhaustStyles.samLightRadius(warhead) * (0.15F + 0.85F * ExhaustStyles.ignition(age)),
                     (0.76F + 0.36F * motorScale) * (1.0F + 0.35F * flash) * ambient,
                     6.0F * ExhaustStyles.samMotorRadius(warhead, age),
                     false,
                     dir,
                     end,
                     rgb,
                     burnStage
                  )
               );
            } else if (InterceptionEffects.isManpad(entityx) && entityx.isAlive() && !PatriotRemoteEffects.hasFlight(entityx.getId())) {
               Vec3 dir = entityx.getDeltaMovement().lengthSqr() > 1.0E-6 ? entityx.getDeltaMovement().normalize() : new Vec3(0.0, 1.0, 0.0);
               MissileProjectile missilex = (MissileProjectile)entityx;
               Vec3 center = new Vec3(
                  Mth.lerp(partialTick, entityx.xo, entityx.getX()),
                  Mth.lerp(partialTick, entityx.yo, entityx.getY()),
                  Mth.lerp(partialTick, entityx.zo, entityx.getZ())
               );
               Vec3 nozzle = ManpadGeometry.nozzle(missilex, center, dir);
               float age = missilex.tickCount + partialTick;
               float flash = ExhaustStyles.launchFlashFromAge(age, 55.0F);
               float burnStage = ExhaustStyles.samBurnStage(age);
               float jetLen = ExhaustStyles.samMotorLength(4, age);
               Vec3 end = nozzle.subtract(dir.scale(jetLen));
               BlockHitResult hit = level.clip(new ClipContext(nozzle, end, Block.COLLIDER, Fluid.NONE, Minecraft.getInstance().player));
               if (hit.getType() != Type.MISS) {
                  end = hit.getLocation().add(dir.scale(0.02));
               }

               lights.add(
                  new FlightLighting.Glow(
                     entityx.getId(),
                     nozzle,
                     ExhaustStyles.samLightRadius(4) * (0.15F + 0.85F * ExhaustStyles.ignition(age)),
                     0.92F * (1.0F + 0.32F * flash) * ambient,
                     6.0F * ExhaustStyles.samMotorRadius(4, age),
                     false,
                     dir,
                     end,
                     engineRgb,
                     burnStage
                  )
               );
            }
         }

         Su24mExhaust.collect(lights, level, ambient, partialTick);
         appendEngineAfterglows(lights);
      }
   }

   private static void appendEngineAfterglows(List<FlightLighting.Glow> lights) {
      long now = level.getGameTime();
      Set<Long> active = new HashSet<>();

      for (FlightLighting.Glow glow : lights) {
         if (!glow.flash()) {
            if (Su24mExhaust.aircraftLight(glow.id())) {
               engineAfterglows.remove(glow.id());
            } else {
               active.add(glow.id());
               engineAfterglows.put(glow.id(), new FlightLighting.EngineAfterglow(glow, now));
            }
         }
      }

      Iterator<Entry<Long, FlightLighting.EngineAfterglow>> iterator = engineAfterglows.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<Long, FlightLighting.EngineAfterglow> entry = iterator.next();
         FlightLighting.EngineAfterglow state = entry.getValue();
         long age = now - state.refreshed();
         if (age > 12L) {
            iterator.remove();
         } else if (!active.contains(entry.getKey()) && age > 0L) {
            float t = 1.0F - (float)age / 12.0F;
            float fade = t * t * (3.0F - 2.0F * t);
            FlightLighting.Glow source = state.glow();
            lights.add(
               new FlightLighting.Glow(
                  source.id(),
                  source.position(),
                  source.radius() * (0.72F + 0.28F * fade),
                  source.strength() * fade,
                  0.0F,
                  false,
                  source.direction(),
                  source.end(),
                  source.rgb(),
                  source.burnStage()
               )
            );
         }
      }
   }

   static void reviewVisibility() {
      sync();
      FlightLighting.Glow light = new FlightLighting.Glow(-990L, new Vec3(0.0, 105.0, -5.0), 13.0F, 1.0F, 3.0F, false, new Vec3(0.0, 0.0, 1.0));
      float open = transmission(light, new Vec3(2.0, 105.0, -4.0));
      float behind = transmission(light, new Vec3(0.0, 105.0, -9.0));
      if (open == 1.0F && behind == 0.0F) {
         LogUtils.getLogger().info("ENGINE_LIGHT_SHADOW open={} behindWall={} PASS", open, behind);
      } else {
         throw new IllegalStateException("Engine light visibility: open=" + open + ", wall=" + behind);
      }
   }

   private static void addEngine(List<FlightLighting.Glow> lights, GuidedMissileEntity missile, float ambient, float partial) {
      if (Boolean.getBoolean("deepstrike.fp5Review")) {
         int age = missile.tickCount;
         Integer previous = reviewAges.put(missile.getId(), age / 10);
         if (previous == null || previous != age / 10) {
            LogUtils.getLogger()
               .info("ENGINE_LIGHT_SOURCE kind={} age={} active={} boost={}", new Object[]{missile.kind(), age, engine(missile), missile.launchBoostBurning()});
         }
      }

      if (engine(missile)) {
         boolean large = missile.kind() == MissileKind.PEACEKEEPER;
         boolean boost = missile.launchBoostBurning();
         Vec3 direction = missile.getDeltaMovement().lengthSqr() > 1.0E-6 ? missile.getDeltaMovement().normalize() : missile.getLookAngle();
         if (missile.kind() == MissileKind.FP5) {
            Vector3f axis = MissileGeometry.flight(missile.kind(), missile.getYRot(), missile.getXRot())
               .transformDirection(new Vector3f(-1.0F, 0.0F, 0.0F));
            direction = new Vec3(axis).normalize();
         }

         Vec3 position = missile.position();
         if (RemoteFlights.get(missile.getId()) != missile) {
            position = new Vec3(
               Mth.lerp(partial, missile.xo, missile.getX()),
               Mth.lerp(partial, missile.yo, missile.getY()),
               Mth.lerp(partial, missile.zo, missile.getZ())
            );
         }

         Vec3 nozzle = position.subtract(direction.scale(MissileGeometry.tailDistance(missile.kind())));
         if (missile.kind() == MissileKind.FP5 && boost) {
            Vector3f offset = MissileGeometry.flight(missile.kind(), missile.getYRot(), missile.getXRot())
               .transformPosition(new Vector3f(0.75F, -0.478125F, 0.0F));
            nozzle = position.add(offset.x, offset.y, offset.z);
         }

         float ageTicks = missile.tickCount + partial;
         float flash = ExhaustStyles.launchFlashFromAge(ageTicks, large ? 95.0F : (boost ? 75.0F : 55.0F));
         float motorAge = ExhaustStyles.motorAge(missile.kind(), ageTicks);
         float ramp = ExhaustStyles.motorIgnition(missile.kind(), motorAge);
         float length = boost ? 8.0F * (0.08F + 0.92F * ramp) : ExhaustStyles.motorLength(missile.kind(), motorAge);
         float burnStage = ExhaustStyles.burnStage(missile);
         float[] rgb = new float[3];
         kelvinToRgb(Mth.lerp(burnStage, 3100.0F, 2100.0F), rgb);
         Vec3 end = nozzle.subtract(direction.scale(length));
         BlockHitResult hit = level.clip(new ClipContext(nozzle, end, Block.COLLIDER, Fluid.NONE, Minecraft.getInstance().player));
         if (hit.getType() != Type.MISS) {
            end = hit.getLocation().add(direction.scale(0.02));
         }

         float pulse = 1.0F + 0.022F * (float)Math.sin((missile.tickCount + partial) * 0.77 + missile.getId() * 0.173);
         float baseRad = large ? 62.0F : (boost ? 18.0F : (missile.kind() == MissileKind.JAGM ? 14.0F : 11.5F));
         float baseHalo = 6.0F * (boost ? 0.66F : ExhaustStyles.radius(missile.kind(), 2));
         float baseStr = (large ? 1.52F : (boost ? 1.05F : Mth.lerp(burnStage, 0.92F, 0.74F))) * (1.0F + 0.3F * flash);
         lights.add(
            new FlightLighting.Glow(
               missile.getId(),
               nozzle,
               baseRad * (0.15F + 0.85F * ramp),
               baseStr * ambient * pulse * (0.12F + 0.88F * ramp),
               baseHalo * (0.22F + 0.78F * ramp),
               false,
               direction,
               end,
               rgb,
               burnStage
            )
         );
      }
   }

   public static void updateEngines() {
      sync();
      if (level != null) {
         cachedEngines.clear();
         collectEngineGlows(cachedEngines, 1.0F, 0.0F);
      }
   }

   public static void collectSmokeLights(List<FlightLighting.Glow> out, float partialTick) {
      sync();
      if (level != null) {
         collectEngineGlows(out, 1.0F, partialTick);
         long now = level.getGameTime();
         float[] flashColorA = new float[3];
         float[] flashColorB = new float[3];

         for (FlightLighting.Flash f : flashes) {
            float ageTicks = Math.max(0.0F, (float)(now - f.start()) + partialTick);
            if (!(ageTicks < 0.0F) && !(ageTicks > f.duration())) {
               float tSeconds = ageTicks * 0.05F;
               float flashComp = (float)Math.exp(-tSeconds / 0.06F);
               float fireballComp = 0.65F * (1.0F - (float)Math.exp(-tSeconds / 0.05F)) * (float)Math.exp(-tSeconds / 0.75F);
               float totalIntensity = flashComp + fireballComp;
               if (!(totalIntensity < 0.01F)) {
                  kelvinToRgb(3600.0F + 2800.0F * (float)Math.exp(-tSeconds / 0.06F), flashColorA);
                  kelvinToRgb(1350.0F + 1950.0F * (float)Math.exp(-tSeconds / 0.65F), flashColorB);
                  float wA = flashComp / totalIntensity;
                  float wB = fireballComp / totalIntensity;
                  float[] rgb = new float[]{
                     flashColorA[0] * wA + flashColorB[0] * wB, flashColorA[1] * wA + flashColorB[1] * wB, flashColorA[2] * wA + flashColorB[2] * wB
                  };
                  float rExp = 0.35F + 0.65F * (1.0F - (float)Math.exp(-tSeconds / 0.14F));
                  Vec3 top = f.position().add(0.0, Math.min(24.0, f.radius() * 0.28 * tSeconds), 0.0);
                  out.add(
                     new FlightLighting.Glow(
                        f.id(),
                        f.position(),
                        f.radius() * rExp * 0.65F,
                        Math.min(1.65F, totalIntensity * 1.45F),
                        f.radius() * 0.25F,
                        true,
                        new Vec3(0.0, 1.0, 0.0),
                        top,
                        rgb,
                        1.0F
                     )
                  );
               }
            }
         }

         MissileExplosionEffects.collectBlastSmokeLights(out, partialTick);
         PatriotRemoteEffects.collectBurstSmokeLights(out, partialTick);
      }
   }

   public static float engineIllumination(double px, double py, double pz) {
      if (level == null) {
         return 0.0F;
      } else {
         sync();
         if (cachedEngines.isEmpty()) {
            collectEngineGlows(cachedEngines, 1.0F, 0.0F);
         }

         float maxIllum = 0.0F;
         Vec3 p = new Vec3(px, py, pz);

         for (int i = 0; i < cachedEngines.size(); i++) {
            FlightLighting.Glow g = cachedEngines.get(i);
            double distSq;
            if (g.end() != null) {
               distSq = distSqToSegment(p, g.position(), g.end());
            } else {
               double dx = g.position().x - px;
               double dy = g.position().y - py;
               double dz = g.position().z - pz;
               distSq = dx * dx + dy * dy + dz * dz;
            }

            double rad = g.radius();
            if (distSq < rad * rad) {
               double dist = Math.sqrt(distSq);
               double u = dist / rad;
               double falloff = (1.0 - u) * (1.0 - u) * (1.0 + 2.0 * u);
               float illum = (float)falloff;
               if (illum > maxIllum) {
                  maxIllum = illum;
               }
            }
         }

         return maxIllum;
      }
   }

   private static double distSqToSegment(Vec3 p, Vec3 a, Vec3 b) {
      Vec3 ab = b.subtract(a);
      Vec3 ap = p.subtract(a);
      double abLenSq = ab.lengthSqr();
      if (abLenSq < 1.0E-6) {
         return p.distanceToSqr(a);
      } else {
         double t = Math.max(0.0, Math.min(1.0, ap.dot(ab) / abLenSq));
         Vec3 proj = a.add(ab.scale(t));
         return p.distanceToSqr(proj);
      }
   }

   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         event = LateWorldEffects.worldView(event);
         sync();
         if (level != null) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 camera = event.getCamera().getPosition();
            long now = level.getGameTime();
            flashes.removeIf(f -> now - f.start() > f.duration());
            List<FlightLighting.Glow> lights = new ArrayList<>();
            float night = Mth.clamp((level.getSkyDarken() - 2) / 8.0F, 0.0F, 1.0F);
            float ambient = 0.65F + 0.35F * night;
            collectEngineGlows(lights, ambient, event.getPartialTick().getGameTimeDeltaPartialTick(false));
            cachedEngines.clear();
            cachedEngines.addAll(lights);
            if (!Boolean.getBoolean("deepstrike.installedSmokeReview") || !"smoke".equals(System.getProperty("deepstrike.reviewEffectIsolation"))) {
               float[] flashColorA = new float[3];
               float[] flashColorB = new float[3];

               for (FlightLighting.Flash f : flashes) {
                  float ageTicks = Math.max(0.0F, (float)(now - f.start()) + event.getPartialTick().getGameTimeDeltaPartialTick(false));
                  float tSeconds = ageTicks * 0.05F;
                  float flashComp = (float)Math.exp(-tSeconds / 0.045F);
                  float fireballComp = 0.35F * (1.0F - (float)Math.exp(-tSeconds / 0.065F)) * (float)Math.exp(-tSeconds / 0.55F);
                  float totalIntensity = flashComp + fireballComp;
                  if (!(totalIntensity < 0.005F)) {
                     float kelvinFlash = 3500.0F + 3000.0F * (float)Math.exp(-tSeconds / 0.05F);
                     float kelvinFireball = 1100.0F + 2100.0F * (float)Math.exp(-tSeconds / 0.5F);
                     kelvinToRgb(kelvinFlash, flashColorA);
                     kelvinToRgb(kelvinFireball, flashColorB);
                     float[] compositeRgb = new float[3];
                     float wA = flashComp / totalIntensity;
                     float wB = fireballComp / totalIntensity;

                     for (int c = 0; c < 3; c++) {
                        compositeRgb[c] = flashColorA[c] * wA + flashColorB[c] * wB;
                     }

                     float rExpansion = 0.25F + 0.75F * (1.0F - (float)Math.exp(-tSeconds / 0.16F));
                     float activeRadius = f.radius() * rExpansion;
                     float strength = Math.min(1.0F, totalIntensity * 1.1F);
                     lights.add(
                        new FlightLighting.Glow(
                           f.id(), f.position(), activeRadius, strength, activeRadius * 0.2F, true, new Vec3(0.0, 1.0, 0.0), null, compositeRgb
                        )
                     );
                  }
               }

               PoseStack pose = event.getPoseStack();
               BufferSource buffers = mc.renderBuffers().bufferSource();
               float far = mc.gameRenderer.getDepthFar() * 0.72F;

               for (FlightLighting.Glow glow : lights) {
                  if (Su24mExhaust.aircraftLight(glow.id())) {
                     Su24mExhaust.renderHalos(pose, buffers, glow, camera, far, level);
                  } else if (glow.halo() > 0.01F && !glow.flash()) {
                     EngineLightRenderer.renderAircraftMotor(pose, buffers, glow, camera, far);
                  } else if (glow.flash() && glow.strength() > 0.02F) {
                     EngineLightRenderer.renderFlash(pose, buffers, glow.position(), camera, far, glow.radius() * 0.6F, glow.strength());
                     EngineLightRenderer.renderAtmosphere(pose, buffers, glow, camera, far);
                  }
               }

               lights.sort(Comparator.comparingDouble(g -> g.position().distanceToSqr(camera)));
               Set<Long> used = new HashSet<>();
               int budget = 0;

               for (FlightLighting.Glow glowx : lights) {
                  double visibleRange = glowx.flash()
                     ? Math.max((double)(glowx.radius() + 64.0F), ((Integer)mc.options.renderDistance().get()).intValue() * 16.0)
                     : glowx.radius() + 48.0F;
                  if (!(glowx.strength() < 0.01F) && !(glowx.position().distanceToSqr(camera) > visibleRange * visibleRange) && budget++ < 8) {
                     used.add(glowx.id());
                     FlightLighting.Surface surface = surfaces.get(glowx.id());
                     Vec3 end = glowx.end() == null ? glowx.position() : glowx.end();
                     if (surface == null
                        || surface.origin().distanceToSqr(glowx.position()) > 1.0
                        || surface.end().distanceToSqr(end) > 1.0
                        || Math.abs(surface.radius() - glowx.radius()) > 0.5
                        || now - surface.refreshed() > (glowx.flash() ? 3 : 8)) {
                        surface = scan(glowx, now);
                        surfaces.put(glowx.id(), surface);
                     }

                     if (!glowx.flash()) {
                        buffers.endBatch();
                        GroundLightRenderer.draw(pose, surface.faces(), glowx, camera);
                     } else {
                        VertexConsumer out = buffers.getBuffer(RenderType.lightning());

                        for (FlightLighting.Face face : surface.faces()) {
                           face(pose, out, face, camera, glowx);
                        }
                     }
                  }
               }

               surfaces.keySet().removeIf(id -> !used.contains(id));
               Set<Long> liveIds = new HashSet<>();

               for (FlightLighting.Glow glowxx : lights) {
                  liveIds.add(glowxx.id());
               }

               shadowFields.keySet().removeIf(id -> !liveIds.contains(id));
               smokeShadowFields.keySet().removeIf(id -> !liveIds.contains(id));
               buffers.endBatch();
            }
         }
      }
   }

   public static boolean hasActiveFlash() {
      return !flashes.isEmpty();
   }

   public static float flashIllumination(double px, double py, double pz) {
      if (!flashes.isEmpty() && level != null) {
         long now = level.getGameTime();
         float total = 0.0F;

         for (FlightLighting.Flash f : flashes) {
            float tSeconds = (float)(now - f.start()) * 0.05F;
            if (!(tSeconds < 0.0F) && !(tSeconds > f.duration() * 0.05F)) {
               double dx = f.position().x - px;
               double dy = f.position().y - py;
               double dz = f.position().z - pz;
               double distSq = dx * dx + dy * dy + dz * dz;
               double rad = f.radius();
               if (distSq < rad * rad) {
                  double dist = Math.sqrt(distSq);
                  float flashComp = (float)Math.exp(-tSeconds / 0.045F);
                  float fireballComp = 0.35F * (1.0F - (float)Math.exp(-tSeconds / 0.065F)) * (float)Math.exp(-tSeconds / 0.55F);
                  float strength = flashComp + fireballComp;
                  float falloff = (float)Math.pow(1.0 - dist / rad, 1.6);
                  total += strength * falloff;
               }
            }
         }

         return Math.min(1.0F, total);
      } else {
         return 0.0F;
      }
   }

   private static FlightLighting.Surface scan(FlightLighting.Glow glow, long now) {
      List<FlightLighting.Face> candidates = new ArrayList<>();
      MutableBlockPos pos = new MutableBlockPos();
      Vec3 emitterEnd = glow.end() == null ? glow.position() : glow.end();
      float scanRadius = glow.radius() + (glow.flash() ? 0.0F : 1.5F);
      AABB bounds = new AABB(glow.position(), emitterEnd).inflate(scanRadius);
      if (Double.isFinite(bounds.getSize()) && !(bounds.getXsize() > 1024.0) && !(bounds.getZsize() > 1024.0)) {
         int minY = Math.max(level.getMinBuildHeight(), (int)Math.floor(bounds.minY));
         int maxY = Math.min(level.getMaxBuildHeight() - 1, (int)Math.ceil(bounds.maxY));

         for (int cx = (int)Math.floor(bounds.minX) >> 4; cx <= (int)Math.floor(bounds.maxX) >> 4; cx++) {
            for (int cz = (int)Math.floor(bounds.minZ) >> 4; cz <= (int)Math.floor(bounds.maxZ) >> 4; cz++) {
               LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
               if (chunk != null) {
                  for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                     if (!chunk.getSection(level.getSectionIndex(sy * 16)).hasOnlyAir()) {
                        for (int y = Math.max(minY, sy * 16); y <= Math.min(maxY, sy * 16 + 15); y++) {
                           for (int x = Math.max((int)Math.floor(bounds.minX), cx * 16); x <= Math.min((int)Math.ceil(bounds.maxX), cx * 16 + 15); x++) {
                              for (int z = Math.max((int)Math.floor(bounds.minZ), cz * 16);
                                 z <= Math.min((int)Math.ceil(bounds.maxZ), cz * 16 + 15);
                                 z++
                              ) {
                                 pos.set(x, y, z);
                                 if (!(distSqToSegment(pos.getCenter(), glow.position(), emitterEnd) > scanRadius * scanRadius)) {
                                    BlockState state = chunk.getBlockState(pos);
                                    if (!state.isAir()) {
                                       for (AABB local : state.getShape(level, pos).toAabbs()) {
                                          AABB box = local.move(x, y, z);

                                          for (Direction dir : Direction.values()) {
                                             if (!level.getBlockState(pos.relative(dir)).isSolidRender(level, pos.relative(dir))) {
                                                Vec3 c = box.getCenter();

                                                c = switch (dir) {
                                                   case UP -> new Vec3(c.x, box.maxY, c.z);
                                                   case DOWN -> new Vec3(c.x, box.minY, c.z);
                                                   case EAST -> new Vec3(box.maxX, c.y, c.z);
                                                   case WEST -> new Vec3(box.minX, c.y, c.z);
                                                   case NORTH -> new Vec3(c.x, c.y, box.minZ);
                                                   case SOUTH -> new Vec3(c.x, c.y, box.maxZ);
                                                   default -> throw new IncompatibleClassChangeError();
                                                };
                                                Vec3 ab = emitterEnd.subtract(glow.position());
                                                double t = ab.lengthSqr() < 1.0E-6
                                                   ? 0.0
                                                   : Mth.clamp(c.subtract(glow.position()).dot(ab) / ab.lengthSqr(), 0.0, 1.0);
                                                Vec3 delta = glow.position().add(ab.scale(t)).subtract(c);
                                                double length = delta.length();
                                                if (length > 0.05
                                                   && length < scanRadius
                                                   && (!glow.flash() || delta.dot(Vec3.atLowerCornerOf(dir.getNormal())) > 0.0)) {
                                                   candidates.add(new FlightLighting.Face(c, dir, box, 1.0F));
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
               }
            }
         }

         candidates.sort(Comparator.comparingDouble(f -> f.center().distanceToSqr(glow.position())));
         List<FlightLighting.Face> faces = new ArrayList<>(candidates.size());
         int shadowBudget = glow.flash() ? 750 : 400;

         for (FlightLighting.Face candidate : candidates) {
            float visibility = 1.0F;
            if (glow.flash() && shadowBudget > 0 && candidate.center().distanceToSqr(glow.position()) < 256.0) {
               visibility = visibility(glow.position(), candidate);
               shadowBudget--;
            }

            if (visibility > 0.0F) {
               faces.add(new FlightLighting.Face(candidate.center(), candidate.normal(), candidate.box(), visibility));
            }
         }

         return new FlightLighting.Surface(glow.position(), emitterEnd, glow.radius(), now, faces);
      } else {
         if (Boolean.getBoolean("deepstrike.authorityReview")) {
            LoggerFactory.getLogger(FlightLighting.class)
               .warn(
                  "FLIGHT_LIGHT_INVALID_SCAN id={} source={} end={} radius={} bounds={}",
                  new Object[]{glow.id(), glow.position(), emitterEnd, glow.radius(), bounds}
               );
         }

         return new FlightLighting.Surface(glow.position(), emitterEnd, glow.radius(), now, List.of());
      }
   }

   private static float visibility(Vec3 source, FlightLighting.Face face) {
      LocalPlayer observer = Minecraft.getInstance().player;
      if (observer == null) {
         return 1.0F;
      } else {
         Vec3 n = Vec3.atLowerCornerOf(face.normal().getNormal());
         Vec3 u = Math.abs(n.y) > 0.5 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
         Vec3 v = n.cross(u).normalize();
         Vec3 target = face.center().add(n.scale(0.025));
         BlockPos own = BlockPos.containing(face.center().subtract(n.scale(0.01)));
         int clear = 0;

         for (int i = 0; i < 3; i++) {
            Vec3 end = i == 0
               ? target
               : (i == 1 ? target.add(u.scale(0.18)).add(v.scale(0.18)) : target.subtract(u.scale(0.18)).subtract(v.scale(0.18)));
            BlockHitResult hit = level.clip(new ClipContext(source, end, Block.COLLIDER, Fluid.NONE, observer));
            if (hit.getType() == Type.MISS || hit.getBlockPos().equals(own) || hit.getLocation().distanceToSqr(end) < 0.04) {
               clear++;
            }
         }

         return clear / 3.0F;
      }
   }

   private static void face(PoseStack pose, VertexConsumer out, FlightLighting.Face face, Vec3 camera, FlightLighting.Glow glow) {
      AABB b = face.box();

      double[][] q = switch (face.normal()) {
         case UP -> new double[][]{
            {b.minX, b.maxY + 0.003, b.minZ},
            {b.maxX, b.maxY + 0.003, b.minZ},
            {b.maxX, b.maxY + 0.003, b.maxZ},
            {b.minX, b.maxY + 0.003, b.maxZ}
         };
         case DOWN -> new double[][]{
            {b.minX, b.minY - 0.003, b.maxZ},
            {b.maxX, b.minY - 0.003, b.maxZ},
            {b.maxX, b.minY - 0.003, b.minZ},
            {b.minX, b.minY - 0.003, b.minZ}
         };
         case EAST -> new double[][]{
            {b.maxX + 0.003, b.minY, b.minZ},
            {b.maxX + 0.003, b.minY, b.maxZ},
            {b.maxX + 0.003, b.maxY, b.maxZ},
            {b.maxX + 0.003, b.maxY, b.minZ}
         };
         case WEST -> new double[][]{
            {b.minX - 0.003, b.minY, b.maxZ},
            {b.minX - 0.003, b.minY, b.minZ},
            {b.minX - 0.003, b.maxY, b.minZ},
            {b.minX - 0.003, b.maxY, b.maxZ}
         };
         case NORTH -> new double[][]{
            {b.minX, b.minY, b.minZ - 0.003},
            {b.maxX, b.minY, b.minZ - 0.003},
            {b.maxX, b.maxY, b.minZ - 0.003},
            {b.minX, b.maxY, b.minZ - 0.003}
         };
         case SOUTH -> new double[][]{
            {b.maxX, b.minY, b.maxZ + 0.003},
            {b.minX, b.minY, b.maxZ + 0.003},
            {b.minX, b.maxY, b.maxZ + 0.003},
            {b.maxX, b.maxY, b.maxZ + 0.003}
         };
         default -> throw new IncompatibleClassChangeError();
      };
      Vec3 src = glow.position();
      Vec3 end = glow.end();
      float rad = glow.radius();
      Vec3i norm = face.normal().getNormal();
      double nx = norm.getX();
      double ny = norm.getY();
      double nz = norm.getZ();
      float vis = face.visibility();
      double cDist = src.distanceTo(face.center());
      if (!(cDist > rad) || end != null && !(distSqToSegment(face.center(), src, end) > rad * rad)) {
         float cr = 1.0F;
         float cg = 0.7F;
         float cb = 0.25F;
         if (glow.rgb() != null) {
            cr = glow.rgb()[0];
            cg = glow.rgb()[1];
            cb = glow.rgb()[2];
         }

         for (double[] v : q) {
            double lx;
            double ly;
            double lz;
            double dist;
            if (end != null) {
               double abx = end.x - src.x;
               double aby = end.y - src.y;
               double abz = end.z - src.z;
               double apx = v[0] - src.x;
               double apy = v[1] - src.y;
               double apz = v[2] - src.z;
               double abLenSq = abx * abx + aby * aby + abz * abz;
               double t = abLenSq > 1.0E-5 ? Math.max(0.0, Math.min(1.0, (apx * abx + apy * aby + apz * abz) / abLenSq)) : 0.0;
               double qx = src.x + abx * t;
               double qy = src.y + aby * t;
               double qz = src.z + abz * t;
               double dx = qx - v[0];
               double dy = qy - v[1];
               double dz = qz - v[2];
               dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
               if (dist >= rad) {
                  out.addVertex(pose.last().pose(), (float)(v[0] - camera.x), (float)(v[1] - camera.y), (float)(v[2] - camera.z))
                     .setColor(cr, cg, cb, 0.0F)
                     ;
                  continue;
               }

               double invD = 1.0 / Math.max(0.05, dist);
               lx = dx * invD;
               ly = dy * invD;
               lz = dz * invD;
            } else {
               double dx = src.x - v[0];
               double dy = src.y - v[1];
               double dz = src.z - v[2];
               dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
               if (dist >= rad) {
                  out.addVertex(pose.last().pose(), (float)(v[0] - camera.x), (float)(v[1] - camera.y), (float)(v[2] - camera.z))
                     .setColor(cr, cg, cb, 0.0F)
                     ;
                  continue;
               }

               double invD = 1.0 / Math.max(0.05, dist);
               lx = dx * invD;
               ly = dy * invD;
               lz = dz * invD;
            }

            double cosTheta = Math.max(0.0, lx * nx + ly * ny + lz * nz);
            double diffuse = 0.2 + 0.8 * cosTheta;
            double u = dist / rad;
            double ourFalloff = Math.pow(1.0 - u, 1.85);
            double coreLobe = Math.exp(-(u / 0.42) * (u / 0.42));
            double midLobe = Math.exp(-(u / 0.78) * (u / 0.78));
            double skirtLobe = Math.exp(-(u / 1.35) * (u / 1.35));
            double invQ = 1.0 / ((1.0 + u * u * 2.5) * (1.0 + u * u * 2.5));
            double amcwFalloff = (0.52 * (coreLobe * 0.52 + midLobe * 0.33 + skirtLobe * 0.15) + 0.48 * invQ) * (1.0 - u * u);
            double falloff = glow.flash() ? ourFalloff : 0.48 * ourFalloff + 0.52 * amcwFalloff;
            float shadow = glow.flash() ? vis : transmission(glow, new Vec3(v[0], v[1], v[2]));
            float alpha = (float)(glow.strength() * falloff * diffuse * shadow);
            float vr = cr;
            float vg = cg;
            float vb = cb;
            if (!glow.flash()) {
               float amcwR = 1.0F;
               float amcwG = (float)(0.59 + 0.28 * coreLobe);
               float amcwB = (float)(0.27 + 0.39 * coreLobe);
               vr = 0.52F * cr + 0.48F * amcwR;
               vg = 0.52F * cg + 0.48F * amcwG;
               vb = 0.52F * cb + 0.48F * amcwB;
            }

            out.addVertex(pose.last().pose(), (float)(v[0] - camera.x), (float)(v[1] - camera.y), (float)(v[2] - camera.z))
               .setColor(vr, vg, vb, Math.min(1.0F, alpha))
               ;
         }
      }
   }

   private FlightLighting() {
   }

   private record EngineAfterglow(FlightLighting.Glow glow, long refreshed) {
   }

   record Face(Vec3 center, Direction normal, AABB box, float visibility) {
   }

   private record Flash(Vec3 position, long start, float radius, long id, int duration, float peakEnergy) {
   }

   public record Glow(long id, Vec3 position, float radius, float strength, float halo, boolean flash, Vec3 direction, Vec3 end, float[] rgb, float burnStage) {
      public Glow(long id, Vec3 position, float radius, float strength, float halo, boolean flash, Vec3 direction, Vec3 end, float[] rgb) {
         this(id, position, radius, strength, halo, flash, direction, end, rgb, 0.0F);
      }

      public Glow(long id, Vec3 position, float radius, float strength, float halo, boolean flash, Vec3 direction) {
         this(id, position, radius, strength, halo, flash, direction, null, null, 0.0F);
      }

      public Glow(long id, Vec3 position, float radius, float strength, float halo, boolean flash) {
         this(id, position, radius, strength, halo, flash, new Vec3(0.0, 1.0, 0.0), null, null, 0.0F);
      }
   }

   private record ShadowField(Vec3 source, Vec3 end, long tick, Long2FloatOpenHashMap values) {
   }

   private record Surface(Vec3 origin, Vec3 end, float radius, long refreshed, List<FlightLighting.Face> faces) {
   }
}
