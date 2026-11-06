package dev.sbw.deepstrike.client.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Pure parsing of a panel snapshot (CommandCenterData.snapshot plus "Error") into simple records.
 * No Minecraft client classes: the screens only read these records.
 */
public final class PanelData {
   private static final Set<String> ACTIVE_STATES = Set.of("PREPARING", "DUTY", "COUNTDOWN", "RUNNING", "RECOVERING");

   private PanelData() {
   }

   public record RoutePt(double x, double y, double z, double speed) {
   }

   public record Unit(UUID id, String name, String group, String family, String state, String missile, String dimension, long position, int ammo) {
      /** Family index matching the terminal rows: 0 FP5, 1 JAGM, 2 Peacekeeper, -1 unknown. */
      public int familyIndex() {
         return switch (this.family) {
            case "FP5" -> 0;
            case "JAGM" -> 1;
            case "PEACEKEEPER" -> 2;
            default -> -1;
         };
      }
   }

   public record Assignment(UUID unit, int count, int launched, String phase) {
   }

   public record Task(
      UUID id,
      String name,
      String state,
      String error,
      long startAt,
      int waveInterval,
      int launchDelay,
      String frequency,
      boolean readyOnly,
      boolean redstone,
      boolean ripple,
      List<Assignment> assignments,
      List<RoutePt> route
   ) {
      public boolean active() {
         return ACTIVE_STATES.contains(this.state);
      }

      public int totalCount() {
         int sum = 0;

         for (Assignment a : this.assignments) {
            sum += a.count();
         }

         return sum;
      }

      public int totalLaunched() {
         int sum = 0;

         for (Assignment a : this.assignments) {
            sum += a.launched();
         }

         return sum;
      }

      /** Whole seconds left of a COUNTDOWN, rounded up; -1 for any other state. */
      public long countdownSeconds(long now) {
         return "COUNTDOWN".equals(this.state) ? Math.max(0L, (this.startAt - now + 19L) / 20L) : -1L;
      }
   }

   public record Flight(UUID mission, String name, String model, double x, double y, double z, double speed, int eta, boolean camera) {
   }

   public record JournalEntry(long tick, String key, String actor, String object, String text) {
   }

   public record Member(String nickname, int role) {
   }

   public record Panel(
      boolean network,
      String player,
      String nickname,
      int role,
      long now,
      String error,
      List<Unit> units,
      List<Task> tasks,
      List<Flight> flights,
      List<JournalEntry> journal,
      List<Member> members
   ) {
      /** True when the launcher at this BlockPos.asLong() is already a unit of the command network. */
      public boolean hasUnitAt(long position) {
         for (Unit u : this.units) {
            if (u.position() == position) {
               return true;
            }
         }

         return false;
      }

      public Unit unit(UUID id) {
         for (Unit u : this.units) {
            if (u.id().equals(id)) {
               return u;
            }
         }

         return null;
      }

      public Task task(UUID id) {
         if (id != null) {
            for (Task t : this.tasks) {
               if (t.id().equals(id)) {
                  return t;
               }
            }
         }

         return null;
      }
   }

   private static List<CompoundTag> rows(CompoundTag tag, String key) {
      List<CompoundTag> out = new ArrayList<>();
      ListTag list = tag.getList(key, 10);

      for (Tag raw : list) {
         out.add((CompoundTag)raw);
      }

      return out;
   }

   public static Panel parse(CompoundTag root) {
      boolean network = root.contains("Network", 10);
      CompoundTag net = root.getCompound("Network");
      List<Unit> units = new ArrayList<>();
      List<Task> tasks = new ArrayList<>();
      List<Flight> flights = new ArrayList<>();
      List<JournalEntry> journal = new ArrayList<>();
      List<Member> members = new ArrayList<>();
      if (network) {
         for (CompoundTag u : rows(net, "Units")) {
            if (u.hasUUID("Id")) {
               units.add(
                  new Unit(
                     u.getUUID("Id"),
                     u.getString("Name"),
                     u.getString("Group"),
                     u.getString("Family"),
                     u.getString("State"),
                     u.getString("Missile"),
                     u.getString("Dimension"),
                     u.getLong("Position"),
                     u.getInt("Ammo")
                  )
               );
            }
         }

         for (CompoundTag t : rows(net, "Tasks")) {
            if (t.hasUUID("Id")) {
               tasks.add(parseTask(t));
            }
         }

         for (CompoundTag e : rows(net, "Journal")) {
            journal.add(new JournalEntry(e.getLong("Tick"), e.contains("Key") ? e.getString("Key") : "", e.getString("Actor"), e.getString("Object"), e.getString("Text")));
         }

         for (CompoundTag m : rows(net, "Members")) {
            members.add(new Member(m.getString("Nickname"), m.getInt("Role")));
         }
      }

      for (CompoundTag f : rows(root, "Flights")) {
         if (f.hasUUID("Mission")) {
            flights.add(
               new Flight(
                  f.getUUID("Mission"),
                  f.getString("Name"),
                  f.getString("Model"),
                  f.getDouble("X"),
                  f.getDouble("Y"),
                  f.getDouble("Z"),
                  f.getDouble("Speed"),
                  f.contains("Eta") ? f.getInt("Eta") : -1,
                  f.getBoolean("Camera")
               )
            );
         }
      }

      return new Panel(
         network,
         root.getString("Player"),
         net.getString("Nickname"),
         root.getInt("Role"),
         root.getLong("Now"),
         root.getString("Error"),
         units,
         tasks,
         flights,
         journal,
         members
      );
   }

   private static Task parseTask(CompoundTag t) {
      List<Assignment> assignments = new ArrayList<>();

      for (CompoundTag a : rows(t, "Assignments")) {
         if (a.hasUUID("Unit")) {
            assignments.add(new Assignment(a.getUUID("Unit"), a.getInt("Count"), a.getInt("Launched"), a.getString("Phase")));
         }
      }

      List<RoutePt> route = new ArrayList<>();

      for (CompoundTag p : rows(t, "Route")) {
         route.add(new RoutePt(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z"), p.getDouble("Speed")));
      }

      return new Task(
         t.getUUID("Id"),
         t.getString("Name"),
         t.getString("State"),
         t.getString("Error"),
         t.getLong("StartAt"),
         t.getInt("WaveInterval"),
         t.getInt("LaunchDelay"),
         t.getString("Frequency"),
         t.getBoolean("ReadyOnly"),
         t.getBoolean("Redstone"),
         t.getBoolean("Ripple"),
         assignments,
         route
      );
   }

   /** Journal line key and arguments are localised by the screen; this only decides whether a structured key exists. */
   public static boolean structured(JournalEntry e) {
      return !e.key().isEmpty();
   }
}
