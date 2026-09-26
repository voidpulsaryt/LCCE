# LCCE

Turns FTB Chunks + FTB Teams into a living, self-managing economy with the help of
Lightman's Currency: claiming costs money, protection is billed per region, wars have a price,
and players can buy and sell land from each other.

Target: **Minecraft 1.21.1, NeoForge**. Requires FTB Chunks, FTB Teams, and Lightman's Currency.
Xaero's Minimap/World Map and BlueMap are optional enhancements, not hard dependencies.

This is a from-scratch rewrite of this same project's own earlier codebase - see this repo's git
history prior to the `Removal for now` commit for the earlier attempt this design is inspired by,
rather than a separate reference project.

## Status

Every system below has real, working logic behind it, verified with `javap` directly against the
actual dependency jars where those were available (Lightman's Currency, Xaero's Minimap/World Map)
and against BlueMap's published API docs, rather than guessed.

Every unit this mod tracks internally is 1 copper - Lightman's Currency's own coin hierarchy
(copper 1, iron 10, gold 100, emerald 1,000, diamond 10,000, netherite 100,000) is what
`CoinValue.fromNumber` uses automatically to break a raw amount into denominations, so config
values and command amounts are always "how many copper-equivalents," letting Lightman's own coin
display handle the rest. Every place this mod shows a currency amount to a player - chat messages,
commands, the World Map info panel, the web dashboard, BlueMap marker labels - formats it through
`CurrencyBridge.formatValue`, which delegates to `CoinValue#getText()` (Lightman's own
`ChainData.formatValue`). That means it automatically follows whatever coin-value display a server
has configured - coin-icon text, wordy text, or (as of Lightman's Currency 2.2.0.0+) a plain
numerical format like `$100` - instead of ever showing a raw, unformatted number.

### Core economy

- **Claiming costs money** (`economy/ClaimCostCalculator`, `integration/ftb/ClaimChunkEconomyListener`):
  claiming an FTB Chunks chunk costs the claiming *player* real Lightman's Currency coins
  directly, straight out of their own wallet (via `CurrencyBridge`, the same way a marketplace
  purchase or a bounty does) - not the team's pooled balance, since claiming land is a personal
  action someone pays for themselves, unlike genuinely shared costs like upkeep or wars. The
  pricing curve still scales off how many chunks the *team* already owns (a solo player's personal
  FTB Team works identically to a party's here), with a configurable free-chunk allowance and an
  exponential cost curve. Unclaiming refunds a configurable percentage straight back to the
  unclaiming player. A player also gets a one-time **pioneer bonus**
  (`claimEconomy.pioneerBonus`) the moment their team claims its first chunk ever. This charges
  through the exact same `ClaimedChunkEvent.BEFORE_CLAIM`/`AFTER_CLAIM` pipeline no matter how the
  claim happens - the keybind below, `/ftbchunks claim`, or the World Map's drag-select claim menu
  (see the Xaero's World Map section) - since they all end up calling FTB Chunks' own
  `ChunkTeamData#claim` internally. Claims can also be placed via a keybind while looking at
  Xaero's minimap - see below.

- **Team bank accounts**: every FTB Team (party or solo) gets a currency balance via FTB Teams'
  own `TeamProperty` system (`integration/ftb/TeamBalance`) - the same mechanism FTB Chunks itself
  uses for per-team settings, so a solo player's personal team and a party's shared team are the
  same code path. This funds the genuinely *shared* costs below (upkeep, wars, state-owned
  marketplace listings) - not claim costs, which come out of the claiming player's own wallet
  directly (see above). `/lcce balance` and `/lcce deposit <amount>` (pulls real coins out of your
  own wallet into your team's balance) let you inspect/fund it; `/lcce admin set <amount>` (ops) is
  left in for testing.

- **Transaction logging** (`integration/currency/TransactionLog`): every charge, refund, deposit,
  and payout in this mod - claim costs, upkeep, wars, marketplace, bounties, everything - pushes a
  real `DepositWithdrawNotification` through Lightman's Currency's own `NotificationAPI`, the same
  type its own bank accounts use. A team-ledger transaction notifies every online member; a
  personal-wallet transaction notifies that player. This is wired into the two choke points every
  transaction already passes through (`TeamBalance.charge`/`refund`,
  `CurrencyBridge.withdrawFromPlayer`/`depositToPlayer`), so nothing elsewhere had to change to
  get full logging - it shows up in a player's normal Lightman's Currency notification log
  (the bell/mail icon on their wallet), not some separate LCCE-only record.

- **Upkeep, including force-load billing and an optional resident tax** (`economy/UpkeepPricing`,
  `UpkeepBilling`, `ForceLoadBilling`, `UpkeepScheduler`): every period, a team's balance is first
  topped up by a Towny-style **per-member tax** (`upkeep.perMemberTaxPerPeriod`, 0/off by default) -
  every *online* member is charged that amount straight from their own wallet into the team's
  balance, so upkeep doesn't have to rely on someone remembering to `/lcce deposit`. The team is
  then billed - in order - for (1) war upkeep, (2) force-loaded chunks
  (`upkeep.forceLoadPricePerChunk`; the least-recently force-loaded chunks get un-force-loaded via
  FTB Chunks' own API if that's unaffordable), then (3) region protection, dismantling the
  lowest-priority line items first if the bill doesn't fit (config: `upkeep.dismantlePriorityOrder`)
  and silently restoring them the moment the team can pay again. `/lcce upkeep` shows the full
  itemized breakdown - war, force-load, and every region's line items - plus exactly when the next
  payment is due.

### Land control

- **Per-region protection** (`region/*`, `integration/protection/RegionProtectionListener`):
  officers can carve a team's claims into named `Region`s (`/lcce region
  create/delete/claim/unclaim/list/info/set/whitelist`), each with its own mob-griefing/
  explosion/PvP toggles and Public/Allies/Team/Private interact & edit tiers (Private falls back to
  a per-region whitelist). A chunk with no region assigned is left entirely alone - FTB Chunks' own
  team-wide protection keeps governing it, so regions are a pure opt-in override, not a second
  parallel permission system.

- **Per-player chunk permissions** (`trust/*`, `integration/ftb/TrustCommand`): `/lcce trust add
  <player> [interact|build]` grants one specific outsider access to one specific chunk without
  making them a team member, buying them in, or opening a whole region. It's the single
  highest-precedence check in the protection listener, overriding region tiers, marketplace
  ownership, and even FTB Chunks' own team-membership check - the latter needs a second listener
  pass at `EventPriority.LOWEST` with `receiveCanceled = true`, since FTB Chunks enforces its own
  team check via a separate listener on the same vanilla events; the only way to let a trusted
  outsider through is to let FTB Chunks deny it first, then explicitly un-cancel the event
  afterward. `/lcce trust remove/list` round it out.

- **Nations** (`nation/*`, `integration/ftb/NationCommand`) - the Towny-style layer of allied
  teams above a single team, each with its own pooled balance separate from any member's own
  `TeamBalance`. `/lcce nation create <name>` founds one with your team as capital; only the
  capital's officers can `/lcce nation add/kick <team>` other member teams, `/lcce nation tax
  <amount>` (an optional per-member-team tax, 0/off by default, collected every upkeep period -
  see `NationBilling` - from whatever a member team has left over *after* its own war/region/
  force-load upkeep, so nation dues never compete with a team's own bills), or `/lcce nation
  disband` outright. A member team's own officers can always `/lcce nation leave` voluntarily -
  only the capital itself can't leave (disbanding is the only way out for it), and anyone can
  `/lcce nation deposit <amount>` from their team's balance to top the nation up beyond the tax.
  `/lcce nation info`/`list` round it out. Server-side only for now - unlike team balance and
  regions, a nation's data isn't synced to any client yet, so there's no World Map/HUD
  visualization of nation borders or balances.

### Conflict

- **Wars** (`war/*`, folded into `UpkeepBilling`): `/lcce war declare <team>` costs an upfront fee
  and adds a recurring cost to *both* sides' upkeep bills for as long as it lasts, escalating the
  longer it drags on. War costs are always paid first, before force-loading or region protection -
  which is what makes a war actually squeeze a team economically instead of just being background
  noise. `/lcce war list` shows every active conflict with its current cost; `/lcce war end <team>`
  makes peace.

- **Siege mode**: config-gated (`war.siegeModeEnabled`, off by default) and priced higher
  (`war.siegeCostMultiplier`). `/lcce war declare <team> siege` lets the attacker's team freely
  PvP and edit inside the defender's *regions* for as long as the siege lasts, on top of the usual
  economic pressure - a direct, aggressive option sitting alongside the default non-aggressive war.

- **Bounties** (`bounty/*`, `integration/ftb/BountyCommand`): `/lcce bounty place <player>
  <amount>` puts a personal reward (paid from your own wallet, not your team's balance) on
  someone's head; whoever kills them collects it automatically, straight into their wallet.
  `/lcce bounty list` shows every active bounty.

### Trading

- **Chunk marketplace** (`marketplace/*`, `integration/ftb/MarketCommand`): officers can list a
  state-owned chunk for sale (`/lcce market sell <price> [everyone|allies|team]`), and a private
  buyer who picks it up (`/lcce market buy`) can resell it again later - it's tracked as privately
  owned (the chunk stays claimed by the team in FTB Chunks, so it still counts toward their claim
  limit) while day-to-day access is entirely down to the private owner's own whitelist/blacklist
  (`/lcce market mode`, `/lcce market whitelist add/remove`) and optional custom label
  (`/lcce market label`), overriding region and team protection outright for that one chunk.
  Officers can always `/lcce market forcebuyback <price>` a privately-owned chunk. `/lcce market
  info` shows a chunk's current listing/ownership state, and `/lcce market cancel` pulls a listing
  before it's bought (state-owned by an officer, or privately-owned by its own owner). A configurable
  cooldown (`marketplace.listingCooldownTicks`) stops instant flipping. Officers can also
  **sell a whole territory as one package** (`CountryListing`) via the World Map's "Sell as
  Country..." right-click option (see below) - one price for a whole drag-selection of the
  team's own claims, bought in a single `/lcce market buy` transaction that transfers every
  chunk in it to the buyer at once (every chunk re-verified as still validly claimed right
  before any money moves, so a partial sale can't happen).

- **Player warps** (`warp/*`, `integration/ftb/WarpCommand`): `/lcce warp set <name> [public]`
  saves a named teleport point, `/lcce warp go <name>` returns to it, `/lcce warp del <name>` removes
  it, and `/lcce warp list` shows all of your own. A warp marked `public` can be visited by anyone via
  `/lcce warp visit <player> <name>` - handy for pointing people at a labeled marketplace shop chunk.

### Visibility

- **Web dashboard** (`dashboard/*`) - a small read-only HTTP server (off by default,
  `dashboard.enabled`; built on the JDK's own `com.sun.net.httpserver`, no extra dependency) showing
  every team's balance, every nation and its capital/members/balance, active bounties, and (once
  `Nation` support was added) which nation a team belongs to on its own detail page, all at `/`; a
  per-team detail page at `/team/<name>` with that team's regions/upkeep/wars/nation; and every
  marketplace listing - single-chunk and "sell as country" packages alike - at `/market`, with the
  mod's own banner in the page header. HTTP handlers never touch Minecraft state directly from a
  request thread - every page hands its work to `server.submit(...)` and blocks on the result,
  since the game's own state is only safe to read from the main server thread.

- **BlueMap integration** (`integration/bluemap/BlueMapIntegration`) - entirely server-side, since
  BlueMap renders and serves its own web map with no client mod needed. Every claimed chunk becomes
  a `ShapeMarker` outlining that chunk, colored per-team, labeled with the team name plus (if set)
  the region name, a private owner's custom label, or a marketplace price. Markers rebuild when
  BlueMap enables and every ~20 seconds afterward. Verified against BlueMap's published Javadoc
  (`BlueMapAPI.onEnable`/`getWorld(Object)`, `MarkerSet.builder()`, `ShapeMarker.builder().shape
  (Shape, float)`, `Shape.createRect(double,double,double,double)`,
  `Color(int,int,int,float)`) - `compileOnly`, guarded by `ModList.isLoaded("bluemap")`.

- **Xaero's Minimap integration** (`client/*`) - fully wired, not just a data layer:
  - **Rendering**: claimed chunks near the player are drawn as colored borders directly over
    Xaero's minimap. This uses FTB Chunks' own client-side sync
    (`ChunksUpdatedFromServerEvent`, the same data feed its own minimap uses - no extra
    networking needed for this part) to know what's claimed, and a genuine Xaero extension point
    found by disassembling the actual minimap jar with `javap`:
    `MinimapInterface#getOverMapRendererHandler().add(MinimapElementRenderer)`. Screen positioning
    is computed from the minimap's own zoom (`MinimapProcessor#getMinimapZoom()`) and on-screen
    transform; see the javadoc on `ClaimBorderRenderer` for the one assumption that couldn't be
    confirmed without a running client (a fixed north-up, non-rotating minimap).
  - **Claiming**: a keybind (unbound by default; bind it under Controls as "LCCE: Claim current
    chunk") sends a small network payload to the server, which claims the chunk the player is
    standing in through FTB Chunks' real `claimAsPlayer` API - so it goes through the exact same
    cost/event pipeline as claiming any other way.
  - The whole integration is `compileOnly` and guarded by `ModList.isLoaded("xaerominimap")` at
    runtime, and every class that touches `KeyMapping` or any `xaero.*` class lives under
    `dev.voidpulsaryt.lcce.client`, only ever reached from behind an
    `FMLEnvironment.dist.isClient()` check - so a dedicated server, or a client without Xaero's
    Minimap installed, never attempts to load any of it.

- **Xaero's World Map integration** (`client/xaero/LcceClaimHighlighter`, `WorldMapClaimMenu`,
  `WorldMapInfoPanel`, `client/xaero/mixin/*`) - claimed chunks are colored (filled + bordered, by
  the claiming team's own real FTB Teams color, with a region's own sub-border drawn one shade
  lighter inside its team's color when that chunk belongs to one) directly by Xaero's own tile
  renderer, and the World Map's native right-click menu gains "Claim/Unclaim/Force Load/Un-Force
  Load Selected", **"New Region from Selection..."**, and **"Sell as Country..."** entries that act
  on whatever chunks are drag-selected - the last two open a small vanilla `Screen`
  (`CreateRegionScreen`/`SellCountryScreen`) to collect a name, or a label/price/buyer-rule, since
  Xaero's own right-click actions can only run a fixed action, not their own input prompt. Hovering
  a claimed chunk also shows a small **info popup** in the map's top-left corner (`WorldMapInfoPanel`,
  Towny-style): the claiming team's name, its balance and nation (both only if the local player is
  actually a member of that team), this chunk's force-load status/price, and - when hovering a
  sub-region - that region's name and all five protection toggles. None of this data
  (`TeamBalance`/`Region`/`Nation`) is normally visible to a client at all (`RegionManager` and
  `NationManager` are plain server-side `SavedData`, and FTB Teams' own property system never syncs
  a value to any client on its own), so three small payloads
  (`TeamBalanceSyncPayload`/`RegionSyncPayload`/`NationSyncPayload`) push a team's current balance,
  regions, and nation membership to its own online members whenever one changes and once on login,
  cached client-side (`ClientTeamBalanceCache`/`ClientRegionCache`/`ClientNationCache`) for the info
  popup (and the nested-region-border coloring) to read. Both needed genuinely different research than the Minimap
  side: World Map has a purpose-built extension point for exactly this,
  `xaero.map.highlight.ChunkHighlighter` + `HighlighterRegistry`, and the menu has none at all - the
  registry is a short-lived object built and sealed (`.end()`) inside
  `WorldMapSession.init()` with no event or accessor reaching outside that method, and
  `GuiMap#getRightClickOptions()` builds and returns its option list with no override point either.
  Both were confirmed by decompiling the actual jar (with Vineflower, not just reading method
  signatures) after finding a real, shipped mod - `ftbxaerocompat` - that solves this exact problem
  for FTB Chunks, and matching its approach: a `@Redirect` Mixin on `WorldMapSession.init()`'s call
  to `HighlighterRegistry#end()` (registering our highlighter first), and an `@Inject` at
  `GuiMap#getRightClickOptions()`'s `RETURN` that appends to the about-to-be-returned option list
  directly (simpler than `ftbxaerocompat`'s own approach, which captures the list mid-method via a
  MixinExtras `@Local` - injecting at `RETURN` needs nothing beyond vanilla Mixin, since the method
  only has the one return statement). The right-click actions send FTB Chunks' own
  `RequestChunkChangePacket` - the same one its own client already uses for claim/unclaim/
  force-load - which calls into `ChunkTeamData#claim`/`unclaim`/`forceLoad` server-side, the same
  methods that fire the `ClaimedChunkEvent`s this mod's economy already listens to, so a chunk
  claimed this way is billed exactly like any other. Every `@Mixin` here uses a string `targets`
  (never a `SomeClass.class` literal) and the mixin config itself is marked `"required": false`, so
  a client without Xaero's World Map installed - an optional dependency for the rest of this mod -
  loads exactly as if these classes didn't exist. One dependency wrinkle: compiling against
  `GuiMap` needs its full superclass chain resolvable, which reaches into `xaero.lib.*` (XaeroLib) -
  not a separate download for this version, since NeoForge's jar-in-jar mechanism embeds it inside
  the World Map jar itself (`META-INF/jarjar/xaerolib-neoforge-1.21.1-1.7.3.jar`) and loads it
  automatically at runtime; it just needed extracting once as its own file for the compiler to see.
  This mod's own earlier attempt at this (a hand-rolled `ElementRenderer`, doing its own screen-space
  math) is gone - it shipped with two real bugs (reading `ElementRenderInfo.renderEntityPos` instead
  of `renderPos` for the map's view center, and `backgroundCoordinateScale` instead of `scale` for
  the zoom) that only surfaced once borders rendered visibly in the wrong place in a real client, and
  `ChunkHighlighter` needs none of that math at all since Xaero's own tile renderer positions it.

- **FTB Teams/Library sidebar buttons** (`assets/lcce/sidebar_buttons/*.json`) - three small icon
  buttons alongside FTB Teams' own "My Team"/"Team Lives" buttons in that sidebar, running
  `/lcce upkeep`, `/lcce market info`, and `/lcce bounty list` respectively. This is FTB Library's
  own public, data-driven extension point (`SidebarButtonManager`, a `SimpleJsonResourceReloadListener`
  scanning every namespace's `sidebar_buttons/*.json`) - no Mixin, no new Java code: a button's
  `"click"` array can be `"command:<text>"` (runs that as a normal server command, exactly like
  typing `/lcce upkeep` in chat) or `"custom:<id>"` (fires an architectury event other mods can
  listen to, for a real GUI screen instead - not used here, but the natural next step if a proper
  in-game LCCE screen gets built later).

- **Branding**: the mod's icon (`icon.png`, wired via `logoFile` in `neoforge.mods.toml`) and
  banner (`assets/lcce/banner.png`, served by and shown in the web dashboard's header) are both
  yours, copied in from `jars/icon.png` and `jars/banner.png`.

## License

MIT - see `LICENSE.md`. Release notes live in `CHANGELOG.md`; `CURSEFORGE_DESCRIPTION.md` and
`MODRINTH_DESCRIPTION.md` are the mod-page texts for those platforms.

## Known gaps

- **Nation territory isn't drawn on the map.** A team's own claim color/border reflects that team
  alone - there's no shared visual treatment (e.g. a common outline or tint) tying together every
  member team's claims within one nation on the World Map or BlueMap. `NationSyncPayload` gives a
  client its own team's nation name (used by the World Map info popup), but nothing yet cross-
  references *other* teams' claims against nation membership for rendering purposes.
- The screen-space math in `ClaimBorderRenderer` (Xaero's Minimap) has one confirmed-by-code-only
  assumption left: borders are drawn axis-aligned rather than rotated to match the minimap's own
  rotation setting, so they'll look slightly off on a minimap set to rotate with the player rather
  than staying north-up (see that class's own javadoc). The World Map side has no such gap -
  `ChunkHighlighter` positions everything itself, no screen-space math on this mod's side at all.

## Building

`./gradlew build` (or `.\gradlew.bat build` on Windows) - the wrapper is committed, so no local
Gradle install is required. Gradle's own toolchain resolver downloads a JDK 21 automatically the
first time (via the `foojay-resolver-convention` plugin in `settings.gradle`) if one isn't already
on your machine. `./gradlew runClient` / `runServer` work the same way for local testing.

This has been verified to actually compile and package (`./gradlew build` succeeds, producing
`build/libs/lcce-0.1.0.jar`) against the real NeoForge 1.21.1 toolchain, FTB Library/Teams/Chunks
from their own Maven, and both Xaero jars and Lightman's Currency as local/Modrinth dependencies -
not just checked with `javap` against the jars in isolation. Compiling it for real caught two real
bugs `javap`-only verification couldn't have: `net.neoforged.neoforge.event.tick.ClientTickEvent`
doesn't exist - the client tick event lives in `net.neoforged.neoforge.client.event.ClientTickEvent`
instead (server tick events really are under `event.tick`, just not the client one); and
`KeyMapping`'s constructor takes a raw `int` keycode, not an `InputConstants.Key` object, so the
claim keybind needed `InputConstants.UNKNOWN.getValue()` rather than the constant itself. Both are
fixed now. Every source file was also found to have a stray UTF-8 BOM (`settings.gradle` and
`gradle.properties` even had one, which made Gradle's own settings parser reject the project
outright with "Unexpected character: '?'") - stripped from all of them.

`jars/lightmanscurrency-1.21-2.3.0.5.jar`, `jars/xaerominimap-neoforge-1.21.1-26.5.0.jar`, and
`jars/xaeroworldmap-neoforge-1.21.1-1.46.0.jar` are referenced directly by `build.gradle` (Xaero's
both `compileOnly`, since they're optional). Keep them there, or update the paths/versions in
`build.gradle` and `gradle.properties` to match whatever you're actually running.

What compiling *doesn't* verify is runtime behavior - and sure enough, actually launching the game
surfaced a bug compiling couldn't: `neoforge.mods.toml` required `lightmanscurrency` version
`[2.2.0,)`, but Lightman's Currency versions its jars as `<minecraft version>-<mod version>` (the
real one here is `1.21-2.3.0.5`), and NeoForge's version-range comparator flattens both sides into
plain numeric tokens - so it compared `[1, 21, 2, 3, 0, 5]` against `[2, 2, 0]`, saw `1 < 2` on the
very first token, and rejected every real release of the mod outright with "Mod lcce requires
lightmanscurrency 2.2.0 or above." Fixed by giving the range the same `1.21-` prefix
(`[1.21-2.2.0,)`). FTB Teams/Chunks and both Xaero jars were checked the same way (extracting their
own `neoforge.mods.toml` and comparing `version =` against what this mod's toml requires of them)
and don't have the same problem - they version as plain numbers with no such prefix.
