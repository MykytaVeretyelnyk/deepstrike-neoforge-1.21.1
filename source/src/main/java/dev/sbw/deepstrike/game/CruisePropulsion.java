package dev.sbw.deepstrike.game;

import net.minecraft.nbt.CompoundTag;

public final class CruisePropulsion {
   public static CruisePropulsion.State create(MissileKind kind, double fuel, double capacity, double maxRange) {
      capacity = Math.max(1.0, capacity);
      fuel = Math.max(0.0, Math.min(capacity, fuel));
      double endurance = 60.0 + Math.max((double)kind.maxRange, maxRange) / FlightUnits.metresPerSecond(kind.minSpeed);
      return new CruisePropulsion.State(fuel, fuel, capacity, 0.0, endurance);
   }

   public static CruisePropulsion.State legacy(MissileKind kind, int fuel, int capacity, double maxRange, double seconds) {
      CruisePropulsion.State state = create(kind, capacity > 0 ? fuel : 1.0, capacity > 0 ? capacity : 1.0, maxRange);
      return state.advance(Math.max(0.0, seconds));
   }

   public static double mass(MissileKind kind, CruisePropulsion.State state) {
      double full = switch (kind) {
         case FP5 -> 2500.0;
         case STORM_SHADOW -> 1300.0;
         case JAGM -> 80.0;
         case SPIKE_NLOS -> 100.0;
         case PEACEKEEPER -> 4000.0;
      };
      return full * (0.8 + 0.2 * state.remaining() / state.capacity());
   }

   public static CompoundTag save(CruisePropulsion.State state) {
      CompoundTag tag = new CompoundTag();
      tag.putInt("Version", 1);
      tag.putDouble("Remaining", state.remaining());
      tag.putDouble("Initial", state.initial());
      tag.putDouble("Capacity", state.capacity());
      tag.putBoolean("TerminalCoast", state.terminalCoast());
      tag.putDouble("Seconds", state.seconds());
      tag.putDouble("Endurance", state.endurance());
      return tag;
   }

   public static CruisePropulsion.State load(CompoundTag tag) {
      if (tag.getInt("Version") != 1) {
         return null;
      } else {
         try {
            return new CruisePropulsion.State(
               tag.getDouble("Remaining"),
               tag.getDouble("Initial"),
               tag.getDouble("Capacity"),
               tag.getDouble("Seconds"),
               tag.getDouble("Endurance"),
               tag.getBoolean("TerminalCoast")
            );
         } catch (IllegalArgumentException var2) {
            return null;
         }
      }
   }

   private CruisePropulsion() {
   }

   public record State(double remaining, double initial, double capacity, double seconds, double endurance, boolean terminalCoast) {
      public State(double remaining, double initial, double capacity, double seconds, double endurance) {
         this(remaining, initial, capacity, seconds, endurance, false);
      }

      public State(double remaining, double initial, double capacity, double seconds, double endurance, boolean terminalCoast) {
         if (Double.isFinite(remaining)
            && Double.isFinite(initial)
            && Double.isFinite(capacity)
            && Double.isFinite(seconds)
            && Double.isFinite(endurance)
            && !(capacity <= 0.0)
            && !(initial < 0.0)
            && !(initial > capacity)
            && !(remaining < 0.0)
            && !(remaining > initial)
            && !(seconds < 0.0)
            && !(endurance <= 0.0)) {
            this.remaining = remaining;
            this.initial = initial;
            this.capacity = capacity;
            this.seconds = seconds;
            this.endurance = endurance;
            this.terminalCoast = terminalCoast;
         } else {
            throw new IllegalArgumentException("Invalid cruise propulsion state");
         }
      }

      public boolean powered() {
         return this.remaining > 1.0E-9 && !this.terminalCoast;
      }

      public CruisePropulsion.State cutOff() {
         return new CruisePropulsion.State(this.remaining, this.initial, this.capacity, this.seconds, this.endurance, true);
      }

      public CruisePropulsion.State advance(double dt) {
         if (Double.isFinite(dt) && !(dt < 0.0)) {
            double time = this.seconds + dt;
            return this.terminalCoast
               ? new CruisePropulsion.State(this.remaining, this.initial, this.capacity, time, this.endurance, true)
               : new CruisePropulsion.State(
                  Math.max(0.0, this.remaining - this.initial / this.endurance * dt), this.initial, this.capacity, time, this.endurance
               );
         } else {
            throw new IllegalArgumentException("Invalid burn interval");
         }
      }
   }
}
