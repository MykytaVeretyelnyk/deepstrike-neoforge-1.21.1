package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ThermalAtlas {
   private static final Map<String, String> MASKS = new HashMap<>();
   private static final Logger LOGGER = LoggerFactory.getLogger(ThermalAtlas.class);

   static void registerReload(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener((ResourceManagerReloadListener)resources -> Minecraft.getInstance().execute(() -> {
         TextureManager textures = Minecraft.getInstance().getTextureManager();

         for (String name : MASKS.values()) {
            if (name.startsWith("runtime_sensor/")) {
               textures.release(location(name));
            }
         }

         MASKS.clear();
      }));
   }

   static String forTexture(String atlas) {
      return MASKS.computeIfAbsent(atlas, ThermalAtlas::create);
   }

   private static ResourceLocation location(String atlas) {
      return ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/entity/" + atlas + ".png");
   }

   private static String create(String atlas) {
      Minecraft mc = Minecraft.getInstance();
      String name = "runtime_sensor/" + atlas;

      try {
         String var10;
         try (InputStream input = mc.getResourceManager().open(location(atlas))) {
            NativeImage image = NativeImage.read(input);

            for (int y = 0; y < image.getHeight(); y++) {
               for (int x = 0; x < image.getWidth(); x++) {
                  image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) & 0xFF000000 | 16316664);
               }
            }

            mc.getTextureManager().register(location(name), new DynamicTexture(image));
            LOGGER.info("Thermal atlas preserves alpha: {} ({}x{})", new Object[]{atlas, image.getWidth(), image.getHeight()});
            var10 = name;
         }

         return var10;
      } catch (IOException var9) {
         LOGGER.error("Cannot create thermal alpha mask for {}", atlas, var9);
         return atlas;
      }
   }

   private ThermalAtlas() {
   }
}
