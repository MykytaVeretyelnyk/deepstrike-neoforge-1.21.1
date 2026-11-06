package dev.sbw.deepstrike.validation;

import dev.sbw.deepstrike.DeepStrikeAddon;
import dev.sbw.deepstrike.game.TerminalMissiles;
import dev.sbw.deepstrike.network.CommandTabletNetwork;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Pure checks of the "launch only through the Skyfront panel" gates. Run with ./gradlew runGts. */
@GameTestHolder("deepstrike")
@PrefixGameTestTemplate(false)
public final class PanelGateTests {
   @GameTest(template = "empty")
   public static void tabletPanelOnlyGate(GameTestHelper helper) {
      for (String gated : new String[]{"save", "arm", "launch", "defense_fire", "defense_reload", "defense_toggle"}) {
         if (!CommandTabletNetwork.panelOnly(gated)) {
            helper.fail("tablet action must be panel-only: " + gated);
            return;
         }
      }

      for (String open : new String[]{"disarm", "refresh", "login", "camera", "delete", "member", "defense_select", "rename", "unbind", "select_relay", "defense_launch", ""}) {
         if (CommandTabletNetwork.panelOnly(open)) {
            helper.fail("tablet action must stay available: " + open);
            return;
         }
      }

      if (CommandTabletNetwork.panelOnly(null)) {
         helper.fail("null action must not be gated");
         return;
      }

      DeepStrikeAddon.LOGGER.info("PANEL_GATE tablet gate ok");
      helper.succeed();
   }

   @GameTest(template = "empty")
   public static void panelActionWhitelist(GameTestHelper helper) {
      for (String ok : new String[]{"login", "refresh", "save", "arm", "disarm", "launch", "delete", "member", "camera", "bind_unit", "defense_select", "defense_reload", "defense_toggle", "defense_fire"}) {
         if (!TerminalMissiles.panelAction(ok)) {
            helper.fail("panel action must be allowed: " + ok);
            return;
         }
      }

      for (String bad : new String[]{"select_relay", "defense_launch", "rename", "unbind", "", "LAUNCH", "launch "}) {
         if (TerminalMissiles.panelAction(bad)) {
            helper.fail("panel action must be rejected: " + bad);
            return;
         }
      }

      if (TerminalMissiles.panelAction(null)) {
         helper.fail("null panel action must be rejected");
         return;
      }

      DeepStrikeAddon.LOGGER.info("PANEL_GATE whitelist ok");
      helper.succeed();
   }
}
