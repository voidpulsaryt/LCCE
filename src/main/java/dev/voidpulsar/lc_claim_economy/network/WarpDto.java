package dev.voidpulsar.lc_claim_economy.network;

import java.util.List;
import java.util.UUID;

/**
 * Network-friendly view of a warp for the client GUI - no bank/claim
 * internals, just what's shown/needed to act on it. {@code dimensionDisplay}
 * is already resolved server-side via {@link
 * dev.voidpulsar.lc_claim_economy.util.WorldDisplayNames}, so the client
 * never needs its own copy of {@code warpWorldDisplayNames}.
 */
public record WarpDto(String name, UUID ownerId, String ownerName, String dimensionDisplay, int x, int y, int z, boolean isPublic, List<String> aliases) {
}
