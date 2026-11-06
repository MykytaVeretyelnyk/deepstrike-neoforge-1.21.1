package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.network.IronDomeNetwork;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.AnMpq65Entity;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;

public final class CommandDefense {
   private static final Map<UUID, UUID> selected = new WeakHashMap<>();

   private static ListTag units(CompoundTag n) {
      if (!n.contains("Defense", 9)) {
         n.put("Defense", new ListTag());
      }

      return n.getList("Defense", 10);
   }

   private static CompoundTag find(CompoundTag n, UUID id) {
      for (Tag raw : units(n)) {
         CompoundTag r = (CompoundTag)raw;
         if (r.getUUID("Id").equals(id)) {
            return r;
         }
      }

      return null;
   }

   private static Entity entity(ServerPlayer p, CompoundTag r) {
      return r != null && r.getString("Dimension").equals(p.level().dimension().location().toString()) ? p.serverLevel().getEntity(r.getUUID("Id")) : null;
   }

   public static void interact(EntityInteract event) {
      Player p = event.getEntity();
      Entity target = event.getTarget();
      if (p.getItemInHand(event.getHand()).is((Item)BFItems.JAGM_CAMERA_TABLET.get())
         && (target instanceof IronDomeEntity || target instanceof PatriotTruckEntity || target instanceof AnMpq65Entity)) {
         event.setCanceled(true);
         event.setCancellationResult(InteractionResult.sidedSuccess(p.level().isClientSide));
         if (p instanceof ServerPlayer player) {
            String result = bind(player, target);
            player.displayClientMessage(Component.translatable("command.deepstrike." + (result.isEmpty() ? "defense_bound" : "error." + result)), true);
         }
      }
   }

   public static String bind(ServerPlayer p, Entity target) {
      CommandCenterData data = CommandCenterData.get(p.serverLevel());
      CompoundTag n = data.session(p);
      if (n == null) {
         String e = data.login(p, p.getGameProfile().getName());
         if (!e.isEmpty()) {
            return e;
         }

         n = data.session(p);
      }

      if (data.may(n, p, 2) && !(p.distanceToSqr(target) > 64.0)) {
         CompoundTag tag = target.getPersistentData();
         if (tag.hasUUID("DeepStrikeOwner") && !tag.getUUID("DeepStrikeOwner").equals(p.getUUID()) && !p.hasPermissions(2)) {
            return "access";
         } else if (units(n).size() >= 32) {
            return "unit_limit";
         } else if (find(n, target.getUUID()) != null) {
            return "already_bound";
         } else if (!data.claimDefense(target.getUUID(), n.getUUID("Id"))) {
            return "access";
         } else {
            if (!tag.hasUUID("DeepStrikeOwner")) {
               tag.putUUID("DeepStrikeOwner", p.getUUID());
            }

            CompoundTag r = new CompoundTag();
            r.putUUID("Id", target.getUUID());
            r.putString("Dimension", p.level().dimension().location().toString());
            r.putString("Name", target instanceof IronDomeEntity ? "Iron Dome" : (target instanceof PatriotTruckEntity ? "MIM-104 Patriot" : "AN/MPQ-65"));
            r.putDouble("X", target.getX());
            r.putDouble("Y", target.getY());
            r.putDouble("Z", target.getZ());
            units(n).add(r);
            data.setDirty();
            return "";
         }
      } else {
         return "access";
      }
   }

