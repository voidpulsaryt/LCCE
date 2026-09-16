# Changelog

All notable changes to this mod are documented here.

## [4.6.0]

### Added

- **`upkeepOnlineRequirement`** config option — controls when a team's (or
  OP&C claim owner's) upkeep countdown is allowed to advance: `ANYONE_ONLINE`
  (default, matches prior behavior) counts down only while someone is online
  anywhere on the server; `TEAM_MEMBER_ONLINE` counts down for a team/owner
  only while one of its own members is online, independently of every other
  team and player; `ALWAYS_CHARGE` counts down off server uptime alone
  regardless of who (if anyone) is online, so teams can be billed - and lose
  protections for non-payment - while everyone is offline. Each FTB team and
  OP&C claim owner now tracks its own upkeep countdown (rather than one
  shared server-wide clock) so `TEAM_MEMBER_ONLINE` can gate them
  independently.
- **`/lcce upkeep_details`** (FTB) and **`/lcce opc_upkeep_details`** (OP&C)
  now report the time remaining until the next upkeep charge, so players can
  see exactly when they'll be billed instead of only finding out after the
  fact.
- **Web dashboard: live "next charge" countdown** in the summary strip,
  ticking down client-side between the dashboard's new ~30-second background
  refresh (previously the dashboard only reloaded after an action).
- **Web dashboard: Map tab** *(requires `blueMapWebUrl`)* — an embedded
  BlueMap iframe deep-linked and centered on the team's claim, plus a direct
  "open in a new tab" link to the same spot.
- **Optional BlueMap integration** (`blueMapClaimOverlaysEnabled`, on by
  default when BlueMap is installed) — draws each FTB team's claimed chunks
  as colored area markers on the live map, refreshed periodically, with a
  popup showing the team's name, balance, and next upkeep charge. FTB Chunks
  only; there is no OP&C claim overlay yet, and no Dynmap equivalent since
  Dynmap has no NeoForge build for this Minecraft version.

### Removed

- **Bank Dashboard screen** (`/lcce dashboard`, default-**B** keybind, added
  in 4.5.0) — removed entirely, along with its command and keybind. Claim/
  upkeep/protection info it surfaced is still available through this mod's
  other commands and the FTB claim breakdown screen.

## [4.5.0]

### Added

