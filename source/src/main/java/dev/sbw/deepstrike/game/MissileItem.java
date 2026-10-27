package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class MissileItem extends Item {
   private final int kind;
   private final boolean graphite;

   public MissileItem(int kind, Properties properties) {
      this(kind, false, properties);
   }

   public MissileItem(int kind, boolean graphite, Properties properties) {
      super(properties);
      this.kind = kind;
      this.graphite = graphite;
   }


   public MissileKind kind() {
      return MissileKind.byId(this.kind);
   }

   public boolean graphite() {
      return this.graphite;
   }

   public boolean isAirframe() {
      return false;
   }

   private static Component text(String key, Object... args) {
      return Component.translatable("tooltip.deepstrike.missile." + key, args);
   }

   public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      Level level = context.level();
      MissileData data = MissileData.fromItemStack(stack);
      if (data != null) {
         if (this.kind() == MissileKind.STORM_SHADOW) {
            tooltip.add(Component.translatable("su24.only_aircraft").withStyle(ChatFormatting.GOLD));
         }

         if (NbtCompat.hasTag(stack) && NbtCompat.getTag(stack).contains("LaunchEstimate")) {
            CompoundTag t = NbtCompat.getTag(stack).getCompound("LaunchEstimate");
            tooltip.add(text("recovered"));
            tooltip.add(
               Component.literal(
                  "§7("
                     + t.getInt("XMin")
                     + ", "
                     + t.getInt("YMin")
                     + ", "
                     + t.getInt("ZMin")
                     + ") — ("
                     + t.getInt("XMax")
                     + ", "
                     + t.getInt("YMax")
                     + ", "
                     + t.getInt("ZMax")
                     + ")"
               )
            );
         }

         tooltip.add(text("title"));
         tooltip.add(text("platform", data.kind().title()));
         String warheadId = data.warhead();

         Component warheadName = (Component)(switch (warheadId) {
            case "he" -> text("warhead.he", data.kind().blastPower);
            case "penetrating" -> text("warhead.penetrating");
            case "graphite" -> text("warhead.graphite");
            case "cluster" -> text(data.kind() == MissileKind.PEACEKEEPER ? "warhead.cluster_heavy" : "warhead.cluster");
            case "thermobaric" -> text("warhead.thermobaric");
            case "nuclear" -> text("warhead.nuclear");
            case "shaped_charge" -> text("warhead.shaped_charge");
            case "none" -> text("warhead.none");
            default -> Component.literal(data.warhead()).withStyle(ChatFormatting.WHITE);
         });
         tooltip.add(text("warhead", warheadName));
         Component engineName = text(data.engine().equals("turbojet") ? "engine.turbojet" : "engine.rocket");
         tooltip.add(text("engine", engineName));
         String camo = data.camo();

         String camoKey = switch (camo) {
            case "desert" -> "desert";
            case "gray" -> "gray";
            case "stealth" -> "stealth";
            case "sky" -> "sky";
            case "sea" -> "sea";
            case "graphite" -> "graphite";
            case "pink" -> "pink";
            default -> data.kind() == MissileKind.FP5 ? "graphite" : "default";
         };
         tooltip.add(Component.translatable("tooltip.deepstrike.camo", new Object[]{Component.translatable("camo.deepstrike." + camoKey)}));
         List<String> mods = data.modules();
         if (!mods.isEmpty()) {
            tooltip.add(text("modules"));

            for (String mod : mods) {
               switch (mod) {
                  case "range":
                     tooltip.add(text("module.range"));
                     break;
                  case "camera":
                     tooltip.add(text("module.camera"));
                     break;
                  case "ecm":
                     tooltip.add(text("module.ecm", data.kind() == MissileKind.PEACEKEEPER ? 5 : 35));
                     break;
                  case "booster":
                     tooltip.add(text(data.kind() == MissileKind.PEACEKEEPER ? "module.stage" : "module.booster"));
                     break;
                  case "gps":
                     tooltip.add(text("module.gps"));
               }
            }
         }

         int fuel = data.fuel();
         int maxFuel = data.maxFuel();
         int percent = maxFuel > 0 ? fuel * 100 / maxFuel : 0;
         int filledBars = Math.max(0, Math.min(10, percent / 10));
         String fuelBar = "§8[" + (filledBars > 0 ? "§6" + "|".repeat(filledBars) : "") + "§8" + ".".repeat(10 - filledBars) + "§8]";
         if (fuel <= 0) {
            tooltip.add(text("fuel.empty", fuelBar, maxFuel));
            tooltip.add(text("fuel.refuel"));
         } else {
            tooltip.add(text("fuel.filled", fuelBar, fuel, maxFuel));
         }

         tooltip.add(text("parameters"));
         tooltip.add(Component.translatable("tooltip.deepstrike.max_range", new Object[]{data.calculateMaxRange()}));
         tooltip.add(text("max_speed", data.calculateMaxSpeed()));
         if (data.hasEcm()) {
            int debuffPercent = (int)Math.round(data.calculateEcmDebuff() * 100.0);
            tooltip.add(text("evasion", debuffPercent));
         }

         if (data.hasCamera()) {
            tooltip.add(text("camera"));
         }
      }
   }
}
