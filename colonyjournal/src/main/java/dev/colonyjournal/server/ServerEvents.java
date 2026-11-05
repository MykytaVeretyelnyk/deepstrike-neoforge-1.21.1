package dev.colonyjournal.server;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.eventbus.EventBus;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import dev.colonyjournal.ColonyJournal;
import dev.colonyjournal.economy.EconomyData;
import dev.colonyjournal.economy.EconomyService;
import dev.colonyjournal.net.Views.ColonyRef;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Server side event wiring: the day poll, re-applying the tax happiness modifiers, claim pickup, colony deletion.
 * {@link #init()} is called once from the mod constructor on both dists, so it only registers listeners.
 */
public final class ServerEvents {
   private static final int POLL_INTERVAL_TICKS = 100;
   /** The MineColonies event bus we subscribed to; subscribing again to the same instance would duplicate handlers. */
   private static EventBus subscribedBus;

   public static void init() {
      NeoForge.EVENT_BUS.addListener(ServerEvents::onServerTick);
      NeoForge.EVENT_BUS.addListener(ServerEvents::onPlayerLoggedIn);
      NeoForge.EVENT_BUS.addListener(ServerEvents::onPlayerLoggedOut);
      NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStarting);
      NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStopped);
   }

   private static void onServerStarting(ServerStartingEvent event) {
      EconomyService.clearCaches();
      ServerHandlers.clearLimits();
      subscribeColonyEvents();
   }

   private static void onServerStopped(ServerStoppedEvent event) {
      EconomyService.clearCaches();
      ServerHandlers.clearLimits();
   }

   private static void subscribeColonyEvents() {
      try {
         EventBus bus = IMinecoloniesAPI.getInstance().getEventBus();
         if (bus == subscribedBus) {
            return;
         }

         bus.subscribe(ColonyDeletedModEvent.class, ServerEvents::onColonyDeleted);
         bus.subscribe(CitizenAddedModEvent.class, ServerEvents::onCitizenAdded);
         subscribedBus = bus;
      } catch (Exception | LinkageError e) {
         ColonyJournal.LOGGER.error("Colony Journal: could not subscribe to the MineColonies event bus", e);
      }
   }

   private static void onServerTick(ServerTickEvent.Post event) {
      MinecraftServer server = event.getServer();
      if (server.getTickCount() % POLL_INTERVAL_TICKS != 0) {
         return;
      }

      try {
         EconomyService.pollAll(server);
      } catch (Exception e) {
         ColonyJournal.LOGGER.warn("Colony Journal: economy poll failed", e);
      }
   }

   private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }

      try {
         ServerHandlers.deliverClaims(player);
      } catch (Exception e) {
         ColonyJournal.LOGGER.warn("Colony Journal: handing out claimable items failed", e);
      }
   }

   private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
      ServerHandlers.forget(event.getEntity().getUUID());
   }

   private static void onColonyDeleted(ColonyDeletedModEvent event) {
      try {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server == null) {
            return;
         }

         IColony colony = event.getColony();
         ColonyRef ref = EconomyService.refOf(colony);
         int moved = EconomyService.data(server).dissolveColony(ref);
         ServerHandlers.deliverClaims(server);
         ColonyJournal.LOGGER.info("Colony Journal: colony {} deleted, {} offers moved to the claim lists", ref, moved);
      } catch (Exception e) {
         ColonyJournal.LOGGER.warn("Colony Journal: colony deletion handling failed", e);
      }
   }

   private static void onCitizenAdded(CitizenAddedModEvent event) {
      try {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server == null || !(event.getCitizen() instanceof com.minecolonies.api.colony.ICitizenData data)) {
            return;
         }

         EconomyService.applyNewCitizen(server, event.getColony(), data);
      } catch (Exception e) {
         ColonyJournal.LOGGER.warn("Colony Journal: tax modifier for a new citizen failed", e);
      }
   }

   private ServerEvents() {
   }
}
