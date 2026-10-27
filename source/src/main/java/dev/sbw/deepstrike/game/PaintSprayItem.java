package dev.sbw.deepstrike.game;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.client.ClientSetup;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.AnMpq65Entity;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import dev.sbw.deepstrike.compat.DistExecutor;

public final class PaintSprayItem extends Item {
   private static final String[] STANDARD = new String[]{"forest", "desert", "urban", "winter", "solid"};
   private static final String[] SPIKE = new String[]{"sand", "green", "multicam"};

   public PaintSprayItem(Properties properties) {
      super(properties);
   }

   public static String[] skins(Entity entity) {
      if (entity instanceof SpikeNlosEntity) {
         return SPIKE;
      } else {
         return !(entity instanceof PatriotTruckEntity) && !(entity instanceof IronDomeEntity) && !(entity instanceof AnMpq65Entity) ? new String[0] : STANDARD;
      }
   }

   public static boolean accepts(Entity entity, String skin) {
      for (String id : skins(entity)) {
         if (id.equals(skin)) {
            return true;
         }
      }

      return false;
   }

   public static Component label(String skin) {
      String key = switch (skin) {
         case "forest", "urban", "winter", "solid", "green", "multicam" -> skin;
         case "desert", "sand" -> "desert";
         default -> null;
      };
      return key == null ? Component.literal(skin) : Component.translatable("camo.deepstrike." + key);
   }

   public static void entityInteract(EntityInteract event) {
      interact(event, event.getTarget());
   }

   public static void entityInteractSpecific(EntityInteractSpecific event) {
      interact(event, event.getTarget());
   }

   private static void interact(PlayerInteractEvent event, Entity target) {
      if (event.getItemStack().is((Item)BFItems.PAINT_SPRAY.get()) && target instanceof VehicleEntity && skins(target).length != 0) {
         InteractionResult result = InteractionResult.sidedSuccess(event.getEntity().level().isClientSide);
         if (event instanceof EntityInteract ei) {
            ei.setCancellationResult(result);
            ei.setCanceled(true);
         } else if (event instanceof EntityInteractSpecific es) {
            es.setCancellationResult(result);
            es.setCanceled(true);
         }
         if (event.getEntity().level().isClientSide) {
            int entityId = target.getId();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.openPaintScreen(entityId));
         }
      }
   }
}
