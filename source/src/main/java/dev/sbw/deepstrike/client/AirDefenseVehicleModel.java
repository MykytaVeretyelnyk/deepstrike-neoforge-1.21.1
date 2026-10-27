package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.client.model.entity.VehicleModel;
import com.atsuishio.superbwarfare.data.vehicle_skin.SkinInfo;
import com.atsuishio.superbwarfare.data.vehicle_skin.VehicleSkin;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.AnimationState;

public class AirDefenseVehicleModel<T extends VehicleEntity & GeoAnimatable> extends VehicleModel<T> {
   public void setCustomAnimations(T entity, long instanceId, AnimationState<T> state) {
      super.setCustomAnimations(entity, instanceId, state);
      if (entity instanceof SpikeNlosEntity spike) {
         GeoBone root = this.getAnimationProcessor().getBone("root");
         if (root != null) {
            root.setRotY(0.0F);
         }

         int loaded = spike.readyMissiles();

         for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
               GeoBone lid = this.getAnimationProcessor().getBone("canister_" + row + "_" + col + "_lid");
               if (lid != null) {
                  lid.setHidden(row * 2 + col >= loaded);
               }
            }
         }
      }

      if (entity instanceof PatriotTruckEntity truck) {
         GeoBone steering = this.getAnimationProcessor().getBone("cab_steering");
         if (steering != null) {
            steering.setRotZ(-4.0F * Mth.lerp(state.getPartialTick(), truck.getRudderRotO(), truck.getRudderRot()));
         }

         float tick = truck.getDeployTicks() + state.getPartialTick();

         float timeline = switch (truck.getDeployState()) {
            case 1 -> tick;
            case 2 -> 160.0F;
            case 3 -> 160.0F - tick;
            default -> 0.0F;
         };
         float lift = smooth((timeline - 80.0F) / 80.0F);
         GeoBone launcher = this.getAnimationProcessor().getBone("launcher");
         if (launcher != null) {
            launcher.setRotX((float)Math.toRadians(38.0) * lift);
         }

         double angle = Math.toRadians(38.0) * lift;
         double dy = 0.15 * Math.cos(angle) + 2.46 * Math.sin(angle) + 1.78 - 1.56;
         double dz = 0.15 * Math.sin(angle) - 2.46 * Math.cos(angle) + 7.66 - 3.1;
         double length = Math.hypot(dy, dz);
         float ramAngle = (float)(-Math.atan2(dy, dz));
         double sleeveLength = Math.hypot(0.27, 1.56);

         for (int side : new int[]{-1, 1}) {
            GeoBone sleeve = this.getAnimationProcessor().getBone("ram_" + side + "_sleeve");
            GeoBone rod = this.getAnimationProcessor().getBone("ram_" + side + "_rod");
            if (sleeve != null) {
               sleeve.setRotX(ramAngle);
            }

            if (rod != null) {
               rod.setRotX(ramAngle);
               rod.setPosY((float)((1.56 + dy / length * sleeveLength - 1.83) * 16.0));
               rod.setPosZ((float)((3.1 + dz / length * sleeveLength - 4.66) * 16.0));
               rod.setScaleZ((float)((length - sleeveLength) / Math.hypot(0.1, 0.54)));
            }
         }
      }
   }

   private static float smooth(float value) {
      float t = Mth.clamp(value, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   public ResourceLocation getAnimationResource(T entity) {
      return entity instanceof PatriotTruckEntity
         ? ResourceLocation.fromNamespaceAndPath("deepstrike", "animations/mim_104_patriot_truck.animation.json")
         : super.getAnimationResource(entity);
   }

   public ResourceLocation getTextureResource(T entity) {
      String skinId = entity.getSkinId();
      if (skinId != null && !skinId.isEmpty()) {
         SkinInfo skin = VehicleSkin.Companion.getSkin(entity);
         if (skin != null && skin.getTexture() != null && !skin.getTexture().isEmpty()) {
            return ResourceLocation.parse(skin.getTexture());
         }
      }

      return super.getTextureResource(entity);
   }
}
