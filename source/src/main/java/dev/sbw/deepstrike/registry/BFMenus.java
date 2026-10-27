package dev.sbw.deepstrike.registry;

import dev.sbw.deepstrike.menu.LauncherMenu;
import dev.sbw.deepstrike.menu.MissileAssemblyMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class BFMenus {
   public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, "deepstrike");
   public static final DeferredHolder<MenuType<?>, MenuType<LauncherMenu>> LAUNCHER = TYPES.register("launcher", () -> IMenuTypeExtension.create(LauncherMenu::new));
   public static final DeferredHolder<MenuType<?>, MenuType<MissileAssemblyMenu>> MISSILE_ASSEMBLY = TYPES.register(
      "missile_assembly", () -> IMenuTypeExtension.create(MissileAssemblyMenu::new)
   );
}
