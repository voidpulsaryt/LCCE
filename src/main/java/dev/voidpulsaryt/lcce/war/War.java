package dev.voidpulsaryt.lcce.war;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * An active state of war between two teams. Wars aren't "dismantled" for non-payment the way
 * region protections are - the whole point is that their upkeep cost is an unavoidable tax on
 * both sides for as long as the war lasts, escalating the longer it drags on (see
 * {@link WarPricing}), until it ends in peace or one side's protection collapses under the strain.
 * <p>
 * A {@link #isSiege()} war is the more aggressive declaration option (config-gated,
 * {@code war.siegeModeEnabled}): on top of the usual economic pressure, the attacker's team can
 * freely PvP and edit inside the defender's regions for as long as the siege lasts - see
 * {@code RegionProtectionListener}.
 */
public final class War {

    private final UUID id;
    private final UUID attackerTeamId;
    private final UUID defenderTeamId;
    private final long declaredAtGameTime;
    private final boolean siege;

    public War(UUID id, UUID attackerTeamId, UUID defenderTeamId, long declaredAtGameTime, boolean siege) {
        this.id = id;
        this.attackerTeamId = attackerTeamId;
        this.defenderTeamId = defenderTeamId;
        this.declaredAtGameTime = declaredAtGameTime;
        this.siege = siege;
    }

    public static War declare(UUID attackerTeamId, UUID defenderTeamId, long now, boolean siege) {
        return new War(UUID.randomUUID(), attackerTeamId, defenderTeamId, now, siege);
    }

    public UUID id() { return id; }
    public UUID attackerTeamId() { return attackerTeamId; }
    public UUID defenderTeamId() { return defenderTeamId; }
    public long declaredAtGameTime() { return declaredAtGameTime; }
    public boolean isSiege() { return siege; }

    public boolean involves(UUID teamId) {
        return attackerTeamId.equals(teamId) || defenderTeamId.equals(teamId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Attacker", attackerTeamId);
        tag.putUUID("Defender", defenderTeamId);
        tag.putLong("DeclaredAt", declaredAtGameTime);
        tag.putBoolean("Siege", siege);
        return tag;
    }

    public static War load(CompoundTag tag) {
        return new War(
                tag.getUUID("Id"),
                tag.getUUID("Attacker"),
                tag.getUUID("Defender"),
                tag.getLong("DeclaredAt"),
                tag.getBoolean("Siege")
        );
    }
}