   public static String action(ServerPlayer p, CompoundTag n, String action, CompoundTag request) {
      if (action.equals("defense_select")) {
         if (request.hasUUID("Unit") && find(n, request.getUUID("Unit")) != null) {
            selected.put(p.getUUID(), request.getUUID("Unit"));
            return "";
         } else {
            return "unit";
         }
      } else {
         UUID id = selected.get(p.getUUID());
         CompoundTag record = id == null ? null : find(n, id);
         Entity e = entity(p, record);
         if (e == null || !e.isAlive()) {
            return "offline";
         } else if (action.equals("defense_reload")) {
            if (e instanceof IronDomeEntity dome) {
               dome.startManualReload();
            } else if (e instanceof PatriotTruckEntity truck) {
               truck.startManualReload();
            }

            return "";
         } else if (action.equals("defense_toggle")) {
            if (e instanceof IronDomeEntity dome) {
               dome.setAutoDefense(!dome.isAutoDefense());
            } else if (e instanceof PatriotTruckEntity truck) {
               truck.toggleDeployment();
            }

            return "";
         } else {
            if (action.equals("defense_fire")) {
               if (!request.hasUUID("Target")) {
                  return "target";
               }

               UUID target = request.getUUID("Target");
               if (e instanceof IronDomeEntity dome) {
                  if (dome.getReadyTamir() <= 0) {
                     return "ammo";
                  }

                  if (dome.scanContacts(p.serverLevel()).stream().noneMatch(c -> c.id().equals(target) && !c.friendly())) {
                     return "target";
                  }

                  Entity missile = p.serverLevel().getEntity(target);
                  if (missile != null && !(dome.distanceToSqr(missile) > 2.025E7)) {
                     return dome.fireManual(target, p) ? "" : "defense_launch";
                  }

                  return "range";
               }

               if (e instanceof PatriotTruckEntity truck) {
                  return PatriotDefenseNetwork.fireFromCommand(p, truck, target, request.getInt("Warhead")) ? "" : "defense_launch";
               }
            }

            return "action";
         }
      }
   }

