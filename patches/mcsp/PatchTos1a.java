import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.*;

// Adapts MCSP 1.0.10 Tos1aEntity (built for a newer Superb Warfare) to SBW 0.8.9.2:
//  - adds the BARREL_ANIM synched-data accessor that newer ArtilleryEntity provides
//  - routes DamageModifier.custom(Function2) to the 0.8.9.2 CustomDamageModifier overload
public class PatchTos1a implements Opcodes {
   static final String OWNER = "tech/mcsp/mcsp/entity/Tos1aEntity";
   static final String ADAPTER = OWNER + "$SbwDamageAdapter";
   static final String ACC = "Lnet/minecraft/network/syncher/EntityDataAccessor;";
   static final String DM = "com/atsuishio/superbwarfare/entity/vehicle/damage/DamageModifier";
   static final String CUSTOM_IF = DM + "$CustomDamageModifier";
   static final String F2 = "kotlin/jvm/functions/Function2";

   public static void main(String[] a) throws IOException {
      try (ZipFile zf = new ZipFile(a[0]); ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(Path.of(a[1])))) {
         for (ZipEntry e : Collections.list(zf.entries())) {
            byte[] data = zf.getInputStream(e).readAllBytes();
            if (e.getName().equals(OWNER + ".class")) {
               data = patch(data);
            }

            ZipEntry ne = new ZipEntry(e.getName());
            ne.setTime(e.getTime());
            zo.putNextEntry(ne);
            zo.write(data);
            zo.closeEntry();
         }

         zo.putNextEntry(new ZipEntry(ADAPTER + ".class"));
         zo.write(adapter());
         zo.closeEntry();
      }
   }

   static byte[] patch(byte[] in) {
      ClassReader cr = new ClassReader(in);
      ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
      cr.accept(new ClassVisitor(ASM9, cw) {
         public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
            if (name.equals("<clinit>")) {
               return new MethodVisitor(ASM9, mv) {
                  public void visitCode() {
                     super.visitCode();
                     visitLdcInsn(Type.getObjectType(OWNER));
                     visitFieldInsn(GETSTATIC, "com/atsuishio/superbwarfare/init/ModSerializers", "INT_LIST_SERIALIZER", "Lnet/neoforged/neoforge/registries/DeferredHolder;");
                     visitMethodInsn(INVOKEVIRTUAL, "net/neoforged/neoforge/registries/DeferredHolder", "get", "()Ljava/lang/Object;", false);
                     visitTypeInsn(CHECKCAST, "net/minecraft/network/syncher/EntityDataSerializer");
                     visitMethodInsn(INVOKESTATIC, "net/minecraft/network/syncher/SynchedEntityData", "defineId", "(Ljava/lang/Class;Lnet/minecraft/network/syncher/EntityDataSerializer;)" + ACC, false);
                     visitFieldInsn(PUTSTATIC, OWNER, "BARREL_ANIM", ACC);
                  }
               };
            }

            if (name.equals("defineSynchedData") && desc.equals("(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V")) {
               return new MethodVisitor(ASM9, mv) {
                  public void visitInsn(int op) {
                     if (op == RETURN) {
                        visitVarInsn(ALOAD, 1);
                        visitFieldInsn(GETSTATIC, OWNER, "BARREL_ANIM", ACC);
                        visitMethodInsn(INVOKESTATIC, OWNER, "$sbwBarrelDefault", "()Ljava/util/List;", false);
                        visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/network/syncher/SynchedEntityData$Builder", "define", "(" + ACC + "Ljava/lang/Object;)Lnet/minecraft/network/syncher/SynchedEntityData$Builder;", false);
                        visitInsn(POP);
                     }

                     super.visitInsn(op);
                  }
               };
            }

            if (name.equals("getDamageModifier")) {
               return new MethodVisitor(ASM9, mv) {
                  public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                     if (op == INVOKEVIRTUAL && owner.equals(DM) && n.equals("custom") && d.equals("(L" + F2 + ";)L" + DM + ";")) {
                        super.visitMethodInsn(INVOKESTATIC, OWNER, "$sbwCustom", "(L" + DM + ";L" + F2 + ";)L" + DM + ";", false);
                     } else {
                        super.visitMethodInsn(op, owner, n, d, itf);
                     }
                  }
               };
            }

            return mv;
         }

         public void visitEnd() {
            visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "BARREL_ANIM", ACC, "Lnet/minecraft/network/syncher/EntityDataAccessor<Ljava/util/List<Ljava/lang/Integer;>;>;", null).visitEnd();

            MethodVisitor m = visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "$sbwBarrelDefault", "()Ljava/util/List;", null, null);
            m.visitCode();
            m.visitTypeInsn(NEW, "java/util/ArrayList");
            m.visitInsn(DUP);
            m.visitIntInsn(BIPUSH, 21);
            m.visitInsn(ICONST_0);
            m.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            m.visitMethodInsn(INVOKESTATIC, "java/util/Collections", "nCopies", "(ILjava/lang/Object;)Ljava/util/List;", false);
            m.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
            m.visitInsn(ARETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();

            MethodVisitor c = visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "$sbwCustom", "(L" + DM + ";L" + F2 + ";)L" + DM + ";", null, null);
            c.visitCode();
            c.visitVarInsn(ALOAD, 0);
            c.visitTypeInsn(NEW, ADAPTER);
            c.visitInsn(DUP);
            c.visitVarInsn(ALOAD, 1);
            c.visitMethodInsn(INVOKESPECIAL, ADAPTER, "<init>", "(L" + F2 + ";)V", false);
            c.visitMethodInsn(INVOKEVIRTUAL, DM, "custom", "(L" + CUSTOM_IF + ";)L" + DM + ";", false);
            c.visitInsn(ARETURN);
            c.visitMaxs(0, 0);
            c.visitEnd();
            super.visitEnd();
         }
      }, 0);
      return cw.toByteArray();
   }

   static byte[] adapter() {
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      cw.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, ADAPTER, null, "java/lang/Object", new String[]{CUSTOM_IF});
      cw.visitField(ACC_PRIVATE | ACC_FINAL, "f", "L" + F2 + ";", null, null).visitEnd();

      MethodVisitor m = cw.visitMethod(ACC_PUBLIC, "<init>", "(L" + F2 + ";)V", null, null);
      m.visitCode();
      m.visitVarInsn(ALOAD, 0);
      m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      m.visitVarInsn(ALOAD, 0);
      m.visitVarInsn(ALOAD, 1);
      m.visitFieldInsn(PUTFIELD, ADAPTER, "f", "L" + F2 + ";");
      m.visitInsn(RETURN);
      m.visitMaxs(0, 0);
      m.visitEnd();

      MethodVisitor c = cw.visitMethod(ACC_PUBLIC, "compute", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;F)F", null, null);
      c.visitCode();
      c.visitVarInsn(ALOAD, 0);
      c.visitFieldInsn(GETFIELD, ADAPTER, "f", "L" + F2 + ";");
      c.visitVarInsn(ALOAD, 2);
      c.visitVarInsn(FLOAD, 3);
      c.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
      c.visitMethodInsn(INVOKEINTERFACE, F2, "invoke", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
      c.visitTypeInsn(CHECKCAST, "java/lang/Float");
      c.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
      c.visitInsn(FRETURN);
      c.visitMaxs(0, 0);
      c.visitEnd();
      cw.visitEnd();
      return cw.toByteArray();
   }
}
