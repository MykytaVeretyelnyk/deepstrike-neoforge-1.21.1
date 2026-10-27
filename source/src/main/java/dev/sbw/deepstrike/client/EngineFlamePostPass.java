package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;

final class EngineFlamePostPass {
   private static final ArrayList<EngineFlamePostPass.Draw> pending = new ArrayList<>();
   private static final BufferSource buffers = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(262144));
   private static boolean afterWorld;
   private static Method instance;
   private static Method inUse;
   private static boolean apiChecked;

   static boolean shaders() {
      if (!apiChecked) {
         apiChecked = true;

         try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            instance = api.getMethod("getInstance");
            inUse = api.getMethod("isShaderPackInUse");
         } catch (ReflectiveOperationException var1) {
            return false;
         }
      }

      try {
         return inUse != null && (Boolean)inUse.invoke(instance.invoke(null));
      } catch (ReflectiveOperationException var2) {
         return false;
      }
   }

   static boolean defer(PoseStack pose, BiConsumer<PoseStack, MultiBufferSource> render) {
      if (!afterWorld && shaders()) {
         PoseStack copy = new PoseStack();
         copy.last().pose().set(pose.last().pose());
         copy.last().normal().set(pose.last().normal());
         pending.add(
            new EngineFlamePostPass.Draw(copy, new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()), render)
         );
         return true;
      } else {
         return false;
      }
   }

   static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_SKY) {
         pending.clear();
         afterWorld = false;
      } else if (event.getStage() == Stage.AFTER_LEVEL) {
         afterWorld = true;
         if (!pending.isEmpty()) {
            org.joml.Matrix4fStack view = RenderSystem.getModelViewStack();
            view.pushMatrix();
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            VertexSorting sorting = RenderSystem.getVertexSorting();

            try {
               for (EngineFlamePostPass.Draw draw : pending) {
                  view.set(draw.view);
                  RenderSystem.applyModelViewMatrix();
                  RenderSystem.setProjectionMatrix(draw.projection, VertexSorting.DISTANCE_TO_ORIGIN);
                  draw.render.accept(draw.pose, buffers);
                  buffers.endBatch();
               }
            } finally {
               pending.clear();
               view.popMatrix();
               RenderSystem.applyModelViewMatrix();
               RenderSystem.setProjectionMatrix(projection, sorting);
            }
         }
      }
   }

   private EngineFlamePostPass() {
   }

   private record Draw(PoseStack pose, Matrix4f view, Matrix4f projection, BiConsumer<PoseStack, MultiBufferSource> render) {
   }
}
