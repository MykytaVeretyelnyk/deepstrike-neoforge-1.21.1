package dev.sbw.deepstrike.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Missile entries stored on a Skyfront terminal block entity as a data attachment. Immutable. */
public record TerminalMissileData(List<TerminalMissileData.Entry> entries, int nextNumber) {
   public static final int MAX_LAUNCHERS = 12;
   public static final int MAX_POINTS = 32;
   public static final TerminalMissileData EMPTY = new TerminalMissileData(List.of(), 1);

   public static final Codec<RoutePoint> POINT_CODEC = RecordCodecBuilder.create(
      i -> i.group(
            Codec.DOUBLE.fieldOf("x").forGetter(RoutePoint::x),
            Codec.DOUBLE.fieldOf("y").forGetter(RoutePoint::y),
            Codec.DOUBLE.fieldOf("z").forGetter(RoutePoint::z),
            Codec.DOUBLE.fieldOf("speed").forGetter(RoutePoint::speed)
         )
         .apply(i, RoutePoint::new)
   );
   public static final Codec<TerminalMissileData> CODEC = RecordCodecBuilder.create(
      i -> i.group(
            TerminalMissileData.Entry.CODEC.listOf().fieldOf("entries").forGetter(TerminalMissileData::entries),
            Codec.INT.optionalFieldOf("next_number", 0).forGetter(TerminalMissileData::nextNumber)
         )
         .apply(i, TerminalMissileData::new)
   );

   /** Entries without a number (data saved before numbering existed) get the next free ones in list order. */
   public TerminalMissileData(List<TerminalMissileData.Entry> entries, int nextNumber) {
      int next = Math.max(1, nextNumber);

      for (TerminalMissileData.Entry e : entries) {
         next = Math.max(next, e.number() + 1);
      }

      List<TerminalMissileData.Entry> fixed = new ArrayList<>(entries.size());

      for (TerminalMissileData.Entry e : entries) {
         fixed.add(e.number() > 0 ? e : e.withNumber(next++));
      }

      this.entries = List.copyOf(fixed);
      this.nextNumber = next;
   }

   public TerminalMissileData.Entry find(BlockPos launcher) {
      for (TerminalMissileData.Entry entry : this.entries) {
         if (entry.launcher().equals(launcher)) {
            return entry;
         }
      }

      return null;
   }

   public TerminalMissileData with(TerminalMissileData.Entry entry) {
      List<TerminalMissileData.Entry> list = new ArrayList<>(this.entries);

      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).launcher().equals(entry.launcher())) {
            // A replaced entry keeps its number unless the caller supplies one.
            list.set(i, entry.number() > 0 ? entry : entry.withNumber(list.get(i).number()));
            return new TerminalMissileData(list, this.nextNumber);
         }
      }

      list.add(entry.number() > 0 ? entry : entry.withNumber(this.nextNumber));
      return new TerminalMissileData(list, this.nextNumber);
   }

   public TerminalMissileData without(BlockPos launcher) {
      List<TerminalMissileData.Entry> list = new ArrayList<>(this.entries);
      list.removeIf(e -> e.launcher().equals(launcher));
      return new TerminalMissileData(list, this.nextNumber);
   }

   public record Entry(BlockPos launcher, List<RoutePoint> route, boolean ballistic, int number) {
      public static final Codec<TerminalMissileData.Entry> CODEC = RecordCodecBuilder.create(
         i -> i.group(
               BlockPos.CODEC.fieldOf("launcher").forGetter(TerminalMissileData.Entry::launcher),
               TerminalMissileData.POINT_CODEC.listOf().optionalFieldOf("route", List.of()).forGetter(TerminalMissileData.Entry::route),
               Codec.BOOL.optionalFieldOf("ballistic", false).forGetter(TerminalMissileData.Entry::ballistic),
               Codec.INT.optionalFieldOf("number", 0).forGetter(TerminalMissileData.Entry::number)
            )
            .apply(i, TerminalMissileData.Entry::new)
      );

      public Entry(BlockPos launcher, List<RoutePoint> route, boolean ballistic, int number) {
         this.launcher = launcher.immutable();
         this.route = List.copyOf(route);
         this.ballistic = ballistic;
         this.number = Math.max(0, number);
      }

      /** Number 0 means "not assigned yet"; {@link TerminalMissileData#with} assigns it. */
      public Entry(BlockPos launcher, List<RoutePoint> route, boolean ballistic) {
         this(launcher, route, ballistic, 0);
      }

      public TerminalMissileData.Entry withNumber(int number) {
         return new TerminalMissileData.Entry(this.launcher, this.route, this.ballistic, number);
      }
   }
}
