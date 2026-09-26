package dev.voidpulsaryt.lcce.trust;

/** How much an explicitly-trusted outsider (not a team member, not a private owner) can do on a chunk. */
public enum TrustLevel {
    INTERACT,
    BUILD;

    public boolean isAtLeast(TrustLevel required) {
        return this.ordinal() >= required.ordinal();
    }
}
