![Lightman's Currency: Claim Economy](https://raw.githubusercontent.com/voidpulsarteam/LCCE/refs/heads/main/docs/images/banner.png)

**Lightman's Currency: Claim Economy** turns land claiming into a real economy, powered by [Lightman's Currency](https://modrinth.com/mod/lightmans-currency) bank accounts. Claiming costs money, protection costs upkeep, and — on the full backend — teams can go to war, place bounties, trade claimed land, and race for a one-time "first claim" bonus.

**Environment:** required on both client and server. **Minecraft:** 1.21.1 · **Loader:** NeoForge

Also available on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/lcce). Full docs on the [wiki](https://github.com/voidpulsarteam/LCCE/wiki).

## Played live on Wattz

This mod runs on **Wattz**, a public server built around this claim economy:

- **Modpack:** [modrinth.com/modpack/wattz](https://modrinth.com/modpack/wattz)
- **Server IP:** `play.wattzpol.net`
- **Live web map:** [map.wattzpol.net](https://map.wattzpol.net/)

---

## Two claim backends

Pick whichever claim mod you already use — if both are installed, FTB takes over.

| | FTB Chunks + Teams + Library | Open Parties and Claims |
|---|---|---|
| Paid claims, upkeep, force-load billing | ✅ | ✅ |
| Build/Land chunk split | ✅ | ✅ (via command) |
| Team bank accounts, per-chunk permissions | ✅ | — |
| Wars, siege mode, bounties, marketplace | ✅ | — |
| Player Warps | ✅ | — |
| Pioneer Bonus, web dashboard | ✅ | — |

## Requirements

| Mod | Version |
|---|---|
| [Lightman's Currency](https://modrinth.com/mod/lightmans-currency) | **exactly** `1.21-2.3.0.5` |
| [FTB Chunks](https://modrinth.com/mod/ftb-chunks) + [FTB Teams](https://modrinth.com/mod/ftb-teams) + [FTB Library](https://modrinth.com/mod/ftb-library) | `2101.1.21` / `2101.1.10` / `2101.1.34`+ (or OP&C below) |
| [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) | `0.27.5`+ (or FTB above) |
| NeoForge | `21.1.234`+ |

Lightman's Currency must match exactly; the claim backends just need to meet the minimum. Wrong or missing version → the mod logs exactly what's wrong and refuses to load.

## Features

- **Paid, refundable claiming** — configurable price, partial refund on unclaim, a free allowance before either kicks in.
- **Build vs. Land chunks** — territory (Land) bills upkeep per group of chunks instead of per chunk, for fewer available protections.
- **Protection upkeep** — PvP, explosions, mob griefing, and block/entity interact protection are paid, periodic, and independently priced, with an auto-drop/auto-restore cycle if you can't pay.
- **Wars** *(FTB)* — declare war to raise a rival's upkeep, with optional siege mode, a scheduled declaration window, and safeguards keeping small teams out.
- **Bounties** *(FTB)* — escrow money on a player's or team's head, collectible in PvP.
- **Player marketplace** *(FTB)* — list, browse, and buy claimed chunks from other players.
- **Player Warps** *(FTB)* — personal teleport points set inside your own claimed land, shareable with a GUI, aliases, per-warp tolls, and claim-tied auto-cleanup.
- **Bank Dashboard** — an in-game balance/upkeep screen with transaction history, on either backend, opened anywhere with **B**.
- **Optional web leaderboard + dashboard** — a public read-only leaderboard, plus (FTB) a login-gated browser dashboard for managing land, protections, and wars.
- **Pioneer Bonus & claim milestones** *(FTB)* — a one-time reward for the server's first claim, plus advancement milestones usable as FTB Quests hooks with no FTB Quests dependency.

Full detail on every system: [Features wiki page](https://github.com/voidpulsarteam/LCCE/wiki/Features).

## Installation

1. Place the mod jar, Lightman's Currency, and your chosen claim backend in `mods/` — **on both server and every client**.
2. Launch once to generate `world/serverconfig/lc_claim_economy-server.toml`.
3. Tune claim price, upkeep period, and protection prices to taste — see the [Configuration wiki page](https://github.com/voidpulsarteam/LCCE/wiki/Configuration).
4. In-game, run `/lcce upkeep_details` and `/lcce leaderboard` to confirm it's running, then follow the [Getting Started guide](https://github.com/voidpulsarteam/LCCE/wiki/Getting-Started).

## Commands

All commands live under `/lcce` — full reference with permissions on the [Commands wiki page](https://github.com/voidpulsarteam/LCCE/wiki/Commands).

```
/lcce upkeep_details
/lcce leaderboard [land|wealth]
/lcce bounty player|team|list <...>
/lcce market sell|cancel|buy|browse
/lcce warp [set|delete|public|alias|list|tp|tpto] <...>
/lcce war peaceful [on|off]
/lcce opc_chunktype land|build|status
/lcce web login
```

## FAQ

**Won't load / version error?** The error names exactly which companion mod and version is wrong — see Requirements above.

**Both FTB Chunks and OP&C installed?** Only the FTB integration runs the economy features (wars, bounties, marketplace, player warps, Pioneer Bonus, per-chunk permissions).

**Is the web leaderboard safe to leave on?** It's read-only but unauthenticated — anyone reaching the port sees player names, balances, and chunk counts. Keep it firewalled/local-only unless you want it public.

More questions answered on the [FAQ wiki page](https://github.com/voidpulsarteam/LCCE/wiki/FAQ). Found a bug? [Open an issue](https://github.com/voidpulsarteam/LCCE/issues).
