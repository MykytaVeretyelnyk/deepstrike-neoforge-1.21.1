package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.FuelHelper;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileItem;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.ModularComponentItem;
import dev.sbw.deepstrike.menu.MissileAssemblyMenu;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MissileAssemblyScreen extends AbstractContainerScreen<MissileAssemblyMenu> implements EquipmentUiTheme {
   private String selectedCamo = "graphite";
   private MissileKind lastAirframeKind = MissileKind.FP5;
   private final Button[] camoButtons = new Button[6];

   @Override
   public String equipmentSkin() {
      return this.selectedCamo;
   }

   public MissileAssemblyScreen(MissileAssemblyMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
      this.imageWidth = 382;
      this.imageHeight = 236;
      this.inventoryLabelY = 138;
      this.inventoryLabelX = 22;
      if (menu.blockEntity != null) {
         this.selectedCamo = menu.blockEntity.getSelectedCamo();
      }
   }

   public static List<MissileAssemblyScreen.CamoOption> getCamoOptions(MissileKind kind) {
      if (kind == MissileKind.STORM_SHADOW) {
         return List.of(
            new MissileAssemblyScreen.CamoOption("desert", FlightUi.localized("ui.deepstrike.missile_assembly_screen.d224a21544"), -4413569),
            new MissileAssemblyScreen.CamoOption("default", FlightUi.localized("ui.deepstrike.missile_assembly_screen.c157fd6271"), -10853784),
            new MissileAssemblyScreen.CamoOption("sky", FlightUi.localized("ui.deepstrike.missile_assembly_screen.c6650363de"), -5654595)
         );
      } else {
         return kind == MissileKind.JAGM
            ? List.of(
               new MissileAssemblyScreen.CamoOption("desert", FlightUi.localized("ui.deepstrike.missile_assembly_screen.a9339b0414"), -4413569),
               new MissileAssemblyScreen.CamoOption("default", FlightUi.localized("ui.deepstrike.missile_assembly_screen.4b19f18191"), -12826056),
               new MissileAssemblyScreen.CamoOption("sea", FlightUi.localized("ui.deepstrike.missile_assembly_screen.dfdf644f66"), -5654595)
            )
            : List.of(
               new MissileAssemblyScreen.CamoOption("graphite", FlightUi.localized("camo.deepstrike.graphite"), -10326921),
               new MissileAssemblyScreen.CamoOption("olive", FlightUi.localized("ui.deepstrike.missile_assembly_screen.e2487c6ca3"), -11903682),
               new MissileAssemblyScreen.CamoOption("desert", FlightUi.localized("ui.deepstrike.missile_assembly_screen.f0826d48af"), -4413569),
               new MissileAssemblyScreen.CamoOption("gray", FlightUi.localized("ui.deepstrike.missile_assembly_screen.4ba03b99ea"), -11380644),
               new MissileAssemblyScreen.CamoOption("stealth", FlightUi.localized("ui.deepstrike.missile_assembly_screen.c95284545b"), -14736860),
               new MissileAssemblyScreen.CamoOption("sky", FlightUi.localized("ui.deepstrike.missile_assembly_screen.442f04c2d2"), -5654595),
               new MissileAssemblyScreen.CamoOption("pink", FlightUi.localized("ui.deepstrike.missile_assembly_screen.eb5fec96e1"), -4355200)
            );
      }
   }

   private MissileKind getAirframeKind() {
      ItemStack stack = ((Slot)((MissileAssemblyMenu)this.menu).slots.get(0)).getItem();
      return !stack.isEmpty() && stack.getItem() instanceof MissileItem m ? m.kind() : MissileKind.FP5;
   }

   protected void init() {
      super.init();
      int startX = this.leftPos + 24;
      int camoY = this.topPos + 116;

      for (int i = 0; i < 6; i++) {
         int index = i;
         int row = 0;
         int bx = startX + i * 17;
         int by = camoY + row * 15;
         this.camoButtons[i] = (Button)this.addRenderableWidget(
            Button.builder(Component.literal(""), b -> this.onCamoClicked(index))
               .bounds(bx, by, 14, 14)
               .build(builder -> new MissileAssemblyScreen.CamoButton(builder, index))
         );
      }

      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.deepstrike.assembly.action"), b -> this.doAssemble())
            .bounds(this.leftPos + 134, this.topPos + 111, 64, 25)
            .build(QuietButton::new)
      );
      this.updateCamoState();
   }

   private void onCamoClicked(int index) {
      List<MissileAssemblyScreen.CamoOption> options = getCamoOptions(this.getAirframeKind());
      if (index < options.size()) {
         this.selectCamo(options.get(index).id());
      }
   }

   private void selectCamo(String camo) {
      this.selectedCamo = camo;
      if (this.minecraft != null && this.minecraft.player != null) {
         this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.2F);
      }
   }

   private void updateCamoState() {
      MissileKind currentKind = this.getAirframeKind();
      this.lastAirframeKind = currentKind;
      List<MissileAssemblyScreen.CamoOption> options = getCamoOptions(currentKind);
      boolean valid = options.stream().anyMatch(o -> o.id().equals(this.selectedCamo));
      if (!valid) {
         this.selectedCamo = options.get(0).id();
      }

      for (int i = 0; i < 6; i++) {
         if (this.camoButtons[i] != null) {
            this.camoButtons[i].visible = i < options.size();
         }
      }
   }

   public void containerTick() {
      super.containerTick();
      MissileKind currentKind = this.getAirframeKind();
      if (currentKind != this.lastAirframeKind) {
         this.updateCamoState();
      }
   }

   private void doAssemble() {
      if (((MissileAssemblyMenu)this.menu).blockEntity != null) {
         if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.PISTON_EXTEND, 0.7F, 1.3F);
         }

         DeepStrikeNetwork.CHANNEL
            .sendToServer(new DeepStrikeNetwork.AssembleMissileAction(((MissileAssemblyMenu)this.menu).blockEntity.getBlockPos(), this.selectedCamo));
      }
   }

   protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
      FlightUi.panel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, -182838503, -10854328);
      g.fill(this.leftPos + 14, this.topPos + 25, this.leftPos + this.imageWidth - 14, this.topPos + 26, -10854328);
      FlightUi.rounded(g, this.leftPos + 14, this.topPos + 32, 184, 73, -14276322);
      FlightUi.rounded(g, this.leftPos + 210, this.topPos + 32, 158, 94, -14078937);
      FlightUi.rounded(g, this.leftPos + 210, this.topPos + 136, 158, 86, -15000298);
      g.fill(this.leftPos + 200, this.topPos + 36, this.leftPos + 201, this.topPos + 218, -10854328);

      for (Slot slot : ((MissileAssemblyMenu)this.menu).slots) {
         int x = this.leftPos + slot.x;
         int y = this.topPos + slot.y;
         boolean output = slot.container == ((MissileAssemblyMenu)this.menu).blockEntity && (slot.getContainerSlot() == 6 || slot.getContainerSlot() == 7);
         FlightUi.rounded(g, x - 2, y - 2, 20, 20, -15197676);
         g.fill(x - 1, y + 16, x + 17, y + 17, output ? -1000096 : -10854328);
      }

      List<MissileAssemblyScreen.CamoOption> options = getCamoOptions(this.getAirframeKind());

      for (int i = 0; i < options.size(); i++) {
         this.drawCamoBox(g, this.leftPos + 24 + i * 17, this.topPos + 116, options.get(i).colorHex(), options.get(i).id().equals(this.selectedCamo));
      }

      this.renderLiveTelemetry(g);
   }

   private void drawHudCorners(GuiGraphics g, int x, int y, int w, int h, int color) {
      g.fill(x, y, x + 3, y + 1, color);
      g.fill(x, y, x + 1, y + 3, color);
      g.fill(x + w - 3, y, x + w, y + 1, color);
      g.fill(x + w - 1, y, x + w, y + 3, color);
      g.fill(x, y + h - 1, x + 3, y + h, color);
      g.fill(x, y + h - 3, x + 1, y + h, color);
      g.fill(x + w - 3, y + h - 1, x + w, y + h, color);
      g.fill(x + w - 1, y + h - 3, x + w, y + h, color);
   }

   private void drawCamoBox(GuiGraphics g, int x, int y, int color, boolean selected) {
      g.fill(x + 2, y + 2, x + 12, y + 12, color);
      if (selected) {
         g.renderOutline(x, y, 14, 14, -6575733);
         g.renderOutline(x + 1, y + 1, 12, 12, -1);
         g.fill(x + 6, y + 6, x + 8, y + 8, -1);
      } else {
         g.renderOutline(x, y, 14, 14, -11775683);
      }
   }

   private void renderLiveTelemetry(GuiGraphics g) {
      ItemStack airframeStack = ((Slot)((MissileAssemblyMenu)this.menu).slots.get(0)).getItem();
      int px = this.leftPos + 220;
      int py = this.topPos + 39;
      if (!airframeStack.isEmpty() && airframeStack.getItem() instanceof MissileItem missileItem) {
         MissileKind var32 = missileItem.kind();
         MissileData prevData = MissileData.fromItemStack(airframeStack);
         ItemStack warheadStack = ((Slot)((MissileAssemblyMenu)this.menu).slots.get(1)).getItem();
         String warhead = prevData != null ? prevData.warhead() : "none";
         if (!warheadStack.isEmpty() && warheadStack.getItem() instanceof ModularComponentItem c && "warhead".equals(c.category())) {
            warhead = c.componentId();
         }

         ItemStack engineStack = ((Slot)((MissileAssemblyMenu)this.menu).slots.get(2)).getItem();
         String engine = prevData != null ? prevData.engine() : "rocket";
         if (!engineStack.isEmpty() && engineStack.getItem() instanceof ModularComponentItem c && "engine".equals(c.category())) {
            engine = c.componentId();
         }

         List<String> modules = new ArrayList<>();
         ItemStack mod1 = ((Slot)((MissileAssemblyMenu)this.menu).slots.get(3)).getItem();
         if (!mod1.isEmpty() && mod1.getItem() instanceof ModularComponentItem c1 && "module".equals(c1.category())) {
            modules.add(c1.componentId());
         }

         ItemStack mod2 = ((Slot)((MissileAssemblyMenu)this.menu).slots.get(4)).getItem();
         if (!mod2.isEmpty() && mod2.getItem() instanceof ModularComponentItem c2 && "module".equals(c2.category()) && !modules.contains(c2.componentId())) {
            modules.add(c2.componentId());
         }

         MissileData data = new MissileData(var32, warhead, engine, modules, this.selectedCamo, 0, 0);
         int maxR = data.calculateMaxRange();
         int maxS = data.calculateMaxSpeed();
         boolean hasEcm = data.hasEcm();
         FlightUi.label(g, this.font.plainSubstrByWidth(var32.title().getString(), 142), px, py, -1182997);
         FlightUi.label(g, maxR + " m · " + maxS + " km/h", px, py + 16, -1000096);

         String whText = switch (warhead) {
            case "he" -> FlightUi.localized("ui.deepstrike.missile_assembly_screen.41daa82b63");
            case "penetrating" -> FlightUi.localized("ui.deepstrike.missile_assembly_screen.83ad538cbb");
            case "graphite" -> FlightUi.localized("ui.deepstrike.missile_assembly_screen.6fc698cbb2");
            case "cluster" -> FlightUi.localized("ui.deepstrike.missile_assembly_screen.3eecca7b3b");
            case "thermobaric" -> FlightUi.localized("ui.deepstrike.missile_assembly_screen.56a6e5baca");
            case "nuclear" -> FlightUi.localized("ui.deepstrike.missile_assembly_screen.d6dd6cdb9e");
            case "shaped_charge" -> FlightUi.localized("ui.deepstrike.missile_assembly_screen.b44677834a");
            default -> FlightUi.localized("ui.deepstrike.missile_assembly_screen.e02ccc53ff");
         };
         FlightUi.label(g, this.font.plainSubstrByWidth(whText, 140), px, py + 32, -5196890);
         String feat = (
               hasEcm
                  ? FlightUi.localized("ui.deepstrike.missile_assembly_screen.beba7a8a96") + (var32 == MissileKind.PEACEKEEPER ? "5" : "35") + "% "
                  : FlightUi.localized("ui.deepstrike.missile_assembly_screen.8f72429ecb")
            )
            + (data.hasCamera() ? FlightUi.localized("ui.deepstrike.missile_assembly_screen.ba6c5e5055") : "");
         FlightUi.label(g, this.font.plainSubstrByWidth(feat, 140), px, py + 46, -5196890);
         ItemStack fuelStack = ((Slot)((MissileAssemblyMenu)this.menu).slots.get(5)).getItem();
         boolean willFuel = !fuelStack.isEmpty() && FuelHelper.isFuelContainer(fuelStack);
         int curFuel = prevData != null ? prevData.fuel() : 0;
         int maxFuel = data.maxFuel();
         if (willFuel) {
            curFuel = maxFuel;
         }

         int percent = maxFuel > 0 ? curFuel * 100 / maxFuel : 0;
         int barY = py + 61;
         int barW = 138;
         int barH = 4;
         g.fill(px, barY, px + barW, barY + barH, -16316668);
         g.renderOutline(px, barY, barW, barH, !willFuel && curFuel <= 0 ? -4355200 : -6575733);
         int fillW = percent * (barW - 2) / 100;
         if (fillW > 0) {
            int barColor = percent > 50 ? -6575733 : (percent > 20 ? -4413569 : -4355200);
            g.fill(px + 1, barY + 1, px + 1 + fillW, barY + barH - 1, barColor);
         }

         String fuelText = willFuel
            ? FlightUi.localized("ui.deepstrike.missile_assembly_screen.6e4758c617")
            : (curFuel > 0 ? "§e" + percent + "% (" + curFuel + "mB)" : FlightUi.localized("ui.deepstrike.missile_assembly_screen.f305bb7df8"));
         FlightUi.label(g, fuelText.replaceAll("(?i)§[0-9a-f]", ""), px, barY + 8, -5196890);
      } else {
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.missile_assembly_screen.7d529ad3d9"), px, py, -9933229, false);
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.missile_assembly_screen.037efaabda"), px, py + 12, -11183548, false);
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.missile_assembly_screen.4e78b52408"), px, py + 22, -11183548, false);
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.missile_assembly_screen.705d6a603b"), px, py + 36, -11973318, false);
      }
   }

   private void renderHangar3D(GuiGraphics g) {
      int hangarX = this.leftPos + 210;
      int hangarY = this.topPos + 136;
      int hangarW = 158;
      int hangarH = 86;
      ItemStack airframe = ((Slot)((MissileAssemblyMenu)this.menu).slots.get(0)).getItem();
      if (!airframe.isEmpty() && airframe.getItem() instanceof MissileItem m) {
         MissileKind kind = m.kind();
         float rotationYaw = (float)System.currentTimeMillis() / 35.0F % 360.0F;
         Lighting.setupFor3DItems();
         PoseStack pose = g.pose();
         pose.pushPose();
         pose.translate(hangarX + hangarW / 2.0F, hangarY + hangarH / 2.0F + 6.0F, 120.0F);

         float scale = switch (kind) {
            case FP5 -> 5.6F;
            case STORM_SHADOW -> 6.0F;
            case JAGM, SPIKE_NLOS -> 10.4F;
            case PEACEKEEPER -> 1.12F;
         };
         pose.scale(scale, -scale, scale);
         pose.mulPose(Axis.XP.rotationDegrees(20.0F));
         pose.mulPose(Axis.YP.rotationDegrees(rotationYaw));
         pose.mulPose(Axis.ZP.rotationDegrees(-15.0F));
         if (kind == MissileKind.PEACEKEEPER) {
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
         }

         String modelName = ModelAssets.missile(kind);
         String texName = ModelAssets.texture(kind, this.selectedCamo, m.graphite());
         ModelRenderer.render(modelName, texName, pose, g.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY, 0.0F, 0.0F, b -> true);
         pose.popPose();
         g.flush();
         Lighting.setupForFlatItems();
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.missile_assembly_screen.1323e2e359"), hangarX + 6, hangarY + 4, -10591157, false);
      } else {
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.missile_assembly_screen.1323e2e359"), hangarX + 6, hangarY + 4, -12697039, false);
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.missile_assembly_screen.43fc0d1f4a"), hangarX + 6, hangarY + 34, -12697039, false);
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.missile_assembly_screen.c166a1bdc5"), hangarX + 6, hangarY + 44, -12697039, false);
      }
   }

   protected void renderLabels(GuiGraphics g, int mx, int my) {
      FlightUi.label(g, FlightUi.localized("ui.deepstrike.missile_assembly_screen.9e5cea6486"), 16, 10, -1182997);
      String[] keys = new String[]{"f33e9d4da9", "b6665a86d8", "5f906bb2c7", "52718f4af4", "52718f4af4", "85284e7d57"};
      int[] xx = new int[]{22, 60, 98, 22, 60, 98};

      for (int i = 0; i < keys.length; i++) {
         FlightUi.label(g, this.font.plainSubstrByWidth(FlightUi.localized("ui.deepstrike.missile_assembly_screen." + keys[i]), 37), xx[i], i < 3 ? 31 : 72, -5196890);
      }

      FlightUi.label(g, this.playerInventoryTitle.getString(), 22, 138, -5196890);
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      this.renderTransparentBackground(g);
      super.render(g, mx, my, partial);
      this.renderHangar3D(g);
      this.renderTooltip(g, mx, my);
      int startX = this.leftPos + 24;
      int camoY = this.topPos + 116;
      List<MissileAssemblyScreen.CamoOption> options = getCamoOptions(this.getAirframeKind());

      for (int i = 0; i < options.size(); i++) {
         int row = 0;
         int bx = startX + i * 17;
         int by = camoY + row * 15;
         if (mx >= bx && mx <= bx + 14 && my >= by && my <= by + 14) {
            g.renderTooltip(this.font, Component.literal(options.get(i).label()), mx, my);
            break;
         }
      }
   }

   private final class CamoButton extends Button {
      private final int index;

      CamoButton(Builder builder, int index) {
         super(builder);
         this.index = index;
      }

      public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
         List<MissileAssemblyScreen.CamoOption> options = MissileAssemblyScreen.getCamoOptions(MissileAssemblyScreen.this.getAirframeKind());
         if (this.index < options.size()) {
            MissileAssemblyScreen.CamoOption option = options.get(this.index);
            MissileAssemblyScreen.this.drawCamoBox(
               g, this.getX(), this.getY(), option.colorHex(), option.id().equals(MissileAssemblyScreen.this.selectedCamo)
            );
         }
      }
   }

   public record CamoOption(String id, String label, int colorHex) {
   }
}
