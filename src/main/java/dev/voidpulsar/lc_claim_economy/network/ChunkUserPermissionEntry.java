package dev.voidpulsar.lc_claim_economy.network;

import java.util.UUID;

/**
 * One row of the per-chunk permission list shown in {@code ChunkUserPermissionsScreen}: a
 * player (or, when {@code allPlayers} is set, the catch-all "everyone else" row) and the
 * {@code ChunkPermissionFlags} bitmask granted to them on that chunk. {@code playerId} is
 * ignored client-side when {@code allPlayers} is true - it's whatever placeholder UUID the
 * server used to key the catch-all entry internally.
 */
public record ChunkUserPermissionEntry(UUID playerId, String displayName, int flags, boolean allPlayers) {
}
