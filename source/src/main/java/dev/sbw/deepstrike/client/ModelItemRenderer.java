package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.AirDefenseMissileItem;
import dev.sbw.deepstrike.game.AirDefenseSpawnItem;
import dev.sbw.deepstrike.game.AirRaidSirenBlock;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileItem;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class ModelItemRenderer extends BlockEntityWithoutLevelRenderer {
   private static ModelItemRenderer instance;
   private static final Map<String, ModelItemRenderer.Bounds> BOUNDS = new HashMap<>();

   public static void register(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (ModelItemRenderer.instance == null) {
               ModelItemRenderer.instance = new ModelItemRenderer();
            }

            return ModelItemRenderer.instance;
         }
      });
   }

   private ModelItemRenderer() {
      super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
   }

   public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
      boolean spikeVehicle = false;
      String model;
      String texture;
      float scale;
      if (stack.getItem() instanceof MissileItem m) {
         MissileData md = MissileData.fromItemStack(stack);
         String camo = md != null ? md.camo() : MissileData.defaultCamo(m.kind());
         model = ModelAssets.missile(m.kind());
         texture = ModelAssets.texture(m.kind(), camo, m.graphite());
         scale = m.kind() == MissileKind.PEACEKEEPER ? 0.042F : (m.kind() == MissileKind.JAGM ? 0.39F : 0.13F);
      } else if (stack.getItem() instanceof AirDefenseMissileItem adm) {
         model = adm.modelName();
         texture = adm.textureName();
         scale = adm.scale();
      } else if (stack.getItem() instanceof BlockItem b && b.getBlock() instanceof LauncherBlock l) {
         model = ModelAssets.launcher(l.family());
         texture = ModelAssets.launcherTexture(l.family(), l.graphite());
         scale = l.family() == LauncherBlock.Family.PEACEKEEPER ? 0.12F : (l.family() == LauncherBlock.Family.JAGM ? 0.22F : 0.13F);
      } else if (stack.getItem() instanceof BlockItem b && b.getBlock() instanceof AirRaidSirenBlock) {
         model = "siren_tower";
         texture = model;
         scale = 0.33F;
      } else {
         if (!(stack.getItem() instanceof AirDefenseSpawnItem)) {
            return;
         }

         if (stack.is((Item)BFItems.PATRIOT_TRUCK.get())) {
            model = "mim_104_patriot_truck";
         } else if (stack.is((Item)BFItems.IRON_DOME.get())) {
            model = "iron_dome";
         } else if (stack.is((Item)BFItems.AN_MPQ_65.get())) {
            model = "an_mpq_65";
         } else {
            if (!stack.is((Item)BFItems.SPIKE_NLOS.get())) {
               return;
            }

            model = "spike_nlos_jltv";
            spikeVehicle = true;
         }

         texture = model + (spikeVehicle ? "_sand" : "_forest");
         scale = 1.0F;
      }

      pose.pushPose();
      pose.translate(0.5, 0.5, 0.5);
      pose.mulPose(Axis.XP.rotationDegrees(22.0F));
      pose.mulPose(Axis.YP.rotationDegrees(-35.0F));
      pose.mulPose(Axis.ZP.rotationDegrees(-12.0F));
      ModelItemRenderer.Bounds bounds = BOUNDS.computeIfAbsent(model, ModelItemRenderer::bounds);
      if (context == ItemDisplayContext.GUI) {
         scale = Math.min(scale * 1.18F, 0.88F / bounds.size);
      } else {
         scale = Math.min(scale * 1.5F, 1.25F / bounds.size);
      }

      pose.scale(scale, scale, scale);
      pose.translate(-bounds.cx, -bounds.cy, -bounds.cz);
      ModelRenderer.render(model, texture, pose, buffers, light, overlay, 0.0F, 0.0F, bx -> !bx.startsWith("cover_"));
      pose.popPose();
   }

   private static ModelItemRenderer.Bounds bounds(String model) {
      float minX = Float.MAX_VALUE;
      float minY = Float.MAX_VALUE;
      float minZ = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;
      float maxZ = -Float.MAX_VALUE;

      for (ModelRenderer.BakedBone bone : ModelRenderer.bakeModel(model).bones) {
         for (ModelRenderer.BakedFace f : bone.staticFaces) {
            minX = Math.min(minX, Math.min(Math.min(f.x0, f.x1), Math.min(f.x2, f.x3)));
            minY = Math.min(minY, Math.min(Math.min(f.y0, f.y1), Math.min(f.y2, f.y3)));
            minZ = Math.min(minZ, Math.min(Math.min(f.z0, f.z1), Math.min(f.z2, f.z3)));
            maxX = Math.max(maxX, Math.max(Math.max(f.x0, f.x1), Math.max(f.x2, f.x3)));
            maxY = Math.max(maxY, Math.max(Math.max(f.y0, f.y1), Math.max(f.y2, f.y3)));
            maxZ = Math.max(maxZ, Math.max(Math.max(f.z0, f.z1), Math.max(f.z2, f.z3)));
         }
      }

      return minX == Float.MAX_VALUE
         ? new ModelItemRenderer.Bounds(0.0F, 0.0F, 0.0F, 1.0F)
         : new ModelItemRenderer.Bounds(
            (minX + maxX) / 2.0F, (minY + maxY) / 2.0F, (minZ + maxZ) / 2.0F, Math.max(0.01F, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)))
         );
   }

   private record Bounds(float cx, float cy, float cz, float size) {
   }
}
