# FAQ

**The mod won't load / crashes on startup with a version error.**
This mod checks the exact version of Lightman's Currency and the minimum version of your claim backend (see [Installation](Installation)). The startup error names exactly which mod and version is wrong — install the matching version rather than just "latest."

**Can I run this with both FTB Chunks and Open Parties and Claims installed?**
Yes, but only the FTB integration will be active for the economy features (wars, siege mode, bounties, marketplace, player warps, Pioneer Bonus, per-chunk permissions). If you want the OP&C integration specifically, don't install the FTB Chunks/Teams/Library trio alongside it.

**Why was my protection disabled?**
Your team's bank account (FTB) or your personal/party account (OP&C) didn't have enough balance to pay upkeep. Top up the account — protections restore automatically at the next billing cycle (FTB), or force-load restores automatically once you next have sufficient funds (OP&C).

**Can members claim chunks?**
No. On FTB Teams, only owners and officers can claim, unclaim, or force-load chunks on behalf of the team. On OP&C, this follows OP&C's own party permission model.

**Can I grant one non-member access to only one chunk?**
Yes, on FTB Chunks — open that chunk's player permissions screen (Shift + middle-click on the map) and add the player, or set an All Players base rule for that single chunk. Not available on the OP&C backend.

**What happens when my party disbands?**
All claimed chunks are unclaimed (with the configured refund), the remaining team account balance is distributed to members, and the team account is deleted. FTB Teams only.

**Can I disable the war system?**
Yes — set `warEnabled = false` server-wide, or have an individual team opt out permanently with `/lcce war peaceful on` (blocked while that team has an active war). The war button disappears from the client UI automatically when the system is off. Wars aren't available on OP&C regardless of these settings.

**What's siege mode?**
An optional (`siegeModeEnabled`) rule that bypasses a besieged team's explosion protection once they've been at war longer than `siegeModeGraceHours`, so a declared war can actually threaten a claim's builds rather than staying purely economic. It only affects this mod's own explosion protection, and never touches PvP or block-edit protection. See [Features](Features#war-system-ftb-teams-only).

**What's the war declaration window for?**
It lets a server restrict *new* war declarations to a recurring weekly time window (e.g. weekends only), so players aren't attacked while at school or work. Ending wars, cancelling a pending declare, and the automatic afford/unafford suspension cycle are never restricted by it — only starting a fresh attack is.

**What's the Pioneer Bonus?**
A one-time reward paid to whoever claims the very first chunk ever claimed on the server. FTB Chunks only. Set `pioneerBonusAmount` to `0` to disable the payout while keeping the milestone advancement.

**Is the web leaderboard safe to leave on?**
It's read-only, but it has no authentication at all — anyone who can reach the configured port sees player names, chunk counts, and balances. Leave `webEnabled` off, or bind it to `127.0.0.1` and put it behind your own reverse proxy, unless you're fine with it being public.

**Is the web dashboard (`/dashboard`) safe to leave on?**
It's login-gated (one-time codes via `/lcce web login`, session cookies), unlike the leaderboard, but the built-in server has no HTTPS. If it's reachable outside your LAN, put a TLS-terminating reverse proxy in front of it so session cookies don't travel in plain HTTP.

**Where is the config file?**
`world/serverconfig/lc_claim_economy-server.toml` on a dedicated server, or `saves/<world>/serverconfig/lc_claim_economy-server.toml` in single-player.

**The commands used to be under `/lc_claim_economy` — did they move?**
Yes, as of 4.4.0 the command root is `/lcce`. The mod id, resource/network namespace, and config filename are unchanged — only the typed command changed.
