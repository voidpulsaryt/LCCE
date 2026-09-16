# Commands

All commands live under `/lcce` (renamed from `/lc_claim_economy` in 4.4.0).

| Command | Backend | Who can use it | Description |
|---|---|---|---|
| `/lcce upkeep_details` | FTB | Solo: anyone. Party: owners/officers (or ranked members with purchase permission) | Show the time until the next upkeep charge and the latest upkeep cost breakdown |
| `/lcce opc_upkeep_details` | OP&C | Anyone | Show the time until your next upkeep charge |
| `/lcce upkeep_priority` | FTB | Same as above | Show the protection dismantle order and current active costs |
| `/lcce bounty player <target> <amount>` | FTB | Anyone | Place an escrowed bounty on a player |
| `/lcce bounty team <target> <amount>` | FTB | Anyone | Place an escrowed bounty on a team |
| `/lcce bounty list` | FTB | Anyone | List all active bounties |
| `/lcce market sell <price_copper>` | FTB | Anyone (acts on the chunk you're standing in) | List your current chunk on the marketplace |
| `/lcce market cancel` | FTB | Anyone | Delist your current chunk |
| `/lcce market buy` | FTB | Anyone | Buy the listing on the chunk you're standing in |
| `/lcce market browse` | FTB | Anyone | List all active listings, cheapest first |
| `/lcce war peaceful` | FTB | Solo: anyone. Party: owners/officers | Show your team's current peaceful-mode status |
| `/lcce war peaceful on\|off` | FTB | Same as above | Opt your team in/out of the war system entirely. Can't enable while a war is active |
| `/lcce warp` | FTB | Anyone | Open the warp GUI (your warps + everyone's public warps) |
| `/lcce warp set <name>` | FTB | Anyone | Create a warp at your position, or move an existing one of yours |
| `/lcce warp delete <name>` | FTB | Anyone (own warps only) | Delete one of your warps |
| `/lcce warp public <name> <true\|false>` | FTB | Anyone (own warps only) | Share/unshare a warp with other players |
| `/lcce warp alias add\|remove <name> <alias>` | FTB | Anyone (own warps only) | Add/remove an extra name a warp can be found under |
| `/lcce warp list` | FTB | Anyone | Clickable chat list of your warps and public warps |
| `/lcce warp tp <name>` | FTB | Anyone | Teleport to one of your own warps |
| `/lcce warp tpto <player> <name>` | FTB | Anyone | Teleport to another player's public warp |
| `/lcce leaderboard` / `leaderboard land` | Either | Anyone | Rank teams by claimed-chunk count |
| `/lcce leaderboard wealth` | Either | Anyone | Rank teams by bank balance |
| `/lcce web login` | FTB | Any online player | Issue a one-time login code for the web dashboard (`webEnabled` + `webDashboardEnabled` required) |
| `/lcce quest_deposit <amount_copper>` | FTB | Server (permission level 2) | Intended for an FTB Quests Command Reward run as the player, to pay a quest reward into their bank account |
| `/lcce clear_wars` | FTB | Server (permission level 2) | Clears all active/pending war state for every tracked team |
| `/lcce opc_chunktype land\|build\|status` | OP&C | Claim/party owner or admin | Set or check a chunk's land/build billing type. Takes effect immediately (unlike the FTB path) |
| `/lcce seed_test_teams` / `clear_test_teams` / `count_test_teams` | FTB | Server (permission level 2), requires `debugTestTeamCommands=true` | Debug tools for seeding/removing fake test teams |

War declarations and endings are **not** a command — they're driven entirely from the in-game **War screen** in FTB Teams settings, or from the web dashboard's Wars tab if `webDashboardEnabled` is on.
