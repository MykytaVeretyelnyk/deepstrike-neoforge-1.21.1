package dev.colonyjournal.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The markup of the guide texts, with no Minecraft classes so it can be unit tested. A section body is a list of lines
 * separated by {@code \n}:
 * <ul>
 * <li>{@code # text} is a sub-heading;</li>
 * <li>{@code - text} is a bullet, optionally {@code - [icon] text};</li>
 * <li>anything else is a paragraph.</li>
 * </ul>
 * An icon is an item id ({@code iron_pickaxe}, {@code minecraft:bread}), {@code coin}, {@code frame:<status>} (a map frame),
 * {@code color:RRGGBB} (a colour square) or {@code line:RRGGBB} (a map line sample).
 */
final class GuideText {
   /** Section ids in display order. The lang keys are {@code journal.colonyjournal.guide.<id>.title} and {@code .body}. */
   static final List<String> IDS = List.of("intro", "steps", "overview", "links", "problems", "buildings", "economy", "happiness", "market", "stats", "glossary", "controls");
   private static final Pattern ICON = Pattern.compile("^\\[([^\\]\\s]+)]\\s*(.*)$");

   enum Kind {
      HEADING,
      PARA,
      BULLET
   }

   record Block(Kind kind, String text, String icon) {
   }

   /** The section a tab's help button opens. */
   static String sectionFor(String tabKey) {
      return switch (tabKey) {
         case "overview" -> "overview";
         case "map" -> "links";
         case "problems" -> "problems";
         case "buildings" -> "buildings";
         case "economy" -> "economy";
         case "market" -> "market";
         case "stats" -> "stats";
         default -> "intro";
      };
   }

   static List<Block> parse(String body) {
      List<Block> out = new ArrayList<>();

      for (String raw : body.split("\n")) {
         String line = raw.strip();
         if (line.isEmpty()) {
            continue;
         }

         if (line.startsWith("# ")) {
            out.add(new Block(Kind.HEADING, line.substring(2).strip(), null));
         } else if (line.startsWith("- ")) {
            String rest = line.substring(2).strip();
            Matcher m = ICON.matcher(rest);
            if (m.matches()) {
               out.add(new Block(Kind.BULLET, m.group(2), m.group(1)));
            } else {
               out.add(new Block(Kind.BULLET, rest, null));
            }
         } else {
            out.add(new Block(Kind.PARA, line, null));
         }
      }

      return out;
   }

   private GuideText() {
   }
}
