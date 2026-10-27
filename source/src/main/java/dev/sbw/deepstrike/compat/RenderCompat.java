package dev.sbw.deepstrike.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;

public final class RenderCompat {
   private RenderCompat() {
   }

   /** Uploads and draws a tesselator buffer, skipping it when nothing was written. */
   public static void draw(BufferBuilder builder) {
      MeshData mesh = builder.build();
      if (mesh != null) {
         BufferUploader.drawWithShader(mesh);
      }
   }
}
