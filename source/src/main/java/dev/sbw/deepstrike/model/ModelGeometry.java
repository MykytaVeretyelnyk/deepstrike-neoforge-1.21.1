package dev.sbw.deepstrike.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class ModelGeometry {
   private static final Map<String, ModelGeometry> CACHE = new HashMap<>();
   public final List<ModelGeometry.Bone> bones = new ArrayList<>();
   public final float textureWidth;
   public final float textureHeight;
   public float noseX;
   public float tailX;
   private final Map<String, Matrix4f> defaultTransforms;

   public static synchronized ModelGeometry get(String name) {
      return resource("/assets/deepstrike/geo/" + name + ".geo.json");
   }

   public static synchronized ModelGeometry resource(String path) {
      return CACHE.computeIfAbsent(path, ModelGeometry::new);
   }

   private ModelGeometry(String path) {
      try {
         try (InputStream in = openResource(path)) {
            if (in == null) {
               throw new IOException("Missing geometry " + path);
            }

            JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
               .getAsJsonObject()
               .getAsJsonArray("minecraft:geometry")
               .get(0)
               .getAsJsonObject();
            JsonObject desc = geo.getAsJsonObject("description");
            this.textureWidth = desc.get("texture_width").getAsFloat();
            this.textureHeight = desc.get("texture_height").getAsFloat();

            for (JsonElement element : geo.getAsJsonArray("bones")) {
               JsonObject b = element.getAsJsonObject();
               Vector3f pivot = position(b, "pivot");
               pivot.x = -pivot.x;
               Vector3f r = rotation(b);
               List<ModelGeometry.Cube> cubes = new ArrayList<>();
               if (b.has("cubes")) {
                  for (JsonElement c0 : b.getAsJsonArray("cubes")) {
                     JsonObject c = c0.getAsJsonObject();
                     Vector3f o = position(c, "origin");
                     Vector3f s = position(c, "size");
                     o.x = -o.x - s.x;
                     Vector3f cp = position(c, "pivot");
                     cp.x = -cp.x;
                     cubes.add(new ModelGeometry.Cube(o, s, around(cp, rotation(c)), c.getAsJsonObject("uv")));
                  }
               }

               List<Vector3f> meshVertices = new ArrayList<>();
               if (b.has("poly_mesh")) {
                  for (JsonElement vertex : b.getAsJsonObject("poly_mesh").getAsJsonArray("positions")) {
                     JsonArray v = vertex.getAsJsonArray();
                     meshVertices.add(new Vector3f(-v.get(0).getAsFloat() / 16.0F, v.get(1).getAsFloat() / 16.0F, v.get(2).getAsFloat() / 16.0F));
                  }
               }

               this.bones
                  .add(
                     new ModelGeometry.Bone(b.get("name").getAsString(), b.has("parent") ? b.get("parent").getAsString() : null, pivot, r, cubes, meshVertices)
                  );
            }

            this.defaultTransforms = Collections.unmodifiableMap(this.computeTransforms(0.0F, 0.0F));
            this.noseX = Float.POSITIVE_INFINITY;
            this.tailX = Float.NEGATIVE_INFINITY;

            for (ModelGeometry.Bone bone : this.bones) {
               for (ModelGeometry.Cube cube : bone.cubes()) {
                  Matrix4f transform = new Matrix4f((Matrix4fc)this.defaultTransforms.get(bone.name())).mul(cube.transform());

                  for (int i = 0; i < 8; i++) {
                     Vector3f vertex = new Vector3f(cube.min())
                        .add((i & 1) == 0 ? 0.0F : cube.size().x, (i & 2) == 0 ? 0.0F : cube.size().y, (i & 4) == 0 ? 0.0F : cube.size().z);
                     transform.transformPosition(vertex);
                     this.noseX = Math.min(this.noseX, vertex.x);
                     this.tailX = Math.max(this.tailX, vertex.x);
                  }
               }
            }
         }
      } catch (IOException var19) {
         throw new IllegalStateException(var19);
      }
   }

   private static InputStream openResource(String path) throws IOException {
      InputStream own = ModelGeometry.class.getResourceAsStream(path);
      if (own != null) {
         return own;
      } else {
         String[] parts = path.replaceFirst("^/", "").split("/");
         if (parts.length > 2 && parts[0].equals("assets")) {
            IModFileInfo owner = ModList.get().getModFileById(parts[1]);
            if (owner != null) {
               Path resource = owner.getFile().findResource(parts);
               if (Files.isRegularFile(resource)) {
                  return Files.newInputStream(resource);
               }
            }
         }

         return null;
      }
   }

   private static Vector3f position(JsonObject o, String key) {
      if (!o.has(key)) {
         return new Vector3f();
      } else {
         JsonArray a = o.getAsJsonArray(key);
         return new Vector3f(a.get(0).getAsFloat() / 16.0F, a.get(1).getAsFloat() / 16.0F, a.get(2).getAsFloat() / 16.0F);
      }
   }

   private static Vector3f rotation(JsonObject o) {
      if (!o.has("rotation")) {
         return new Vector3f();
      } else {
         JsonArray a = o.getAsJsonArray("rotation");
         float d = (float) (Math.PI / 180.0);
         return new Vector3f(-a.get(0).getAsFloat() * d, -a.get(1).getAsFloat() * d, a.get(2).getAsFloat() * d);
      }
   }

   public static Matrix4f around(Vector3f p, float rx, float ry, float rz) {
      return new Matrix4f().translation(p).rotateZYX(rz, ry, rx).translate(-p.x, -p.y, -p.z);
   }

   public static Matrix4f around(Vector3f p, Vector3f r) {
      return around(p, r.x, r.y, r.z);
   }

   public Map<String, Matrix4f> defaultTransforms() {
      return this.defaultTransforms;
   }

   public Map<String, Matrix4f> transforms(float yaw, float elevation) {
      return yaw == 0.0F && elevation == 0.0F ? this.defaultTransforms : this.computeTransforms(yaw, elevation);
   }

   private Map<String, Matrix4f> computeTransforms(float yaw, float elevation) {
      Map<String, Matrix4f> result = new LinkedHashMap<>(this.bones.size());
      float yawRad = (float)Math.toRadians(yaw);
      float elevRad = (float)Math.toRadians(elevation);

      for (ModelGeometry.Bone b : this.bones) {
         float rx = b.rotation.x;
         float ry = b.rotation.y;
         float rz = b.rotation.z;
         if (b.name.equals("turret_yaw")) {
            ry -= yawRad;
         }

         if (b.name.equals("cradle_pitch")) {
            rz -= elevRad;
         }

         Matrix4f parent = b.parent == null ? null : result.get(b.parent);
         Matrix4f m = parent == null ? new Matrix4f() : new Matrix4f(parent);
         m.mul(around(b.pivot, rx, ry, rz));
         result.put(b.name, m);
      }

      return result;
   }

   public static Matrix4f flight(float yaw, float pitch) {
      return new Matrix4f().rotation(FlightOrientation.rotation(yaw, pitch));
   }

   public record Bone(String name, String parent, Vector3f pivot, Vector3f rotation, List<ModelGeometry.Cube> cubes, List<Vector3f> meshVertices) {
   }

   public record Cube(Vector3f min, Vector3f size, Matrix4f transform, JsonObject faces) {
   }
}
