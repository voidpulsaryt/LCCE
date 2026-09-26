package dev.voidpulsaryt.lcce.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidpulsaryt.lcce.network.ClaimChunkPayload;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Unbound by default (bind it in Controls) - claims the chunk the player is currently standing in. */
public final class ClaimKeyBindings {

    public static final KeyMapping CLAIM_CHUNK = new KeyMapping(
            "key.lcce.claim_chunk", InputConstants.UNKNOWN.getValue(), "key.categories.lcce"
    );

    private ClaimKeyBindings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CLAIM_CHUNK);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (CLAIM_CHUNK.consumeClick()) {
            PacketDistributor.sendToServer(new ClaimChunkPayload());
        }
    }
}
