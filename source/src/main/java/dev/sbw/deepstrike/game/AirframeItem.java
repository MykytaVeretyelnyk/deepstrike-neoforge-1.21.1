package dev.sbw.deepstrike.game;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class AirframeItem extends MissileItem {
   public AirframeItem(MissileKind kind, Properties properties) {
      super(kind.ordinal(), false, properties);
   }

   @Override
   public boolean isAirframe() {
      return true;
   }

   @Override
   public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      Level level = context.level();
      tooltip.add(Component.translatable("tooltip.deepstrike.airframe.title"));
      tooltip.add(Component.translatable("tooltip.deepstrike.airframe.type"));
      MissileData data = MissileData.fromItemStack(stack);
      if (data != null) {
         int fuel = data.fuel();
         int maxFuel = data.maxFuel();
         int percent = maxFuel > 0 ? fuel * 100 / maxFuel : 0;
         String fuelBar = "§8[" + (percent > 0 ? "§6" + "|".repeat(Math.max(1, percent / 10)) : "") + "§8" + ".".repeat(Math.max(0, 10 - percent / 10)) + "§8]";
         if (fuel <= 0) {
            tooltip.add(Component.translatable("tooltip.deepstrike.fuel.empty", new Object[]{fuelBar, maxFuel}));
            tooltip.add(Component.translatable("tooltip.deepstrike.airframe.refuel"));
         } else {
            tooltip.add(Component.translatable("tooltip.deepstrike.fuel.ready", new Object[]{fuelBar, fuel, maxFuel}));
         }

         tooltip.add(Component.translatable("tooltip.deepstrike.camo", new Object[]{formatCamo(data.kind(), data.camo())}));
         tooltip.add(Component.translatable("tooltip.deepstrike.airframe.profile"));
         tooltip.add(Component.translatable("tooltip.deepstrike.max_range", new Object[]{data.calculateMaxRange()}));
         tooltip.add(Component.translatable("tooltip.deepstrike.speed", new Object[]{data.calculateMaxSpeed()}));
         tooltip.add(Component.translatable("tooltip.deepstrike.airframe.warhead"));
      }

      tooltip.add(Component.translatable("tooltip.deepstrike.airframe.configure"));
   }

   private static Component formatCamo(MissileKind kind, String camo) {
      String c = camo == null ? "" : camo.trim().toLowerCase();

      return switch (c) {
         case "desert" -> Component.translatable("camo.deepstrike.desert");
         case "gray" -> Component.translatable("camo.deepstrike.gray");
         case "stealth" -> Component.translatable("camo.deepstrike.stealth");
         case "sky" -> Component.translatable("camo.deepstrike.sky");
         case "sea" -> Component.translatable("camo.deepstrike.sea");
         case "graphite" -> Component.translatable("camo.deepstrike.graphite");
         case "pink" -> Component.translatable("camo.deepstrike.pink");
         default -> Component.translatable(kind == MissileKind.FP5 ? "camo.deepstrike.graphite" : "camo.deepstrike.default");
      };
   }
}
