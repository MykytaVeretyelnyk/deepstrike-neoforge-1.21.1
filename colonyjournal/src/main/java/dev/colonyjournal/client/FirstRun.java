package dev.colonyjournal.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Remembers that the first-steps guide was shown, in a tiny file in the config directory. Any failure to read or write the
 * file only means the guide is offered again, at most once per game session.
 */
final class FirstRun {
   static final String FILE = "colonyjournal-client.txt";
   private static boolean handled;

   /** True when the first-steps guide should open now. Never throws. */
   static boolean consume() {
      if (handled) {
         return false;
      }

      handled = true;
      try {
         return check(FMLPaths.CONFIGDIR.get().resolve(FILE));
      } catch (IOException | RuntimeException | LinkageError e) {
         return true;
      }
   }

   /** True and writes the flag file when it does not exist yet; false when it does. */
   static boolean check(Path file) throws IOException {
      if (Files.exists(file)) {
         return false;
      }

      Path parent = file.getParent();
      if (parent != null) {
         Files.createDirectories(parent);
      }

      Files.writeString(file, "first_steps_shown=true\n", StandardCharsets.UTF_8);
      return true;
   }

   private FirstRun() {
   }
}
