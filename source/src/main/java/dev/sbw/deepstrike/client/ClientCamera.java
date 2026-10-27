package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.DeepStrikeAddon;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.validation.AirDefenseReview;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Pre;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;

public final class ClientCamera {
   private static final KeyMapping EXIT = new KeyMapping("key.deepstrike.exit_camera", 86, "key.categories.deepstrike");
   private static Entity previous;
   private static Entity followed;
   private static GuidedMissileEntity cameraAnchor;
   private static CameraType previousMode;
   private static int pendingId = -1;
   private static int pendingTicks;
   private static float lookYaw;
   private static float lookPitch;
   private static float spikeYawOffset;
   private static float spikePitchOffset;
   private static double zoom = 1.0;
   private static Method cameraPositionMethod;
   private static Method cameraRotationMethod;
   private static boolean cameraMethodsResolved;

   public static void movement(MovementInputUpdateEvent event) {
      if (followed != null) {
         Input input = event.getInput();
         input.forwardImpulse = 0.0F;
         input.leftImpulse = 0.0F;
         input.up = false;
         input.down = false;
         input.left = false;
         input.right = false;
         input.jumping = false;
      }
   }

   public static void scroll(MouseScrollingEvent event) {
      if (followed != null && Minecraft.getInstance().screen == null) {
         zoom = Mth.clamp(zoom * Math.pow(1.2, event.getScrollDeltaY()), 1.0, 6.0);
         event.setCanceled(true);
      }
   }

   public static void fov(ComputeFov event) {
      if (Boolean.getBoolean("deepstrike.fp5Review") && Boolean.getBoolean("deepstrike.reviewFar")) {
         boolean pvo = Boolean.getBoolean("deepstrike.airDefenseReview");
         if (!pvo || AirDefenseReview.phase == 4) {
            event.setFOV(Double.parseDouble(System.getProperty("deepstrike.reviewFarFov", "8")));
         } else if (AirDefenseReview.phase == 6) {
            event.setFOV(30.0);
         }
      } else if (followed != null) {
         event.setFOV(85.0 / zoom);
      }
   }

   public static void register(RegisterKeyMappingsEvent event) {
      event.register(EXIT);
   }

   public static void view(int entityId) {
      Minecraft mc = Minecraft.getInstance();
      if (entityId < 0) {
         restore();
      } else {
         Entity missile = RemoteFlights.get(entityId);
         if (missile == null) {
            missile = mc.level == null ? null : mc.level.getEntity(entityId);
         }

         if (missile == null) {
            pendingId = entityId;
            pendingTicks = 80;
            mc.player.displayClientMessage(Component.translatable("message.deepstrike.camera_wait"), true);
         } else {
            pendingId = -1;
            apply(mc, missile);
         }
      }
   }

   public static boolean turn(double dx, double dy) {
      Minecraft mc = Minecraft.getInstance();
      if (followed == null || mc.screen != null) {
         return false;
      } else if (spike() != null && !mc.mouseHandler.isRightPressed()) {
         SpikeTvCamera.moveCursor(dx, dy);
         return true;
      } else if (spike() != null) {
         spikeYawOffset = Mth.clamp(spikeYawOffset + (float)dx * 0.09F, -100.0F, 100.0F);
         spikePitchOffset = Mth.clamp(spikePitchOffset + (float)dy * 0.09F, -75.0F, 75.0F);
         return true;
      } else {
         float sensitivity = 0.6F;
         lookYaw = Mth.wrapDegrees(lookYaw + (float)dx * 0.15F * sensitivity);
         lookPitch = Mth.clamp(lookPitch + (float)dy * 0.15F * sensitivity, -89.0F, 89.0F);
         return true;
      }
   }

   static double viewFov() {
      return 85.0 / zoom;
   }

   static String equipmentSkin() {
      return followed instanceof GuidedMissileEntity m ? (m.graphite() ? "gray" : m.camo()) : "";
   }

   public static boolean isFollowed(Entity entity) {
      return followed == entity;
   }

   public static GuidedMissileEntity spike() {
      return followed instanceof GuidedMissileEntity m && m.kind() == MissileKind.SPIKE_NLOS ? m : null;
   }

