package dev.colonyjournal.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Fits text into a width without Minecraft classes: the width of a string is passed in, so it can be unit tested. */
final class TextFit {
   static final String ELLIPSIS = "…";

   private TextFit() {
   }

   /** The text cut so that it, with an ellipsis, is at most maxW wide; the text itself when it fits. */
   static String ellipsize(String text, int maxW, ToIntFunction<String> width) {
      if (width.applyAsInt(text) <= maxW) {
         return text;
      }

      int end = text.length();
      while (end > 0 && width.applyAsInt(text.substring(0, end).stripTrailing() + ELLIPSIS) > maxW) {
         end--;
      }

      return end <= 0 ? "" : text.substring(0, end).stripTrailing() + ELLIPSIS;
   }

   /**
    * Breaks the text at spaces into at most maxLines lines of at most maxW; a word wider than a line is cut by letters.
    * When text is left over the last line ends with an ellipsis.
    */
   static List<String> wrap(String text, int maxW, int maxLines, ToIntFunction<String> width) {
      List<String> lines = new ArrayList<>();
      if (maxLines <= 0 || maxW <= 0) {
         return lines;
      }

      String rest = text.strip();
      while (!rest.isEmpty()) {
         if (lines.size() == maxLines - 1) {
            lines.add(ellipsize(rest, maxW, width));
            return lines;
         }

         if (width.applyAsInt(rest) <= maxW) {
            lines.add(rest);
            return lines;
         }

         int cut = -1;
         for (int i = 1; i <= rest.length(); i++) {
            if (width.applyAsInt(rest.substring(0, i)) > maxW) {
               break;
            }

            if (i == rest.length() || rest.charAt(i) == ' ') {
               cut = i;
            }
         }

         if (cut <= 0) {
            // one word wider than the line
            int i = 1;
            while (i < rest.length() && width.applyAsInt(rest.substring(0, i + 1)) <= maxW) {
               i++;
            }

            cut = i;
         }

         lines.add(rest.substring(0, cut).strip());
         rest = rest.substring(cut).strip();
      }

      return lines;
   }
}
