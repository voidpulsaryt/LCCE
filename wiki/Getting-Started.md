# Getting Started

Pick the walkthrough for your claim backend.

## FTB Chunks path

### Step 1 — Get some money
Players need a Lightman's Currency bank account with a positive balance. Place an **ATM** or use your wallet to deposit money. As a server operator, you can give money directly:
```
/lcbank give players <playername> <amount>
```

### Step 2 — Claim your first chunks
Open the FTB Chunks map (`M` by default). Click a chunk to claim it — the claim price is deducted from your bank account automatically. If you don't have enough, the claim is rejected with a chat message. Unclaiming refunds part of the price (80% by default).

### Step 3 — Switch chunks to Land (optional)
By default all claimed chunks are **Build chunks**. To mark some as **Land chunks** (cheaper upkeep, restricted protections), hold **Alt** and click or drag over chunks in the FTB Chunks map. A confirmation appears in chat. Land chunks use separate block interact/edit protection settings, visible in FTB Teams config under *"Land Chunk Protection"*.

### Step 3.5 — Configure per-player permissions per chunk (optional)
**Shift + middle-click** a claimed chunk in the FTB Chunks map to open **Chunk Player Permissions**. There you can set an **All Players (Base)** default and add specific players by name or UUID, toggling Block edit / Block interact / Entity interact / PvP per player. This applies only to that exact chunk and doesn't change team-wide protection settings.

### Step 4 — Enable protections
Open FTB Teams settings → protection properties. Each protection shows its per-chunk price next to the toggle. If your balance can't cover the new upkeep, the change is blocked and reverted. Protections take effect immediately; the upkeep charge lands at the next billing period.

### Step 5 — Set up a party (for teams)
Create a party via FTB Teams. This mod automatically creates a linked bank account for the team — fund it through any ATM by selecting the team account from the account list. Only **owners and officers** can claim chunks, force-load them, or manage war for the team.

### Step 6 — Force-loading chunks
Enable force-load on any claimed chunk as usual in the FTB Chunks map. No upfront cost, but each force-loaded chunk adds a charge to the next upkeep bill.

### Step 7 — Monitor upkeep
When upkeep is charged, a chat message summarizes the payment — click **[See more]** for a full breakdown of which protections were active and what each cost. Owners and officers can also run:
```
/lcce upkeep_details
/lcce upkeep_priority
```
to check the latest breakdown or the protection dismantle order at any time.

### Step 8 — War (optional)
If enabled, open FTB Teams settings and click **War** (visible to owners/officers). The war screen shows declared-against-you wars, wars you've declared, and eligible targets. Click a team row to declare (queued to the next upkeep period) or to end an existing war — costs are shown in tooltips before you confirm.

> Declaring war increases your periodic upkeep. Make sure your team account can cover the new total before committing.

If your server has a war declaration window configured, new declarations only work inside it — the screen shows when it's closed and when it reopens. A team can opt out of war entirely at any time with `/lcce war peaceful on`.

### Step 9 — Bounties (optional)
```
/lcce bounty player <playername> <amount>
/lcce bounty team <teamname> <amount>
/lcce bounty list
```

### Step 10 — Marketplace (optional)
Stand in a claimed chunk you own and run `/lcce market sell <price_copper>` to list it. Anyone can browse (`/lcce market browse`) and buy (standing in that chunk, `/lcce market buy`). `/lcce market cancel` delists your own chunk.

### Step 11 — Player Warps (optional)
Stand somewhere inside a chunk your team has claimed and run `/lcce warp set home` to create a warp there. Run `/lcce warp` to open the GUI — it lists your warps alongside every public warp other players have shared, with buttons to teleport, toggle public/private, or delete. To share a warp, `/lcce warp public home true`; others can then reach it with `/lcce warp tpto <yourname> home` or from their own GUI.

### Step 12 — Web dashboard (optional)
If your server has `webEnabled` and `webDashboardEnabled` on, run `/lcce web login` to get a one-time code, then enter it on the dashboard's login page to manage your land, protections, and wars from a browser.

---

## Open Parties and Claims path

Claiming, unclaiming, and force-load upkeep work through OP&C's own claim tools — this mod bills them automatically in the background. There's no dedicated claim-price overlay on the OP&C side, so keep an eye on your bank balance directly.

To split chunks into cheaper "land" billing versus full "build" billing, stand in the chunk and run:
```
/lcce opc_chunktype land
/lcce opc_chunktype build
/lcce opc_chunktype status
```
Unlike the FTB path, this takes effect immediately rather than at the next upkeep period.

If upkeep can't be paid, your force-load setting is disabled until the balance is restored — claims themselves are left alone. Wars, bounties, the marketplace, player warps, and the Pioneer Bonus aren't available on this backend.

You can still check the web leaderboard (`/lcce leaderboard`, or the `webEnabled` HTTP page) — the login-gated dashboard is FTB-only.
