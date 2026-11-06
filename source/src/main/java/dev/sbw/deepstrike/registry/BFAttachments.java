package dev.sbw.deepstrike.registry;

import dev.sbw.deepstrike.game.TerminalMissileData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BFAttachments {
   public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "deepstrike");
   public static final DeferredHolder<AttachmentType<?>, AttachmentType<TerminalMissileData>> TERMINAL_MISSILES = ATTACHMENTS.register(
      "terminal_missiles", () -> AttachmentType.builder(() -> TerminalMissileData.EMPTY).serialize(TerminalMissileData.CODEC).build()
   );

   private BFAttachments() {
   }
}
