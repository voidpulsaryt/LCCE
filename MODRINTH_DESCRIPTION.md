![Lightman's Currency: Claim Economy](https://raw.githubusercontent.com/voidpulsaryt/LCCE/main/src/main/resources/assets/lcce/banner.png)

**LCCE** turns FTB Chunks land claiming into a real, player-driven economy powered by [Lightman's Currency](https://modrinth.com/mod/lightmans-currency). Claiming costs coins, protection costs upkeep, teams can form nations and go to war, and players can buy and sell land.

**Environment:** required on both client and server. **Minecraft:** 1.21.1 · **Loader:** NeoForge

Source and issues: [GitHub](https://github.com/voidpulsaryt/LCCE)

## Features

- **Paid claims** - claim cost comes out of your own wallet and scales with how much your team already owns. Unclaiming refunds part of it.
- **Team bank and upkeep** - a shared team balance pays recurring upkeep for wars, force-loaded chunks, and region protection. Unaffordable protections lapse and return automatically once paid.
- **Regions** - carve your claims into named regions with their own PvP, explosion, mob-griefing, interact, and edit rules.
- **Nations** - ally teams under a capital with a pooled balance and optional member tax.
- **Wars, sieges, bounties** - wars cost both sides and escalate over time; place coin bounties on players.
- **Marketplace** - list chunks or whole territories for sale, with buyer restrictions and private ownership.
- **Player warps, per-player chunk trust, sidebar shortcuts.**
- **Follows your currency format** - amounts display using your Lightman's Currency coin display setting, including numerical `$100` style.

## Optional integrations

- **Xaero's World Map** - team-colored claims, region borders, drag-select claim/region/sell menu, hover info popup.
- **Xaero's Minimap** - claim borders and a claim keybind.
- **BlueMap** - claim, region, and listing markers on the web map.
- **Web dashboard** - read-only status pages (disabled by default).

## Requirements

| Mod | Version |
|---|---|
| [Lightman's Currency](https://modrinth.com/mod/lightmans-currency) | `1.21-2.2.0` or newer (built against `1.21-2.3.0.5`) |
| [FTB Chunks](https://modrinth.com/mod/ftb-chunks) + [FTB Teams](https://modrinth.com/mod/ftb-teams) + [FTB Library](https://modrinth.com/mod/ftb-library) | `2101.1.x` |
| NeoForge | `21.1.x` |

## Getting started

Run `/lcce balance` to see your team's balance, `/lcce upkeep` for what you owe, and `/lcce region list`, `/lcce market info`, `/lcce nation info` to explore the rest. Everything is under `/lcce`.

Released under the [MIT License](https://github.com/voidpulsaryt/LCCE/blob/main/LICENSE.md).
