package dev.voidpulsar.lc_claim_economy.web;

/**
 * One row of leaderboard data: a team/party/player name plus their current
 * bank balance (in copper, the base currency unit - see
 * {@code CurrencyAmounts#totalCopper}) and claimed chunk count. Kept as plain
 * types only (no FTB/OP&C types) since instances of this cross the
 * {@code ModCompat} package boundary and get serialized directly to JSON.
 */
public record LeaderboardEntry(String name, long balanceCopper, int claimedChunks) {
}
