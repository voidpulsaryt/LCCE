package dev.voidpulsaryt.lcce.nation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A Towny-style nation: a named group of FTB Teams ("towns" in this mod's own vocabulary) allied
 * under one capital team, with its own pooled currency balance separate from any member's own
 * {@code TeamBalance}. The capital's officers are the only ones who can invite/kick member teams,
 * change the per-member tax, or disband the nation outright - simpler than Towny's own
 * king/assistant hierarchy, but the same basic shape.
 */
public final class Nation {

    private final UUID id;
    private final UUID capitalTeamId;
    private String name;
    private final Set<UUID> memberTeamIds;
    private BigInteger balance;
    private long taxPerMemberPerPeriod;

    public Nation(UUID id, UUID capitalTeamId, String name, Set<UUID> memberTeamIds, BigInteger balance, long taxPerMemberPerPeriod) {
        this.id = id;
        this.capitalTeamId = capitalTeamId;
        this.name = name;
        this.memberTeamIds = memberTeamIds;
        this.balance = balance;
        this.taxPerMemberPerPeriod = taxPerMemberPerPeriod;
    }

    public static Nation create(UUID capitalTeamId, String name) {
        Set<UUID> members = new HashSet<>();
        members.add(capitalTeamId);
        return new Nation(UUID.randomUUID(), capitalTeamId, name, members, BigInteger.ZERO, 0L);
    }

    public UUID id() { return id; }
    public UUID capitalTeamId() { return capitalTeamId; }
    public String name() { return name; }
    public void rename(String name) { this.name = name; }
    public Set<UUID> memberTeamIds() { return memberTeamIds; }
    public BigInteger balance() { return balance; }
    public void setBalance(BigInteger balance) { this.balance = balance.max(BigInteger.ZERO); }
    public long taxPerMemberPerPeriod() { return taxPerMemberPerPeriod; }
    public void setTaxPerMemberPerPeriod(long tax) { this.taxPerMemberPerPeriod = Math.max(0L, tax); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Capital", capitalTeamId);
        tag.putString("Name", name);
        tag.putString("Balance", balance.toString());
        tag.putLong("Tax", taxPerMemberPerPeriod);

        ListTag memberList = new ListTag();
        for (UUID memberId : memberTeamIds) {
            memberList.add(StringTag.valueOf(memberId.toString()));
        }
        tag.put("Members", memberList);
        return tag;
    }

    public static Nation load(CompoundTag tag) {
        Set<UUID> members = new HashSet<>();
        ListTag memberList = tag.getList("Members", Tag.TAG_STRING);
        for (Tag t : memberList) {
            try {
                members.add(UUID.fromString(t.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // stale/corrupted entry, safe to skip
            }
        }
        return new Nation(
                tag.getUUID("Id"),
                tag.getUUID("Capital"),
                tag.getString("Name"),
                members,
                new BigInteger(tag.getString("Balance")),
                tag.getLong("Tax")
        );
    }
}
