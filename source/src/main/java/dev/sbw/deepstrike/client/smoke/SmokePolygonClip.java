package dev.sbw.deepstrike.client.smoke;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SmokePolygonClip {
   private static final double EPS = 1.0E-7;
   private static int reviewMaximum;

   public static List<List<Vec3>> freeAir(List<Vec3> cloud, Vec3 source, List<AABB> solids) {
      return freeAir(cloud, source, solids, false, true);
   }

   public static List<List<Vec3>> freeAir(List<Vec3> cloud, Vec3 source, List<AABB> solids, boolean solidsMerged, boolean joinFragments) {
      List<List<Vec3>> pieces = new ArrayList<>();
      pieces.add(cloud);
      AABB cloudBounds = bounds(cloud);
      List<AABB> relevant = new ArrayList<>();

      for (AABB solid : solids) {
         if (solid.inflate(0.00390625).intersects(cloudBounds) && intersects(cloud, solid.inflate(0.00390625))) {
            relevant.add(solid);
         }
      }

      if (relevant.isEmpty()) {
         return pieces;
      } else {
         if (!solidsMerged) {
            relevant = SmokeFlow.mergeFullBoxes(relevant);
         }

         if (relevant.size() == 1) {
            AABB box = relevant.get(0).inflate(0.00390625);

            for (int plane = 0; plane < 6; plane++) {
               boolean covers = true;

               for (int other = 0; other < 6; other++) {
                  if (other != plane && outsideCount(cloud, box, other) != 0) {
                     covers = false;
                     break;
                  }
               }

               if (covers) {
                  int axis = plane / 2;
                  int side = plane % 2;
                  List<Vec3> outside = half(cloud, axis, side == 0 ? min(box, axis) : max(box, axis), side == 0 ? -1 : 1);
                  return area(outside) > 1.0E-7 && contains(outside, source) ? List.of(outside) : List.of();
               }
            }
         }

         for (AABB solidx : relevant) {
            AABB box = solidx.inflate(0.00390625);
            List<List<Vec3>> next = new ArrayList<>();

            for (List<Vec3> polygon : pieces) {
               if (!box.intersects(bounds(polygon))) {
                  next.add(polygon);
               } else if (!intersects(polygon, box)) {
                  next.add(polygon);
               } else {
                  List<Vec3> remaining = polygon;
                  int[] planes = new int[]{0, 1, 2, 3, 4, 5};
                  int[] counts = new int[6];

                  for (int plane = 0; plane < 6; plane++) {
                     counts[plane] = outsideCount(polygon, box, plane);
                  }

                  for (int n = 1; n < 6; n++) {
                     int plane = planes[n];

                     int j;
                     for (j = n; j > 0 && counts[planes[j - 1]] < counts[plane]; j--) {
                        planes[j] = planes[j - 1];
                     }

                     planes[j] = plane;
                  }

                  for (int plane : planes) {
                     if (remaining.isEmpty()) {
                        break;
                     }

                     int axis = plane / 2;
                     int side = plane % 2;
                     double boundary = side == 0 ? min(box, axis) : max(box, axis);
                     int sign = side == 0 ? -1 : 1;
                     List<Vec3> outside = half(remaining, axis, boundary, sign);
                     if (area(outside) > 1.0E-7) {
                        next.add(outside);
                     }

                     remaining = half(remaining, axis, boundary, -sign);
                     if (area(remaining) <= 1.0E-7) {
                        remaining = List.of();
                     }
                  }
               }
            }

            pieces = next;
            if (next.isEmpty()) {
               return next;
            }
         }

         if (pieces.size() > 1 && joinFragments) {
            pieces = joinSharedEdges(pieces);
         }

         if (Boolean.getBoolean("deepstrike.fp5Review") && pieces.size() > reviewMaximum) {
            reviewMaximum = pieces.size();
            if (reviewMaximum > 20) {
               LogUtils.getLogger().info("SMOKE_LOCAL_FRAGMENTS pieces={} obstacles={}", reviewMaximum, relevant.size());
            }
         }

         if (pieces.isEmpty()) {
            return pieces;
         } else if (pieces.size() == 1) {
            return contains(pieces.get(0), source) ? pieces : List.of();
         } else {
            int[] parent = new int[pieces.size()];
            int i = 0;

            while (i < parent.length) {
               parent[i] = i++;
            }

            Map<SmokePolygonClip.LineKey, List<SmokePolygonClip.Edge>> lines = new HashMap<>();

            for (int ix = 0; ix < pieces.size(); ix++) {
               List<Vec3> polygonx = pieces.get(ix);

               for (int j = 0; j < polygonx.size(); j++) {
                  Vec3 a = polygonx.get(j);
                  Vec3 b = polygonx.get((j + 1) % polygonx.size());
                  Vec3 delta = b.subtract(a);
                  if (!(delta.lengthSqr() < 9.999999999999998E-15)) {
                     Vec3 direction = delta.normalize();
                     if (direction.x < -1.0E-7
                        || Math.abs(direction.x) < 1.0E-7
                           && (direction.y < -1.0E-7 || Math.abs(direction.y) < 1.0E-7 && direction.z < 0.0)) {
                        direction = direction.scale(-1.0);
                     }

                     Vec3 anchor = a.subtract(direction.scale(a.dot(direction)));
                     SmokePolygonClip.LineKey key = new SmokePolygonClip.LineKey(
                        quant(direction.x),
                        quant(direction.y),
                        quant(direction.z),
                        quant(anchor.x),
                        quant(anchor.y),
                        quant(anchor.z)
                     );
                     double u = a.dot(direction);
                     double v = b.dot(direction);
                     lines.computeIfAbsent(key, k -> new ArrayList<>()).add(new SmokePolygonClip.Edge(ix, Math.min(u, v), Math.max(u, v)));
                  }
               }
            }

            for (List<SmokePolygonClip.Edge> edges : lines.values()) {
               edges.sort(Comparator.comparingDouble(SmokePolygonClip.Edge::start));
               ArrayList<SmokePolygonClip.Edge> active = new ArrayList<>();

               for (SmokePolygonClip.Edge edge : edges) {
                  active.removeIf(e -> e.end <= edge.start + 1.0E-7);

                  for (SmokePolygonClip.Edge otherx : active) {
                     if (Math.min(otherx.end, edge.end) - edge.start > 1.0E-7) {
                        parent[root(parent, edge.piece)] = root(parent, otherx.piece);
                     }
                  }

                  active.add(edge);
               }
            }

            Set<Integer> roots = new HashSet<>();

            for (int ix = 0; ix < pieces.size(); ix++) {
               if (contains(pieces.get(ix), source)) {
                  roots.add(root(parent, ix));
               }
            }

            List<List<Vec3>> connected = new ArrayList<>();

            for (int ixx = 0; ixx < pieces.size(); ixx++) {
               if (roots.contains(root(parent, ixx))) {
                  connected.add(pieces.get(ixx));
               }
            }

            return connected;
         }
      }
   }

   private static int outsideCount(List<Vec3> polygon, AABB box, int plane) {
      int axis = plane / 2;
      int side = plane % 2;
      int count = 0;
      double boundary = side == 0 ? min(box, axis) : max(box, axis);

      for (Vec3 p : polygon) {
         if (side == 0 ? coord(p, axis) < boundary : coord(p, axis) > boundary) {
            count++;
         }
      }

      return count;
   }

   private static List<List<Vec3>> joinSharedEdges(List<List<Vec3>> pieces) {
      ArrayList<List<Vec3>> result = new ArrayList<>(pieces);
      IdentityHashMap<List<Vec3>, List<SmokePolygonClip.SegmentKey>> keys = new IdentityHashMap<>();
      IdentityHashMap<List<Vec3>, IdentityHashMap<List<Vec3>, Set<Long>>> rejected = new IdentityHashMap<>();
      IdentityHashMap<List<Vec3>, Vec3> normals = new IdentityHashMap<>();

      boolean changed;
      do {
         changed = false;
         Map<SmokePolygonClip.SegmentKey, SmokePolygonClip.Shared> edges = new HashMap<>();

         for (int i = 0; i < result.size(); i++) {
            List<Vec3> polygon = result.get(i);
            if (polygon != null) {
               for (int j = 0; j < polygon.size(); j++) {
                  Vec3 a = polygon.get(j);
                  Vec3 b = polygon.get((j + 1) % polygon.size());
                  List<SmokePolygonClip.SegmentKey> polygonKeys = keys.get(polygon);
                  if (polygonKeys == null) {
                     polygonKeys = new ArrayList<>(polygon.size());

                     for (int n = 0; n < polygon.size(); n++) {
                        SmokePolygonClip.PointKey ka = pointKey(polygon.get(n));
                        SmokePolygonClip.PointKey kb = pointKey(polygon.get((n + 1) % polygon.size()));
                        polygonKeys.add(compare(ka, kb) < 0 ? new SmokePolygonClip.SegmentKey(ka, kb) : new SmokePolygonClip.SegmentKey(kb, ka));
                     }

                     keys.put(polygon, polygonKeys);
                  }

                  SmokePolygonClip.SegmentKey key = polygonKeys.get(j);
                  SmokePolygonClip.Shared other = edges.get(key);
                  if (other == null) {
                     edges.put(key, new SmokePolygonClip.Shared(i, j));
                  } else {
                     List<Vec3> previous = result.get(other.polygon);
                     if (previous != null) {
                        int k = other.edge;
                        long edgePair = (long)j << 32 | k & 4294967295L;
                        IdentityHashMap<List<Vec3>, Set<Long>> cannotJoin = rejected.get(polygon);
                        Set<Long> rejectedEdges = cannotJoin == null ? null : cannotJoin.get(previous);
                        if ((rejectedEdges == null || !rejectedEdges.contains(edgePair))
                           && !(a.distanceToSqr(previous.get((k + 1) % previous.size())) > 1.0E-20)
                           && !(b.distanceToSqr(previous.get(k)) > 1.0E-20)) {
                           ArrayList<Vec3> joined = new ArrayList<>();

                           for (int n = 1; n <= polygon.size(); n++) {
                              joined.add(polygon.get((j + n) % polygon.size()));
                           }

                           for (int n = 2; n < previous.size(); n++) {
                              joined.add(previous.get((k + n) % previous.size()));
                           }

                           Vec3 normal = normals.computeIfAbsent(polygon, SmokePolygonClip::normal);
                           boolean convex = true;

                           for (int n = 0; n < joined.size(); n++) {
                              Vec3 u = joined.get(n);
                              Vec3 v = joined.get((n + 1) % joined.size());
                              Vec3 w = joined.get((n + 2) % joined.size());
                              if (v.subtract(u).cross(w.subtract(v)).dot(normal) < -1.0E-12) {
                                 convex = false;
                                 break;
                              }
                           }

                           if (convex) {
                              result.set(i, joined);
                              result.set(other.polygon, null);
                              changed = true;
                              break;
                           }

                           rejected.computeIfAbsent(polygon, k0 -> new IdentityHashMap<>()).computeIfAbsent(previous, k0 -> new HashSet<>()).add(edgePair);
                        }
                     }
                  }
               }
            }
         }

         result.removeIf(Objects::isNull);
      } while (changed);

      return result;
   }

   private static SmokePolygonClip.PointKey pointKey(Vec3 p) {
      return new SmokePolygonClip.PointKey(Math.round(p.x * 1.0E8), Math.round(p.y * 1.0E8), Math.round(p.z * 1.0E8));
   }

   private static int compare(SmokePolygonClip.PointKey a, SmokePolygonClip.PointKey b) {
      int c = Long.compare(a.x, b.x);
      if (c == 0) {
         c = Long.compare(a.y, b.y);
      }

      return c == 0 ? Long.compare(a.z, b.z) : c;
   }

   private static long quant(double value) {
      return Math.round(value * 100000.0);
   }

   private static int root(int[] parent, int i) {
      while (parent[i] != i) {
         parent[i] = parent[parent[i]];
         i = parent[i];
      }

      return i;
   }

   public static boolean intersects(List<Vec3> polygon, AABB box) {
      if (polygon.size() < 3) {
         return false;
      } else {
         Vec3 a = polygon.get(0);
         Vec3 b = polygon.get(1);
         Vec3 c = polygon.get(2);
         double ux = b.x - a.x;
         double uy = b.y - a.y;
         double uz = b.z - a.z;
         double vx = c.x - a.x;
         double vy = c.y - a.y;
         double vz = c.z - a.z;
         double nx = uy * vz - uz * vy;
         double ny = uz * vx - ux * vz;
         double nz = ux * vy - uy * vx;
         double dot = nx * ((box.minX + box.maxX) * 0.5 - a.x)
            + ny * ((box.minY + box.maxY) * 0.5 - a.y)
            + nz * ((box.minZ + box.maxZ) * 0.5 - a.z);
         double extent = (
               Math.abs(nx) * (box.maxX - box.minX) + Math.abs(ny) * (box.maxY - box.minY) + Math.abs(nz) * (box.maxZ - box.minZ)
            )
            * 0.5;
         if (Math.abs(dot) > extent + 1.0E-8 * (1.0 + Math.abs(dot) + extent)) {
            return false;
         } else {
            List<Vec3> overlap = polygon;

            for (int axis = 0; axis < 3 && !overlap.isEmpty(); axis++) {
               overlap = half(overlap, axis, min(box, axis), 1);
               overlap = half(overlap, axis, max(box, axis), -1);
            }

            return area(overlap) > 1.0E-7;
         }
      }
   }

   private static List<Vec3> half(List<Vec3> polygon, int axis, double boundary, int sign) {
      if (polygon.isEmpty()) {
         return List.of();
      } else {
         boolean inside = false;
         boolean outside = false;

         for (Vec3 point : polygon) {
            if ((coord(point, axis) - boundary) * sign >= 0.0) {
               inside = true;
            } else {
               outside = true;
            }

            if (inside && outside) {
               break;
            }
         }

         if (!outside) {
            return polygon;
         } else if (!inside) {
            return List.of();
         } else {
            List<Vec3> out = new ArrayList<>(polygon.size() + 1);
            Vec3 before = polygon.get(polygon.size() - 1);
            double a = (coord(before, axis) - boundary) * sign;

            for (Vec3 after : polygon) {
               double b = (coord(after, axis) - boundary) * sign;
               if (a >= 0.0 != b >= 0.0) {
                  out.add(before.lerp(after, a / (a - b)));
               }

               if (b >= 0.0) {
                  out.add(after);
               }

               before = after;
               a = b;
            }

            return out;
         }
      }
   }

   private static boolean contains(List<Vec3> polygon, Vec3 point) {
      if (polygon.size() < 3) {
         return false;
      } else {
         Vec3 normal = normal(polygon);
         if (normal.lengthSqr() < 9.999999999999998E-15) {
            return false;
         } else {
            double sign = 0.0;

            for (int i = 0; i < polygon.size(); i++) {
               Vec3 a = polygon.get(i);
               Vec3 b = polygon.get((i + 1) % polygon.size());
               double side = b.subtract(a).cross(point.subtract(a)).dot(normal);
               if (Math.abs(side) > 1.0E-7) {
                  if (sign != 0.0 && Math.signum(side) != sign) {
                     return false;
                  }

                  sign = Math.signum(side);
               }
            }

            return true;
         }
      }
   }

   private static boolean touch(List<Vec3> a, List<Vec3> b) {
      AABB x = bounds(a);
      AABB y = bounds(b);
      if (!(x.maxX + 1.0E-7 < y.minX)
         && !(y.maxX + 1.0E-7 < x.minX)
         && !(x.maxY + 1.0E-7 < y.minY)
         && !(y.maxY + 1.0E-7 < x.minY)
         && !(x.maxZ + 1.0E-7 < y.minZ)
         && !(y.maxZ + 1.0E-7 < x.minZ)) {
         for (int i = 0; i < a.size(); i++) {
            Vec3 p = a.get(i);
            Vec3 u = a.get((i + 1) % a.size()).subtract(p);
            double length = u.length();
            if (!(length < 1.0E-7)) {
               Vec3 direction = u.scale(1.0 / length);

               for (int j = 0; j < b.size(); j++) {
                  Vec3 q = b.get(j);
                  Vec3 r = b.get((j + 1) % b.size());
                  if (!(q.subtract(p).cross(direction).lengthSqr() > 9.999999999999998E-15)
                     && !(r.subtract(p).cross(direction).lengthSqr() > 9.999999999999998E-15)) {
                     double s = q.subtract(p).dot(direction);
                     double t = r.subtract(p).dot(direction);
                     if (Math.min(length, Math.max(s, t)) - Math.max(0.0, Math.min(s, t)) > 1.0E-7) {
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static Vec3 normal(List<Vec3> p) {
      Vec3 n = Vec3.ZERO;
      Vec3 first = p.get(0);

      for (int i = 1; i < p.size() - 1; i++) {
         n = n.add(p.get(i).subtract(first).cross(p.get(i + 1).subtract(first)));
      }

      return n;
   }

   private static double area(List<Vec3> p) {
      if (p.size() < 3) {
         return 0.0;
      } else {
         Vec3 first = p.get(0);
         double x = 0.0;
         double y = 0.0;
         double z = 0.0;

         for (int i = 1; i < p.size() - 1; i++) {
            Vec3 a = p.get(i);
            Vec3 b = p.get(i + 1);
            double ax = a.x - first.x;
            double ay = a.y - first.y;
            double az = a.z - first.z;
            double bx = b.x - first.x;
            double by = b.y - first.y;
            double bz = b.z - first.z;
            x += ay * bz - az * by;
            y += az * bx - ax * bz;
            z += ax * by - ay * bx;
         }

         return Math.sqrt(x * x + y * y + z * z) * 0.5;
      }
   }

   public static AABB bounds(List<Vec3> p) {
      Vec3 first = p.get(0);
      double x0 = first.x;
      double y0 = first.y;
      double z0 = first.z;
      double x1 = x0;
      double y1 = y0;
      double z1 = z0;

      for (Vec3 v : p) {
         x0 = Math.min(x0, v.x);
         y0 = Math.min(y0, v.y);
         z0 = Math.min(z0, v.z);
         x1 = Math.max(x1, v.x);
         y1 = Math.max(y1, v.y);
         z1 = Math.max(z1, v.z);
      }

      return new AABB(x0, y0, z0, x1, y1, z1);
   }

   private static double coord(Vec3 p, int a) {
      return a == 0 ? p.x : (a == 1 ? p.y : p.z);
   }

   private static double min(AABB p, int a) {
      return a == 0 ? p.minX : (a == 1 ? p.minY : p.minZ);
   }

   private static double max(AABB p, int a) {
      return a == 0 ? p.maxX : (a == 1 ? p.maxY : p.maxZ);
   }

   private SmokePolygonClip() {
   }

   private record Edge(int piece, double start, double end) {
   }

   private record LineKey(long dx, long dy, long dz, long x, long y, long z) {
   }

   private record PointKey(long x, long y, long z) {
   }

   private record SegmentKey(SmokePolygonClip.PointKey a, SmokePolygonClip.PointKey b) {
   }

   private record Shared(int polygon, int edge) {
   }
}
