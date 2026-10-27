package dev.sbw.deepstrike.game;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ModularComponentItem extends Item {
   private final String category;
   private final String componentId;

   public ModularComponentItem(String category, String componentId, Properties properties) {
      super(properties);
      this.category = category;
      this.componentId = componentId;
   }

   public String category() {
      return this.category;
   }

   public String componentId() {
      return this.componentId;
   }

   public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      Level level = context.level();
      if ("warhead".equals(this.category)) {
         tooltip.add(Component.translatable("tooltip.deepstrike.component.warhead.title"));
         String var5 = this.componentId;
         switch (var5) {
            case "he":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.he.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.he.radius"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.he.structures"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.he.description"));
               break;
            case "penetrating":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.penetrating.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.penetrating.depth"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.penetrating.crater"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.penetrating.description"));
               break;
            case "graphite":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.graphite.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.graphite.fibers"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.graphite.systems"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.graphite.description"));
               break;
            case "cluster":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.cluster.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.cluster.airburst"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.cluster.spread"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.cluster.description"));
               break;
            case "thermobaric":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.thermobaric.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.thermobaric.radius"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.thermobaric.burn"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.thermobaric.description"));
         }
      } else if ("engine".equals(this.category)) {
         tooltip.add(Component.translatable("tooltip.deepstrike.component.engine.title"));
         String var7 = this.componentId;
         switch (var7) {
            case "rocket":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.rocket.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.rocket.launch"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.rocket.description"));
               break;
            case "turbojet":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.turbojet.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.turbojet.range"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.turbojet.speed"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.turbojet.description"));
         }
      } else if ("module".equals(this.category)) {
         tooltip.add(Component.translatable("tooltip.deepstrike.component.module.title"));
         String var8 = this.componentId;
         switch (var8) {
            case "range":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.range.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.range.range"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.range.capacity"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.range.description"));
               break;
            case "camera":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.camera.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.camera.video"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.camera.compatibility"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.camera.description"));
               break;
            case "ecm":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.ecm.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.ecm.chance"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.ecm.lock"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.ecm.description"));
               break;
            case "booster":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.booster.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.booster.speed"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.booster.time"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.booster.description"));
               break;
            case "gps":
               tooltip.add(Component.translatable("tooltip.deepstrike.component.gps.type"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.gps.guidance"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.gps.wind"));
               tooltip.add(Component.translatable("tooltip.deepstrike.component.gps.description"));
         }
      }

      tooltip.add(Component.translatable("tooltip.deepstrike.component.installation"));
   }
}
