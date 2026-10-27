package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.network.FrequencyNetwork;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class FrequencySettingsScreen extends Screen implements EquipmentUiTheme {
   private final Screen parent;
   private final int defenseId;
   private final InteractionHand hand;
   private final List<String> initial;
   private EditBox[] fields;
   private String error = "";

   @Override
   public String equipmentSkin() {
      return this.parent instanceof EquipmentUiTheme ? EquipmentUiTheme.inherited(this.parent) : EquipmentUiTheme.vehicle(this.defenseId);
   }

   private FrequencySettingsScreen(Screen parent, int defenseId, InteractionHand hand, List<String> initial) {
      super(Component.translatable(hand == null ? "screen.deepstrike.frequency.defense_title" : "screen.deepstrike.frequency.tablet_title"));
      this.parent = parent;
      this.defenseId = defenseId;
      this.hand = hand;
      this.initial = initial;
   }

   public static void tablet(InteractionHand hand, ItemStack stack) {
      Minecraft mc = Minecraft.getInstance();
      mc.setScreen(new FrequencySettingsScreen(mc.screen, -1, hand, RadioFrequency.tabletChannels(stack)));
   }

   public static void defense(Screen parent, int entityId, List<String> values) {
      Minecraft.getInstance().setScreen(new FrequencySettingsScreen(parent, entityId, null, values));
   }

   protected void init() {
      int count = this.hand == null ? 1 : 3;
      String[] preserved = this.fields == null ? null : Arrays.stream(this.fields).map(EditBox::getValue).toArray(String[]::new);
      this.fields = new EditBox[count];
      int x = this.width / 2 - 145;
      int y = this.height / 2 - 55;

      for (int i = 0; i < count; i++) {
         EditBox box = new InstrumentField(this.font, x, y + i * 36, 290, 20, Component.translatable("screen.deepstrike.frequency.label"));
         box.setMaxLength(this.hand == null ? 220 : 12);
         if (this.hand != null) {
            box.setFilter(value -> value.matches("[0-9]{0,12}"));
         }

         box.setBordered(false);
         box.setTextColor(-1182997);
         box.setFormatter((v, c) -> FlightUi.text(v).getVisualOrderText());
         box.setValue(
            preserved != null ? preserved[i] : (this.hand == null ? String.join(",", this.initial) : (i < this.initial.size() ? this.initial.get(i) : ""))
         );
         this.fields[i] = (EditBox)this.addRenderableWidget(box);
      }

      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.deepstrike.save"), b -> this.save()).bounds(x, y + count * 36 + 12, 140, 20).build(QuietButton::new)
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.deepstrike.back"), b -> this.onClose())
            .bounds(x + 150, y + count * 36 + 12, 140, 20)
            .build(QuietButton::new)
      );
   }

   private void save() {
      if (this.hand != null) {
         for (EditBox box : this.fields) {
            if (!RadioFrequency.valid(box.getValue())) {
               this.error = "screen.deepstrike.frequency.invalid";
               return;
            }
         }

         FrequencyNetwork.CHANNEL
            .sendToServer(
               new FrequencyNetwork.SetTablet(
                  this.hand == InteractionHand.OFF_HAND, this.fields[0].getValue(), this.fields[1].getValue(), this.fields[2].getValue()
               )
            );
      } else {
         try {
            RadioFrequency.parse(this.fields[0].getValue(), 16);
         } catch (IllegalArgumentException var5) {
            this.error = "screen.deepstrike.frequency.invalid_list";
            return;
         }

         FrequencyNetwork.CHANNEL.sendToServer(new FrequencyNetwork.SetDefense(this.defenseId, this.fields[0].getValue()));
      }

      this.onClose();
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      this.renderTransparentBackground(g);
      int x = this.width / 2 - 145;
      int y = this.height / 2 - 55;
      FlightUi.panel(g, x - 24, y - 78, 338, this.fields.length * 36 + 153, -182838503, -10854328);
      FlightUi.label(g, this.title.getString(), x, y - 62, -1182997);
      g.fill(x, y - 43, x + 290, y - 42, -10854328);
      int hintY = y - 34;

      for (FormattedCharSequence line : this.font
         .split(
            Component.translatable(this.hand == null ? "screen.deepstrike.frequency.defense_hint" : "screen.deepstrike.frequency.tablet_hint")
               .copy()
               .withStyle(FlightUi.STYLE),
            290
         )) {
         g.drawString(this.font, line, x, hintY, -5196890, false);
         hintY += 11;
      }

      for (int i = 0; i < this.fields.length; i++) {
         FlightUi.label(
            g,
            (this.hand == null
                  ? Component.translatable("screen.deepstrike.frequency.friendly")
                  : Component.translatable("screen.deepstrike.frequency.channel", new Object[]{i + 1}))
               .getString(),
            x,
            y + i * 36 - 12,
            -5196890
         );
         FlightUi.rounded(g, x - 5, y + i * 36 - 4, 300, 25, -14078937);
      }

      if (!this.error.isEmpty()) {
         int yy = y + this.fields.length * 36 + 40;

         for (FormattedCharSequence line : this.font.split(Component.translatable(this.error).copy().withStyle(FlightUi.STYLE), 290)) {
            g.drawString(this.font, line, x, yy, -1076610, false);
            yy += 11;
         }
      }

      super.render(g, mx, my, partial);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
