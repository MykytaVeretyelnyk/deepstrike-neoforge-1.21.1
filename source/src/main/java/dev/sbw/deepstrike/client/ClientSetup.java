package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.DudMissileEntity;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LaunchCoverEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileItem;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.LauncherLayout;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.model.ModelGeometry;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFMenus;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import dev.sbw.deepstrike.DeepStrikeAddon;
import dev.sbw.deepstrike.compat.TickEvent;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;

public final class ClientSetup {
   public static void register(IEventBus bus) {
      DeepStrikeAddon.CONTAINER.registerConfig(Type.CLIENT, MapProviders.CONFIG, "dsm-client.toml");
      TickEvent.installClient(NeoForge.EVENT_BUS);
      bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(MapProviders::selected));
      bus.addListener(ClientSetup::registerRenderers);
      NeoForge.EVENT_BUS.addListener(UiHelp::before);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, UiHelp::after);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, UiHelp::click);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, UiHelp::key);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, UiHelp::scroll);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, UiHelp::drag);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, UiHelp::typed);
      bus.addListener(PatriotThermalCamera::registerShaders);
      bus.addListener(EngineLightRenderer::registerShaders);
      bus.addListener(GroundLightRenderer::register);
      bus.addListener(MissileEffectsRenderTypes::registerShaders);
      bus.addListener(DistantFlightRenderer::registerShaders);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PatriotThermalCamera::computeFov);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PatriotThermalCamera::renderTick);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PatriotThermalCamera::beforeGui);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PatriotThermalCamera::beforeVignette);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Fp5LaunchReview::guiProbe);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Fp5LaunchReview::vignetteProbe);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PatriotThermalCamera::computeAngles);
      bus.addListener(ClientSetup::setupMenus);
      bus.addListener(ClientSetup::registerItemExtensions);
      bus.addListener(ClientCamera::register);
      bus.addListener(SpikeTvCamera::register);
      NeoForge.EVENT_BUS.addListener(SpikeTvCamera::tick);
      NeoForge.EVENT_BUS.addListener(SpikeTvCamera::mouse);
      NeoForge.EVENT_BUS.addListener(ClientCamera::movement);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpikeTvCamera::render);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientCamera::computeCameraAngles);
      bus.addListener(PatriotDefenseScreen::register);
      bus.addListener(ExhaustParticle::register);
      bus.addListener(BlastParticle::register);
      bus.addListener(ThermalAtlas::registerReload);
      NeoForge.EVENT_BUS.addListener(MissileExplosionEffects::tick);
      NeoForge.EVENT_BUS.addListener(MissileExplosionEffects::renderFlash);
      NeoForge.EVENT_BUS.addListener(MissileExplosionEffects::renderSkyGlow);
      NeoForge.EVENT_BUS.addListener(MissileExplosionEffects::onRenderLevelStage);
      NeoForge.EVENT_BUS.addListener(MissileExplosionEffects::onComputeFogColor);
      NeoForge.EVENT_BUS.addListener(MissileExplosionEffects::onComputeCameraAngles);
      NeoForge.EVENT_BUS.addListener(MissileExplosionEffects::registerClientCommands);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, FlightDisplay::renderTick);
      NeoForge.EVENT_BUS.addListener(FlightAuthorityReview::tick);
      NeoForge.EVENT_BUS.addListener(RemoteFlights::tick);
      NeoForge.EVENT_BUS.addListener(RemoteFlights::renderTick);
      NeoForge.EVENT_BUS.addListener(StageTwoVisualReview::renderTick);
      NeoForge.EVENT_BUS.addListener(StageTwoVisualReview::serverTick);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LateWorldEffects::capture);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EngineFlamePostPass::render);
      NeoForge.EVENT_BUS.addListener(RemoteFlights::render);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RemoteFlights::renderSensor);
      NeoForge.EVENT_BUS.addListener(PatriotRemoteEffects::tick);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PatriotRemoteEffects::renderTick);
      NeoForge.EVENT_BUS.addListener(PatriotRemoteEffects::render);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PatriotRemoteEffects::renderSensor);
      NeoForge.EVENT_BUS.addListener(ClientCamera::scroll);
      NeoForge.EVENT_BUS.addListener(ClientCamera::fov);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientCamera::tick);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientCamera::renderTick);
      NeoForge.EVENT_BUS.addListener(PatriotDefenseScreen::clientTick);
      NeoForge.EVENT_BUS.addListener(ModelPreviewScreen::onClientTick);
      NeoForge.EVENT_BUS.addListener(WorldVisualReview::tick);
      NeoForge.EVENT_BUS.addListener(Su24mScreen::clientTick);
      NeoForge.EVENT_BUS.addListener(Su24mReview::tick);
      NeoForge.EVENT_BUS.addListener(Su24mReview::render);
      NeoForge.EVENT_BUS.addListener(EngineGroundReview::scene);
      NeoForge.EVENT_BUS.addListener(SmokeVisualReview::tick);
      NeoForge.EVENT_BUS.addListener(SmokeVisualReview::render);
      NeoForge.EVENT_BUS.addListener(Fp5LaunchReview::tick);
      NeoForge.EVENT_BUS.addListener(Fp5LaunchReview::render);
      NeoForge.EVENT_BUS.addListener(Fp5LaunchReview::serverTick);
      NeoForge.EVENT_BUS.addListener(PatriotCanisterReview::serverTick);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PatriotCanisterReview::renderLate);
      NeoForge.EVENT_BUS.addListener(ExhaustVisualReview::tick);
      NeoForge.EVENT_BUS.addListener(ExhaustVisualReview::render);
      NeoForge.EVENT_BUS.addListener(ExhaustVisualReview::scene);
      NeoForge.EVENT_BUS.addListener(ClientCamera::renderOverlay);
      NeoForge.EVENT_BUS.addListener(LaunchCountdownHud::render);
      NeoForge.EVENT_BUS.addListener(DeepStrikeMapScreen::onScreenRenderPost);
      NeoForge.EVENT_BUS.addListener(SonicBoomEffects::onClientTick);
      NeoForge.EVENT_BUS.addListener(SonicBoomEffects::onPlaySound);
      NeoForge.EVENT_BUS.addListener(SonicBoomEffects::onRenderGuiOverlay);
      NeoForge.EVENT_BUS.addListener(SonicBoomEffects::onComputeCameraAngles);
      NeoForge.EVENT_BUS.addListener(ContrailRenderer::clientTick);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ContrailRenderer::onRenderLevelStage);
      NeoForge.EVENT_BUS.addListener(FlightLighting::render);
      NeoForge.EVENT_BUS.addListener((dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent e) -> {
         if (e.phase == Phase.END) {
            MissileFlightAcoustics.clientTick();
         }
      });
   }

   public static void openIronDomeScreen(int vehicleId) {
      Minecraft.getInstance().setScreen(new IronDomeDefenseScreen(vehicleId));
   }

   public static void openPatriotScreen(int vehicleId) {
      Minecraft.getInstance().setScreen(new PatriotDefenseScreen(vehicleId));
   }

   public static void openPaintScreen(int vehicleId) {
      Minecraft.getInstance().setScreen(new VehiclePaintScreen(vehicleId));
   }

   private static void registerItemExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
      for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
         if (item instanceof dev.sbw.deepstrike.game.AirDefenseMissileItem
            || item instanceof dev.sbw.deepstrike.game.AirDefenseSpawnItem
            || item instanceof dev.sbw.deepstrike.game.MissileItem
            || item instanceof dev.sbw.deepstrike.game.ModelBlockItem) {
            ModelItemRenderer.register(extensions -> event.registerItem(extensions, item));
         }
      }
   }

   private static void setupMenus(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
      event.register(BFMenus.LAUNCHER.get(), LauncherInventoryScreen::new);
      event.register(BFMenus.MISSILE_ASSEMBLY.get(), MissileAssemblyScreen::new);
   }

   private static void registerRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)BFEntities.GUIDED_MISSILE.get(), ClientSetup.MissileRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.DUD_MISSILE.get(), ClientSetup.DudRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.PATRIOT_INTERCEPTOR.get(), PatriotInterceptorRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.LAUNCH_COVER.get(), ClientSetup.CoverRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)BFBlocks.LAUNCHER_BE.get(), context -> new ClientSetup.LauncherRenderer());
      event.registerBlockEntityRenderer((BlockEntityType)BFBlocks.SIREN_BE.get(), AirRaidSirenRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.PATRIOT_TRUCK.get(), AirDefenseVehicleRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.IRON_DOME.get(), AirDefenseVehicleRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.AN_MPQ_65.get(), AirDefenseVehicleRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.FP5_BOOSTER.get(), Fp5BoosterRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.SPIKE_NLOS.get(), AirDefenseVehicleRenderer::new);
      event.registerEntityRenderer((EntityType)BFEntities.SU24M.get(), Su24mRenderer::new);
   }

   private static ResourceLocation texture(String name) {
      return ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/entity/" + name + ".png");
   }

   static void renderExhaust(GuidedMissileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
      renderExhaust(e, partial, pose, buffers, false);
   }

   static void renderExhaust(GuidedMissileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean depth) {
      if (e.fp5BoosterBurning()) {
         pose.pushPose();
         pose.translate(0.75, -0.478125, 0.0);
         Fp5BoosterFire.render(pose, buffers, (float)((e.tickCount + partial) * 1.0), 2, true, depth);
         pose.popPose();
      }

      if (e.kind() != MissileKind.FP5 && e.kind() != MissileKind.STORM_SHADOW) {
         if (e.exhaustVisible()) {
            float age = e.tickCount + partial;
            float motorAge = ExhaustStyles.motorAge(e.kind(), age);
            pose.pushPose();
            pose.translate(MissileGeometry.tailDistance(e.kind()), 0.0, 0.0);
            Minecraft mc = Minecraft.getInstance();
            double distance = e.position().distanceToSqr(mc.gameRenderer.getMainCamera().getPosition());
            Fp5BoosterFire.renderMotor(
               pose,
               buffers,
               age,
               ExhaustStyles.motorRadius(e.kind(), motorAge),
               ExhaustStyles.motorLength(e.kind(), motorAge),
               ExhaustStyles.motorBrightness(e.kind(), motorAge),
               distance,
               depth
            );
            pose.popPose();
         }
      }
   }

   static void renderDistantExhaust(GuidedMissileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
      if (e.kind() != MissileKind.FP5 && e.kind() != MissileKind.STORM_SHADOW) {
         if (e.exhaustVisible()) {
            ExhaustPlume.renderVariant(
               pose, buffers, e.tickCount + partial, (float)MissileGeometry.tailDistance(e.kind()), e.kind(), 2, ExhaustStyles.burnStage(e)
            );
         }
      }
   }

   private ClientSetup() {
   }

   private static final class CoverRenderer extends EntityRenderer<LaunchCoverEntity> {
      CoverRenderer(Context context) {
         super(context);
         this.shadowRadius = 0.18F;
      }

      public ResourceLocation getTextureLocation(LaunchCoverEntity e) {
         return ClientSetup.texture("jagm_atlas");
      }

      public void render(LaunchCoverEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
         pose.pushPose();
         pose.mulPose(ModelGeometry.flight(e.getYRot(), e.getXRot()));
         float tumble = e.spin(partial);
         pose.mulPose(Axis.ZP.rotationDegrees(tumble * 13.0F));
         pose.mulPose(Axis.XP.rotationDegrees(tumble * 7.0F));
         ModelRenderer.render("jagm_cover", "jagm_atlas", pose, buffers, light, OverlayTexture.NO_OVERLAY, 0.0F, 0.0F, b -> true);
         pose.popPose();
      }
   }

   private static final class DudRenderer extends EntityRenderer<DudMissileEntity> {
      DudRenderer(Context context) {
         super(context);
      }

      public ResourceLocation getTextureLocation(DudMissileEntity e) {
         return ClientSetup.texture(ModelAssets.texture(e.kind(), e.camo(), e.graphite()));
      }

      public void render(DudMissileEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
         DudWreckRenderer.render(e, pose, buffers, light);
         DudImpactRenderer.render(e, pose, buffers, light);
      }
   }

   private static final class LauncherRenderer implements BlockEntityRenderer<LauncherBlockEntity> {
      private final Map<LauncherBlockEntity, ClientSetup.LauncherRenderer.LauncherPose> poses = new WeakHashMap<>();

      @Override
      public net.minecraft.world.phys.AABB getRenderBoundingBox(LauncherBlockEntity be) {
         return be.getRenderBoundingBox();
      }

      public void render(LauncherBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
         String texture = ModelAssets.launcherTexture(be.family(), ((LauncherBlock)be.getBlockState().getBlock()).graphite());
         float yaw = be.renderYaw();
         float elevation = be.renderElevation();
         ModelGeometry model = ModelGeometry.get(ModelAssets.launcher(be.family()));
         ClientSetup.LauncherRenderer.LauncherPose cached = this.poses.get(be);
         if (cached == null || cached.yaw() != yaw || cached.elevation() != elevation) {
            cached = new ClientSetup.LauncherRenderer.LauncherPose(yaw, elevation, model.transforms(yaw, elevation));
            this.poses.put(be, cached);
         }

         Map<String, Matrix4f> transforms = cached.transforms();
         pose.pushPose();
         pose.mulPose(LauncherLayout.root(be.family(), be.facingYaw()));
         ModelRenderer.render(
            ModelAssets.launcher(be.family()), texture, pose, buffers, light, overlay, transforms, b -> !b.startsWith("cover_") && !b.equals("booster")
         );
         pose.popPose();
         int cap = be.capacity();
         Matrix4f baseMount = null;

         for (int i = 0; i < cap; i++) {
            if (be.slot(i).getItem() instanceof MissileItem missile) {
               if (baseMount == null) {
                  baseMount = LauncherLayout.baseMount(be.family(), be.facingYaw(), transforms);
               }

               Matrix4f mount = LauncherLayout.slotMount(be.family(), baseMount, i);
               float t = be.loadProgress(i, partial);
               t = t * t * (3.0F - 2.0F * t);
               pose.pushPose();
               pose.mulPose(mount);
               if (be.family() == LauncherBlock.Family.JAGM) {
                  pose.translate((1.0F - t) * 2.4, 0.0, 0.0);
               } else if (be.family() == LauncherBlock.Family.PEACEKEEPER) {
                  pose.translate(0.0F, (1.0F - t) * 3.5F, 0.0F);
               } else {
                  pose.translate(0.0, (1.0F - t) * 0.9, 0.0);
               }

               MissileData md = MissileData.fromItemStack(be.slot(i));
               String camo = md != null ? md.camo() : MissileData.defaultCamo(missile.kind());
               ModelRenderer.render(
                  ModelAssets.missile(missile.kind()),
                  ModelAssets.texture(missile.kind(), camo, missile.graphite()),
                  pose,
                  buffers,
                  light,
                  overlay,
                  0.0F,
                  0.0F,
                  b -> true
               );
               pose.popPose();
               if (be.family() == LauncherBlock.Family.JAGM && be.coverVisible(i)) {
                  pose.pushPose();
                  pose.mulPose(mount);
                  pose.translate(-1.196875, 0.0, 0.0);
                  float close = Mth.clamp((t - 0.45F) / 0.55F, 0.0F, 1.0F);
                  pose.translate(0.0, -0.203125, 0.0);
                  pose.mulPose(Axis.ZP.rotationDegrees(85.0F * (1.0F - close)));
                  pose.translate(0.0, 0.203125, 0.0);
                  ModelRenderer.render("jagm_cover", "jagm_atlas", pose, buffers, light, overlay, 0.0F, 0.0F, b -> true);
                  pose.popPose();
               }
            }
         }
      }

      public boolean shouldRenderOffScreen(LauncherBlockEntity be) {
         return false;
      }

      public int getViewDistance() {
         return 160;
      }

      private record LauncherPose(float yaw, float elevation, Map<String, Matrix4f> transforms) {
      }
   }

   private static final class MissileRenderer extends EntityRenderer<GuidedMissileEntity> {
      MissileRenderer(Context context) {
         super(context);
         this.shadowRadius = 0.0F;
      }

      public ResourceLocation getTextureLocation(GuidedMissileEntity e) {
         return ClientSetup.texture(ModelAssets.texture(e.kind(), e.camo(), e.graphite()));
      }

      public boolean shouldRender(GuidedMissileEntity e, Frustum frustum, double x, double y, double z) {
         return !RemoteFlights.hidesTracked(e) && !ClientCamera.isFollowed(e) && super.shouldRender(e, frustum, x, y, z);
      }

      public void render(GuidedMissileEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
         pose.pushPose();
         pose.mulPose(FlightOrientation.interpolate(e.yRotO, e.xRotO, e.getYRot(), e.getXRot(), partial));
         pose.pushPose();
         pose.mulPose(MissileGeometry.exportRotation(e.kind()));
         String atlas = ModelAssets.texture(e.kind(), e.camo(), e.graphite());
         ModelRenderer.render(
            ModelAssets.missile(e.kind()),
            PatriotThermalCamera.isThermalTarget(e) ? ThermalAtlas.forTexture(atlas) : atlas,
            pose,
            buffers,
            light,
            OverlayTexture.NO_OVERLAY,
            0.0F,
            0.0F,
            e::visibleFlightBone
         );
         pose.popPose();
         ClientSetup.renderExhaust(e, partial, pose, buffers);
         pose.popPose();
      }
   }
}
