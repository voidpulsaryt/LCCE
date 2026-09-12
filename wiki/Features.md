# Features

## Paid chunk claiming

Claiming a chunk draws funds from the player's or team's bank account. The price is server-configurable (`claimPrice`). Unclaiming refunds a configurable percentage (`unclaimRefundRatio`). The first *N* chunks per team or player can be made free (`freeChunks`), also exempt from protection upkeep.

- **FTB Chunks:** an unaffordable claim is blocked outright before it happens.
- **OP&C:** claims can't be vetoed ahead of time by OP&C's API, so an unaffordable claim is allowed for an instant and then automatically unclaimed.

## Build and Land chunks

Every claimed chunk is either a **Build chunk** or a **Land chunk**:

| | Build chunk | Land chunk |
|---|---|---|
| Purpose | Base, builds, infrastructure | Territory, borders, open land |
| Available protections | Mob griefing, explosions, PvP, block interact/edit, entity interact | Block interact and block edit only |
| Upkeep billing | Per chunk | Per group of `landChunkGroupSize` chunks (cheaper for large territories) |
| Switch type (FTB Chunks) | Alt + click/drag in the FTB Chunks map | Same |
| Switch type (OP&C) | `/lcce opc_chunktype build` while standing in the chunk | `/lcce opc_chunktype land` |

On FTB Chunks, switching a chunk's type is queued to take effect at the next upkeep period (to prevent cost-dodging). On OP&C, `opc_chunktype` takes effect immediately.

## Protection upkeep

Protecting your land isn't free — protections are paid for periodically (`upkeepPeriodMinutes`, default every hour):

```
cost = base_protection_price × number_of_billable_chunks
```

Each active protection (mob griefing, explosions, PvP, block interact, block edit, entity interact) adds its own per-chunk price to the bill, independently configurable.

**If the account can't pay:**
- **FTB Chunks:** protections are stripped one by one in a configurable priority order (`protectionDismantleOrderLand` / `protectionDismantleOrderBuild`). Once the balance is restored, they're re-enabled automatically in reverse order.
- **OP&C:** there's no per-protection toggle to flip, so instead the owner's **force-load** is disabled until upkeep is paid again — claims and protection settings themselves are left alone.
- A protection change that would make upkeep unaffordable is blocked at the UI on FTB — the toggle doesn't apply and an alert appears.

Changes to protections and force-loads are queued to the next upkeep period on FTB Chunks to prevent mid-period exploits; OP&C changes apply immediately.

## Per-chunk player permissions *(FTB Chunks only)*

Each claimed chunk can define **player-specific access**, independent of FTB Teams' own role permissions and at no extra cost:

- Block edit
- Block interact
- Entity interact
- PvP/attack

Open it from the FTB Chunks map with **Shift + middle-click** on a claimed chunk. Set an **All Players (Base)** profile for chunk-wide defaults, then add specific players as overrides:

```
effective_allow = all_players_base + specific_player_flags
```

Only team owners/officers can edit these permissions.

## Team bank accounts *(FTB Teams only)*

Creating an FTB party automatically creates a linked Lightman's Currency bank account that mirrors the team hierarchy at all times:

- Team owner → account owner
- Officers → account admins
- Members → account members

The account can't be deleted while the party is alive. When the party disbands, the remaining balance and any chunk refunds are distributed to each member's personal account, and claimed chunks are unclaimed. Joining a party dissolves your personal claims and refunds them to your personal account.

On OP&C, party-owned claims are billed against a resolved party account the same way, but there's no equivalent auto-provisioned/mirrored account object — solo and party ownership bill directly through OP&C's own owner/party model.

## War system *(FTB Teams only)*

Teams with claimed chunks can declare war on each other. War raises upkeep:

- **Incoming wars** scale upkeep exponentially: for base upkeep `b` and `k` incoming wars, the surcharge is `b × Σ(l^n)` for `n = 0..k-1`, where `l` is `warCostMultiplier` (default `1.2`).
- **Outgoing wars** cost a flat `x × target's base upkeep` per war, where `x` is `warOutgoingCostMultiplier` (default `2.0`), regardless of how many wars you already have.

Declarations and endings are queued to the next upkeep period. If a team can't afford full upkeep, its outgoing wars are frozen until the balance is restored. The whole system can be disabled server-wide (`warEnabled`).