   private static void apply(Minecraft mc, Entity missile) {
      if (followed == null) {
         previous = mc.getCameraEntity();
         previousMode = mc.options.getCameraType();
      }

      followed = missile;
      RemoteCameraTerrain.start(missile.getUUID());
      zoom = 1.0;
      lookYaw = missile.getYRot();
      lookPitch = missile.getXRot();
      spikePitchOffset = 0.0F;
      spikeYawOffset = 0.0F;
      SpikeTvCamera.resetCursor();
      cameraAnchor = new GuidedMissileEntity(mc.level, missile instanceof GuidedMissileEntity m ? m.kind() : MissileKind.JAGM);
      updateAnchor(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
      cameraAnchor.xo = cameraAnchor.getX();
      cameraAnchor.yo = cameraAnchor.getY();
      cameraAnchor.zo = cameraAnchor.getZ();
      cameraAnchor.yRotO = cameraAnchor.getYRot();
      cameraAnchor.xRotO = cameraAnchor.getXRot();
      mc.setScreen(null);
      mc.setCameraEntity(cameraAnchor);
      mc.options.setCameraType(CameraType.FIRST_PERSON);
      mc.player.displayClientMessage(Component.translatable("message.deepstrike.camera_on"), true);
   }

   public static void tick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (followed != null && RemoteFlights.get(followed.getId()) != null) {
            followed = RemoteFlights.get(followed.getId());
         }

         if (pendingId >= 0) {
            Entity target = RemoteFlights.get(pendingId);
            if (target == null) {
               target = mc.level == null ? null : mc.level.getEntity(pendingId);
            }

            if (target != null) {
               pendingId = -1;
               apply(mc, target);
            } else if (--pendingTicks <= 0) {
               pendingId = -1;
               if (mc.player != null) {
                  mc.player.displayClientMessage(Component.translatable("message.deepstrike.camera_lost"), true);
               }
            }
         }

         if (followed != null) {
            updateAnchor(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            RemoteCameraTerrain.tick();
         }

         if (followed == null || followed.isAlive() && mc.level != null && !EXIT.consumeClick()) {
            if (!PatriotThermalCamera.isActive() && followed != null && cameraAnchor != null && mc.getCameraEntity() != cameraAnchor) {
               mc.setCameraEntity(cameraAnchor);
            }

            if (followed == null) {
               EXIT.consumeClick();
            }
         } else {
            restore();
         }
      }
   }

   public static void renderTick(RenderTickEvent event) {
      if (event.phase == Phase.START && followed != null && cameraAnchor != null && !PatriotThermalCamera.isActive()) {
         RemoteFlights.updatePoses();
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && followed.isAlive()) {
            updateAnchor(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            if (mc.getCameraEntity() != cameraAnchor) {
               mc.setCameraEntity(cameraAnchor);
            }
         }
      }
   }

   private static void updateAnchor(float partial) {
      if (followed != null && cameraAnchor != null) {
         if (followed instanceof GuidedMissileEntity missile) {
            Vec3 direction = followed.getViewVector(partial);
            if (direction.lengthSqr() < 1.0E-5) {
               direction = new Vec3(0.0, 0.0, 1.0);
            }

            Vec3 origin = RemoteFlights.get(followed.getId()) == followed ? followed.position() : followed.getPosition(partial);
            Vec3 cameraPos = origin.add(direction.normalize().scale(MissileGeometry.noseDistance(missile.kind()) + 0.05));
            cameraAnchor.setPos(cameraPos.x, cameraPos.y, cameraPos.z);
            cameraAnchor.setYRot(followed.getYRot());
            cameraAnchor.setXRot(followed.getXRot());
         } else {
            double eyeH = followed.getEyeHeight() > 0.5 ? followed.getEyeHeight() : 2.0;
            Vec3 cameraPos = followed.position().add(0.0, eyeH, 0.0);
            cameraAnchor.setPos(cameraPos.x, cameraPos.y, cameraPos.z);
            cameraAnchor.setYRot(followed.getYRot());
            cameraAnchor.setXRot(followed.getXRot());
         }

         cameraAnchor.xo = cameraAnchor.getX();
         cameraAnchor.yo = cameraAnchor.getY();
         cameraAnchor.zo = cameraAnchor.getZ();
         cameraAnchor.yRotO = cameraAnchor.getYRot();
         cameraAnchor.xRotO = cameraAnchor.getXRot();
      }
   }

   public static void renderOverlay(Pre event) {
      if (spike() == null) {
         if (followed != null && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            GuiGraphics g = event.getGuiGraphics();
            UiViewport.begin(g);
            int w = UiViewport.width();
            int h = UiViewport.height();
            int cx = w / 2;
            int cy = h / 2;
            int margin = Math.max(12, w / 50);
            int base = h - 109;
            int cardW = Math.max(88, Math.min(126, w / 5));
            FlightInstruments.card(g, margin, margin, Math.min(260, w / 4), 43);
            String name = followed instanceof GuidedMissileEntity missile ? missile.kind().title().getString() : followed.getType().getDescription().getString();
            FlightInstruments.type(g, FlightUi.clip(name, Math.min(240, w / 4 - 20)), margin + 12, margin + 8, -1182997);
            FlightInstruments.type(g, FlightInstruments.tr("observation"), margin + 12, margin + 25, -5196890);
            FlightInstruments.heading(g, cx, margin + 15, Math.min(270, w / 3), followed.getYRot());
            FlightInstruments.card(g, w - margin - 100, margin, 100, 43);
            g.fill(w - margin - 87, margin + 11, w - margin - 82, margin + 16, -1076610);
            FlightInstruments.type(
               g,
               FlightUi.localized(followed instanceof GuidedMissileEntity ? "hud.deepstrike.camera.recording" : "ui.deepstrike.client_camera.bbb8b68e23"),
               w - margin - 74,
               margin + 8,
               -1182997
            );
            FlightInstruments.type(g, String.format(Locale.ROOT, "%.1f×", zoom), w - margin - 86, margin + 25, -5196890);
            FlightInstruments.reticle(g, cx, cy, 24, false);
            FlightInstruments.metric(g, margin, base, cardW, FlightInstruments.tr("altitude"), String.format(Locale.ROOT, "%.0f", followed.getY()), "m");
            FlightInstruments.metric(
               g,
               margin + cardW + 7,
               base,
               cardW,
               FlightInstruments.tr("speed"),
               String.format(Locale.ROOT, "%.0f", FlightUnits.kilometresPerHour(followed.getDeltaMovement().length())),
               "km/h"
            );
            String coords = String.format(Locale.ROOT, "X %.0f   Y %.0f   Z %.0f", followed.getX(), followed.getY(), followed.getZ());
            int coordinatesWidth = Minecraft.getInstance().font.width(FlightUi.text(coords)) + 24;
            FlightInstruments.card(g, w - margin - coordinatesWidth, base + 16, coordinatesWidth, 32);
            FlightInstruments.type(g, coords, w - margin - coordinatesWidth + 12, base + 27, -1182997);
            FlightInstruments.footer(g, FlightUi.localized("ui.deepstrike.client_camera.4e814f6a65"), w, h);
            UiViewport.end(g);
         }
      }
   }

