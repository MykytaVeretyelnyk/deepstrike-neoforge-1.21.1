package dev.sbw.deepstrike.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.LogicalSide;

/**
 * Forge 1.20-style phased tick events, re-posted on the NeoForge game bus from
 * the split Pre/Post events of NeoForge 1.21.
 */
public abstract class TickEvent extends Event {
   public final TickEvent.Type type;
   public final LogicalSide side;
   public final TickEvent.Phase phase;

   protected TickEvent(TickEvent.Type type, LogicalSide side, TickEvent.Phase phase) {
      this.type = type;
      this.side = side;
      this.phase = phase;
   }

   public static void install(IEventBus gameBus) {
      gameBus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre e) -> gameBus.post(new ServerTickEvent(TickEvent.Phase.START, e.getServer())));
      gameBus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> gameBus.post(new ServerTickEvent(TickEvent.Phase.END, e.getServer())));
      gameBus.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Pre e) -> gameBus.post(new LevelTickEvent(TickEvent.Phase.START, e.getLevel())));
      gameBus.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> gameBus.post(new LevelTickEvent(TickEvent.Phase.END, e.getLevel())));
   }

   public static void installClient(IEventBus gameBus) {
      gameBus.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Pre e) -> gameBus.post(new ClientTickEvent(TickEvent.Phase.START)));
      gameBus.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> gameBus.post(new ClientTickEvent(TickEvent.Phase.END)));
      gameBus.addListener(
         (net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e) -> gameBus.post(
            new RenderTickEvent(TickEvent.Phase.START, e.getPartialTick().getGameTimeDeltaPartialTick(true))
         )
      );
      gameBus.addListener(
         (net.neoforged.neoforge.client.event.RenderFrameEvent.Post e) -> gameBus.post(
            new RenderTickEvent(TickEvent.Phase.END, e.getPartialTick().getGameTimeDeltaPartialTick(true))
         )
      );
   }

   public static enum Phase {
      START,
      END;
   }

   public static enum Type {
      LEVEL,
      PLAYER,
      CLIENT,
      SERVER,
      RENDER;
   }

   public static class ClientTickEvent extends TickEvent {
      public ClientTickEvent(TickEvent.Phase phase) {
         super(TickEvent.Type.CLIENT, LogicalSide.CLIENT, phase);
      }
   }

   public static class ServerTickEvent extends TickEvent {
      private final MinecraftServer server;

      public ServerTickEvent(TickEvent.Phase phase, MinecraftServer server) {
         super(TickEvent.Type.SERVER, LogicalSide.SERVER, phase);
         this.server = server;
      }

      public MinecraftServer getServer() {
         return this.server;
      }
   }

   public static class LevelTickEvent extends TickEvent {
      public final Level level;

      public LevelTickEvent(TickEvent.Phase phase, Level level) {
         super(TickEvent.Type.LEVEL, level.isClientSide ? LogicalSide.CLIENT : LogicalSide.SERVER, phase);
         this.level = level;
      }
   }

   public static class RenderTickEvent extends TickEvent {
      public final float renderTickTime;

      public RenderTickEvent(TickEvent.Phase phase, float renderTickTime) {
         super(TickEvent.Type.RENDER, LogicalSide.CLIENT, phase);
         this.renderTickTime = renderTickTime;
      }
   }
}