The **War screen**, built into FTB Teams' Team Settings, shows active/pending wars, upkeep cost breakdowns, and target vulnerability indicators.

### Siege mode

When `siegeModeEnabled` is on, a team that's been at war (incoming or outgoing) longer than `siegeModeGraceHours` (default 12) has its **explosion protection bypassed entirely** — for all explosions, not just the war opponent's. Intended for packs with large-scale explosive weapons, so a declared war can mean a claim is actually damageable instead of staying economically costly but physically untouchable. The grace period is measured from a team's first war of its current streak and doesn't reset for additional wars while already at war; it clears once the team has no active wars. PvP and block-edit protection are unaffected.

### War declaration window

`warDeclarationWindowEnabled` restricts *new* war declarations to a recurring weekly UTC window (`warDeclarationWindowStartDay`/`Hour` → `warDeclarationWindowEndDay`/`Hour`), which can wrap past the end of the week. Ending an existing war, cancelling your own pending declaration, and the automatic suspend/restore cycle for unaffordable wars are unaffected — only starting a fresh attack is time-boxed. Declaring outside the window returns a message naming the next open window.

### War participation safeguards

Two independent ways to keep small or unwilling teams out of war entirely:

- `warMinClaimedChunks` — a team needs more claimed chunks than this to declare or be targeted by war (`0` disables the check).
- `/lcce war peaceful on|off` — any team's own permanent opt-out, regardless of size. Blocked from enabling while the team has an active war (end it first), so it can't be used as a mid-siege escape hatch.

## Bounty system *(FTB Teams only)*

Put escrowed money on a rival's head, payable to whoever kills them in PvP:

```
/lcce bounty player <target> <amount>
/lcce bounty team <target> <amount>
/lcce bounty list
```

A **player bounty** pays out to whoever lands the killing blow in any PvP kill. A **team bounty** only pays out if the killer's team is actually at war with the victim's team — an unrelated or friendly kill can't collect it. Multiple bounties on the same target stack. You can't bounty yourself or your own team.

## Player marketplace *(FTB Chunks only)*

List, browse, and buy claimed chunks from other players. All commands are position-based — the chunk you're standing in is always the target, so there are no coordinates to get wrong:

```
/lcce market sell <price_copper>
/lcce market cancel
/lcce market buy
/lcce market browse
```

Ownership transfer is sequenced safely: the seller's chunk is unclaimed and the buyer's claim confirmed successful *before* any money moves, with an automatic rollback (reclaiming for the seller) if the buyer-side claim fails — a failed transfer never charges the buyer or strands the chunk unclaimed. The buyer receives the chunk with default protection/force-load state; land/build classification and per-player chunk permissions don't carry over from the seller.

## Player Warps *(FTB Teams only)*

Personal, player-owned teleport points, tied into the claim economy rather than bolted on as a standalone system:

```
/lcce warp
/lcce warp set <name>
/lcce warp delete <name>
/lcce warp public <name> <true|false>
/lcce warp alias add|remove <name> <alias>
/lcce warp list
/lcce warp tp <name>
/lcce warp tpto <player> <name>
```

A bare `/lcce warp` opens an in-game GUI (built with FTB Library's own screen toolkit, matching this mod's other custom screens) listing your warps and every public warp other players have shared, with buttons to create, delete, toggle public/private, and teleport — clicking TP teleports you and closes the menu. `/lcce warp list` gives the same information as a clickable chat list.

- **Tied to claims:** when `warpRequireOwnClaim` (default on) is enabled, a warp can only be set inside a chunk your own team currently has claimed. Unclaim that chunk later and any warps on it are deleted automatically — a warp can never point into land nobody protects anymore.
- **Limits and costs:** `maxWarpsPerPlayer` caps how many warps one player can own; `warpCreateCostCopper` charges for creating a new one (moving an existing warp is free); a per-teleport `warpCooldownSeconds` prevents spam.
- **Sharing:** a warp is private until you make it public (`/lcce warp public <name> true`), after which anyone can teleport to it — optionally for a toll (`warpTeleportCostCopper`) paid directly to you.
- **Aliases:** give a warp one or more extra names it can also be found/teleported to by (`/lcce warp alias add <name> <alias>`), unique among your own warps.
- **World naming:** `warpWorldDisplayNames` lets a server owner give dimensions friendly display names (e.g. "The Nether" instead of a raw id) shown throughout the warp GUI and list.

