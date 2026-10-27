package dev.sbw.deepstrike.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class HelpLocaleReviewScreen extends Screen {
   private final String locale;
   private Language previous;
   private final SpotlightTour tour = new SpotlightTour();
   private static final String[] TOPICS = new String[]{"control", "launch", "delete", "map", "zone", "save", "adjust", "hide", "journal", "frequency"};

   HelpLocaleReviewScreen(String locale) {
      super(Component.literal(locale));
      this.locale = locale;
   }

   protected void init() {
      if (this.previous == null) {
         this.previous = Language.getInstance();
      }

      boolean rtl = List.of("ar_sa", "fa_ir", "he_il", "yi_de", "zlm_arab").contains(this.locale);
      Language.inject(ClientLanguage.loadFrom(this.minecraft.getResourceManager(), List.of("en_us", this.locale), rtl));
      int longest = -1;
      Component selected = null;

      for (String topic : TOPICS) {
         MutableComponent component = Component.translatable("help.deepstrike." + topic);
         String value = component.getString();
         if (value.startsWith("help.deepstrike.")) {
            throw new IllegalStateException("Missing packaged help for " + this.locale + "/" + topic);
         }

         int lines = this.minecraft.font.split(component.copy().withStyle(FlightUi.STYLE), 332).size();
         if (lines * 13 + 54 > this.height - 24) {
            throw new IllegalStateException("Help card exceeds viewport: " + this.locale + "/" + topic);
         }

         if (lines > longest) {
            longest = lines;
            selected = component;
         }
      }

      this.tour.start(List.of(new SpotlightTour.Step(() -> new SpotlightTour.Box(40, 100, 260, 30), selected)));
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      FlightUi.desktop(g, this.width, this.height);
      FlightUi.label(g, "DEEPSTRIKE / " + this.locale, 40, 30, -1182997);
      FlightUi.control(g, 40, 100, 260, 30, Component.translatable("screen.deepstrike.map.fullscreen").getString(), false, true, false, false);
      this.tour.render(g, this.width, this.height, mx, my);
   }

   public void removed() {
      if (this.previous != null) {
         Language.inject(this.previous);
         this.previous = null;
      }
   }
}