   public static CompoundTag snapshot(ServerPlayer p, CompoundTag n) {
      CompoundTag result = new CompoundTag();
      ListTag directory = new ListTag();

      for (Tag raw : units(n)) {
         CompoundTag r = (CompoundTag)raw;
         Entity e = entity(p, r);
         CompoundTag row = r.copy();
         row.putBoolean("Online", e != null && e.isAlive());
         if (e != null) {
            row.putDouble("X", e.getX());
            row.putDouble("Y", e.getY());
            row.putDouble("Z", e.getZ());
         }

         directory.add(row);
      }

      result.put("Units", directory);
      UUID id = selected.get(p.getUUID());
      CompoundTag record = id == null ? null : find(n, id);
      Entity e = entity(p, record);
      if (record == null) {
         return result;
      } else {
         CompoundTag radar = record.copy();
         radar.putBoolean("Online", e != null && e.isAlive());
         ListTag contacts = new ListTag();
         if (e instanceof IronDomeEntity dome) {
            radar.putBoolean("Iron", true);
            radar.putInt("Ready0", dome.getReadyTamir());
            radar.putBoolean("Enabled", dome.isAutoDefense());
            radar.putDouble("Range", 8000.0);
            radar.putInt("ReloadTicks", dome.getReloadTicks());
            radar.putInt("Reserve0", dome.getReserveTamir());
            radar.putInt("Reserve1", 0);
            radar.putBoolean("Aircraft", dome.aircraftTargets());
            radar.putInt("ZoneX", (int)Math.round(dome.getZoneX()));
            radar.putInt("ZoneZ", (int)Math.round(dome.getZoneZ()));
            radar.putInt("ZoneR", (int)Math.round(dome.getZoneRadius()));

            for (IronDomeNetwork.Contact c : dome.scanContacts(p.serverLevel())) {
               CompoundTag ct = contact(c.id(), c.x(), c.y(), c.z(), c.speedKmh(), c.kind(), c.friendly());
               ct.putInt("Alt", (int)c.altitude());
               ct.putInt("Status", c.status());
               boolean known = RadarDisclosure.impactKnown(c.id());
               ct.putBoolean("HasImpact", known);
               if (known) {
                  ct.putInt("ImpactX", (int)Math.round(c.impactX()));
                  ct.putInt("ImpactZ", (int)Math.round(c.impactZ()));
               }

               contacts.add(ct);
               if (contacts.size() >= 128) {
                  break;
               }
            }
         } else if (e instanceof PatriotTruckEntity truck) {
            truck.refreshDefenseInventory();
            radar.putInt("Ready0", truck.readyPac2());
            radar.putInt("Ready1", truck.readyPac3());
            radar.putBoolean("Enabled", truck.isDeployed());
            radar.putBoolean("RadarConnected", truck.radarConnected());
            radar.putDouble("Range", truck.radarRange());
            radar.putInt("ReloadTicks", truck.getReloadTicks());
            radar.putInt("Reserve0", 0);
            radar.putInt("Reserve1", 0);
            radar.putBoolean("Aircraft", false);
            radar.putInt("ZoneX", 0);
            radar.putInt("ZoneZ", 0);
            radar.putInt("ZoneR", 0);

            for (PatriotTruckEntity.RadarContact cx : truck.scan(p.serverLevel())) {
               contacts.add(patriotContact(p, truck, cx));
               if (contacts.size() >= 128) {
                  break;
               }
            }
         }

         if (e instanceof AnMpq65Entity) {
            Set<UUID> seen = new HashSet<>();

            for (Tag raw : units(n)) {
               if (entity(p, (CompoundTag)raw) instanceof PatriotTruckEntity truck) {
                  boolean bound = false;

                  for (int slot = 0; slot < truck.getContainerSize(); slot++) {
                     ItemStack stack = truck.getItem(slot);
                     CompoundTag tag = NbtCompat.getTag(stack);
                     if (stack.is((Item)BFItems.AIR_DEFENSE_TERMINAL.get())
                        && tag != null
                        && tag.hasUUID("TargetUUID")
                        && tag.getUUID("TargetUUID").equals(e.getUUID())) {
                        bound = true;
                     }
                  }

                  if (bound) {
                     for (PatriotTruckEntity.RadarContact cxx : truck.scan(p.serverLevel())) {
                        if (seen.add(cxx.id()) && contacts.size() < 128) {
                           contacts.add(patriotContact(p, truck, cxx));
                        }
                     }
                  }
               }
            }
         }

         radar.putBoolean("Controllable", e instanceof IronDomeEntity || e instanceof PatriotTruckEntity);
         if (e instanceof VehicleEntity vehicle) {
            radar.putString("Skin", vehicle.getSkinId());
         }

         if (e != null) {
            radar.putDouble("X", e.getX());
            radar.putDouble("Y", e.getY());
            radar.putDouble("Z", e.getZ());
         }

         radar.put("Contacts", contacts);
         result.put("Radar", radar);
         return result;
      }
   }

   private static CompoundTag patriotContact(ServerPlayer p, PatriotTruckEntity truck, PatriotTruckEntity.RadarContact c) {
      CompoundTag ct = contact(c.id(), c.x(), c.y(), c.z(), c.speedKmh(), RadarDisclosure.model(c.id(), c.kind()), c.friendly());
      ct.putInt("Alt", (int)c.altitude());
      ct.putInt("Status", 0);
      Entity entity = p.serverLevel().getEntity(c.id());
      boolean known = entity != null && RadarDisclosure.impactKnown(c.id());
      ct.putBoolean("HasImpact", known);
      if (known) {
         Vec3 impact = entity instanceof GuidedMissileEntity missile
            ? IronDomeEntity.predictImpactPoint(missile)
            : entity.position().add(entity.getDeltaMovement().scale(50.0));
         ct.putInt("ImpactX", (int)Math.round(impact.x));
         ct.putInt("ImpactZ", (int)Math.round(impact.z));
      }

      return ct;
   }

   private static CompoundTag contact(UUID id, double x, double y, double z, double speed, int kind, boolean friendly) {
      CompoundTag c = new CompoundTag();
      c.putUUID("Id", id);
      c.putDouble("X", x);
      c.putDouble("Y", y);
      c.putDouble("Z", z);
      c.putDouble("Speed", speed);
      c.putInt("Kind", kind);
      c.putBoolean("Friendly", friendly);
      return c;
   }
}
