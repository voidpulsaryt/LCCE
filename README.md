<p align="center">
  <img src="docs/images/banner.png" alt="Lightman's Currency: Claim Economy" width="700">
</p>

<p align="center">
<img src="https://img.shields.io/badge/Minecraft-1.21.1-4f8a3d" alt="Minecraft 1.21.1">
<img src="https://img.shields.io/badge/Loader-NeoForge-e05d2c" alt="NeoForge">
<img src="https://img.shields.io/badge/version-4.5.0-c98a1f" alt="Version 4.5.0">
<img src="https://img.shields.io/badge/license-All%20Rights%20Reserved-lightgrey" alt="License: All Rights Reserved">
</p>

**Claim Economy** turns land claiming into a real, live economy. Claiming a chunk costs money, protecting it costs ongoing upkeep, and — if you're running the full backend — teams can go to war, place bounties on each other, trade claimed land on a player marketplace, and race for a one-time "first claim" bonus. All of it is backed by [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency) bank accounts, so the money is the same money your players already bank, spend, and trade with everywhere else on your server.

📖 **[Wiki](../../wiki)** · 🐛 **[Report an issue](../../issues)** · 📋 **[Changelog](CHANGELOG.md)**

---

## Two claim backends, your choice

| | [FTB Chunks](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-neoforge) + [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-neoforge) | [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims) |
|---|---|---|
| Paid claims, upkeep, force-load billing | ✅ | ✅ |
| Build / Land chunk price split | ✅ | ✅ (via command) |
| Team bank accounts, mirrored roles | ✅ | — |
| Per-chunk player permissions | ✅ | — |
| Wars, siege mode, bounties, marketplace | ✅ | — |
| Player Warps | ✅ | — |
| Pioneer bonus, claim milestones | ✅ | — |
| Web leaderboard / dashboard | ✅ | leaderboard only |

If both are installed, the FTB integration takes over. Full breakdown on the [Features](../../wiki/Features) wiki page.

## Highlights

- **Paid, refundable claiming** — configurable price per chunk, partial refund on unclaim, a free allowance before either kicks in.
- **Build vs. Land chunks** — territory (Land) bills upkeep once per group of chunks instead of per chunk, at the cost of fewer available protections.
- **Protection upkeep** — PvP, explosions, mob griefing, and block/entity interact protection are all paid, periodic, and independently priced. Run out of money and protections drop themselves in a configurable order — then restore automatically once you're paid up again.
- **Wars** *(FTB only)* — declare war to raise a rival's upkeep; optional siege mode strips explosion protection from long-besieged teams; a configurable weekly declaration window and a minimum-chunk-count/peaceful opt-out protect small teams from being dragged in.
- **Bounties** *(FTB only)* — put escrowed money on a player's or team's head, collectible in PvP.
- **Player marketplace** *(FTB only)* — list, browse, and buy claimed chunks from other players, with an atomic unclaim → claim → payment handoff so a failed transfer never strands a chunk or charges a buyer.
- **Player Warps** *(FTB only)* — personal teleport points set inside your own claimed land, managed and browsed from an in-game GUI, shareable publicly for an optional toll, with aliases and server-configurable world display names.
- **Bank Dashboard** — an in-game balance/upkeep screen, openable anywhere with **B** or `/lcce dashboard`, working identically on both backends. Transactions themselves (upkeep, claims, market, warps) show up in Lightman's Currency's own account history, alongside everything else in your account.
- **Optional web leaderboard + login-gated dashboard** — a built-in HTTP server for a public read-only leaderboard, plus (FTB only) a passwordless-login player dashboard for managing land, protections, and wars from a browser.
- **Pioneer Bonus & claim milestones** *(FTB only)* — a one-time reward for the server's first-ever claim, plus hidden advancements at claim/war milestones for FTB Quests hooks — no FTB Quests dependency required.

See the [Features](../../wiki/Features) wiki page for the full detail on every system.

## Requirements

| Mod | Version |
|---|---|
| [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency) | exactly `1.21-2.3.0.5` |
| **Either:** FTB Chunks + FTB Teams + FTB Library | `2101.1.21` / `2101.1.10` / `2101.1.34` or newer |
| **Or:** Open Parties and Claims | `0.27.5` or newer |
| NeoForge | `21.1.234` or newer, Minecraft `1.21.1` |

Lightman's Currency is checked as an **exact** version match (this mod hooks a large, sensitive API surface of it); the claim backends are checked as a **minimum** version. If a requirement is missing or too old, the mod logs exactly what's wrong and refuses to start rather than misbehaving silently. Full detail on the [Installation](../../wiki/Installation) wiki page.

## Quick install

1. Drop the mod jar, Lightman's Currency, and your chosen claim backend into `mods/` — **on both server and every client**.
2. Launch once to generate `world/serverconfig/lc_claim_economy-server.toml`.
3. Tune claim price, upkeep period, protection prices, and (optionally) the web server — see [Configuration](../../wiki/Configuration).
4. In-game, start with `/lcce upkeep_details` and `/lcce leaderboard` to confirm it's running, then follow the [Getting Started](../../wiki/Getting-Started) walkthrough.

## Commands

All commands live under `/lcce`. Full reference with permissions and backend availability on the [Commands](../../wiki/Commands) wiki page.

```
/lcce upkeep_details
/lcce upkeep_priority
/lcce leaderboard [land|wealth]
/lcce bounty player|team|list <...>
/lcce market sell|cancel|buy|browse <...>
/lcce warp [set|delete|public|alias|list|tp|tpto] <...>
/lcce dashboard
/lcce war peaceful [on|off]
/lcce opc_chunktype land|build|status
/lcce web login
```

## Documentation

Everything beyond this overview lives on the **[wiki](../../wiki)**:

- [Installation](../../wiki/Installation) — requirements, version pinning, first launch
- [Getting Started](../../wiki/Getting-Started) — step-by-step tutorial for both backends
- [Features](../../wiki/Features) — every system in detail
- [Configuration](../../wiki/Configuration) — every config key, default, and what it does
- [Commands](../../wiki/Commands) — full command reference
- [FAQ](../../wiki/FAQ)

## Media

<img src="docs/images/icon.png" alt="Claim Economy mod icon" width="96" align="left">

The square mod icon (512×512) doubles as the CurseForge/Modrinth listing icon — [`docs/images/icon.png`](docs/images/icon.png). The banner above lives at [`docs/images/banner.png`](docs/images/banner.png).

<br clear="left">

## License

All rights reserved — see [LICENSE.md](LICENSE.md). This repository is source-available for transparency and issue tracking, not under an open-source license.

## Credits

Built by the [Voidpulsar](https://github.com/voidpulsarteam) team, on top of [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency) and either [FTB Chunks](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-neoforge)/[FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-neoforge) or [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims).