- **Player Warps** *(FTB Teams only)* — personal, player-owned teleport
  points, tied into the claim economy rather than bolted on as a standalone
  system:
  - `/lcce warp` opens an in-game GUI (built with FTB Library's own screen
    toolkit, matching this mod's other custom screens) listing your warps
    and every warp other players have made public, with buttons to create,
    delete, toggle public/private, and teleport. `/lcce warp list` gives a
    clickable chat-based fallback with the same information.
  - `/lcce warp set <name>` creates or moves a warp to your current
    position; `warpRequireOwnClaim` (default `true`) restricts this to
    chunks your own team currently has claimed, and a warp is
    automatically deleted the moment its chunk is unclaimed.
  - `/lcce warp public <name> <true|false>` shares a warp so other players
    can teleport to it via `/lcce warp tpto <player> <name>` or the GUI,
    optionally charging them a toll (`warpTeleportCostCopper`) paid
    straight to the owner.
  - `/lcce warp alias add|remove <name> <alias>` gives a warp one or more
    extra names it can also be found/teleported to by, unique per-owner.
  - Server owners get full cost/limit control: `warpsEnabled`,
    `maxWarpsPerPlayer`, `warpCreateCostCopper`, `warpTeleportCostCopper`,
    `warpRequireOwnClaim`, `warpCooldownSeconds`, and
    `warpWorldDisplayNames` (custom friendly names for dimensions shown in
    the warp list/GUI).
- **`/lcce dashboard`** — opens the Bank Dashboard screen directly (client-only,
  no server round trip), as a more discoverable alternative to the default-**B**
  keybind.

### Changed

- **Transaction history moved into Lightman's Currency's own account
  notification log.** Every claim-economy money movement (upkeep charges,
  claim purchases, unclaim refunds, the pioneer bonus, market sales/purchases,
  and now player warp costs/tolls) is pushed straight onto the relevant
  account via `IBankAccount.pushNotification(...)`/`DepositWithdrawNotification.Custom`,
  the same feed LC itself uses for interest, transfers, and salary payments.
  This mod's own separate ledger, the Bank Dashboard's **History** button,
  and the Transaction History screen are removed - one less UI to check, and
  transactions now show up in the same place as everything else in a
  player's or team's account.

### Fixed

- **Bank Dashboard text looked blurry** when Minecraft's menu background blur
  video setting was on. The panel background wasn't fully opaque, so a sliver
  of the blurred world behind it bled through and washed out the text on top
  (vanilla buttons were unaffected since their texture is opaque). The panel
  and header backgrounds are now fully opaque.

## [4.4.1]

### Fixed

- **Client crash to desktop when opening FTB Chunks' Settings gear on FTB
  Library 2101.1.34+.** `EditConfigScreenConfigEntryButtonMixin`'s claim/build
  visibility lock injected right before a `ConfigValue.getCanEdit()` call
  inside `EditConfigScreen$ConfigEntryButton`'s constructor; FTB Library
  2101.1.34 moved that call out of the constructor (into a lazily-evaluated
  key-text supplier and into `draw`/`onClicked`), so the injection matched
  nothing and Mixin's `defaultRequire: 1` turned the miss into a hard crash.
  The lock now applies at the constructor's return instead - `configValue` is
  always assigned by then, and every consumer of `getCanEdit()` reads it live
  rather than caching it at construction, so this works unchanged against
  both the old and new FTB Library layouts. Minimum FTB Library bumped to
  `2101.1.34` accordingly.

## [4.4.0]

### Changed

- **Command root renamed from `/lc_claim_economy` to `/lcce`.** Every subcommand
  (`war`, `market`, `bounty`, `leaderboard`, `upkeep_details`, etc.) moves under
  the new short root; `LcClaimEconomy.MOD_ID` (resource/network namespace,
  config filename) is unchanged. The in-game clickable upkeep-details link and
  all doc comments referencing the old command were updated to match.

### Added

- **Authenticated web dashboard** (`webDashboardEnabled`, off by default,
  requires `webEnabled` and FTB Chunks/Teams - no Open Parties and Claims
  backend yet) at `/dashboard`, alongside the existing read-only leaderboard:
  - **Passwordless login**: `/lcce web login` generates a short-lived,
    single-use code (`LoginCodeService`) that's entered on the dashboard's
    login page to start a session (`SessionManager`, `HttpOnly` cookie).
    Nothing is ever stored or transmitted that could be reused if leaked
    beyond that one short window.
  - **Land**: view all claimed chunks (position, dimension, land/build,
    force-load and pending status) with unclaim and force-load/unload actions.
  - **Protections**: view and toggle all six build protections (PvP,
    explosions, mob-griefing, block edit/interact, entity interact) with live
    price and "pending until next period" state - routes through the exact
    same `TeamPropertyHandler` pricing/queueing path as the in-game GUI.
  - **Wars**: incoming/outgoing/available lists, declare/end actions, and the
    declaration-window banner when one's configured and currently closed.
  - **Team**: roster with rank/online status (view-only - FTB Teams exposes
    invite/kick/promote only through its own in-game GUI, not any stable API
    or command this mod could safely drive), plus a peaceful-mode toggle (see
    below).
  - Actions that need to act as the player in FTB Chunks/Teams' own APIs
    (unclaim, force-load, wars) require that player to currently be online;
    protection and peaceful-mode toggles work regardless.
  - **Cosmetic customization** (`webTheme` config section): site name, accent
    color, logo URL, and raw custom CSS, all applied at runtime via a new
    public `/api/theme` endpoint - used by both the dashboard and the
    existing leaderboard page, so a server can reskin both without touching
    mod files. The leaderboard page also gained a link to the dashboard when
    it's enabled.
  - Both web pages were restyled to match: a layered gradient background and
    glass (blurred, translucent) panels replace the old flat Nord fills,
    while keeping the same underlying Nord palette and layout.
- **Siege Mode** (`siegeModeEnabled`, off by default). While on, a team that's
  been at war for longer than `siegeModeGraceHours` (default 12) has its
  explosion protection bypassed entirely - intended for packs with large-scale
  explosive weapons (missiles, artillery, nukes), so a declared war can mean a
  claim is actually able to be damaged rather than staying economically
  costly but physically untouchable. Deliberately blanket per-team rather
  than scoped to the specific war opponent, since FTB Chunks' explosion check
  has no attacker context to scope against. Grace period is measured from a
  team's first war of the current war-streak and doesn't reset for additional
  wars while already at war; clears once they have no active wars. PvP and
  block-edit protection are untouched by this setting.
- **War participation safeguards for small/solo teams**:
  - `warMinClaimedChunks` (0 = off, default): a team needs more than this many
    claimed chunks before it can declare or be targeted by war.
  - `/lcce war peaceful on|off`: any team's own opt-out of the war system
    entirely, independent of size. Blocked from enabling while the team has
    an active war (end it first) so it can't be used as a mid-siege escape
    hatch. `war peaceful` with no argument reports current status.
  - Both gates fold into the same `WarService.isWarEligibleTeam` check used
    everywhere wars are declared, listed, or targeted, so exempt teams simply
    don't appear as available targets rather than failing after the fact;
    `WarService.toggleWar` still gives a specific reason (peaceful vs. too
    small vs. generic) when a declare is rejected.

## [4.3.0]

### Added

- **Configurable war declaration window.** New wars can now be restricted to
  a recurring weekly window (e.g. "Friday 22:00 UTC to Sunday 22:00 UTC"),
  matching the request for a Towny SiegeWar-style schedule so players aren't
  attacked while at school or work:
  - New `[war]` config options in `lc_claim_economy-server.toml`:
    `warDeclarationWindowEnabled` (off by default), `warDeclarationWindowStartDay`/
    `warDeclarationWindowStartHourUtc`, and `warDeclarationWindowEndDay`/
    `warDeclarationWindowEndHourUtc`. Days are UTC and support windows that
    wrap past the end of the week (e.g. start `SUNDAY`, end `FRIDAY`).
  - New pure, unit-tested `WarDeclarationWindow` service does the minute-of-week
    math; a degenerate config (`start == end`) is treated as always-open so a
    typo can't accidentally lock every team out of declaring war.
  - The check gates only the moment a team declares a **brand-new** war
    (`WarService.toggleWar`). Ending an existing war, cancelling your own
    pending declare, and the automatic suspend/restore cycle for wars a team
    can't currently afford are all unaffected and remain available any time -
    only starting a fresh attack is time-boxed.
  - Declaring outside the window returns a chat message naming the next
    allowed window instead of silently failing.
- **War window state surfaced client-side**, not just enforced server-side:
  `SyncWarStatePayload` now carries whether the window is currently open and
  a human-readable description of it. In the War screen's Declare section,
  the heading shows "closed until ..." in red, each Declare button's sword
  icon dims, and both the button and its info tooltip explain when the
  window reopens - so players see this before clicking, not just after.

## [4.2.1]

### Added

- **Player-to-player chunk marketplace (FTB Chunks only).** `MarketService`
  and its command frontend, `/lc_claim_economy market sell|cancel|buy|browse`:
  - All commands are position-based - `sell <price_copper>` lists whatever
    chunk you're currently standing in, `cancel` delists it, `buy` purchases
    the listing on the chunk you're standing in, `browse` prints all active
    listings (position, dimension, price, seller) sorted cheapest first.
  - Ownership transfer is sequenced safely: the seller's chunk is unclaimed
    and the buyer's claim is confirmed successful *before* any money moves,
    with an automatic rollback (reclaiming for the seller) if the buyer-side
    claim fails, so a failed transfer never charges the buyer or strands the
    chunk unclaimed.
  - Transfers run through a new `ClaimBatchContext.runAsInternalTransfer`
    suppression so `ChunkClaimHandler` doesn't also charge the buyer the
    normal claim price or pay the seller an unclaim refund on top of the
    agreed sale price.
  - Scope note: the buyer receives a freshly-claimed chunk with default
    protection/force-load state - land/build classification and per-player
    chunk permissions are not carried over from the seller.
- **Transaction ledger and economy statistics**, tracking upkeep charges,
  claim purchases, unclaim refunds, the pioneer bonus, and market sales/
  purchases:
  - Every player/team account now keeps a capped (50-entry), newest-first
    history (`LcClaimEconomySavedData.LedgerEntry`), viewable in-game via a
    new **History** button on the Bank Dashboard, opening
    `TransactionHistoryScreen` - a scrollable, ftblibrary-free list showing
    each entry's description, timestamp, and signed amount.
  - Server-wide aggregate counters (total upkeep charged/missed, claim spend,
    unclaim refunds, market volume) are now shown on the web dashboard under
    a new **Economy Activity** panel - answers "is offline upkeep actually
    being charged, how often, and how much" at a glance, without exposing
    any individual account's history over the unauthenticated web server.
  - Wired into both backends: `ChunkClaimHandler`/`ClaimBatchContext` (FTB
    single and bulk claims/unclaims), `OpcClaimEconomyListener` (OP&C claims/
    unclaims), and `UpkeepSettlementService`/`OpcUpkeepService` (periodic
    upkeep, on both a successful charge and a missed/frozen payment).

### Fixed

- **Free-allowance chunks paid out an unclaim refund they were never charged
  for**, on both backends:
  - OP&C (`OpcClaimEconomyListener.handleUnclaim`) refunded on *every*
    unclaim unconditionally - it never checked the free-chunk allowance at
    all, unlike the claim side right above it which does. A chunk claimed
    for free within `freeChunks` still paid out a refund when unclaimed,
    manufacturing money from nothing.
  - FTB Chunks (`ChunkClaimHandler`) had a subtler version of the same bug:
    `ChunkTeamData.getClaimedChunks()` caches its result, and FTB Chunks'
    own `unclaim()` only invalidates that cache *after* firing the
    `AFTER_UNCLAIM` event this mod listens on - confirmed by decompiling the
    installed FTB Chunks jar. Reading the team's claimed-chunk count from
    inside that listener could return a stale, pre-removal count, tipping
    the free-allowance comparison the wrong way. Fixed by counting from the
    claim manager's own live (uncached) chunk collection instead of the
    per-team cached one.
- **Mod refused to load against any FTB Chunks/OP&C release newer than the
  exact point version this mod was built against**, even though
  `neoforge.mods.toml` already declares an open-ended `[X,)` version range
  for both. `ModCompatibility.requireExactVersion` did a hard string-equality
  check independent of that declared range, so every routine FTB Chunks or
  OP&C bugfix release (e.g. 2101.1.20 -> 2101.1.21) broke this mod's loading
  entirely with an `IllegalStateException` until it was manually re-pinned
  and republished. Replaced with `requireMinimumVersion`, a proper
  `DefaultArtifactVersion` comparison matching the toml's own minimum-version
  intent - Lightman's Currency (a required, API-heavy dependency) still gets
  an exact-match check, since a version bump there is far more likely to
  carry an actual breaking change. Bumped the pinned `ftb_chunks_version` to
  2101.1.21 to match what's actually current.