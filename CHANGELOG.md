# Changelog

All notable changes to this mod are documented here. This is a from-scratch rewrite: version
numbering restarts at 0.1.0 and does not continue the earlier codebase's release history.

## [0.1.0] - Unreleased

### Added

- **Paid claims**: claiming an FTB Chunks chunk charges the claiming player's own Lightman's
  Currency wallet, on an exponential curve scaled by how many chunks the team already owns, with a
  free-chunk allowance, a percentage refund on unclaim, and a one-time pioneer bonus. Works the same
  via keybind, `/ftbchunks claim`, or the World Map claim menu.
- **Team bank accounts** (`/lcce balance`, `/lcce deposit`, `/lcce admin set`) funding shared costs.
- **Upkeep**: per-period billing for war costs, force-loaded chunks, and region protection, with
  priority-ordered dismantling when a team can't pay and automatic restoration once it can; optional
  per-member resident tax; `/lcce upkeep` itemized breakdown.
- **Regions**: named sub-areas of a team's claims with their own mob-griefing/explosion/PvP toggles
  and Public/Allies/Team/Private interact and edit tiers.
- **Per-player chunk trust** (`/lcce trust`).
- **Nations**: teams allied under a capital with a pooled balance, optional per-member tax, and
  add/kick/leave/disband/deposit commands. Membership is synced to clients.
- **Wars and siege mode** (`/lcce war`), **bounties** (`/lcce bounty`).
- **Chunk marketplace**, private chunk ownership with access lists, force buy-back, and "sell as
  country" multi-chunk packages (`/lcce market`).
- **Player warps** (`/lcce warp`).
- **Transaction logging** through Lightman's Currency's own notification system.
- **Currency display**: every amount shown to players follows the server's configured Lightman's
  Currency coin display format (coin icons, wordy, or numerical such as `$100`).
- **Web dashboard** (optional, off by default): teams, nations, bounties, regions, wars, marketplace.
- **BlueMap integration** (optional): claim, region, and listing markers.
- **Xaero's Minimap integration** (optional): claim borders and a claim keybind.
- **Xaero's World Map integration** (optional): team-colored claim overlays with nested region
  borders, drag-select right-click actions (claim, unclaim, force load, new region, sell as country),
  and a hover info popup.
- **FTB Teams sidebar buttons** for upkeep, market info, and bounties.
