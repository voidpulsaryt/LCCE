package dev.voidpulsar.lc_claim_economy.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class LcClaimEconomyConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    private LcClaimEconomyConfig() {
    }

    public static final class Server {
        public final ModConfigSpec.LongValue claimPrice;
        public final ModConfigSpec.IntValue freeChunks;
        public final ModConfigSpec.IntValue landChunkGroupSize;
        public final ModConfigSpec.DoubleValue unclaimRefundRatio;
        public final ModConfigSpec.LongValue forceLoadUpkeepPrice;
        public final ModConfigSpec.IntValue upkeepPeriodMinutes;
        public final ModConfigSpec.BooleanValue chargeUpkeepWhileEmpty;
        public final ModConfigSpec.LongValue mobGriefProtectionPrice;
        public final ModConfigSpec.LongValue explosionProtectionPrice;
        public final ModConfigSpec.LongValue pvpDisablePrice;
        public final ModConfigSpec.LongValue blockInteractProtectionPrice;
        public final ModConfigSpec.LongValue blockEditProtectionPrice;
        public final ModConfigSpec.LongValue entityInteractProtectionPrice;
        public final ModConfigSpec.DoubleValue warCostMultiplier;
        public final ModConfigSpec.DoubleValue warOutgoingCostMultiplier;
        public final ModConfigSpec.BooleanValue warEnabled;
        public final ModConfigSpec.BooleanValue warDeclarationWindowEnabled;
        public final ModConfigSpec.ConfigValue<String> warDeclarationWindowStartDay;
        public final ModConfigSpec.IntValue warDeclarationWindowStartHourUtc;
        public final ModConfigSpec.ConfigValue<String> warDeclarationWindowEndDay;
        public final ModConfigSpec.IntValue warDeclarationWindowEndHourUtc;
        public final ModConfigSpec.BooleanValue siegeModeEnabled;
        public final ModConfigSpec.IntValue siegeModeGraceHours;
        public final ModConfigSpec.IntValue warMinClaimedChunks;
        public final ModConfigSpec.ConfigValue<List<? extends String>> protectionDismantleOrderBuild;
        public final ModConfigSpec.ConfigValue<List<? extends String>> protectionDismantleOrderLand;
        public final ModConfigSpec.BooleanValue debugTestTeamCommands;
        public final ModConfigSpec.BooleanValue disableCoinMint;
        public final ModConfigSpec.BooleanValue webEnabled;
        public final ModConfigSpec.IntValue webPort;
        public final ModConfigSpec.ConfigValue<String> webBindAddress;
        public final ModConfigSpec.IntValue webLeaderboardSize;
        public final ModConfigSpec.BooleanValue webDashboardEnabled;
        public final ModConfigSpec.IntValue webSessionMinutes;
        public final ModConfigSpec.IntValue webLoginCodeMinutes;
        public final ModConfigSpec.ConfigValue<String> webSiteName;
        public final ModConfigSpec.ConfigValue<String> webAccentColor;
        public final ModConfigSpec.ConfigValue<String> webLogoUrl;
        public final ModConfigSpec.ConfigValue<String> webCustomCss;
        public final ModConfigSpec.LongValue pioneerBonusAmount;
        public final ModConfigSpec.BooleanValue warpsEnabled;
        public final ModConfigSpec.IntValue maxWarpsPerPlayer;
        public final ModConfigSpec.LongValue warpCreateCostCopper;
        public final ModConfigSpec.LongValue warpTeleportCostCopper;
        public final ModConfigSpec.BooleanValue warpRequireOwnClaim;
        public final ModConfigSpec.IntValue warpCooldownSeconds;
        public final ModConfigSpec.ConfigValue<List<? extends String>> warpWorldDisplayNames;

        Server(ModConfigSpec.Builder builder) {
            builder.comment("Lightman's Currency: FTB Claim Economy server configuration").push("general");

            claimPrice = builder
                    .comment("Cost in copper units (main coin chain) to claim one chunk. Default: 10000 copper = 1 Diamond coin.")
                    .defineInRange("claimPrice", 10_000L, 0L, Long.MAX_VALUE);

            freeChunks = builder
                    .comment("The first N claimed chunks per team or player are free to claim and exempt from protection upkeep")
                    .defineInRange("freeChunks", 0, 0, Integer.MAX_VALUE);

            landChunkGroupSize = builder
                    .comment("Land chunks (state territory) pay the protection price once per group of this many chunks; the billable land chunk count is rounded up to the next full group (minimum 1 group when any land chunk is billable). Build chunks always pay per chunk. A value of 1 makes land cost the same as build.")
                    .defineInRange("landChunkGroupSize", 5, 1, Integer.MAX_VALUE);

            unclaimRefundRatio = builder
                    .comment("Fraction of the claim price refunded when unclaiming a chunk (0 = none, 1 = full refund, 0.8 = 80%)")
                    .defineInRange("unclaimRefundRatio", 0.8D, 0.0D, 1.0D);

            forceLoadUpkeepPrice = builder
                    .comment("Upkeep cost in copper units per force-loaded chunk per upkeep period (force-loading itself is free). Default: 1 Netherite coin (100000 copper).")
                    .defineInRange("forceLoadUpkeepPrice", 100_000L, 0L, Long.MAX_VALUE);

            upkeepPeriodMinutes = builder
                    .comment("How often upkeep is charged, in real-time minutes")
                    .defineInRange("upkeepPeriodMinutes", 60, 1, 10080);

            chargeUpkeepWhileEmpty = builder
                    .comment("If false (default), the upkeep countdown pauses whenever no players are online, so a period "
                            + "only elapses across time players actually spent on the server. If true, the countdown keeps "
                            + "running off server uptime alone regardless of whether anyone is connected, so teams can be "
                            + "billed - and have protections suspended for non-payment - while every player is offline.")
                    .define("chargeUpkeepWhileEmpty", false);

            disableCoinMint = builder
                    .comment("If true, prevents use of Lightman's Currency's Coin Mint block server-wide, "
                            + "so players can't mint their own money out of raw materials and bypass the claim economy. "
                            + "Lightman's Currency also has its own mint/melt recipe restrictions in its own config; "
                            + "this option is a full, simple on/off switch independent of that.")
                    .define("disableCoinMint", false);

            builder.pop();
            builder.comment("Per-protection base prices added to upkeep calculation (b in c = b * n)").push("protectionPrices");

            mobGriefProtectionPrice = builder
                    .comment("Price when mob griefing protection is enabled (Allow Mob Griefing = false). Default: 80 copper.")
                    .defineInRange("mobGriefProtectionPrice", 80L, 0L, Long.MAX_VALUE);

            explosionProtectionPrice = builder
                    .comment("Price when explosion protection is enabled (Allow Explosion Damage = false). Default: 70 copper (second cheapest).")
                    .defineInRange("explosionProtectionPrice", 70L, 0L, Long.MAX_VALUE);

            pvpDisablePrice = builder
                    .comment("Price when PvP is disabled (Allow PvP Combat = false). Default: 50 copper (cheapest protection).")
                    .defineInRange("pvpDisablePrice", 50L, 0L, Long.MAX_VALUE);

            blockInteractProtectionPrice = builder
                    .comment("Price when block interact mode is not public. Default: 100 copper.")
                    .defineInRange("blockInteractProtectionPrice", 100L, 0L, Long.MAX_VALUE);

            blockEditProtectionPrice = builder
                    .comment("Price when block edit mode is not public. Default: 100 copper.")
                    .defineInRange("blockEditProtectionPrice", 100L, 0L, Long.MAX_VALUE);

            entityInteractProtectionPrice = builder
                    .comment("Price when entity interact mode is not public. Default: 100 copper.")
                    .defineInRange("entityInteractProtectionPrice", 100L, 0L, Long.MAX_VALUE);

            builder.pop();
            builder.comment("War declarations between claim teams (teams or solo players with claimed chunks)").push("war");

            warEnabled = builder
                    .comment("Enable the war system. When false, war costs are ignored, war actions are blocked, and the war button is hidden on clients.")
                    .define("warEnabled", true);

            warOutgoingCostMultiplier = builder
                    .comment("Flat multiplier x for outgoing war cost. Declaring war on a team costs x * their base upkeep per period, regardless of how many wars you have declared.")
                    .defineInRange("warOutgoingCostMultiplier", 2.0D, 0.0D, 100.0D);

            warCostMultiplier = builder
                    .comment("Incoming war exponent l. With base upkeep b and k incoming wars, the incoming surcharge is b * sum(l^n for n=0..k-1). First incoming war uses l^0 = 1.")
                    .defineInRange("warCostMultiplier", 1.2D, 1.0D, 100.0D);

            warDeclarationWindowEnabled = builder
                    .comment("If true, new wars can only be declared during a recurring weekly window (e.g. weekends only). "
                            + "Does not affect ending an existing war, cancelling a pending declare, or the automatic "
                            + "suspend/restore of outgoing wars a team can't currently afford - those are always allowed.")
                    .define("warDeclarationWindowEnabled", false);

            warDeclarationWindowStartDay = builder
                    .comment("Day the war declaration window opens, UTC. One of: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.")
                    .define("warDeclarationWindowStartDay", "FRIDAY", obj -> obj instanceof String s && isValidDay(s));

            warDeclarationWindowStartHourUtc = builder
                    .comment("Hour (0-23, UTC) on warDeclarationWindowStartDay that the window opens.")
                    .defineInRange("warDeclarationWindowStartHourUtc", 22, 0, 23);

            warDeclarationWindowEndDay = builder
                    .comment("Day the war declaration window closes, UTC. Same values as warDeclarationWindowStartDay. "
                            + "Can be earlier in the week than the start day, in which case the window wraps around "
                            + "the end of the week (e.g. start SUNDAY, end FRIDAY covers Sun-Fri, closed Fri-Sun).")
                    .define("warDeclarationWindowEndDay", "SUNDAY", obj -> obj instanceof String s && isValidDay(s));

            warDeclarationWindowEndHourUtc = builder
                    .comment("Hour (0-23, UTC) on warDeclarationWindowEndDay that the window closes.")
                    .defineInRange("warDeclarationWindowEndHourUtc", 22, 0, 23);

            siegeModeEnabled = builder
                    .comment("If true, a team's purchased explosion protection is bypassed entirely - for ALL explosions, "
                            + "not just ones caused by the war opponent - on any chunk belonging to a team that has been "
                            + "at war (incoming or outgoing) for longer than siegeModeGraceHours below. Intended for packs "
                            + "with large-scale explosive weapons (missiles, artillery, nukes) where war should mean a "
                            + "claim can actually be damaged rather than staying economically-costly-but-physically-safe. "
                            + "This only removes this mod's own explosion protection; it has no effect on any defense/"
                            + "interception system the weapon mod itself provides - a team with the right in-game defenses "
                            + "can still stop an attack even while sieged. PvP and block-edit protection are unaffected by "
                            + "this setting; a besieged team's build stays otherwise protected from direct griefing. Off "
                            + "by default.")
                    .define("siegeModeEnabled", false);

            siegeModeGraceHours = builder
                    .comment("Grace period, in hours, between a team first entering a state of war and siegeModeEnabled "
                            + "actually starting to bypass their explosion protection. Gives the defender warning to "
                            + "prepare (build up defenses, move valuables, etc.) rather than becoming vulnerable the "
                            + "instant a war is declared. Measured from the team's own first war of the current streak - "
                            + "it does not reset for each additional war while already at war, and clears once they have "
                            + "no active wars at all. 0 means no grace period (vulnerable immediately). Has no effect "
                            + "unless siegeModeEnabled is true.")
                    .defineInRange("siegeModeGraceHours", 12, 0, 720);

            warMinClaimedChunks = builder
                    .comment("A team (solo or party) must have more than this many claimed chunks before it can declare "
                            + "war or be targeted by one. Protects small/new teams - especially solo players on larger "
                            + "servers - from being drawn into war before they have much at stake. 0 disables this check "
                            + "(default, matches prior behavior). See also warEnabled's sibling per-team escape hatch: "
                            + "any team can independently opt out of war entirely via /lcce war peaceful, regardless of size.")
                    .defineInRange("warMinClaimedChunks", 0, 0, Integer.MAX_VALUE);

            builder.pop();
            builder.comment("Order in which protections are disabled when upkeep cannot be paid (first = dropped first). Use FTB property id paths without namespace.").push("protectionDismantle");

            protectionDismantleOrderLand = builder
                    .comment("Land-chunk protections dismantled first when upkeep fails")
                    .defineList(
                            "protectionDismantleOrderLand",
                            List.of(
                                    "land_block_edit_mode",
                                    "land_block_interact_mode"
                            ),
                            obj -> obj instanceof String
                    );

            protectionDismantleOrderBuild = builder
                    .comment("Build-chunk protections dismantled after all land protections are off")
                    .defineList(
                            "protectionDismantleOrderBuild",
                            List.of(
                                    "entity_interact_mode",
                                    "block_edit_mode",
                                    "block_interact_mode",
                                    "allow_mob_griefing",
                                    "allow_explosions",
                                    "allow_pvp"
                            ),
                            obj -> obj instanceof String
                    );

            builder.pop();
            builder.comment("Debug-only features for development and testing").push("debug");

            debugTestTeamCommands = builder
                    .comment("Allow /lcce seed_test_teams, clear_test_teams, and count_test_teams. Keep disabled on production servers.")
                    .define("debugTestTeamCommands", false);

            builder.pop();
            builder.comment("Optional built-in web server for a live leaderboard/info page. Off by default.").push("web");

            webEnabled = builder
                    .comment("If true, starts a small built-in HTTP server serving a live leaderboard and server info page. "
                            + "That leaderboard page itself is always read-only and unauthenticated. Anyone who can reach the "
                            + "configured port/address can view it, including player names, chunk counts, and account balances - "
                            + "keep the port firewalled/off if that's sensitive on your server. See webDashboardEnabled below "
                            + "for the separate, login-gated player dashboard.")
                    .define("webEnabled", false);

            webPort = builder
                    .comment("Port the built-in web server listens on, if webEnabled is true.")
                    .defineInRange("webPort", 8123, 1, 65535);

            webBindAddress = builder
                    .comment("Address the built-in web server binds to. \"0.0.0.0\" listens on all network interfaces "
                            + "(reachable from other machines); \"127.0.0.1\" restricts it to the local machine only "
                            + "(e.g. to sit behind your own reverse proxy).")
                    .define("webBindAddress", "0.0.0.0");

            webLeaderboardSize = builder
                    .comment("Maximum number of entries shown per leaderboard (balance, claimed chunks) on the web page.")
                    .defineInRange("webLeaderboardSize", 10, 1, 100);

            webDashboardEnabled = builder
                    .comment("If true (and webEnabled is also true), adds a login-gated player dashboard at /dashboard where "
                            + "team members can view and manage their own team's land, protections, and wars from a browser. "
                            + "FTB Chunks/Teams only - has no effect on the Open Parties and Claims backend. Players log in with "
                            + "a short one-time code generated in-game via /lcce web login (no passwords are stored "
                            + "or transmitted). This server has no built-in HTTPS - if it's reachable from outside your LAN, put "
                            + "a reverse proxy with TLS in front of it, since session cookies otherwise travel in plain HTTP.")
                    .define("webDashboardEnabled", false);

            webSessionMinutes = builder
                    .comment("How long a dashboard login session stays valid after the player logs in, in minutes.")
                    .defineInRange("webSessionMinutes", 720, 1, 43_200);

            webLoginCodeMinutes = builder
                    .comment("How long a one-time login code from /lcce web login stays valid before it expires unused, in minutes.")
                    .defineInRange("webLoginCodeMinutes", 5, 1, 60);

            builder.pop();
            builder.comment("Cosmetic customization for the web leaderboard and dashboard pages - no functional effect.").push("webTheme");

            webSiteName = builder
                    .comment("Site name shown in the page title and header of both the leaderboard and dashboard pages.")
                    .define("webSiteName", "Claim Economy");

            webAccentColor = builder
                    .comment("Accent color (CSS hex, e.g. \"#4f8cff\") used for headings, highlights, and buttons on both web pages.")
                    .define("webAccentColor", "#88C0D0");

            webLogoUrl = builder
                    .comment("Optional logo image URL shown next to the site name in the header. Leave blank for no logo. "
                            + "Loaded directly by each visitor's browser - can be any publicly reachable image URL.")
                    .define("webLogoUrl", "");

            webCustomCss = builder
                    .comment("Optional raw CSS appended after the built-in stylesheet on both web pages, for customization "
                            + "beyond the accent color above (fonts, layout tweaks, etc.). Empty by default.")
                    .define("webCustomCss", "");

            builder.pop();
            builder.comment("Server flavor: one-time bonus for claiming first (FTB Chunks only)").push("flavor");

            pioneerBonusAmount = builder
                    .comment("One-time reward, in copper units, paid to whichever team claims the very first chunk on this server. Default: 5 diamond coins (50000 copper). Set to 0 to disable.")
                    .defineInRange("pioneerBonusAmount", 50_000L, 0L, Long.MAX_VALUE);

            builder.pop();
            builder.comment("Player-owned warps: personal teleport points players can set inside their own team's claimed land and optionally share with others (/lcce warp).").push("warps");

            warpsEnabled = builder
                    .comment("Enable the /lcce warp system entirely.")
                    .define("warpsEnabled", true);

            maxWarpsPerPlayer = builder
                    .comment("Maximum number of warps a single player can own at once.")
                    .defineInRange("maxWarpsPerPlayer", 3, 0, 1000);

            warpCreateCostCopper = builder
                    .comment("Cost in copper units to create a new warp. Moving an existing warp to a new location is free. Default: 5000 copper.")
                    .defineInRange("warpCreateCostCopper", 5_000L, 0L, Long.MAX_VALUE);

            warpTeleportCostCopper = builder
                    .comment("Toll in copper units charged when teleporting to another player's public warp, paid directly to that warp's owner. Free (0) by default. Teleporting to your own warps never costs a toll.")
                    .defineInRange("warpTeleportCostCopper", 0L, 0L, Long.MAX_VALUE);

            warpRequireOwnClaim = builder
                    .comment("If true, a warp can only be created on a chunk claimed by the creator's own team. If that chunk is later unclaimed, any warps sitting on it are deleted.")
                    .define("warpRequireOwnClaim", true);

            warpCooldownSeconds = builder
                    .comment("Minimum time in seconds a player must wait between two warp teleports (own or others').")
                    .defineInRange("warpCooldownSeconds", 5, 0, 3600);

            warpWorldDisplayNames = builder
                    .comment("Optional friendly display names for dimensions shown in the warp GUI/list, as \"namespace:path=Display Name\" entries "
                            + "(e.g. \"minecraft:the_nether=The Nether\"). A dimension not listed here falls back to its id's path, prettified "
                            + "(underscores replaced with spaces, first letter capitalized).")
                    .defineList("warpWorldDisplayNames", List.of(), obj -> obj instanceof String s && s.contains("="));

            builder.pop();
        }

        private static boolean isValidDay(String value) {
            try {
                java.time.DayOfWeek.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