   public static void computeCameraAngles(ComputeCameraAngles event) {
      Minecraft mc = Minecraft.getInstance();
      if (followed != null && cameraAnchor != null && mc.player != null && mc.level != null && !PatriotThermalCamera.isActive()) {
         Vec3 nose = cameraAnchor.getPosition((float)event.getPartialTick());
         float yaw = lookYaw;
         float pitch = lookPitch;
         if (spike() != null) {
            Vec3 velocity = followed.getDeltaMovement();
            float baseYaw = velocity.horizontalDistanceSqr() > 1.0E-6 ? (float)Math.toDegrees(Math.atan2(-velocity.x, velocity.z)) : followed.getYRot();
            float basePitch = velocity.lengthSqr() > 1.0E-6
               ? (float)(-Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance())))
               : followed.getXRot();
            yaw = baseYaw + spikeYawOffset;
            pitch = Mth.clamp(basePitch + spikePitchOffset, -89.0F, 89.0F);
         }

         event.setYaw(yaw);
         event.setPitch(pitch);
         event.setRoll(0.0F);
         if (!moveCameraToNose(event.getCamera(), nose, yaw, pitch)) {
            event.getCamera().setup(mc.level, cameraAnchor, false, false, (float)event.getPartialTick());
         }

         if (Boolean.getBoolean("deepstrike.stage2Visual")) {
            StageTwoVisualReview.inspectCamera(event.getCamera(), followed, yaw, pitch);
         }
      }
   }

   static boolean moveCameraToNose(Camera camera, Vec3 nose, float yaw, float pitch) {
      if (!cameraMethodsResolved) {
         cameraMethodsResolved = true;

         for (Method method : Camera.class.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() == void.class) {
               if (parameters.length == 1 && parameters[0] == Vec3.class) {
                  cameraPositionMethod = method;
               }

               if (parameters.length == 2
                  && parameters[0] == float.class
                  && parameters[1] == float.class
                  && (method.getName().equals("setRotation") || method.getName().equals("setRotation") || Modifier.isProtected(method.getModifiers()))) {
                  cameraRotationMethod = method;
               }
            }
         }

         try {
            if (cameraPositionMethod != null) {
               cameraPositionMethod.setAccessible(true);
            }

            if (cameraRotationMethod != null) {
               cameraRotationMethod.setAccessible(true);
            }
         } catch (RuntimeException var10) {
            DeepStrikeAddon.LOGGER.error("SPIKE camera access failed", var10);
            cameraPositionMethod = null;
            cameraRotationMethod = null;
         }
      }

      try {
         if (cameraPositionMethod != null && cameraRotationMethod != null) {
            cameraPositionMethod.invoke(camera, nose);
            cameraRotationMethod.invoke(camera, yaw, pitch);
            return true;
         } else {
            return false;
         }
      } catch (ReflectiveOperationException var9) {
         DeepStrikeAddon.LOGGER.error("SPIKE nose camera update failed", var9);
         cameraPositionMethod = null;
         cameraRotationMethod = null;
         return false;
      }
   }

   public static void restore() {
      RemoteCameraTerrain.clear();
      Minecraft mc = Minecraft.getInstance();
      pendingId = -1;
      if (previous != null && mc.level != null && previous.isAlive()) {
         mc.setCameraEntity(previous);
      } else if (mc.player != null) {
         mc.setCameraEntity(mc.player);
      }

      if (previousMode != null) {
         mc.options.setCameraType(previousMode);
      }

      previous = null;
      followed = null;
      cameraAnchor = null;
      previousMode = null;
   }

   private ClientCamera() {
   }
}
