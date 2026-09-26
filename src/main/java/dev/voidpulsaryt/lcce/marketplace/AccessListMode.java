package dev.voidpulsaryt.lcce.marketplace;

import java.util.Set;
import java.util.UUID;

/** A private owner's choice of dial: only let specific people in, or let everyone in but them. */
public enum AccessListMode {
    WHITELIST,
    BLACKLIST;

    public boolean isAllowed(Set<UUID> list, UUID playerId) {
        return switch (this) {
            case WHITELIST -> list.contains(playerId);
            case BLACKLIST -> !list.contains(playerId);
        };
    }
}
