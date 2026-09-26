package dev.voidpulsaryt.lcce.config;

import dev.voidpulsaryt.lcce.marketplace.BuyerRule;
import dev.voidpulsaryt.lcce.region.ProtectionLineItem;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Server-synced config. Claim economy, upkeep, wars, and the marketplace are all wired up to real
 * logic; Xaero's map integration doesn't need its own config since it just visualizes state owned
 * by the sections below.
 */
public final class LCCEConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue FREE_CLAIM_CHUNKS;
    public static final ModConfigSpec.LongValue CLAIM_BASE_COST;
    public static final ModConfigSpec.DoubleValue CLAIM_COST_GROWTH;
    public static final ModConfigSpec.DoubleValue UNCLAIM_REFUND_PERCENT;
    public static final ModConfigSpec.LongValue CLAIM_PIONEER_BONUS;

    public static final ModConfigSpec.LongValue UPKEEP_PERIOD_TICKS;
    public static final ModConfigSpec.LongValue UPKEEP_MOB_GRIEFING_PRICE;
    public static final ModConfigSpec.LongValue UPKEEP_EXPLOSIONS_PRICE;
    public static final ModConfigSpec.LongValue UPKEEP_PVP_PRICE;
    public static final ModConfigSpec.LongValue UPKEEP_PRICE_PER_TIER_LEVEL;
    public static final ModConfigSpec.LongValue UPKEEP_FORCE_LOAD_PRICE;
    public static final ModConfigSpec.LongValue UPKEEP_PER_MEMBER_TAX;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> UPKEEP_DISMANTLE_PRIORITY;

    public static final ModConfigSpec.LongValue WAR_DECLARATION_FEE;
    public static final ModConfigSpec.LongValue WAR_BASE_UPKEEP_COST;
    public static final ModConfigSpec.DoubleValue WAR_UPKEEP_GROWTH_PER_PERIOD;
    public static final ModConfigSpec.DoubleValue WAR_ATTACKER_COST_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue WAR_SIEGE_MODE_ENABLED;
    public static final ModConfigSpec.DoubleValue WAR_SIEGE_COST_MULTIPLIER;

    public static final ModConfigSpec.LongValue MARKET_LISTING_COOLDOWN_TICKS;
    public static final ModConfigSpec.EnumValue<BuyerRule> MARKET_DEFAULT_BUYER_RULE;

    public static final ModConfigSpec.LongValue BOUNTY_MINIMUM;

    public static final ModConfigSpec.BooleanValue DASHBOARD_ENABLED;
    public static final ModConfigSpec.IntValue DASHBOARD_PORT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Claim economy - the price of claiming/unclaiming chunks with FTB Chunks")
                .push("claimEconomy");

        FREE_CLAIM_CHUNKS = builder
                .comment("Number of chunks a team (or solo player) may claim for free before the cost curve kicks in.")
                .defineInRange("freeClaimChunks", 25, 0, Integer.MAX_VALUE);

        CLAIM_BASE_COST = builder
                .comment(
                        "Cost, in the smallest unit of your lowest-value coin, of the first chunk claimed",
                        "past the free allowance."
                )
                .defineInRange("claimBaseCost", 10L, 0L, Long.MAX_VALUE);

        CLAIM_COST_GROWTH = builder
                .comment(
                        "Multiplier applied per chunk claimed past the free allowance.",
                        "cost(n) = claimBaseCost * claimCostGrowth ^ (n - freeClaimChunks), where n is the",
                        "0-indexed count of chunks already owned (free chunks included) before this claim.",
                        "1.0 = flat pricing after the free allowance; >1.0 = sprawl gets progressively pricier."
                )
                .defineInRange("claimCostGrowth", 1.02, 1.0, 10.0);

        UNCLAIM_REFUND_PERCENT = builder
                .comment("Fraction of the original claim cost refunded when a chunk is unclaimed. 0 = no refund, 1 = full refund.")
                .defineInRange("unclaimRefundPercent", 0.5, 0.0, 1.0);

        CLAIM_PIONEER_BONUS = builder
                .comment(
                        "One-time currency bonus paid into a team's balance the moment they claim their very",
                        "first chunk ever. 0 disables it."
                )
                .defineInRange("pioneerBonus", 100L, 0L, Long.MAX_VALUE);

        builder.pop();

        builder.comment(
                "Upkeep - protection isn't a one-time purchase. Every region's active protections are",
                "re-billed on a recurring timer, straight out of the owning team's balance."
        ).push("upkeep");

        UPKEEP_PERIOD_TICKS = builder
                .comment("How often (in ticks; 20 ticks = 1 second) upkeep is billed. Default is one Minecraft day.")
                .defineInRange("periodTicks", 24000L, 1L, Long.MAX_VALUE);

        UPKEEP_MOB_GRIEFING_PRICE = builder
                .comment("Price per period for a region's mob-griefing protection, while it's turned on.")
                .defineInRange("mobGriefingProtectionPrice", 5L, 0L, Long.MAX_VALUE);

        UPKEEP_EXPLOSIONS_PRICE = builder
                .comment("Price per period for a region's explosion protection, while it's turned on.")
                .defineInRange("explosionProtectionPrice", 5L, 0L, Long.MAX_VALUE);

        UPKEEP_PVP_PRICE = builder
                .comment("Price per period for a region's PvP protection, while it's turned on.")
                .defineInRange("pvpProtectionPrice", 5L, 0L, Long.MAX_VALUE);

        UPKEEP_PRICE_PER_TIER_LEVEL = builder
                .comment(
                        "Price per period, per permission-tier level above Public, for a region's interact and",
                        "edit tiers. Allies = 1x this price, Team = 2x, Private = 3x (each tier costs more than the",
                        "one below it since it locks out more people)."
                )
                .defineInRange("pricePerPermissionTierLevel", 3L, 0L, Long.MAX_VALUE);

        UPKEEP_FORCE_LOAD_PRICE = builder
                .comment(
                        "Price per period, per force-loaded chunk. Unlike region protection, a force-loaded",
                        "chunk that becomes unaffordable can't be 'dismantled' in place - the least-recently",
                        "force-loaded chunks just get un-force-loaded until what's left fits the budget. 0 disables it."
                )
                .defineInRange("forceLoadPricePerChunk", 10L, 0L, Long.MAX_VALUE);

        UPKEEP_PER_MEMBER_TAX = builder
                .comment(
                        "Towny-style resident tax: each ONLINE team member is charged this much, straight from",
                        "their own wallet into the team's balance, every upkeep period - an automatic top-up so",
                        "a team doesn't have to rely on someone remembering to /lcce deposit. Charged before war",
                        "and region upkeep are billed, so it directly funds them. 0 disables it (the default) -",
                        "upkeep stays purely deposit-funded unless a server opts into this."
                )
                .defineInRange("perMemberTaxPerPeriod", 0L, 0L, Long.MAX_VALUE);

        UPKEEP_DISMANTLE_PRIORITY = builder
                .comment(
                        "Order in which protections get dismantled when a team can't afford the full upkeep bill,",
                        "listed from first-to-go (lowest priority) to last-to-go (highest priority). Valid entries: " +
                                "mob_griefing, explosions, pvp, interact_tier, edit_tier."
                )
                .defineList("dismantlePriorityOrder",
                        () -> List.of("interact_tier", "edit_tier", "pvp", "explosions", "mob_griefing"),
                        entry -> entry instanceof String s && ProtectionLineItem.fromConfigKey(s).isPresent());

        builder.pop();

        builder.comment(
                "Wars - declaring war adds a recurring cost to BOTH sides' upkeep bills for as long as it",
                "lasts, escalating the longer it drags on. The idea is to economically occupy the enemy",
                "until their protection breaks, not to end the war outright - so there's no config here",
                "for a maximum duration or an automatic winner."
        ).push("war");

        WAR_DECLARATION_FEE = builder
                .comment("One-time cost to declare a war, paid immediately by the attacker.")
                .defineInRange("declarationFee", 100L, 0L, Long.MAX_VALUE);

        WAR_BASE_UPKEEP_COST = builder
                .comment("Base recurring upkeep cost added to the defender's bill each period a war is active.")
                .defineInRange("baseUpkeepCost", 20L, 0L, Long.MAX_VALUE);

        WAR_UPKEEP_GROWTH_PER_PERIOD = builder
                .comment(
                        "Multiplier applied to the war's upkeep cost per elapsed upkeep period since it was",
                        "declared: cost(periodsElapsed) = baseUpkeepCost * upkeepGrowthPerPeriod ^ periodsElapsed.",
                        "1.0 = flat cost for the whole war; >1.0 = the longer it drags on, the more it costs both sides."
                )
                .defineInRange("upkeepGrowthPerPeriod", 1.05, 1.0, 10.0);

        WAR_ATTACKER_COST_MULTIPLIER = builder
                .comment(
                        "The defender is always billed the full formula above. The attacker is billed that same",
                        "amount multiplied by this - e.g. 0.5 means the attacker pays half of what the war is",
                        "currently costing the defender."
                )
                .defineInRange("attackerCostMultiplier", 0.5, 0.0, 10.0);

        WAR_SIEGE_MODE_ENABLED = builder
                .comment(
                        "Allows declaring a war as a 'siege' (/lcce war declare <team> siege) instead of the",
                        "default economic-pressure war. While a siege is active, the attacking team can freely",
                        "PvP and edit inside the defender's regions, bypassing their protection entirely - a",
                        "direct, aggressive option on top of the default non-aggressive economic war. Off by default."
                )
                .define("siegeModeEnabled", false);

        WAR_SIEGE_COST_MULTIPLIER = builder
                .comment("Siege wars cost this much more than a normal war (applied on top of the normal formula, both sides).")
                .defineInRange("siegeCostMultiplier", 2.0, 1.0, 100.0);

        builder.pop();

        builder.comment(
                "Marketplace - selling claimed chunks (state-owned by a team, or privately owned after a",
                "resale) to individual players."
        ).push("marketplace");

        MARKET_LISTING_COOLDOWN_TICKS = builder
                .comment(
                        "How long (in ticks) a chunk must have been held - since it was claimed, or since it was",
                        "last bought - before it can be listed for sale again. Stops instant flipping."
                )
                .defineInRange("listingCooldownTicks", 72000L, 0L, Long.MAX_VALUE);

        MARKET_DEFAULT_BUYER_RULE = builder
                .comment(
                        "Default buyer rule for a new listing when the seller doesn't specify one: everyone,",
                        "allies_only, or team_only (relative to the team that originally claimed the chunk)."
                )
                .defineEnum("defaultBuyerRule", BuyerRule.TEAM);

        builder.pop();

        builder.comment("Bounties - placing a currency reward on a player's head, claimed by whoever kills them.")
                .push("bounty");

        BOUNTY_MINIMUM = builder
                .comment("Smallest bounty that can be placed in one go (multiple placements on the same player stack).")
                .defineInRange("minimumBounty", 10L, 1L, Long.MAX_VALUE);

        builder.pop();

        builder.comment(
                "Web dashboard - a small read-only HTTP server showing team balances, regions, wars,",
                "and marketplace listings. Off by default since it opens a network port."
        ).push("dashboard");

        DASHBOARD_ENABLED = builder
                .comment("Whether to start the dashboard's HTTP server at all.")
                .define("enabled", false);

        DASHBOARD_PORT = builder
                .comment("Port the dashboard listens on, if enabled.")
                .defineInRange("port", 8123, 1, 65535);

        builder.pop();

        SPEC = builder.build();
    }

    private LCCEConfig() {}
}
