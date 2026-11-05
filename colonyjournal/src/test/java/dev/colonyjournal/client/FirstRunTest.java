package dev.colonyjournal.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.client.GuideText.Block;
import dev.colonyjournal.client.GuideText.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirstRunTest {
   @Test
   void showsOnceThenRemembers(@TempDir Path dir) throws IOException {
      Path file = dir.resolve("config").resolve(FirstRun.FILE);
      assertTrue(FirstRun.check(file));
      assertTrue(Files.exists(file));
      assertFalse(FirstRun.check(file));
      assertFalse(FirstRun.check(file));
   }

   @Test
   void unwritableLocationThrowsSoTheCallerCanFallBack(@TempDir Path dir) throws IOException {
      // the parent "directory" is a regular file, so the flag file can be neither created nor read
      Path blocker = dir.resolve("blocker");
      Files.writeString(blocker, "x");
      assertThrows(IOException.class, () -> FirstRun.check(blocker.resolve(FirstRun.FILE)));
   }

   @Test
   void parsesGuideMarkup() {
      List<Block> blocks = GuideText.parse("Intro line\n\n# Head\n- plain bullet\n- [iron_pickaxe] with icon\n- [frame:ok] frame");
      assertEquals(5, blocks.size());
      assertEquals(Kind.PARA, blocks.get(0).kind());
      assertEquals(Kind.HEADING, blocks.get(1).kind());
      assertEquals("Head", blocks.get(1).text());
      assertEquals(Kind.BULLET, blocks.get(2).kind());
      assertEquals(null, blocks.get(2).icon());
      assertEquals("iron_pickaxe", blocks.get(3).icon());
      assertEquals("with icon", blocks.get(3).text());
      assertEquals("frame:ok", blocks.get(4).icon());
   }
}
