package dev.colonyjournal.client;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColonyView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client side entry points: the key binding and the screen opener used by the journal item. */
public final class JournalClient {
   private static final KeyMapping OPEN = new KeyMapping("key.colonyjournal.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.colonyjournal");

   public static void init(IEventBus bus) {
      bus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN));
      NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
         while (OPEN.consumeClick()) {
            open();
         }
      });
   }

   public static void open() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return;
      }

      IColonyView colony = IMinecoloniesAPI.getInstance().getColonyManager().getClosestColonyView(mc.level, mc.player.blockPosition());
      if (colony == null) {
         mc.player.displayClientMessage(Component.translatable("journal.colonyjournal.no_colony"), true);
      } else {
         mc.setScreen(new JournalScreen(colony));
      }
   }

   private JournalClient() {
   }
}
