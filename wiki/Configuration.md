# Configuration

The config file is generated on first launch at:

```
world/serverconfig/lc_claim_economy-server.toml
```

(or `saves/<world>/serverconfig/lc_claim_economy-server.toml` in single-player). Reload by restarting the server; some values apply immediately, others (protection/war changes) are queued to the next upkeep period as noted in [Features](Features).

## `general`

| Key | Default | Description |
|---|---|---|
| `claimPrice` | `10000` | Cost in copper units to claim one chunk (10000 copper = 1 Diamond coin) |
| `freeChunks` | `0` | First *N* claimed chunks per team/player are free and exempt from protection upkeep |
| `landChunkGroupSize` | `5` | Land chunks are billed once per this many chunks (rounded up, minimum 1 group when any land chunk is billable). `1` makes land cost the same as build |
| `unclaimRefundRatio` | `0.8` | Fraction of claim price refunded on unclaim (`0`–`1`) |
| `forceLoadUpkeepPrice` | `100000` | Upkeep cost per force-loaded chunk per period (100000 copper = 1 Netherite coin) |
| `upkeepPeriodMinutes` | `60` | How often upkeep is charged, in real-time minutes (`1`–`10080`) |
| `upkeepOnlineRequirement` | `ANYONE_ONLINE` | Controls when a team's (or OP&C claim owner's) upkeep countdown is allowed to advance. `ANYONE_ONLINE`: counts down only while at least one player is online anywhere on the server. `TEAM_MEMBER_ONLINE`: counts down for a team/owner only while one of its own members is online, independently of everyone else. `ALWAYS_CHARGE`: counts down off server uptime alone regardless of who (if anyone) is online, so teams can be billed — and lose protections for non-payment — while everyone is offline |
| `disableCoinMint` | `false` | Blocks use of Lightman's Currency's Coin Mint block server-wide |

## `protectionPrices`

Added to upkeep per billable chunk.

| Key | Default | Triggers when… |
|---|---|---|
| `mobGriefProtectionPrice` | `80` | Allow Mob Griefing = false |
| `explosionProtectionPrice` | `70` | Allow Explosion Damage = false |
| `pvpDisablePrice` | `50` | Allow PvP Combat = false |
| `blockInteractProtectionPrice` | `100` | Block Interact Mode ≠ Public |
| `blockEditProtectionPrice` | `100` | Block Edit Mode ≠ Public |
| `entityInteractProtectionPrice` | `100` | Entity Interact Mode ≠ Public |

## `war`

| Key | Default | Description |
|---|---|---|
| `warEnabled` | `true` | Enable the war system entirely; when off, war costs are ignored, war actions are blocked, and the war button is hidden client-side |
| `warOutgoingCostMultiplier` | `2.0` | Flat multiplier `x` — declaring war costs `x × target's base upkeep` per period, regardless of existing war count |
| `warCostMultiplier` | `1.2` | Incoming war exponent `l` — surcharge is `b × Σ(l^n)` for `n = 0..k-1` over `k` incoming wars |
| `warDeclarationWindowEnabled` | `false` | Restrict *new* war declarations to a recurring weekly window. Doesn't affect ending a war, cancelling a pending declare, or the automatic suspend/restore of unaffordable wars |
| `warDeclarationWindowStartDay` | `FRIDAY` | Day the window opens (UTC). One of `MONDAY`…`SUNDAY` |
| `warDeclarationWindowStartHourUtc` | `22` | Hour (0–23, UTC) the window opens |
| `warDeclarationWindowEndDay` | `SUNDAY` | Day the window closes (UTC). Can wrap past the end of the week |
| `warDeclarationWindowEndHourUtc` | `22` | Hour (0–23, UTC) the window closes |
| `siegeModeEnabled` | `false` | Bypasses explosion protection entirely on any chunk of a team at war longer than `siegeModeGraceHours`. Only removes this mod's own explosion protection — has no effect on a weapon mod's own defenses. PvP and block-edit protection are unaffected |
| `siegeModeGraceHours` | `12` | Hours between a team's first war of the current streak and siege mode taking effect. `0` = immediate |
| `warMinClaimedChunks` | `0` | A team needs more claimed chunks than this to declare or be targeted by war. `0` disables the check |

## `protectionDismantle`

