package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.AirRaidSirenBlock;
import dev.sbw.deepstrike.game.AirRaidSirenBlockEntity;
import dev.sbw.deepstrike.network.AirRaidSirenNetwork;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

public final class AirRaidSirenScreen extends Screen {
   private final BlockPos pos;
   private final AirRaidSirenBlock.Tier tier;
   private int variant;
   private int audibleRadius;
   private int zoneRadius;
   private int mapSpan;
   private boolean zoneOnly;
   private boolean selecting;
   private double zoneX;
   private double zoneZ;
   private TerrainMap terrain;
   private int mapX;
   private int mapY;
   private int mapSize;
   private int panelX;

   public static void open(BlockPos pos) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.level.getBlockEntity(pos) instanceof AirRaidSirenBlockEntity siren) {
         mc.setScreen(new AirRaidSirenScreen(pos, siren));
      }
   }

   private AirRaidSirenScreen(BlockPos pos, AirRaidSirenBlockEntity siren) {
      super(text("title"));
      this.pos = pos.immutable();
      this.tier = siren.tier();
      this.variant = siren.variant();
      this.audibleRadius = siren.audibleRadius();
      this.zoneRadius = siren.zoneRadius();
      this.zoneOnly = siren.zoneOnly();
      this.zoneX = siren.zoneX();
      this.zoneZ = siren.zoneZ();
      this.mapSpan = this.tier == AirRaidSirenBlock.Tier.TOWER ? 10000 : (this.tier == AirRaidSirenBlock.Tier.ADVANCED ? 10000 : 4000);
   }

   private static Component text(String key, Object... args) {
      return Component.translatable("screen.deepstrike.siren." + key, args);
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      this.mapSize = Math.max(130, Math.min(this.height - 72, this.width - 280));
      this.panelX = Math.max(16, (this.width - this.mapSize - 230) / 2);
      this.mapX = this.panelX + 218;
      this.mapY = Math.max(40, (this.height - this.mapSize) / 2);
      if (this.terrain == null) {
         this.terrain = new TerrainMap(192);
      }

      this.refreshMap();
      this.addRenderableWidget(
         Button.builder(Component.literal("◀"), b -> this.variant = (this.variant + 2) % 3)
            .bounds(this.panelX, this.mapY + 65, 24, 20)
            .build(QuietButton::new)
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("▶"), b -> this.variant = (this.variant + 1) % 3)
            .bounds(this.panelX + 160, this.mapY + 65, 24, 20)
            .build(QuietButton::new)
      );
      this.addRenderableWidget(
         Button.builder(
               text("preview"),
               b -> {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null && mc.level != null) {
                     mc.level
                        .playLocalSound(
                           mc.player.getX(),
                           mc.player.getY(),
                           mc.player.getZ(),
                           BFSounds.siren(this.tier.ordinal(), this.variant),
                           SoundSource.BLOCKS,
                           0.7F,
                           1.0F,
                           false
                        );
                  }
               }
            )
            .bounds(this.panelX + 37, this.mapY + 88, 110, 16)
            .build(QuietButton::new)
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("−"), b -> this.audibleRadius = Math.max(16, this.audibleRadius - this.radiusStep()))
            .bounds(this.panelX, this.mapY + 124, 24, 20)
            .build(QuietButton::new)
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("+"), b -> this.audibleRadius = Math.min(this.maxAudible(), this.audibleRadius + this.radiusStep()))
            .bounds(this.panelX + 160, this.mapY + 124, 24, 20)
            .build(QuietButton::new)
      );
      if (this.tier == AirRaidSirenBlock.Tier.TOWER) {
         this.addRenderableWidget(
            Button.builder(text("zone_only"), b -> this.zoneOnly = !this.zoneOnly).bounds(this.panelX, this.mapY + 156, 184, 20).build(QuietButton::new)
         );
         this.addRenderableWidget(
            Button.builder(Component.literal("−"), b -> this.zoneRadius = Math.max(64, this.zoneRadius - 100))
               .bounds(this.panelX, this.mapY + 208, 24, 20)
               .build(QuietButton::new)
         );
         this.addRenderableWidget(
            Button.builder(Component.literal("+"), b -> this.zoneRadius = Math.min(5000, this.zoneRadius + 100))
               .bounds(this.panelX + 160, this.mapY + 208, 24, 20)
               .build(QuietButton::new)
         );
         this.addRenderableWidget(
            Button.builder(text("select_map"), b -> this.selecting = true).bounds(this.panelX, this.mapY + 240, 184, 20).build(QuietButton::new)
         );
      }

      this.addRenderableWidget(Button.builder(text("zoom_out"), b -> {
         this.mapSpan = Math.min(20000, this.mapSpan * 2);
         this.refreshMap();
      }).bounds(this.mapX, this.mapY + this.mapSize + 5, 90, 18).build(QuietButton::new));
      this.addRenderableWidget(Button.builder(text("zoom_in"), b -> {
         this.mapSpan = Math.max(1000, this.mapSpan / 2);
         this.refreshMap();
      }).bounds(this.mapX + 95, this.mapY + this.mapSize + 5, 90, 18).build(QuietButton::new));
      this.addRenderableWidget(
         Button.builder(
               Component.translatable("screen.deepstrike.map.fullscreen"),
               b -> this.minecraft
                  .setScreen(
                     new ZoneXaeroMapScreen(
                        this,
                        this.tier == AirRaidSirenBlock.Tier.TOWER ? this.zoneX : this.pos.getX() + 0.5,
                        this.tier == AirRaidSirenBlock.Tier.TOWER ? this.zoneZ : this.pos.getZ() + 0.5,
                        this.tier == AirRaidSirenBlock.Tier.TOWER ? this.zoneRadius : (this.tier == AirRaidSirenBlock.Tier.ADVANCED ? 5000.0 : 2000.0),
                        this.tier == AirRaidSirenBlock.Tier.TOWER ? (x, z) -> {
                           this.zoneX = x;
                           this.zoneZ = z;
                           this.selecting = false;
                        } : null
                     )
                  )
            )
            .bounds(this.mapX + 190, this.mapY + this.mapSize + 5, Math.max(100, this.mapSize - 190), 18)
            .build(QuietButton::new)
      );
      this.addRenderableWidget(Button.builder(Component.translatable("screen.deepstrike.save"), b -> {
         AirRaidSirenNetwork.configure(this.pos, this.variant, this.audibleRadius, this.zoneOnly, this.zoneX, this.zoneZ, this.zoneRadius);
         this.onClose();
      }).bounds(this.panelX, Math.min(this.height - 35, this.mapY + this.mapSize - 22), 184, 22).build(QuietButton::new));
   }

   private int radiusStep() {
      return this.tier == AirRaidSirenBlock.Tier.TOWER ? 100 : 16;
   }

   private int maxAudible() {
      return this.tier == AirRaidSirenBlock.Tier.TOWER ? 5000 : 300;
   }

   private void refreshMap() {
      if (this.terrain != null) {
         this.terrain.refresh(this.pos.getX() + 0.5, this.pos.getZ() + 0.5, this.mapSpan, this.mapSpan);
      }
   }

   public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
      this.renderTransparentBackground(graphics);
      FlightUi.panel(graphics, this.panelX - 12, this.mapY - 30, this.mapSize + 242, this.mapSize + 65, -182838503, -10854328);
      FlightUi.rounded(graphics, this.panelX - 4, this.mapY, 192, 42, -14078937);
      FlightUi.rounded(graphics, this.panelX - 4, this.mapY + 46, 192, 58, -14078937);
      FlightUi.rounded(graphics, this.panelX - 4, this.mapY + 108, 192, 36, -14078937);
      if (this.tier == AirRaidSirenBlock.Tier.TOWER) {
         FlightUi.rounded(graphics, this.panelX - 4, this.mapY + 150, 192, 133, -14078937);
      }

      FlightUi.label(graphics, text("header", text("tier." + this.tier.name().toLowerCase(Locale.ROOT))).getString(), this.panelX, this.mapY - 19, -1182997);
      if (this.terrain != null) {
         this.terrain.render(graphics, this.mapX, this.mapY, this.mapSize, this.mapSize);
      }

      graphics.renderOutline(this.mapX, this.mapY, this.mapSize, this.mapSize, this.selecting ? -4355200 : -8419483);
      int cx = this.mapX + this.mapSize / 2;
      int cy = this.mapY + this.mapSize / 2;
      graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, -1);
      int ringX = this.mapX + (int)Math.round((this.zoneX - this.pos.getX() - 0.5) * this.mapSize / this.mapSpan) + this.mapSize / 2;
      int ringY = this.mapY + (int)Math.round((this.zoneZ - this.pos.getZ() - 0.5) * this.mapSize / this.mapSpan) + this.mapSize / 2;
      if (this.tier == AirRaidSirenBlock.Tier.TOWER) {
         double rr = (double)this.zoneRadius * this.mapSize / this.mapSpan;

         for (int i = 0; i < 180; i++) {
            double a = i * Math.PI * 2.0 / 180.0;
            int px = ringX + (int)Math.round(rr * Math.cos(a));
            int py = ringY + (int)Math.round(rr * Math.sin(a));
            if (px >= this.mapX && px < this.mapX + this.mapSize && py >= this.mapY && py < this.mapY + this.mapSize) {
               graphics.fill(px, py, px + 2, py + 2, -4355200);
            }
         }

         if (ringX >= this.mapX && ringX < this.mapX + this.mapSize && ringY >= this.mapY && ringY < this.mapY + this.mapSize) {
            graphics.fill(ringX - 2, ringY - 2, ringX + 3, ringY + 3, -4355200);
         }
      }

      String[] names = new String[]{"continuous", "intermittent", "wave"};
      graphics.drawString(this.font, text("detection"), this.panelX, this.mapY + 7, -6313858, false);
      graphics.drawString(
         this.font,
         text("blocks", this.tier == AirRaidSirenBlock.Tier.BASIC ? 2000 : (this.tier == AirRaidSirenBlock.Tier.ADVANCED ? 5000 : 10000)),
         this.panelX,
         this.mapY + 25,
         -1,
         false
      );
      if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.pos) instanceof AirRaidSirenBlockEntity siren) {
         graphics.drawString(
            this.font, text(siren.active() ? "alarm" : "clear"), this.panelX + 91, this.mapY + 7, siren.active() ? -4355200 : -6575733, false
         );
      }

      graphics.drawString(this.font, text("signal", this.variant + 1), this.panelX, this.mapY + 49, -6313858, false);
      graphics.drawCenteredString(this.font, text("variant." + names[this.variant]), this.panelX + 92, this.mapY + 71, -4413569);
      graphics.drawString(this.font, text("audibility"), this.panelX, this.mapY + 110, -6313858, false);
      graphics.drawCenteredString(this.font, text("blocks", this.audibleRadius), this.panelX + 92, this.mapY + 130, -1);
      if (this.tier == AirRaidSirenBlock.Tier.TOWER) {
         graphics.drawString(
            this.font, text(this.zoneOnly ? "mode.zone" : "mode.radius"), this.panelX, this.mapY + 181, this.zoneOnly ? -4413569 : -3483999, false
         );
         graphics.drawString(this.font, text("zone_radius"), this.panelX, this.mapY + 194, -6313858, false);
         graphics.drawCenteredString(this.font, text("blocks", this.zoneRadius), this.panelX + 92, this.mapY + 214, -1);
         graphics.drawString(
            this.font,
            (Component)(this.selecting ? text("click_map") : Component.literal("X " + Mth.floor(this.zoneX) + "  Z " + Mth.floor(this.zoneZ))),
            this.panelX,
            this.mapY + 265,
            -4413569,
            false
         );
      }

      graphics.drawString(this.font, text("scale", this.mapSpan), this.mapX + 4, this.mapY + this.mapSize - 13, -1513243, true);
      super.render(graphics, mx, my, partialTick);
   }

   public boolean mouseClicked(double x, double y, int button) {
      if (button == 0
         && this.selecting
         && this.tier == AirRaidSirenBlock.Tier.TOWER
         && x >= this.mapX
         && x < this.mapX + this.mapSize
         && y >= this.mapY
         && y < this.mapY + this.mapSize) {
         this.zoneX = this.pos.getX() + 0.5 + (x - this.mapX - this.mapSize / 2.0) * this.mapSpan / this.mapSize;
         this.zoneZ = this.pos.getZ() + 0.5 + (y - this.mapY - this.mapSize / 2.0) * this.mapSpan / this.mapSize;
         this.selecting = false;
         return true;
      } else {
         return super.mouseClicked(x, y, button);
      }
   }

   public void removed() {
      if (this.terrain != null) {
         this.terrain.close();
         this.terrain = null;
      }
   }
}
