import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.*;

// Adapts AshVehicle 1.21.1-4.6-8.9 (built for a newer Superb Warfare) to SBW 0.8.9.2:
//  - MouseMovementHandler.getMousePos() is static here (was called on INSTANCE)
//  - ProjectileTool.causeCustomExplode takes a Projectile and has no trailing damage-multiplier float
//  - CustomExplosion.Builder.damageMultiplier does not exist
//  - RenderHelper.isInGui does not exist
public class PatchAsh implements Opcodes {
   static final String MMH = "com/atsuishio/superbwarfare/client/MouseMovementHandler";
   static final String PT = "com/atsuishio/superbwarfare/tools/ProjectileTool";
   static final String BUILDER = "com/atsuishio/superbwarfare/tools/CustomExplosion$Builder";
   static final String RH = "com/atsuishio/superbwarfare/client/RenderHelper";
   static final String TIP = "Lnet/minecraft/world/entity/projectile/ThrowableItemProjectile;";
   static final String PROJ = "Lnet/minecraft/world/entity/projectile/Projectile;";
   static int changed;

   public static void main(String[] a) throws IOException {
      try (ZipFile zf = new ZipFile(a[0]); ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(Path.of(a[1])))) {
         for (ZipEntry e : Collections.list(zf.entries())) {
            byte[] data = zf.getInputStream(e).readAllBytes();
            if (e.getName().endsWith(".class")) {
               data = patch(e.getName(), data);
            }

            ZipEntry ne = new ZipEntry(e.getName());
            ne.setTime(e.getTime());
            zo.putNextEntry(ne);
            zo.write(data);
            zo.closeEntry();
         }
      }

      System.out.println("rewritten call sites: " + changed);
   }

   static byte[] patch(String name, byte[] in) {
      ClassReader cr = new ClassReader(in);
      ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
      cr.accept(new ClassVisitor(ASM9, cw) {
         public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
            return new MethodVisitor(ASM9, super.visitMethod(acc, n, d, sig, ex)) {
               public void visitMethodInsn(int op, String owner, String mn, String md, boolean itf) {
                  if (op == INVOKEVIRTUAL && owner.equals(MMH) && mn.equals("getMousePos")) {
                     // the GETSTATIC INSTANCE that fed the receiver stays on the stack; drop it and call the static method
                     super.visitInsn(POP);
                     super.visitMethodInsn(INVOKESTATIC, owner, mn, md, false);
                     changed++;
                  } else if (op == INVOKESTATIC && owner.equals(PT) && mn.equals("causeCustomExplode") && md.startsWith("(" + TIP)) {
                     String rest = md.substring(TIP.length() + 1);
                     if (rest.equals("FFF)V")) {
                        super.visitInsn(POP);
                        rest = "FF)V";
                     }

                     super.visitMethodInsn(op, owner, mn, "(" + PROJ + rest, itf);
                     changed++;
                  } else if (op == INVOKEVIRTUAL && mn.equals("gearRot") && md.equals("(F)F")) {
                     // newer SBW interpolates the landing gear by partial tick; 0.8.9.2 only has the plain getter
                     super.visitInsn(POP);
                     super.visitMethodInsn(INVOKEVIRTUAL, owner, "getGearRot", "()F", itf);
                     changed++;
                  } else if (op == INVOKEVIRTUAL && owner.equals(BUILDER) && mn.equals("damageMultiplier")) {
                     super.visitInsn(POP);
                     changed++;
                  } else if (op == INVOKESTATIC && owner.equals(RH) && mn.equals("isInGui")) {
                     super.visitInsn(ICONST_0);
                     changed++;
                  } else {
                     super.visitMethodInsn(op, owner, mn, md, itf);
                  }
               }
            };
         }
      }, 0);
      return cw.toByteArray();
   }
}
