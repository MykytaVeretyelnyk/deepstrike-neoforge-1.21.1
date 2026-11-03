package dev.warmode.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum WarTeam {
   BLUE("blue", ChatFormatting.BLUE, 0x4F86FF),
   RED("red", ChatFormatting.RED, 0xFF5050);

   public final String id;
   public final ChatFormatting format;
   public final int color;

   WarTeam(String id, ChatFormatting format, int color) {
      this.id = id;
      this.format = format;
      this.color = color;
   }

   public WarTeam other() {
      return this == BLUE ? RED : BLUE;
   }

   public Component title() {
      return Component.translatable("team.warmode." + this.id).withStyle(this.format);
   }

   public String scoreboardName() {
      return "war_" + this.id;
   }

   public static WarTeam byId(String id) {
      for (WarTeam team : values()) {
         if (team.id.equalsIgnoreCase(id)) {
            return team;
         }
      }

      return null;
   }

   public static WarTeam byOrdinal(int ordinal) {
      return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
   }
}
