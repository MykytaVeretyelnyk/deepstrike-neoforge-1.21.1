package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileAssemblyBlock;
import dev.sbw.deepstrike.game.MissileAssemblyBlockEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RadarDisclosure;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.menu.LauncherMenu;
import dev.sbw.deepstrike.menu.MissileAssemblyMenu;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.network.IronDomeNetwork;
import dev.sbw.deepstrike.network.MissileCameraStream;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import dev.sbw.deepstrike.network.SpikeNlosNetwork;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GuiCaptureReview {
   private static final List<GuiCaptureReview.Shot> shots = new ArrayList<>();
   private static int index = -1;
   private static int age;
   private static int frames;
   private static volatile int realJagm = -1;
   private static volatile int realSpike = -1;
   private static volatile int liveDome = -1;
   private static boolean radarOpened;
   private static boolean radarFired;
   private static volatile int hudFixture = -1;
   private static boolean hudOpened;
   private static volatile boolean radarServerVerified;
   private static volatile UUID radarThreat;
   private static boolean spikeViewing;
   private static boolean spikeCommandVerified;
   private static boolean spikeClickSent;
   private static final List<String> spikeTrace = new ArrayList<>();
   private static volatile double realSpikeCommandSpeed;
   private static boolean realViewing;
   private static final List<String> motionTrace = new ArrayList<>();
   private static float motionYaw;
   private static Vec3 lastCamera;
   private static long lastFrame;
   private static double maxCameraStep;
   private static boolean captured;
   private static boolean baseRequested;
   private static boolean helpVerified;
   private static volatile int zoneLiveId = -1;
   private static boolean zoneSent;
   private static volatile boolean zoneServerVerified;
   private static volatile Throwable zoneFailure;
   private static double zoneExpectedX;
   private static double zoneExpectedZ;
   private static volatile boolean baseReady;
   private static Entity patriot;
   private static Entity dome;
   private static Entity spike;
   private static GuidedMissileEntity jagm;
   private static GuidedMissileEntity tv;
   private static final BlockPos PAD = new BlockPos(0, 101, 0);
   private static final BlockPos ASSEMBLY = new BlockPos(4, 101, 0);
   private static final List<RoutePoint> ROUTE = List.of(
      new RoutePoint(100.0, 180.0, 90.0, 600.0), new RoutePoint(450.0, 200.0, 200.0, 600.0), new RoutePoint(650.0, 110.0, 400.0, 550.0)
   );
   private static final UUID TARGET = UUID.fromString("6677aabb-1111-2222-3333-444455556666");
   private static final Logger LOG = LoggerFactory.getLogger(GuiCaptureReview.class);

   private static Object get(Object object, String name) {
      try {
         Field f = (object instanceof Class<?> c ? c : object.getClass()).getDeclaredField(name);
         f.setAccessible(true);
         return f.get(object instanceof Class ? null : object);
      } catch (Exception var4) {
         throw new IllegalStateException(name, var4);
      }
   }

   private static void set(Object object, String name, Object value) {
      try {
         Field f = (object instanceof Class<?> c ? c : object.getClass()).getDeclaredField(name);
         f.setAccessible(true);
         f.set(object instanceof Class ? null : object, value);
      } catch (Exception var5) {
         throw new IllegalStateException(name, var5);
      }
   }

   private static void call(Object object, String name) {
      try {
         Method m = object.getClass().getDeclaredMethod(name);
         m.setAccessible(true);
         m.invoke(object);
      } catch (Exception var3) {
         throw new IllegalStateException(name, var3);
      }
   }

   private static ItemStack item(String id) {
      return new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("deepstrike", id)));
   }

   private static Entity entity(Minecraft mc, String id, int number) {
      Entity entity = ((EntityType)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("deepstrike", id))).create(mc.level);
      entity.setPos(number % 3 * 5, 101.0, 5.0);
      entity.setNoGravity(true);
      mc.level.addEntity(entity);
      return entity;
   }

   private static void verifyRadarCodec() {
      try {
         Class<PatriotDefenseNetwork.Contact> type = PatriotDefenseNetwork.Contact.class;
         Method encode = type.getDeclaredMethod("encode", type, FriendlyByteBuf.class);
         Method decode = type.getDeclaredMethod("decode", FriendlyByteBuf.class);
         encode.setAccessible(true);
         decode.setAccessible(true);

         for (int assessment = -1; assessment <= 1; assessment++) {
            PatriotDefenseNetwork.Contact value = new PatriotDefenseNetwork.Contact(TARGET, 42, 400.0, 180.0, 350.0, 680.0F, 180.0F, -1, false, assessment);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

            try {
               encode.invoke(null, value, buffer);
               if (!value.equals(decode.invoke(null, buffer)) || buffer.readableBytes() != 0) {
                  throw new IllegalStateException("Radar contact codec changed disclosure");
               }
            } finally {
               buffer.release();
            }
         }

         LOG.info("RADAR_DISCLOSURE_CODEC_PASS unknownModel=-1 assessments=-1,0,1");
      } catch (ReflectiveOperationException var10) {
         throw new IllegalStateException(var10);
      }
   }

   private static void scene(Minecraft mc) {
      verifyRadarCodec();
      mc.options.hideGui = false;
      mc.options.guiScale().set(2);
      mc.options.fov().set(70);
      mc.resizeDisplay();
      mc.player.setPos(0.0, 104.0, 20.0);
      mc.player.setYRot(180.0F);
      mc.player.setXRot(15.0F);
      patriot = entity(mc, "mim_104_patriot_truck", 801001);
      dome = entity(mc, "iron_dome", 801002);
      spike = entity(mc, "spike_nlos_jltv", 801003);
      jagm = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
      jagm.setPos(0.0, 180.0, 0.0);
      jagm.setNoGravity(true);
      mc.level.addEntity(jagm);
      tv = new GuidedMissileEntity(mc.level, MissileKind.SPIKE_NLOS);
      tv.setPos(60.0, 180.0, 0.0);
      tv.setNoGravity(true);
      mc.level.addEntity(tv);

      for (int i = 0; i < 9; i++) {
         mc.player
            .getInventory()
            .setItem(
               i,
               item(
                  new String[]{
                     "fp5", "storm_shadow", "jagm", "peacekeeper", "spike_nlos_missile", "deepstrike_laptop", "jagm_camera_tablet", "paint_spray", "tamir"
                  }[i]
               )
            );
      }

      add("00-xaero-normal", "Обычная карта Xaero · интерфейс сохранён", () -> MapProviders.openNativeMap());
      add("01-xaero-map", "Карта пуска · Xaero", () -> map(mc, true, false, false));
      add("01-xaero-clean", "Карта · скрытые панели", () -> {
         map(mc, true, true, false);
         DeepStrikeMapScreen screen = (DeepStrikeMapScreen)mc.screen;
         List<RoutePoint> points = List.copyOf((List)get(screen, "route"));
         double x = screen.getCameraX();
         double z = screen.getCameraZ();
         screen.keyPressed(72, 0, 0);
         if (!(Boolean)get(screen, "chromeHidden") || screen.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.visible)) {
            throw new IllegalStateException("Map chrome did not hide");
         } else if (points.equals(get(screen, "route")) && x == screen.getCameraX() && z == screen.getCameraZ()) {
            LOG.info("GUI_CLEAN_MAP_PASS routePreserved=true cameraPreserved=true hiddenFields=true");
         } else {
            throw new IllegalStateException("Hiding chrome changed the mission or camera");
         }
      });
      add("01-xaero-route", "Карта · список точек", () -> {
         map(mc, true, false, false);
         set(mc.screen, "routeOpen", true);
      });
      add("01-xaero-profile", "Карта · график высоты", () -> {
         map(mc, true, false, false);
         set(mc.screen, "profileOpen", true);
         set(mc.screen, "panelH", Math.max(100, mc.screen.height - 179));
         call(mc.screen, "layoutFields");
      });
      add("01-xaero-settings", "Карта Xaero · настройки маршрута", () -> map(mc, true, true, false));
      add("01-xaero-presets", "Карта Xaero · пресеты", () -> map(mc, true, true, true));
      add("01-xaero-context", "Карта Xaero · меню точки", () -> {
         map(mc, true, false, false);
         context(mc.screen);
      });
      add("02-built-in-map", "Встроенная карта пуска", () -> map(mc, false, false, false));
      add("02-built-in-settings", "Встроенная карта · настройки", () -> map(mc, false, true, false));
      add("02-built-in-presets", "Встроенная карта · пресеты", () -> map(mc, false, true, true));
      add("02-built-in-context", "Встроенная карта · меню точки", () -> {
         map(mc, false, false, false);
         context(mc.screen);
      });
      add("03-route-point", "Промежуточная точка", () -> mc.setScreen(new RoutePointScreen(null, ROUTE.get(0), false, MissileKind.FP5, p -> {})));
      add("03-terminal-point", "Конечная точка", () -> mc.setScreen(new RoutePointScreen(null, ROUTE.get(2), true, MissileKind.FP5, p -> {})));
      add("03-route-error", "Редактор точки · ошибка", () -> {
         mc.setScreen(new RoutePointScreen(null, ROUTE.get(0), false, MissileKind.FP5, p -> {}));
         set(mc.screen, "error", "Высота вне допустимого диапазона");
      });

      for (String family : List.of("fp5_launcher", "jagm_launcher", "peacekeeper_platform")) {
         add("04-inventory-" + family, "Инвентарь · " + family, () -> inventory(mc, family));
      }

      add("05-assembly-fp5", "Станция сборки · FP-5", () -> assembly(mc, "fp5"));
      add("05-assembly-scalp", "Станция сборки · SCALP EG", () -> assembly(mc, "storm_shadow"));
      add("05-assembly-jagm", "Станция сборки · JAGM", () -> assembly(mc, "jagm"));
      add("06-patriot-stowed", "Patriot · походное положение", () -> defense(mc, false, 0));
      add("06-patriot-ready", "Patriot · боевой режим", () -> defense(mc, false, 2));
      add("06-patriot-radar", "Patriot · карта радара Xaero", () -> {
         defense(mc, false, 2);
         mc.setScreen(new AirDefenseMapScreen(mc.screen, patriot.getId()));
      });
      add("06-patriot-optical", "Patriot · оптика", () -> {
         defense(mc, false, 2);
         mode(mc, PatriotThermalCamera.CameraMode.NORMAL);
      });
      add("06-patriot-night", "Patriot · ПНВ", () -> {
         defense(mc, false, 2);
         mode(mc, PatriotThermalCamera.CameraMode.NIGHT_VISION);
      });
      add("06-patriot-reload", "Patriot · перезарядка", () -> defense(mc, false, 4));
      add("07-iron-dome", "Iron Dome · тепловизор", () -> defense(mc, true, 2));
      add("07-iron-dome-radar", "Iron Dome · карта радара Xaero", () -> {
         defense(mc, true, 2);
         mc.setScreen(new AirDefenseMapScreen(mc.screen, dome.getId()));
      });
      add("07-iron-dome-optical", "Iron Dome · оптика", () -> {
         defense(mc, true, 2);
         mode(mc, PatriotThermalCamera.CameraMode.NORMAL);
      });
      add("07-iron-dome-night", "Iron Dome · ПНВ", () -> {
         defense(mc, true, 2);
         mode(mc, PatriotThermalCamera.CameraMode.NIGHT_VISION);
      });
      add("08-spike-map", "SPIKE NLOS · целеуказание", () -> {
         mc.player.startRiding(spike, true);
         mc.setScreen(new SpikeNlosMapScreen(spike.getId()));
         set(mc.screen, "selected", true);
      });

      for (String type : List.of("basic", "advanced", "tower")) {
         add(
            "09-siren-" + type,
            "Сирена · " + type,
            () -> AirRaidSirenScreen.open(new BlockPos(type.equals("tower") ? 8 : (type.equals("advanced") ? 10 : 12), 101, 0))
         );
      }

      add("10-frequency-tablet", "Планшет · три канала", () -> FrequencySettingsScreen.tablet(InteractionHand.MAIN_HAND, item("jagm_camera_tablet")));
      add("10-frequency-defense", "ПВО · частоты своих", () -> FrequencySettingsScreen.defense(null, patriot.getId(), List.of("101", "202", "303")));
      add("11-journal-empty", "Журнал · пустое состояние", () -> UnitJournalScreen.open(PAD));
      add("11-journal-entries", "Журнал · примеры записей", () -> {
         UnitJournalScreen.open(PAD);
         journal(mc);
      });
      add("12-paint", "Окраска техники", () -> {
         mc.player.setPos(patriot.position().add(0.0, 1.0, 2.0));
         mc.setScreen(new VehiclePaintScreen(patriot.getId()));
      });
      add("13-countdown", "HUD · обратный отсчёт", () -> mc.setScreen(null));
      add("13-countdown-waiting", "HUD · ожидание синхронизации", () -> mc.setScreen(null));
      add("14-camera-jagm", "HUD · камера JAGM", () -> prepareHudCamera(mc, MissileKind.JAGM));
      add("15-spike-tv", "HUD · ТВ-наведение SPIKE", () -> prepareHudCamera(mc, MissileKind.SPIKE_NLOS));
      add("15-spike-thermal", "HUD · тепловизор SPIKE", () -> prepareHudCamera(mc, MissileKind.SPIKE_NLOS));

      for (String skin : List.of("forest", "desert", "urban", "winter", "solid")) {
         add("17-camo-iron-" + skin, "Iron Dome · " + skin, () -> {
            defense(mc, true, 2);
            ((VehicleEntity)dome).setSkinId(skin);
         });
         add("17-camo-patriot-" + skin, "Patriot · " + skin, () -> {
            defense(mc, false, 2);
            ((VehicleEntity)patriot).setSkinId(skin);
         });
      }

      for (String skin : List.of("sand", "green", "multicam")) {
         add("17-camo-spike-" + skin, "SPIKE · " + skin, () -> {
            ((VehicleEntity)spike).setSkinId(skin);
            mc.player.startRiding(spike, true);
            mc.setScreen(new SpikeNlosMapScreen(spike.getId()));
         });
      }

      add("17-camo-remote-desert", "Remote radar · desert", () -> {
         CompoundTag radar = new CompoundTag();
         radar.putString("Skin", "desert");
         radar.putBoolean("Online", true);
         radar.putBoolean("Iron", true);
         CompoundTag defense = new CompoundTag();
         defense.put("Radar", radar);
         CommandTabletScreen.latestSnapshot.put("Defense", defense);
         CompoundTag origin = new CompoundTag();
         origin.putString("Name", "Iron Dome");
         mc.setScreen(new CommandDefenseScreen(null, origin));
      });

      for (String tier : List.of("basic", "advanced")) {
         add(
            "19-zone-siren-" + tier,
            "Сирена · полный обзор " + tier,
            () -> {
               AirRaidSirenScreen.open(new BlockPos(tier.equals("advanced") ? 10 : 12, 101, 0));
               if (mc.screen instanceof AirRaidSirenScreen parent) {
                  Button full = parent.children()
                     .stream()
                     .filter(c -> c instanceof Button b && b.getMessage().getString().equals(FlightUi.localized("screen.deepstrike.map.fullscreen")))
                     .map(c -> (Button)c)
                     .findFirst()
                     .orElseThrow();
                  full.onPress();
                  if (!(
                     mc.screen instanceof ZoneXaeroMapScreen zone
                        && (Double)get(zone, "radius") == (tier.equals("advanced") ? 5000 : 2000)
                        && get(zone, "onCenter") == null
                  )) {
                     throw new IllegalStateException("Readonly siren detection zone mismatch");
                  }
               } else {
                  throw new IllegalStateException("Missing siren " + tier);
               }
            }
         );
      }

      add(
         "19-zone-siren-return",
         "Сирена · карта и возврат",
         () -> {
            AirRaidSirenScreen.open(new BlockPos(8, 101, 0));
            if (mc.screen instanceof AirRaidSirenScreen parent) {
               Button full = parent.children()
                  .stream()
                  .filter(c -> c instanceof Button b && b.getMessage().getString().equals(FlightUi.localized("screen.deepstrike.map.fullscreen")))
                  .map(c -> (Button)c)
                  .findFirst()
                  .orElseThrow();
               full.onPress();
               if (mc.screen instanceof ZoneXaeroMapScreen zone) {
                  set(zone, "selecting", true);
                  double var10 = zone.worldX(zone.width / 2.0 + 65.0);
                  double wz = zone.worldZ(zone.height / 2.0 + 30.0);
                  zone.mouseClicked(zone.width / 2.0 + 65.0, zone.height / 2.0 + 30.0, 0);
                  zone.onClose();
                  if (mc.screen == parent
                     && !(Math.abs((Double)get(parent, "zoneX") - var10) > 0.001)
                     && !(Math.abs((Double)get(parent, "zoneZ") - wz) > 0.001)) {
                     LOG.info("SIREN_XAERO_RETURN_PASS retainedCenter=true retainedParent=true");
                     Button reopen = parent.children()
                        .stream()
                        .filter(c -> c instanceof Button b && b.getMessage().getString().equals(FlightUi.localized("screen.deepstrike.map.fullscreen")))
                        .map(c -> (Button)c)
                        .findFirst()
                        .orElseThrow();
                     reopen.onPress();
                  } else {
                     throw new IllegalStateException("Siren map return lost selected center");
                  }
               } else {
                  throw new IllegalStateException("Siren expand failed");
               }
            } else {
               throw new IllegalStateException("Missing siren parent");
            }
         }
      );
      add("19-zone-fullscreen", "Xaero · выбор зоны", () -> mc.setScreen(new ZoneXaeroMapScreen(null, 8.0, 0.0, 500.0, (x, z) -> {
         if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalStateException("Invalid zone selection");
         }
      })));
      add("18-help-planner", "Подсказки · карта пуска", () -> map(mc, true, true, false));
      add("18-help-defense", "Подсказки · ПВО", () -> defense(mc, false, 2));
      add("18-help-launch", "Подсказки · пуск ПВО", () -> defense(mc, false, 2));
      add("18-help-zone", "Подсказки · большая карта", () -> mc.setScreen(new ZoneXaeroMapScreen(null, 0.0, 0.0, 1500.0, (x, z) -> {})));
      add("16-controls", "Стиль · состояния кнопок", () -> mc.setScreen(new InstrumentControlsReviewScreen()));
      add("01-xaero-small", "Планировщик · компактный масштаб GUI", () -> {
         mc.options.guiScale().set(4);
         mc.resizeDisplay();
         map(mc, true, true, false);
      });
      add("00-xaero-after", "Обычная карта Xaero · после планировщика", () -> {
         mc.options.guiScale().set(2);
         mc.resizeDisplay();
         MapProviders.openNativeMap();
      });
      if (Boolean.getBoolean("deepstrike.guiScaleReview")) {
         List<GuiCaptureReview.Shot> originals = List.copyOf(shots);
         shots.clear();
         int maximum = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
         Set<String> wanted = Set.of(
            "00-xaero-normal",
            "01-xaero-map",
            "01-xaero-clean",
            "01-xaero-route",
            "01-xaero-profile",
            "01-xaero-settings",
            "01-xaero-presets",
            "02-built-in-settings",
            "03-route-error",
            "04-inventory-fp5_launcher",
            "05-assembly-fp5",
            "06-patriot-ready",
            "06-patriot-radar",
            "07-iron-dome",
            "07-iron-dome-radar",
            "08-spike-map",
            "09-siren-tower",
            "10-frequency-tablet",
            "11-journal-entries",
            "12-paint",
            "13-countdown",
            "14-camera-jagm",
            "15-spike-tv",
            "15-spike-thermal",
            "16-controls"
         );

         for (int scale = 0; scale <= maximum; scale++) {
            int selectedScale = scale;

            for (GuiCaptureReview.Shot original : originals) {
               if (wanted.contains(original.id)
                  || original.id.startsWith("17-camo-")
                  || original.id.startsWith("18-help-")
                  || original.id.startsWith("19-zone-")) {
                  add(original.id + "-scale-" + scale, original.title + " · GUI " + (scale == 0 ? "Auto" : scale), () -> {
                     mc.options.guiScale().set(selectedScale);
                     mc.resizeDisplay();
                     original.open.run();
                  });
               }
            }
         }

         add("20-jagm-flight", "JAGM · полёт и свободный обзор 0.6×", () -> {
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            mc.setScreen(null);
            mc.getSingleplayerServer().execute(() -> {
               ServerLevel level = mc.getSingleplayerServer().overworld();
               GuidedMissileEntity missile = new GuidedMissileEntity(level, MissileKind.JAGM);
               missile.setPos(0.0, 1000.0, 0.0);
               missile.setMissionId(UUID.randomUUID());
               missile.setOwnerId(mc.player.getUUID());
               missile.setWarhead("none");
               missile.setMission(List.of(new RoutePoint(800.0, 1000.0, 0.0, 450.0), new RoutePoint(800.0, 1030.0, 800.0, 450.0)), false);
               missile.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 12);
               missile.setDeltaMovement(4.0, 0.0, 0.0);
               level.addFreshEntity(missile);
               realJagm = missile.getId();
            });
         });
      }

      add("24-zone-server", "Iron Dome · серверная зона Xaero", () -> {
         mc.setScreen(null);
         zoneLiveId = -1;
         zoneSent = false;
         zoneServerVerified = false;
         zoneFailure = null;
         mc.getSingleplayerServer().execute(() -> {
            try {
               ServerLevel level = mc.getSingleplayerServer().overworld();
               ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
               IronDomeEntity battery = (IronDomeEntity)((EntityType)BFEntities.IRON_DOME.get()).create(level);
               battery.setNoGravity(true);
               battery.setPos(0.0, 110.0, 10.0);
               battery.setAutoDefense(false);
               level.addFreshEntity(battery);
               player.setGameMode(GameType.CREATIVE);
               if (!player.startRiding(battery, true)) {
                  throw new IllegalStateException("Zone fixture seat rejected");
               }

               zoneLiveId = battery.getId();
            } catch (Throwable var4x) {
               zoneFailure = var4x;
            }
         });
      });

      for (ResourceLocation entry : mc.getResourceManager().listResources("lang", id -> id.getNamespace().equals("deepstrike") && id.getPath().endsWith(".json")).keySet()) {
         String locale = entry.getPath().substring(5, entry.getPath().length() - 5);
         add("26-locale-" + locale, "Help locale · " + locale, () -> mc.setScreen(new HelpLocaleReviewScreen(locale)));
      }

      if (Boolean.getBoolean("deepstrike.guiScaleReview")) {
         add("22-radar-live", "Iron Dome · пуск с карты радара", () -> {
            mc.setScreen(null);
            mc.getSingleplayerServer().execute(() -> {
               ServerLevel level = mc.getSingleplayerServer().overworld();
               ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
               IronDomeEntity battery = (IronDomeEntity)((EntityType)BFEntities.IRON_DOME.get()).create(level);
               battery.setPos(0.0, 101.0, 10.0);
               battery.setYRot(0.0F);
               battery.setAutoDefense(false);
               battery.setReadyTamir(4);
               battery.setFriendlyFrequencies(List.of());
               level.addFreshEntity(battery);
               player.setGameMode(GameType.CREATIVE);
               if (!player.startRiding(battery, true)) {
                  throw new IllegalStateException("Radar fixture seat failed");
               } else {
                  liveDome = battery.getId();
                  GuidedMissileEntity target = new GuidedMissileEntity(level, MissileKind.FP5);
                  target.setPos(0.0, 240.0, 600.0);
                  target.setMissionId(UUID.randomUUID());
                  target.setFrequency("777");
                  target.setWarhead("none");
                  target.setMission(List.of(new RoutePoint(0.0, 240.0, -1600.0, 350.0)), false);
                  target.setLaunchVector(new Vec3(0.0, 0.0, -1.0), 10);
                  target.setDeltaMovement(0.0, 0.0, -0.5);
                  level.addFreshEntity(target);
                  radarThreat = target.getUUID();
               }
            });
         });
      }

      if (Boolean.getBoolean("deepstrike.guiScaleReview")) {
         add("21-spike-flight", "SPIKE · полёт, камера и команды", () -> {
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            mc.setScreen(null);
            mc.getSingleplayerServer().execute(() -> {
               ServerLevel level = mc.getSingleplayerServer().overworld();
               ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
               SpikeNlosEntity vehicle = (SpikeNlosEntity)((EntityType)BFEntities.SPIKE_NLOS.get()).create(level);
               vehicle.setPos(150.0, 240.0, 150.0);
               vehicle.setNoGravity(true);
               level.addFreshEntity(vehicle);
               player.setGameMode(GameType.CREATIVE);
               if (!player.startRiding(vehicle, true)) {
                  throw new IllegalStateException("SPIKE seat rejected");
               } else {
                  GuidedMissileEntity missile = new GuidedMissileEntity(level, MissileKind.SPIKE_NLOS);
                  missile.setPos(150.0, 246.0, 150.0);
                  missile.setLaunchVehicle(vehicle.getUUID());
                  missile.setMissionId(UUID.randomUUID());
                  missile.setOwnerId(player.getUUID());
                  missile.setWarhead("none");
                  missile.setMission(List.of(new RoutePoint(1600.0, 120.0, 150.0, 250.0)), false);
                  missile.configureSpike(260.0, 250.0);
                  missile.setLaunchVector(new Vec3(0.0, 1.0, 0.0), 10);
                  missile.setDeltaMovement(0.0, 1.0, 0.0);
                  level.addFreshEntity(missile);
                  realSpike = missile.getId();
                  MissileCameraStream.begin(player, missile);
               }
            });
         });
      }

      String filter = System.getProperty("deepstrike.guiCaptureFilter", "");
      if (!filter.isEmpty()) {
         shots.removeIf(shot -> Arrays.stream(filter.split(",")).noneMatch(shot.id::startsWith));
      }
   }

   private static void add(String id, String title, Runnable r) {
      shots.add(new GuiCaptureReview.Shot(id, title, r));
   }

   private static void map(Minecraft mc, boolean xaero, boolean settings, boolean presets) {
      if (xaero) {
         DeepStrikeMapScreen.open(PAD, Direction.EAST, LauncherBlock.Family.FP5);
         if (!(mc.screen instanceof DeepStrikeMapScreen)) {
            throw new IllegalStateException("Xaero map did not open");
         }
      } else {
         mc.setScreen(new DeepStrikeScreen(PAD, Direction.EAST, LauncherBlock.Family.FP5));
      }

      ((List)get(mc.screen, "route")).addAll(ROUTE);
      set(mc.screen, "selected", 1);
      set(mc.screen, "settings", settings);
      if (presets) {
         Class type = get(mc.screen, "tab").getClass();
         set(mc.screen, "tab", Enum.valueOf(type, "PRESETS"));
      }

      if (settings && !presets) {
         call(mc.screen, "syncConfig");
         call(mc.screen, "layoutFields");
      }

      if (xaero) {
         if (!settings && !presets) {
            verifyPlanner((DeepStrikeMapScreen)mc.screen);
         } else {
            call(mc.screen, "fitRoute");
         }
      }
   }

   private static void verifyPlanner(DeepStrikeMapScreen screen) {
      if (screen.children().size() != 5) {
         throw new IllegalStateException("Xaero widgets leaked into mission planner");
      } else {
         List<RoutePoint> points = (List<RoutePoint>)get(screen, "route");
         List<RoutePoint> original = List.copyOf(points);
         double initialX = screen.getCameraX();
         double initialZ = screen.getCameraZ();
         screen.mouseClicked(screen.width * 0.55, screen.height * 0.4, 0);
         screen.mouseDragged(screen.width * 0.55 + 12.0, screen.height * 0.4 + 7.0, 0, 12.0, 7.0);
         screen.mouseReleased(screen.width * 0.55 + 12.0, screen.height * 0.4 + 7.0, 0);
         if (screen.getCameraX() != initialX && screen.getCameraZ() != initialZ) {
            screen.setCameraX(initialX);
            screen.setCameraZ(initialZ);
            set(screen, "selected", 1);
            call(screen, "syncConfig");
            EditBox[] boxes = (EditBox[])get(screen, "config");
            boxes[1].setValue("225");
            call(screen, "applyConfig");
            if (points.get(1).y() == 225.0 && points.get(0).y() == original.get(0).y() && points.get(2).y() == original.get(2).y()) {
               points.clear();
               points.addAll(original);
               set(screen, "selected", 1);
               call(screen, "syncConfig");
               call(screen, "fitRoute");
               LOG.info("GUI_PLANNER_INPUT_PASS children=5 pan=true selectedAltitudeOnly=true");
            } else {
               throw new IllegalStateException("Editing a waypoint changed other altitudes");
            }
         } else {
            throw new IllegalStateException("Map drag failed");
         }
      }
   }

   private static void context(Screen s) {
      set(s, "menuX", s.width / 2);
      set(s, "menuY", s.height / 2);
      set(s, "menuPoint", 1);
   }

   private static void inventory(Minecraft mc, String id) {
      mc.level.setBlock(PAD, ((Block)BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("deepstrike", id))).defaultBlockState(), 3);
      LauncherBlockEntity be = (LauncherBlockEntity)mc.level.getBlockEntity(PAD);
      be.setItem(0, item(id.equals("jagm_launcher") ? "jagm" : (id.equals("peacekeeper_platform") ? "peacekeeper" : "fp5")));
      mc.setScreen(
         new LauncherInventoryScreen(new LauncherMenu(0, mc.player.getInventory(), be), mc.player.getInventory(), Component.translatable("block.deepstrike." + id))
      );
   }

   private static void assembly(Minecraft mc, String id) {
      mc.level.setBlock(ASSEMBLY, ((MissileAssemblyBlock)BFBlocks.MISSILE_ASSEMBLY_STATION.get()).defaultBlockState(), 3);
      MissileAssemblyBlockEntity be = (MissileAssemblyBlockEntity)mc.level.getBlockEntity(ASSEMBLY);
      be.setItem(0, item(id));
      be.setItem(1, item("warhead_he"));
      mc.setScreen(new MissileAssemblyScreen(new MissileAssemblyMenu(0, mc.player.getInventory(), be), mc.player.getInventory(), be.getDisplayName()));
   }

   private static void defense(Minecraft mc, boolean iron, int state) {
      mc.player.startRiding(iron ? dome : patriot, true);
      mc.setScreen((Screen)(iron ? new IronDomeDefenseScreen(dome.getId()) : new PatriotDefenseScreen(patriot.getId())));
      set(mc.screen, "selectedId", TARGET);
   }

   private static void mode(Minecraft mc, PatriotThermalCamera.CameraMode mode) {
      ((PatriotThermalCamera)get(mc.screen, "thermalCamera")).setMode(mode);
   }

   private static void journal(Minecraft mc) {
      set(
         mc.screen,
         "entries",
         List.of("[12:30] FP-5 · Пуск ракеты · частота 101", "[12:31] Цель: X 650 / Y 110 / Z 400", "[12:32] Ракета достигла конечной точки")
      );
   }

   private static void prepareHudCamera(Minecraft mc, MissileKind kind) {
      int previous = hudFixture;
      hudFixture = -1;
      hudOpened = false;
      mc.setScreen(null);
      mc.getSingleplayerServer().execute(() -> {
         ServerLevel level = mc.getSingleplayerServer().overworld();
         Entity old = level.getEntity(previous);
         if (old != null) {
            old.discard();
         }

         GuidedMissileEntity missile = new GuidedMissileEntity(level, kind);
         missile.setOwnerId(mc.player.getUUID());
         missile.setWarhead("none");
         missile.setHasCamera(true);
         missile.setPos(0.0, 180.0, 0.0);
         missile.setNoGravity(true);
         level.addFreshEntity(missile);
         hudFixture = missile.getId();
      });
   }

   static void tick(Minecraft mc) {
      try {
         mc.player.setInvulnerable(true);
         mc.player.setHealth(20.0F);
         mc.player.deathTime = 0;
         if (mc.screen instanceof DeathScreen) {
            mc.setScreen(null);
         }

         if (!baseRequested) {
            baseRequested = true;
            mc.getSingleplayerServer()
               .execute(
                  () -> {
                     ServerLevel level = mc.getSingleplayerServer().overworld();
                     ServerPlayer player = (ServerPlayer)mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                     level.getChunk(0, 0);
                     player.teleportTo(level, 0.0, 104.0, 20.0, 180.0F, 15.0F);

                     for (String tier : List.of("tower")) {
                        level.setBlock(
                           new BlockPos(8, 101, 0), ((Block)BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("deepstrike", "siren_" + tier))).defaultBlockState(), 3
                        );
                     }

                     baseReady = true;
                  }
               );
         } else if (baseReady && mc.level.hasChunkAt(new BlockPos(8, 101, 0))) {
            if (shots.isEmpty()) {
               scene(mc);
            }

            if (index < 0 || captured && age >= (Boolean.getBoolean("deepstrike.guiScaleReview") ? 12 : 28)) {
               ClientCamera.restore();
               mc.player.stopRiding();
               mc.setCameraEntity(mc.player);
               mc.options.hideGui = false;
               LaunchCountdownHud.accept(new DeepStrikeNetwork.LaunchCountdown(PAD, TARGET, 0L, 0L, false, "READY"));
               if (++index >= shots.size()) {
                  LOG.info("FP5_OPTIONS_FINISHED guiCaptures={}", shots.size());
                  mc.stop();
                  return;
               }

               age = 0;
               frames = 0;
               captured = false;
               helpVerified = false;
               shots.get(index).open.run();
               if (shots.get(index).id.startsWith("09-siren") && !(mc.screen instanceof AirRaidSirenScreen)) {
                  throw new IllegalStateException("Siren fixture did not open its screen");
               }

               String var10003 = mc.screen == null ? "HUD" : mc.screen.getClass().getName();
               LOG.info("GUI_CAPTURE_OPEN id={} class={}", shots.get(index).id, var10003);
            }

            age++;
            if (MapProviders.nativeUiHidden()) {
               throw new IllegalStateException("Xaero hiddenUI leaked outside its scoped render call");
            } else {
               String id = shots.get(index).id;
               if (!id.startsWith("22-")
                  && (
                     mc.screen instanceof PatriotDefenseScreen
                        || mc.screen instanceof AirDefenseMapScreen map && map.console instanceof PatriotDefenseScreen
                  )) {
                  mc.player.startRiding(patriot, true);
                  int deploy = id.contains("stowed") ? 0 : 2;
                  int reload = id.contains("reload") ? 150 : 0;
                  PatriotDefenseScreen.accept(
                     new PatriotDefenseNetwork.TargetState(
                        patriot.getId(),
                        true,
                        8000.0,
                        8,
                        12,
                        2,
                        2,
                        reload,
                        deploy,
                        0,
                        List.of("101"),
                        List.of(
                           new PatriotDefenseNetwork.Contact(
                              TARGET, jagm.getId(), 400.0, 180.0, 350.0, 680.0F, 180.0F, RadarDisclosure.model(TARGET, 0), false
                           )
                        )
                     )
                  );
               }

               if (!id.startsWith("22-")
                  && (
                     mc.screen instanceof IronDomeDefenseScreen
                        || mc.screen instanceof AirDefenseMapScreen map && map.console instanceof IronDomeDefenseScreen
                  )) {
                  mc.player.startRiding(dome, true);
                  IronDomeDefenseScreen.accept(
                     new IronDomeNetwork.TargetState(
                        dome.getId(),
                        0.0,
                        101.0,
                        5.0,
                        16,
                        id.contains("radar") ? 0 : 40,
                        0,
                        0.0,
                        0.0,
                        1500.0,
                        true,
                        true,
                        List.of("101"),
                        List.of(
                           new IronDomeNetwork.Contact(
                              TARGET, jagm.getId(), 400.0, 180.0, 350.0, 680.0F, 180.0F, RadarDisclosure.model(TARGET, 0), 650.0, 400.0, true, 0, false
                           )
                        )
                     )
                  );
               }

               if (mc.screen instanceof SpikeNlosMapScreen) {
                  mc.player.startRiding(spike, true);
                  SpikeNlosMapScreen.accept(
                     new SpikeNlosNetwork.Snapshot(
                        spike.getId(), List.of(new SpikeNlosNetwork.Contact(jagm.getId(), TARGET, "Цель", 400.0, 180.0, 350.0, false)), 4
                     )
                  );
               }

               if (id.startsWith("18-help-") && !helpVerified && age >= 6 && UiHelp.ready(mc.screen)) {
                  Pre press = new Pre(mc.screen, 20.0, mc.screen.height - 18, 0);
                  NeoForge.EVENT_BUS.post(press);
                  if (!press.isCanceled() || !((SpotlightTour)get(UiHelp.class, "tour")).active()) {
                     throw new IllegalStateException("Help button did not open modal tour");
                  }

                  Pre outside = new Pre(mc.screen, mc.screen.width / 2, mc.screen.height / 2, 0);
                  NeoForge.EVENT_BUS.post(outside);
                  if (!outside.isCanceled()) {
                     throw new IllegalStateException("Help leaked click to screen");
                  }

                  if (id.startsWith("18-help-launch")) {
                     SpotlightTour help = (SpotlightTour)get(UiHelp.class, "tour");
                     List<SpotlightTour.Step> steps = (List<SpotlightTour.Step>)get(help, "steps");
                     int launchStep = -1;

                     for (int i = 0; i < steps.size(); i++) {
                        if (steps.get(i).explanation().getString().contains(FlightUi.localized("help.deepstrike.launch"))) {
                           launchStep = i;
                           break;
                        }
                     }

                     if (launchStep < 0) {
                        throw new IllegalStateException("Dynamic PVO launch lacks its real action explanation");
                     }

                     set(help, "index", launchStep);
                  }

                  helpVerified = true;
                  LOG.info("HELP_INPUT_PASS screen={} modal=true", mc.screen.getClass().getSimpleName());
               }

               if (id.startsWith("24-zone-server")) {
                  if (zoneFailure != null) {
                     throw new IllegalStateException("Server zone verification failed", zoneFailure);
                  }

                  if (!zoneSent && zoneLiveId >= 0 && mc.level.getEntity(zoneLiveId) != null) {
                     Entity battery = mc.level.getEntity(zoneLiveId);
                     mc.player.startRiding(battery, true);
                     IronDomeDefenseScreen console = new IronDomeDefenseScreen(zoneLiveId);
                     mc.setScreen(console);
                     call(console, "openZoneMap");
                     if (!(mc.screen instanceof ZoneXaeroMapScreen zone)) {
                        throw new IllegalStateException("IronDome zone button did not open Xaero");
                     }

                     set(zone, "selecting", true);
                     double var35 = zone.width / 2.0 + 80.0;
                     double var38 = zone.height / 2.0 + 40.0;
                     zoneExpectedX = zone.worldX(var35);
                     zoneExpectedZ = zone.worldZ(var38);
                     zone.mouseClicked(var35, var38, 0);
                     zoneSent = true;
                  }

                  if (zoneSent && age > 35 && !zoneServerVerified) {
                     mc.getSingleplayerServer().execute(() -> {
                        try {
                           IronDomeEntity battery = (IronDomeEntity)mc.getSingleplayerServer().overworld().getEntity(zoneLiveId);
                           if (Math.abs(battery.getZoneX() - zoneExpectedX) > 0.001 || Math.abs(battery.getZoneZ() - zoneExpectedZ) > 0.001) {
                              throw new IllegalStateException("Zone map did not update authoritative center");
                           }

                           zoneServerVerified = true;
                        } catch (Throwable var2x) {
                           zoneFailure = var2x;
                        }
                     });
                  }
               }

               if (id.equals("11-journal-entries")) {
                  journal(mc);
               }

               if (id.startsWith("13-")) {
                  long t = mc.level.getGameTime();
                  LaunchCountdownHud.accept(new DeepStrikeNetwork.LaunchCountdown(PAD, TARGET, t + 60L, t, true, "READY"));
                  if (id.contains("waiting")) {
                     set(LaunchCountdownHud.class, "receivedNanos", System.nanoTime() - 2000000000L);
                  }
               }

               if (mc.screen instanceof AirDefenseMapScreen radar && !id.startsWith("22-") && age == 6) {
                  radar.targets()
                     .stream()
                     .filter(t -> t.id().equals(TARGET))
                     .findFirst()
                     .ifPresent(t -> radar.mouseClicked(radar.mapX(t.x()), radar.mapZ(t.z()), 0));
               }

               if (id.startsWith("14-camera") || id.startsWith("15-spike")) {
                  if (!hudOpened) {
                     if (hudFixture >= 0 && mc.level.getEntity(hudFixture) != null) {
                        ClientCamera.view(hudFixture);
                        hudOpened = true;
                        age = 0;
                        return;
                     }

                     if (age > 200) {
                        throw new IllegalStateException("HUD camera fixture did not arrive");
                     }

                     return;
                  }

                  Entity followed = (Entity)get(ClientCamera.class, "followed");
                  if (followed == null || followed.getId() != hudFixture) {
                     throw new IllegalStateException("HUD camera fixture lost authorized view");
                  }

                  if (id.startsWith("15-spike")) {
                     set(SpikeTvCamera.class, "thermal", id.contains("thermal"));
                  }
               }

               if (id.startsWith("15-spike") && age == 4) {
                  float yaw = mc.player.getYRot();
                  Window window = mc.getWindow();
                  mc.player.turn(window.getWidth() * 0.15, -window.getHeight() * 0.075);
                  if (mc.player.getYRot() != yaw
                     || Math.abs((Double)get(SpikeTvCamera.class, "cursorX") - 0.3) > 1.0E-4
                     || Math.abs((Double)get(SpikeTvCamera.class, "cursorY") + 0.15) > 1.0E-4) {
                     throw new IllegalStateException("SPIKE cursor did not receive isolated mouse input");
                  }

                  LOG.info("GUI_SPIKE_CURSOR_PASS x=0.3 y=-0.15 playerRotationUnchanged=true");
               }

               if (id.startsWith("22-radar")) {
                  Entity battery = mc.level.getEntity(liveDome);
                  if (battery != null && !radarOpened) {
                     mc.player.startRiding(battery, true);
                     IronDomeDefenseScreen console = new IronDomeDefenseScreen(liveDome);
                     mc.setScreen(console);
                     mc.setScreen(new AirDefenseMapScreen(console, liveDome));
                     radarOpened = true;
                  }

                  if (age >= 40 && !radarFired && mc.screen instanceof AirDefenseMapScreen radar) {
                     AirDefenseMapScreen.Target target = radar.targets().stream().filter(t -> t.id().equals(radarThreat)).findFirst().orElse(null);
                     if (target != null) {
                        if (!RadarDisclosure.modelKnown(target.id()) && target.kind() != -1) {
                           throw new IllegalStateException("Server disclosed unknown radar model");
                        }

                        if (target.assessment() != -1 != RadarDisclosure.impactKnown(target.id())) {
                           throw new IllegalStateException("Server disclosed unknown impact assessment");
                        }

                        LOG.info("RADAR_LIVE_DISCLOSURE_PASS modelKnown={} impactKnown={}", target.kind() != -1, target.assessment() != -1);
                        radar.mouseClicked(radar.mapX(target.x()), radar.mapZ(target.z()), 0);
                        Button button = (Button)get(radar, "primary");
                        if (!button.active) {
                           throw new IllegalStateException("Live radar fire disabled");
                        }

                        button.onPress();
                        radarFired = true;
                        LOG.info("RADAR_LIVE_CLICK_SENT target={}", radarThreat);
                     }
                  }

                  if (age == 90) {
                     mc.getSingleplayerServer().execute(() -> {
                        IronDomeEntity live = (IronDomeEntity)mc.getSingleplayerServer().overworld().getEntity(liveDome);
                        if (radarFired && live.getReadyTamir() < 4) {
                           radarServerVerified = true;
                           LOG.info("RADAR_LIVE_SERVER_FIRE_PASS ready={}", live.getReadyTamir());
                        } else {
                           throw new IllegalStateException("Radar map click failed to launch real Tamir");
                        }
                     });
                  }
               }

               if (id.startsWith("21-spike")) {
                  if (RemoteFlights.get(realSpike) != null && !spikeViewing) {
                     ClientCamera.view(realSpike);
                     spikeViewing = true;
                  }

                  if (age == 65 && spikeViewing) {
                     mouseCall(
                        mc.mouseHandler,
                        new String[]{"onPress", "onPress"},
                        new Class[]{long.class, int.class, int.class, int.class},
                        mc.getWindow().getWindow(),
                        1,
                        1,
                        0
                     );
                     float old = (Float)get(ClientCamera.class, "spikeYawOffset");
                     mc.player.turn(60.0, 20.0);
                     mouseCall(
                        mc.mouseHandler,
                        new String[]{"onPress", "onPress"},
                        new Class[]{long.class, int.class, int.class, int.class},
                        mc.getWindow().getWindow(),
                        1,
                        0,
                        0
                     );
                     if (Math.abs((Float)get(ClientCamera.class, "spikeYawOffset") - old - 5.4) > 0.001) {
                        throw new IllegalStateException("SPIKE RMB camera did not respond");
                     }

                     set(ClientCamera.class, "spikeYawOffset", 0.0F);
                     set(ClientCamera.class, "spikePitchOffset", 0.0F);
                     LOG.info("SPIKE_RMB_INPUT_PASS");
                  }

                  if (age == 75) {
                     mc.options.keyUp.setDown(true);
                  }

                  if (age == 83) {
                     set(ClientCamera.class, "spikePitchOffset", 45.0F);
                  }

                  if (age == 85) {
                     mc.options.keyUp.setDown(false);
                  }

                  if (age == 110) {
                     mc.getSingleplayerServer()
                        .execute(
                           () -> {
                              GuidedMissileEntity missile = (GuidedMissileEntity)mc.getSingleplayerServer().overworld().getEntity(realSpike);
                              CompoundTag tag = missile.saveWithoutId(new CompoundTag());
                              CompoundTag target = tag.getList("Route", 10).getCompound(0);
                              realSpikeCommandSpeed = tag.getDouble("CommandedSpeed");
                              if (!(realSpikeCommandSpeed <= 250.0) && target.getDouble("X") != 1600.0) {
                                 spikeCommandVerified = true;
                                 LOG.info(
                                    "SPIKE_SERVER_CONTROLS_PASS speed={} target={}, {}, {}",
                                    new Object[]{realSpikeCommandSpeed, target.getDouble("X"), target.getDouble("Y"), target.getDouble("Z")}
                                 );
                              } else {
                                 throw new IllegalStateException(
                                    "Actual SPIKE TV command packets not applied speed="
                                       + realSpikeCommandSpeed
                                       + " target="
                                       + target
                                       + " owns="
                                       + MissileCameraStream.owns(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()), missile)
                                 );
                              }
                           }
                        );
                  }
               }

               if (id.startsWith("20-jagm")) {
                  GuidedMissileEntity replica = RemoteFlights.get(realJagm);
                  if (replica != null && !realViewing) {
                     ClientCamera.view(realJagm);
                     realViewing = true;
                     motionYaw = (Float)get(ClientCamera.class, "lookYaw");
                  }

                  if (age == 40 && realViewing) {
                     float before = mc.player.getYRot();
                     mc.player.turn(100.0, 50.0);
                     float yaw = (Float)get(ClientCamera.class, "lookYaw");
                     float pitch = (Float)get(ClientCamera.class, "lookPitch");
                     if (Math.abs(Mth.wrapDegrees(yaw - motionYaw) - 9.0F) > 0.001 || mc.player.getYRot() != before) {
                        throw new IllegalStateException("0.6x free look input failed");
                     }

                     motionYaw = yaw;
                     LOG.info("GUI_JAGM_INPUT_PASS yawDelta=9 playerRotationUnchanged=true");
                  }
               }

               jagm.setPos(0.0, 180.0, 0.0);
               tv.setPos(60.0, 180.0, 0.0);
               jagm.setDeltaMovement(0.2, 0.0, 0.5);
               tv.setDeltaMovement(0.2, 0.0, 0.5);
               if (age > 300) {
                  throw new IllegalStateException("No render capture for " + id);
               }
            }
         }
      } catch (Throwable var9) {
         LOG.error("GUI_CAPTURE_FAILURE", var9);
         throw new IllegalStateException(var9);
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (event.phase == Phase.END && index >= 0 && index < shots.size() && !captured) {
         if (!shots.get(index).id.startsWith("18-help-") || helpVerified) {
            if (shots.get(index).id.startsWith("24-zone-server")) {
               if (zoneFailure != null) {
                  throw new IllegalStateException("Server zone verification failed", zoneFailure);
               }

               if (!zoneServerVerified) {
                  return;
               }
            }

            if (!shots.get(index).id.startsWith("14-camera") && !shots.get(index).id.startsWith("15-spike") || hudOpened) {
               if (shots.get(index).id.startsWith("22-radar")) {
                  if (age < 100) {
                     return;
                  }

                  if (!radarServerVerified) {
                     if (age < 300) {
                        return;
                     }

                     throw new IllegalStateException("Live radar server test incomplete");
                  }
               } else if (shots.get(index).id.startsWith("21-spike")) {
                  if (spikeViewing && age > 35) {
                     GuidedMissileEntity missile = RemoteFlights.get(realSpike);
                     Camera camera = mc.gameRenderer.getMainCamera();
                     if (missile == null) {
                        throw new IllegalStateException("Live SPIKE lost");
                     }

                     double dot = new Vec3(camera.getLookVector()).dot(missile.getDeltaMovement().normalize());
                     if (age >= 85 && !spikeClickSent) {
                        set(SpikeTvCamera.class, "cursorX", 0.0);
                        set(SpikeTvCamera.class, "cursorY", 0.45);
                        SpikeTvCamera.mouse(new net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre(0, 1, 0));
                        spikeClickSent = true;
                        set(ClientCamera.class, "spikePitchOffset", 0.0F);
                     }

                     if (dot < 0.999 && age != 65 && (age < 83 || spikeClickSent && age > 87)) {
                        throw new IllegalStateException("SPIKE camera misses flight direction: " + dot);
                     }

                     spikeTrace.add(age + "," + missile.getX() + "," + missile.getY() + "," + missile.getZ() + "," + camera.getXRot() + "," + dot);
                  }

                  if (age < 120) {
                     return;
                  }

                  if (!spikeCommandVerified || spikeTrace.size() < 100) {
                     throw new IllegalStateException("SPIKE live verification incomplete");
                  }

                  LOG.info("SPIKE_LIVE_CAMERA_PASS frames={} serverControls=true", spikeTrace.size());
               } else if (shots.get(index).id.startsWith("20-jagm")) {
                  if (realViewing && age > 45) {
                     Camera camerax = mc.gameRenderer.getMainCamera();
                     GuidedMissileEntity replica = RemoteFlights.get(realJagm);
                     if (replica == null) {
                        throw new IllegalStateException("Real JAGM disappeared during camera review");
                     }

                     Entity anchor = (Entity)get(ClientCamera.class, "cameraAnchor");
                     double error = camerax.getPosition().distanceTo(anchor.position());
                     if (error > 0.001) {
                        throw new IllegalStateException("Camera detached from interpolated nose: " + error);
                     }

                     if (Math.abs(Mth.wrapDegrees(camerax.getYRot() - motionYaw)) > 0.001) {
                        throw new IllegalStateException("Flight rotation changed independent view");
                     }

                     long now = System.nanoTime();
                     double dt = lastFrame == 0L ? 0.0 : (now - lastFrame) / 1.0E9;
                     double step = lastCamera == null ? 0.0 : camerax.getPosition().distanceTo(lastCamera);
                     maxCameraStep = Math.max(maxCameraStep, step);
                     motionTrace.add(
                        now
                           + ","
                           + age
                           + ","
                           + camerax.getPosition().x
                           + ","
                           + camerax.getPosition().y
                           + ","
                           + camerax.getPosition().z
                           + ","
                           + camerax.getYRot()
                           + ","
                           + dt
                           + ","
                           + step
                     );
                     lastFrame = now;
                     lastCamera = camerax.getPosition();
                  }

                  if (age < 120) {
                     return;
                  }

                  if (motionTrace.size() < 100) {
                     throw new IllegalStateException("Insufficient live camera frames");
                  }

                  LOG.info("GUI_JAGM_MOTION_PASS frames={} maxFrameStep={} independentLook=true", motionTrace.size(), maxCameraStep);
               } else if (age < (Boolean.getBoolean("deepstrike.guiScaleReview") ? 8 : 18) || ++frames < 5) {
                  return;
               }

               try {
                  if (mc.screen instanceof PatriotDefenseScreen || mc.screen instanceof IronDomeDefenseScreen) {
                     verifyDefense(mc.screen);
                  }

                  if (mc.screen instanceof SpikeNlosMapScreen map) {
                     if (!MapProviders.ready()) {
                        throw new IllegalStateException("SPIKE map provider unavailable");
                     }

                     double x = map.width * 0.5;
                     double y = map.height * 0.4;
                     double initial = map.worldX(x);
                     map.mouseClicked(x, y, 0);
                     map.mouseDragged(x + 12.0, y, 0, 12.0, 0.0);
                     map.mouseReleased(x + 12.0, y, 0);
                     if (map.worldX(x) == initial) {
                        throw new IllegalStateException("SPIKE map cannot pan");
                     }

                     map.mouseClicked(x + 12.0, y, 0);
                     map.mouseDragged(x, y, 0, -12.0, 0.0);
                     map.mouseReleased(x, y, 0);
                     double wx = map.worldX(x + 100.0);
                     map.mouseClicked(x + 100.0, y, 1);
                     double selectedX = Double.parseDouble(((EditBox)get(map, "targetX")).getValue());
                     if (Math.abs(wx - selectedX) > 0.11) {
                        throw new IllegalStateException("SPIKE waypoint differs from Xaero location");
                     }

                     LOG.info("SPIKE_XAERO_INPUT_PASS pan=true targetX={}", selectedX);
                  }

                  if (mc.screen instanceof AirDefenseMapScreen radar && !shots.get(index).id.startsWith("22-")) {
                     AirDefenseMapScreen.Target target = radar.targets().stream().filter(t -> t.id().equals(TARGET)).findFirst().orElse(null);
                     if (target == null) {
                        if (age < 80) {
                           return;
                        }

                        throw new IllegalStateException(
                           "Radar map missed staged snapshot age="
                              + age
                              + " state="
                              + get(radar.console, "latest")
                              + " last="
                              + get(radar.console, "lastSnapshotTick")
                              + " now="
                              + mc.level.getGameTime()
                        );
                     }

                     radar.mouseClicked(radar.mapX(target.x()), radar.mapZ(target.z()), 0);
                     if (!TARGET.equals(get(radar, "selected"))) {
                        throw new IllegalStateException("Radar target click missed");
                     }

                     if (!((Button)get(radar, "primary")).active) {
                        throw new IllegalStateException("Selected radar target cannot fire");
                     }

                     LOG.info(
                        "RADAR_MAP_INPUT_PASS console={} liveSnapshot=true clickSelection=true fireEnabled=true", radar.console.getClass().getSimpleName()
                     );
                  }

                  if (mc.screen instanceof ZoneXaeroMapScreen zone && !shots.get(index).id.startsWith("24-zone-server") && get(zone, "onCenter") != null) {
                     double oldX = zone.worldX(zone.width / 2.0);
                     double oldZ = zone.worldZ(zone.height / 2.0);
                     zone.mouseClicked(zone.width / 2.0, zone.height / 2.0, 0);
                     zone.mouseDragged(zone.width / 2.0 + 45.0, zone.height / 2.0 + 20.0, 0, 45.0, 20.0);
                     zone.mouseReleased(zone.width / 2.0 + 45.0, zone.height / 2.0 + 20.0, 0);
                     if (Math.abs(zone.worldX(zone.width / 2.0) - oldX) < 1.0) {
                        throw new IllegalStateException("Zone map did not pan");
                     }

                     set(zone, "selecting", true);
                     double wx = zone.worldX(zone.width / 2.0);
                     double wz = zone.worldZ(zone.height / 2.0);
                     zone.mouseClicked(zone.width / 2.0, zone.height / 2.0, 0);
                     if (Math.abs((Double)get(zone, "zoneX") - wx) > 0.001 || Math.abs((Double)get(zone, "zoneZ") - wz) > 0.001) {
                        throw new IllegalStateException("Zone coordinate projection mismatch");
                     }

                     LOG.info("ZONE_XAERO_INPUT_PASS pan=true centerProjection=true x={} z={}", wx, wz);
                  }

                  if (mc.screen != null && !shots.get(index).id.startsWith("18-help-")) {
                     verifyViewport(mc.screen);
                  }

                  if (mc.screen instanceof DeepStrikeMapScreen planner) {
                     if (!Double.isFinite(planner.getMapScale()) || planner.getMapScale() <= 0.0) {
                        throw new IllegalStateException("Invalid planner zoom");
                     }

                     LOG.info("GUI_PLANNER_RENDER_PASS scale={} globalHiddenUI={}", planner.getMapScale(), MapProviders.nativeUiHidden());
                  }
               } catch (RuntimeException var16) {
                  throw var16;
               }

               if (ClientCamera.spike() != null && mc.screen == null) {
                  Camera cameraxx = mc.gameRenderer.getMainCamera();
                  Vec3 ray = SpikeTvCamera.aimDirection(cameraxx);
                  Vector4f projected = new Vector4f(
                     (float)(-ray.dot(new Vec3(cameraxx.getLeftVector()))),
                     (float)ray.dot(new Vec3(cameraxx.getUpVector())),
                     (float)(-ray.dot(new Vec3(cameraxx.getLookVector()))),
                     1.0F
                  );
                  mc.gameRenderer.getProjectionMatrix(ClientCamera.viewFov()).transform(projected);
                  double errorx = Math.hypot(
                     projected.x / projected.w - (Double)get(SpikeTvCamera.class, "cursorX"),
                     projected.y / projected.w + (Double)get(SpikeTvCamera.class, "cursorY")
                  );
                  if (errorx > 0.002) {
                     throw new IllegalStateException("SPIKE ray misses visible cursor: " + errorx);
                  }

                  LOG.info("GUI_SPIKE_RAY_PASS projectionError={}", errorx);
               }

               if (mc.screen instanceof DeathScreen) {
                  throw new IllegalStateException("Death screen contaminated GUI capture");
               } else {
                  try {
                     NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

                     try {
                        Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
                        Files.createDirectories(dir);
                        GuiCaptureReview.Shot current = shots.get(index);
                        if (current.id.startsWith("21-spike")) {
                           Files.write(dir.resolve("spike-camera-motion.csv"), spikeTrace);
                        }

                        if (current.id.startsWith("20-jagm")) {
                           Files.write(dir.resolve("jagm-camera-motion.csv"), motionTrace);
                        }

                        shot.writeToFile(dir.resolve(current.id + ".png"));
                        Files.writeString(
                           dir.resolve("captions.tsv"),
                           current.id
                              + "\t"
                              + current.title
                              + "\t"
                              + (mc.screen == null ? "HUD" : mc.screen.getClass().getName())
                              + "\t"
                              + shot.getWidth()
                              + "x"
                              + shot.getHeight()
                              + "\n",
                           StandardOpenOption.CREATE,
                           StandardOpenOption.APPEND
                        );
                        if (current.id.startsWith("09-siren") && !(mc.screen instanceof AirRaidSirenScreen)) {
                           throw new IllegalStateException("Siren screen closed before capture");
                        }

                        if (current.id.contains("-radar")
                           && (current.id.startsWith("06-") || current.id.startsWith("07-"))
                           && !(mc.screen instanceof AirDefenseMapScreen)) {
                           throw new IllegalStateException("Radar map closed before capture");
                        }

                        if (current.id.startsWith("24-zone-server")) {
                           LOG.info("IRON_DOME_ZONE_SERVER_PASS x={} z={}", zoneExpectedX, zoneExpectedZ);
                        }

                        captured = true;
                        LOG.info("GUI_CAPTURE_SAVED {}", current.id);
                        if (current.id.startsWith("01-xaero-clean")) {
                           DeepStrikeMapScreen map = (DeepStrikeMapScreen)mc.screen;
                           map.mouseClicked(map.width - 100, 24.0, 0);
                           if ((Boolean)get(map, "chromeHidden")) {
                              throw new IllegalStateException("Show GUI button did not restore panels");
                           }

                           if (!((EditBox)get(map, "frequency")).visible) {
                              throw new IllegalStateException("Frequency field did not return");
                           }

                           map.setFocused(null);
                           map.keyPressed(72, 0, 0);
                           LOG.info("GUI_CHROME_BUTTON_PASS restoredFields=true hotkey=true");
                        }
                     } catch (Throwable var14) {
                        if (shot != null) {
                           try {
                              shot.close();
                           } catch (Throwable var13) {
                              var14.addSuppressed(var13);
                           }
                        }

                        throw var14;
                     }

                     if (shot != null) {
                        shot.close();
                     }
                  } catch (Exception var15) {
                     throw new IllegalStateException(var15);
                  }
               }
            }
         }
      }
   }

   private static void verifyViewport(Screen screen) {
      if (UiViewport.applies(screen)) {
         Window window = Minecraft.getInstance().getWindow();
         double physicalUnit = UiViewport.scale() * window.getGuiScale();
         if (physicalUnit > 3.001 || screen.width < 1000 || screen.height < 600) {
            throw new IllegalStateException("Mod interface consumes too much view space");
         }

         if (screen instanceof DeepStrikeMapScreen planner) {
            boolean settings = (Boolean)get(screen, "settings");
            boolean clean = (Boolean)get(screen, "chromeHidden");
            double blocked = clean
               ? 4368.0 / ((double)screen.width * screen.height)
               : 42.0 / screen.height
                  + 29.0 / screen.height
                  + (
                     settings
                        ? (double)((Integer)get(screen, "panelW")).intValue()
                           * ((Integer)get(screen, "panelH")).intValue()
                           / ((double)screen.width * screen.height)
                        : 0.0
                  );
            if (blocked > 0.3) {
               throw new IllegalStateException("Planner leaves insufficient map area: " + blocked);
            }

            double expected = planner.getMapScale() / window.getGuiScale() / UiViewport.scale();
            if (Math.abs(expected - planner.getEffectiveMapScale()) > 1.0E-5) {
               throw new IllegalStateException("Planner physical map scale mismatch");
            }

            LOG.info(
               "GUI_MAP_SPACE_PASS scale={} clearAreaFraction={} physicalUnit={}",
               new Object[]{Minecraft.getInstance().options.guiScale().get(), 1.0 - blocked, physicalUnit}
            );
         }
      }

      for (GuiEventListener child : screen.children()) {
         if (child instanceof AbstractWidget widget
            && widget.visible
            && (
               widget.getX() < 0
                  || widget.getY() < 0
                  || widget.getX() + widget.getWidth() > screen.width
                  || widget.getY() + widget.getHeight() > screen.height
            )) {
            throw new IllegalStateException(
               "Widget outside viewport: " + screen.getClass() + " " + widget.getMessage().getString() + " " + screen.width + "x" + screen.height
            );
         }
      }

      if (UiViewport.applies(screen)) {
         for (GuiEventListener childx : screen.children()) {
            if (childx instanceof EditBox box && box.visible && box.active) {
               Minecraft mc = Minecraft.getInstance();
               Window windowx = mc.getWindow();
               double x = (box.getX() + 3.0) / screen.width * windowx.getScreenWidth();
               double y = (box.getY() + 3.0) / screen.height * windowx.getScreenHeight();
               mouseCall(mc.mouseHandler, new String[]{"onMove", "onMove"}, new Class[]{long.class, double.class, double.class}, windowx.getWindow(), x, y);
               mouseCall(
                  mc.mouseHandler, new String[]{"onPress", "onPress"}, new Class[]{long.class, int.class, int.class, int.class}, windowx.getWindow(), 0, 1, 0
               );
               mouseCall(
                  mc.mouseHandler, new String[]{"onPress", "onPress"}, new Class[]{long.class, int.class, int.class, int.class}, windowx.getWindow(), 0, 0, 0
               );
               if (!box.isFocused()) {
                  throw new IllegalStateException("Scaled mouse missed EditBox: " + screen.getClass());
               }

               LOG.info("GUI_SCALED_MOUSE_PASS screen={} scale={}", screen.getClass().getSimpleName(), mc.options.guiScale().get());
               break;
            }
         }
      }

      LOG.info(
         "GUI_VIEWPORT_PASS screen={} scale={} canvas={}x{}",
         new Object[]{screen.getClass().getSimpleName(), Minecraft.getInstance().options.guiScale().get(), screen.width, screen.height}
      );
   }

   private static void mouseCall(Object handler, String[] names, Class<?>[] types, Object... args) {
      String[] var4 = names;
      int var5 = names.length;
      int var6 = 0;

      while (var6 < var5) {
         String name = var4[var6];

         try {
            Method method = handler.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(handler, args);
            return;
         } catch (NoSuchMethodException var9) {
            var6++;
         } catch (ReflectiveOperationException var10) {
            throw new IllegalStateException(var10);
         }
      }

      throw new IllegalStateException("Missing native mouse callback");
   }

   private static void verifyDefense(Screen screen) {
      double sx = ((Number)get(screen, "screenX")).doubleValue();
      double sy = ((Number)get(screen, "screenY")).doubleValue();
      double scale = ((Number)get(screen, "uiScale")).doubleValue();
      PatriotThermalCamera camera = (PatriotThermalCamera)get(screen, "thermalCamera");
      PatriotThermalCamera.CameraMode original = camera.getMode();
      int[] xs = new int[]{535, 575, 625};
      PatriotThermalCamera.CameraMode[] expected = new PatriotThermalCamera.CameraMode[]{
         PatriotThermalCamera.CameraMode.NORMAL, PatriotThermalCamera.CameraMode.NIGHT_VISION, PatriotThermalCamera.CameraMode.THERMAL
      };

      for (int i = 0; i < 3; i++) {
         screen.mouseClicked(sx + xs[i] * scale, sy + 77.0 * scale, 0);
         if (camera.getMode() != expected[i]) {
            throw new IllegalStateException("Moved camera mode button missed " + screen.getClass() + " " + i);
         }
      }

      camera.setMode(original);
      float zoom = camera.getZoom();
      boolean iron = screen instanceof IronDomeDefenseScreen;
      screen.mouseClicked(sx + (iron ? 50 : 642) * scale, sy + (iron ? 335 : 101) * scale, 0);
      if (camera.getZoom() != Math.min(12.0F, zoom + 1.0F)) {
         throw new IllegalStateException("Moved zoom button missed");
      } else {
         screen.mouseClicked(sx + (iron ? 28 : 624) * scale, sy + (iron ? 335 : 101) * scale, 0);
         if (camera.getZoom() != zoom) {
            throw new IllegalStateException("Moved zoom out missed");
         } else {
            LOG.info("GUI_DEFENSE_INPUT_PASS screen={} modes=3 zoom=true", screen.getClass().getSimpleName());
         }
      }
   }

   private GuiCaptureReview() {
   }

   private record Shot(String id, String title, Runnable open) {
   }
}
