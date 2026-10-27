package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class CommandReviewRecorder {
   private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "dsm-command-capture");
      t.setDaemon(true);
      return t;
   });
   private static final AtomicInteger pending = new AtomicInteger();
   private static long started;
   private static long last;
   private static int index;
   private static volatile Throwable failure;
   private static boolean ended;

   static void frame(Minecraft mc) {
      if (Boolean.getBoolean("deepstrike.commandRecord")) {
         if (failure != null) {
            throw new IllegalStateException("Recording failed", failure);
         } else {
            long now = System.nanoTime();
            if (started == 0L) {
               started = now;
            }

            Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase") + "/recording");
            if (now - started >= 30000000000L) {
               if (!ended) {
                  ended = true;
                  IO.execute(() -> {
                     try {
                        Files.writeString(dir.resolve("DONE.txt"), "Framebuffer-only recording; no desktop or audio captured.\n");
                     } catch (Throwable var2) {
                        failure = var2;
                     }
                  });
               }
            } else if (now - last >= 66666667L && pending.get() < 2) {
               last = now;
               int frame = index++;
               double timestamp = (now - started) / 1.0E9;
               NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());
               pending.incrementAndGet();
               IO.execute(
                  () -> {
                     try {
                        NativeImage e = shot;

                        try {
                           Files.createDirectories(dir);
                           shot.writeToFile(dir.resolve(String.format(Locale.ROOT, "frame-%05d.png", frame)));
                           Files.writeString(
                              dir.resolve("frames.csv"),
                              String.format(Locale.ROOT, "%05d,%.9f\n", frame, timestamp),
                              StandardOpenOption.CREATE,
                              StandardOpenOption.APPEND
                           );
                        } catch (Throwable var14) {
                           if (shot != null) {
                              try {
                                 e.close();
                              } catch (Throwable var13) {
                                 var14.addSuppressed(var13);
                              }
                           }

                           throw var14;
                        }

                        if (shot != null) {
                           shot.close();
                        }
                     } catch (Throwable var15) {
                        failure = var15;
                     } finally {
                        pending.decrementAndGet();
                     }
                  }
               );
            }
         }
      }
   }
}
