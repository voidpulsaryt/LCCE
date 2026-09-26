package dev.voidpulsaryt.lcce.client.xaero;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;

import java.util.UUID;

/** One claimed chunk, as handed to Xaero's minimap element renderer. */
public record ClaimBorderElement(ChunkDimPos pos, UUID teamId, int color) {
}
