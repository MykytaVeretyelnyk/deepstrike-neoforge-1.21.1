package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Pre;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

public final class PatriotThermalCamera implements AutoCloseable {
   private static ShaderInstance thermalShader;
   private static ShaderInstance opticalShader;
   private static ShaderInstance nvgShader;
   private static PatriotThermalCamera active;
   private static final BlendMode GUI_BLEND = new BlendMode();
   private TextureTarget frame;
   private Entity previousCamera;
   private CameraType previousMode;
   private GuidedMissileEntity anchor;
   private boolean tracking;
   private int trackedEntityId = -1;
   private UUID trackedUuid;
   private Vec3 lastTarget = Vec3.ZERO;
   private double fov = 12.0;
   private PatriotThermalCamera.CameraMode mode = PatriotThermalCamera.CameraMode.THERMAL;
   private float zoom = 1.0F;

   static boolean isActive() {
      return active != null && active.tracking;
   }

   static void registerShaders(RegisterShadersEvent event) {
      try {
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "optical_view"), DefaultVertexFormat.POSITION_TEX),
            s -> opticalShader = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "thermal_view"), DefaultVertexFormat.POSITION_TEX),
            s -> thermalShader = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "nvg_view"), DefaultVertexFormat.POSITION_TEX), s -> nvgShader = s
         );
      } catch (IOException var2) {
         throw new IllegalStateException("Cannot load Patriot camera shader", var2);
      }
   }

   static boolean isTracked(Entity entity) {
      return isTracked(entity.getUUID(), entity.getId());
   }

   static boolean isTracked(UUID uuid, int id) {
      return active != null && active.tracking && (active.trackedUuid != null ? active.trackedUuid.equals(uuid) : active.trackedEntityId == id);
   }

   static boolean isThermalTarget(UUID uuid, int id) {
      return isTracked(uuid, id) && active.mode == PatriotThermalCamera.CameraMode.THERMAL;
   }

   static boolean isThermalTarget(Entity entity) {
      return isTracked(entity) && active.mode == PatriotThermalCamera.CameraMode.THERMAL;
   }

   static ShaderInstance thermalShader() {
      return thermalShader;
   }

   static void renderTick(RenderTickEvent event) {
      if (event.phase == Phase.START && active != null && active.tracking && active.anchor != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            RemoteFlights.updatePoses();
            mc.setCameraEntity(active.anchor);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
         }
      }
   }

   static void computeFov(ComputeFov event) {
      if (active != null && active.tracking) {
         event.setFOV(Math.max(0.05, active.fov / Math.max(1.0F, active.zoom)));
      }
   }

   static void computeAngles(ComputeCameraAngles event) {
      PatriotThermalCamera camera = active;
      Minecraft mc = Minecraft.getInstance();
      if (camera != null && camera.tracking && mc.level != null && mc.getCameraEntity() == camera.anchor) {
         Entity target = camera.target();
         Vec3 point = target != null && target.isAlive() ? trackedPoint(target) : PatriotRemoteEffects.opticalPoint(camera.trackedUuid, camera.trackedEntityId);
         if (point == null) {
            point = camera.lastTarget;
         }

         Vec3 origin = camera.anchor.position();
         Vec3 direction = point.subtract(origin);
         if (!(direction.lengthSqr() < 1.0E-8)) {
            event.setYaw((float)Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F);
            event.setPitch((float)(-Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()))));
            event.setRoll(0.0F);
            ClientCamera.moveCameraToNose(event.getCamera(), origin, event.getYaw(), event.getPitch());
         }
      }
   }

   void track(Entity truck, PatriotDefenseNetwork.Contact contact) {
      if (contact == null) {
         this.releaseCamera();
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (truck != null && mc.level != null && truck.level() == mc.level && truck.isAlive()) {
            this.track(truck.position().add(0.0, 5.0, 0.0), contact.x(), contact.y(), contact.z(), contact.entityId(), contact.id());
         } else {
            this.releaseCamera();
         }
      }
   }

   public void track(Entity vehicle, double tx, double ty, double tz, int targetEntityId) {
      Minecraft mc = Minecraft.getInstance();
      if (vehicle != null && mc.level != null && vehicle.level() == mc.level && vehicle.isAlive()) {
         this.track(vehicle.position().add(0.0, 5.0, 0.0), tx, ty, tz, targetEntityId);
      } else {
         this.releaseCamera();
      }
   }

   public void track(Vec3 origin, double tx, double ty, double tz, int targetEntityId) {
      this.track(origin, tx, ty, tz, targetEntityId, null);
   }

   public void track(Vec3 origin, double tx, double ty, double tz, int targetEntityId, UUID targetUuid) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         this.releaseCamera();
      } else {
         RemoteFlights.updatePoses();
         if (this.anchor == null) {
            this.previousCamera = mc.getCameraEntity();
            this.previousMode = mc.options.getCameraType();
            this.anchor = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
         }

         Vec3 target = new Vec3(tx, ty, tz);
         this.trackedEntityId = targetEntityId;
         this.trackedUuid = targetUuid;
         Entity replica = this.target();
         if (replica != null) {
            target = trackedPoint(replica);
         } else {
            Vec3 remote = PatriotRemoteEffects.opticalPoint(targetUuid, targetEntityId);
            if (remote != null) {
               target = remote;
            }
         }

         this.trackedEntityId = targetEntityId;
         this.lastTarget = target;
         Vec3 direction = target.subtract(origin);
         this.anchor.setPos(origin);
         this.anchor.setYRot((float)Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F);
         this.anchor.setXRot((float)(-Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()))));
         this.anchor.xo = this.anchor.getX();
         this.anchor.yo = this.anchor.getY();
         this.anchor.zo = this.anchor.getZ();
         this.anchor.yRotO = this.anchor.getYRot();
         this.anchor.xRotO = this.anchor.getXRot();
         this.fov = Math.max(0.16, Math.min(35.0, Math.toDegrees(2.0 * Math.atan(18.0 / Math.max(1.0, direction.length())))));
         this.tracking = true;
         active = this;
         mc.setCameraEntity(this.anchor);
         mc.options.setCameraType(CameraType.FIRST_PERSON);
      }
   }

   private Entity target() {
      Entity target = this.trackedUuid == null ? RemoteFlights.get(this.trackedEntityId) : RemoteFlights.get(this.trackedUuid);
      ClientLevel level = Minecraft.getInstance().level;
      if (target == null && level != null) {
         Entity candidate = level.getEntity(this.trackedEntityId);
         if (candidate != null && (this.trackedUuid == null || this.trackedUuid.equals(candidate.getUUID()))) {
            target = candidate;
         }
      }

      return target;
   }

   private static Vec3 trackedPoint(Entity target) {
      Vec3 interceptor = PatriotRemoteEffects.opticalPoint(target.getUUID(), target.getId());
      if (interceptor != null) {
         return interceptor;
      } else {
         return target instanceof GuidedMissileEntity missile
            ? MissileGeometry.worldCenter(missile.kind(), missile.position(), missile.getYRot(), missile.getXRot())
            : target.position();
      }
   }

   static void beforeGui(Pre event) {
      event.getGuiGraphics().flush();
      GUI_BLEND.apply();
      RenderSystem.defaultBlendFunc();
      if (active != null && active.tracking) {
         active.captureWorld();
      }
   }

   static void beforeVignette(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
      if (event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CAMERA_OVERLAYS)) {
         event.getGuiGraphics().flush();
         GUI_BLEND.apply();
         GameRenderer.getPositionTexShader().apply();
         RenderSystem.defaultBlendFunc();
      }
   }

   void capture(GuiGraphics graphics) {
      if (this.tracking) {
         graphics.flush();
      }
   }

   private void captureWorld() {
      Minecraft mc = Minecraft.getInstance();
      RenderTarget source = mc.getMainRenderTarget();
      if (this.frame == null) {
         this.frame = new TextureTarget(source.width, source.height, false, Minecraft.ON_OSX);
      }

      if (this.frame.width != source.width || this.frame.height != source.height) {
         this.frame.resize(source.width, source.height, Minecraft.ON_OSX);
      }

      GL30.glBindFramebuffer(36008, source.frameBufferId);
      GL30.glBindFramebuffer(36009, this.frame.frameBufferId);
      GL30.glBlitFramebuffer(0, 0, source.width, source.height, 0, 0, this.frame.width, this.frame.height, 16384, 9728);
      source.bindWrite(true);
   }

   boolean render(GuiGraphics graphics, int x, int y, int w, int h) {
      if (this.tracking && this.frame != null) {
         graphics.flush();

         ShaderInstance currentShader = switch (this.mode) {
            case NORMAL -> opticalShader != null ? opticalShader : GameRenderer.getPositionTexShader();
            case NIGHT_VISION -> nvgShader != null ? nvgShader : GameRenderer.getPositionTexShader();
            case THERMAL -> thermalShader != null ? thermalShader : GameRenderer.getPositionTexShader();
         };
         RenderSystem.setShader(() -> currentShader);
         RenderSystem.setShaderTexture(0, this.frame.getColorTextureId());
         currentShader.apply();
         RenderSystem.disableBlend();
         RenderSystem.disableCull();
         RenderSystem.disableDepthTest();
         float halfU = 0.5F;
         float halfV = 0.5F;
         float sourceAspect = (float)this.frame.width / this.frame.height;
         float panelAspect = (float)w / h;
         if (panelAspect < sourceAspect) {
            halfU *= panelAspect / sourceAspect;
         } else {
            halfV *= sourceAspect / panelAspect;
         }

         Matrix4f matrix = graphics.pose().last().pose();
         BufferBuilder builder = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         builder.addVertex(matrix, x, y + h, 0.0F).setUv(0.5F - halfU, 0.5F - halfV);
         builder.addVertex(matrix, x + w, y + h, 0.0F).setUv(0.5F + halfU, 0.5F - halfV);
         builder.addVertex(matrix, x + w, y, 0.0F).setUv(0.5F + halfU, 0.5F + halfV);
         builder.addVertex(matrix, x, y, 0.0F).setUv(0.5F - halfU, 0.5F + halfV);
         dev.sbw.deepstrike.compat.RenderCompat.draw(builder);
         RenderSystem.enableCull();
         RenderSystem.enableDepthTest();
         return true;
      } else {
         return false;
      }
   }

   public PatriotThermalCamera.CameraMode getMode() {
      return this.mode;
   }

   public void setMode(PatriotThermalCamera.CameraMode mode) {
      this.mode = mode;
   }

   public void cycleMode() {
      PatriotThermalCamera.CameraMode[] modes = PatriotThermalCamera.CameraMode.values();
      this.mode = modes[(this.mode.ordinal() + 1) % modes.length];
   }

   public float getZoom() {
      return this.zoom;
   }

   public void zoomIn() {
      this.zoom = Math.min(12.0F, this.zoom + 1.0F);
   }

   public void zoomOut() {
      this.zoom = Math.max(1.0F, this.zoom - 1.0F);
   }

   public void adjustZoom(float delta) {
      this.zoom = Math.max(1.0F, Math.min(12.0F, this.zoom + delta));
   }

   public void stopTracking() {
      this.releaseCamera();
   }

   private void releaseCamera() {
      Minecraft mc = Minecraft.getInstance();
      if (this.anchor != null && mc.getCameraEntity() == this.anchor) {
         mc.setCameraEntity(
            (Entity)(this.previousCamera != null && this.previousCamera.isAlive() && this.previousCamera.level() == mc.level
               ? this.previousCamera
               : mc.player)
         );
         if (this.previousMode != null) {
            mc.options.setCameraType(this.previousMode);
         }
      }

      this.anchor = null;
      this.previousCamera = null;
      this.previousMode = null;
      this.tracking = false;
      this.trackedEntityId = -1;
      this.trackedUuid = null;
      if (active == this) {
         active = null;
      }
   }

   @Override
   public void close() {
      this.releaseCamera();
      if (this.frame != null) {
         this.frame.destroyBuffers();
         this.frame = null;
      }
   }

   public static enum CameraMode {
      NORMAL(FlightUi.localized("ui.deepstrike.patriot_thermal_camera.2b3ea28c54")),
      NIGHT_VISION(FlightUi.localized("ui.deepstrike.patriot_thermal_camera.1b2d727d3a")),
      THERMAL(FlightUi.localized("ui.deepstrike.patriot_thermal_camera.23fd59fe2b"));

      public final String title;

      private CameraMode(String title) {
         this.title = title;
      }
   }
}
