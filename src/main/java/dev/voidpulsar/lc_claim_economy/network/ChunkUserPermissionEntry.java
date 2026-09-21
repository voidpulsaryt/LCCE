package dev.voidpulsar.lc_claim_economy.network;

import java.util.UUID;

/**
 * One row of the per-chunk permission list shown in {@code ChunkUserPermissionsScreen}: a
 * player, the catch-all "everyone else" row ({@code allPlayers}), or the catch-all "anyone
 * from an allied team" row ({@code allAllies}) - plus the {@code ChunkPermissionFlags}
 * bitmask granted to them on that chunk. {@code playerId} is ignored client-side for either
 * catch-all row - it's whatever placeholder UUID the server used to key that row internally.
 * Exactly one of {@code allPlayers}/{@code allAllies} is ever true for a given row.
 */
public record ChunkUserPermissionEntry(UUID playerId, String displayName, int flags, boolean allPlayers, boolean allAllies) {
}
