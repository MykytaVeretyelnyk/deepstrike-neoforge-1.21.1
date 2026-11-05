package dev.colonyjournal.client;

import java.nio.file.Files;
import java.nio.file.Path;

/** Finds the project directory whatever the working directory of the test JVM is. */
final class TestPaths {
   static final Path ROOT = findRoot();

   private static Path findRoot() {
      Path dir = Path.of("").toAbsolutePath();
      for (Path p = dir; p != null; p = p.getParent()) {
         if (Files.isDirectory(p.resolve("src/main/java/dev/colonyjournal"))) {
            return p;
         }

         if (Files.isDirectory(p.resolve("colonyjournal/src/main/java/dev/colonyjournal"))) {
            return p.resolve("colonyjournal");
         }
      }

      throw new IllegalStateException("project directory not found from " + dir);
   }

   private TestPaths() {
   }
}