## Pioneer Bonus *(FTB Chunks only)*

The very first chunk ever claimed on a server triggers a one-time reward: a configurable currency deposit (`pioneerBonusAmount`, default 5 Diamond coins) straight to the claiming player's account, plus a hidden advancement usable as an FTB Quests (or any advancement-aware) task trigger. Set the amount to `0` to disable the payout while keeping the advancement.

## Claim milestones

Hidden, toast-free advancements fire automatically at 5 / 10 / 25 / 50 / 100 / 250 / 500 total chunks claimed by a team, plus at declaring or ending a war. Designed to be used as FTB Quests task triggers **without requiring FTB Quests to be installed**.

## Force-load upkeep

Enabling force-load on a chunk is free, but each force-loaded chunk adds a periodic charge (`forceLoadUpkeepPrice`).

## Bank Dashboard

A standalone bank/upkeep dashboard screen, openable anywhere with `/lcce dashboard` or a keybind (default **B**). Unlike most of this mod's UI, it's built without any FTB-library dependency, so it works identically on either backend.

Every claim-economy money movement (upkeep charges, claim purchases, unclaim refunds, the pioneer bonus, market sales/purchases, and player warp costs/tolls) is recorded directly into the same account's Lightman's Currency transaction/notification log used for interest, transfers, and salary payments - there's no separate claim-economy history screen to check.

## Web leaderboard and dashboard

A small, optional built-in HTTP server (`webEnabled`, off by default) serves:

- **A public, read-only leaderboard page** — top balances and top claimed-chunk counts, refreshing client-side every ~10 seconds. No login, no write access, no authentication at all — anyone who can reach the port sees player names, balances, and chunk counts, so keep it firewalled or bound to `127.0.0.1` unless you want it public.
- **A login-gated player dashboard** at `/dashboard` (`webDashboardEnabled`, FTB Chunks/Teams only), where team members can view and manage their own team's land, protections, and wars from a browser:
  - **Passwordless login** — `/lcce web login` generates a short-lived, single-use code entered on the dashboard's login page to start a session. Nothing reusable is ever stored or transmitted.
  - **Land** — view all claimed chunks (position, dimension, land/build, force-load and pending status) with unclaim and force-load/unload actions.
  - **Protections** — view and toggle all six build protections with live pricing and "pending until next period" state, routed through the same pricing/queueing path as the in-game GUI.
  - **Wars** — incoming/outgoing/available lists, declare/end actions, and the declaration-window banner when one applies and is currently closed.
  - **Team** — roster with rank/online status (view-only), plus the peaceful-mode toggle.
  - Actions that need to act as the player in FTB's own APIs (unclaim, force-load, wars) require that player to be online; protection and peaceful-mode toggles work regardless.
  - This server has no built-in HTTPS — if it's reachable from outside your LAN, put a reverse proxy with TLS in front of it, since session cookies otherwise travel in plain HTTP.
- **Economy Activity panel** on the dashboard — server-wide aggregate counters (total upkeep charged/missed, claim spend, unclaim refunds, market volume), without exposing any individual account's history over the unauthenticated leaderboard.
- **Cosmetic theming** (`webTheme` config section) — site name, accent color, logo URL, and raw custom CSS, applied at runtime and shared by both pages, so a server can reskin without touching mod files.

## Coin Mint restriction *(optional)*

`disableCoinMint` lets a server block Lightman's Currency's Coin Mint block entirely, closing off a way to mint currency from raw materials and bypass the claim economy.

## Tax Collector placement restriction *(FTB Teams only)*

Placing Lightman's Currency's Tax Collector block inside a claimed chunk requires the placer's team to own that chunk and hold sufficient purchase rank.

## In-game UI extensions

- Claim prices and your current balance shown directly in the FTB Chunks map UI.
- Protection prices shown next to each toggle in the FTB Teams config screen.
- Pending-state indicators for queued changes (e.g. "→ Ally pending").
- Chunk Player Permissions screen from the claim map, per chunk.
- Claim cost breakdown popup with current price/balance and a bulk-claim cost projection.