Order protections are dropped in when upkeep can't be paid (first = dropped first). Use FTB property id paths without namespace.

| Key | Default order |
|---|---|
| `protectionDismantleOrderLand` | `land_block_edit_mode`, `land_block_interact_mode` |
| `protectionDismantleOrderBuild` | `entity_interact_mode`, `block_edit_mode`, `block_interact_mode`, `allow_mob_griefing`, `allow_explosions`, `allow_pvp` |

## `web`

| Key | Default | Description |
|---|---|---|
| `webEnabled` | `false` | Starts the built-in HTTP server (leaderboard + info page). The leaderboard itself is always read-only and unauthenticated |
| `webPort` | `8123` | Port the server listens on (`1`–`65535`) |
| `webBindAddress` | `"0.0.0.0"` | Bind address — `0.0.0.0` for all interfaces, `127.0.0.1` for local-only (e.g. behind your own reverse proxy) |
| `webLeaderboardSize` | `10` | Max entries shown per leaderboard (`1`–`100`) |
| `webDashboardEnabled` | `false` | Adds the login-gated player dashboard at `/dashboard` (requires `webEnabled`). FTB Chunks/Teams only |
| `webSessionMinutes` | `720` | How long a dashboard login session stays valid, in minutes (`1`–`43200`) |
| `webLoginCodeMinutes` | `5` | How long a `/lcce web login` one-time code stays valid before expiring unused, in minutes (`1`–`60`) |

## `webTheme`

Cosmetic only — no functional effect. Shared by both web pages.

| Key | Default | Description |
|---|---|---|
| `webSiteName` | `"Claim Economy"` | Site name in the page title and header |
| `webAccentColor` | `"#88C0D0"` | Accent color (CSS hex) for headings, highlights, and buttons |
| `webLogoUrl` | `""` | Optional logo image URL next to the site name; loaded directly by each visitor's browser |
| `webCustomCss` | `""` | Optional raw CSS appended after the built-in stylesheet |

## `blueMap`

Only takes effect if the [BlueMap](https://bluemap.bluecolored.de/) mod is also installed alongside this mod.

| Key | Default | Description |
|---|---|---|
| `blueMapClaimOverlaysEnabled` | `true` | Draws each FTB team's claimed chunks as colored area markers on the live BlueMap map, refreshed every ~10 seconds. FTB Chunks only — there is no OP&C claim overlay yet |
| `blueMapWebUrl` | `""` | Externally-reachable base URL of your BlueMap web app (e.g. `"http://myserver.com:8100"`), used to embed a live map view and a direct "view on map" link on the web dashboard's Map tab. This mod can't auto-detect BlueMap's externally reachable address (reverse proxy, different port/domain, etc.), so it must be set explicitly. Leave blank to hide the Map tab entirely |

## `warps`

Player-owned warps (`/lcce warp`) — see [Features](Features).

| Key | Default | Description |
|---|---|---|
| `warpsEnabled` | `true` | Enable the `/lcce warp` system entirely |
| `maxWarpsPerPlayer` | `3` | Maximum warps a single player can own at once |
| `warpCreateCostCopper` | `5000` | Cost to create a new warp. Moving an existing warp to a new location is free |
| `warpTeleportCostCopper` | `0` | Toll charged when teleporting to another player's public warp, paid to that warp's owner. Teleporting to your own warps is always free |
| `warpRequireOwnClaim` | `true` | A warp can only be created on a chunk your own team has claimed. Warps are deleted automatically if their chunk is later unclaimed |
| `warpCooldownSeconds` | `5` | Minimum time between two warp teleports (own or others') |
| `warpWorldDisplayNames` | *(empty)* | Friendly dimension names shown in the warp GUI/list, as `"namespace:path=Display Name"` entries (e.g. `"minecraft:the_nether=The Nether"`). Unlisted dimensions fall back to a prettified path |

## `flavor`

| Key | Default | Description |
|---|---|---|
| `pioneerBonusAmount` | `50000` | One-time reward for the first chunk ever claimed on the server, in copper (50000 = 5 Diamond coins). `0` disables the payout |

## `debug`

| Key | Default | Description |
|---|---|---|
| `debugTestTeamCommands` | `false` | Enables `/lcce seed_test_teams`, `clear_test_teams`, `count_test_teams`. Keep off in production |
