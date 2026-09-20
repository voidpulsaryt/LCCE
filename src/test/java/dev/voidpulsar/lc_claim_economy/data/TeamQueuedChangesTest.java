package dev.voidpulsar.lc_claim_economy.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamQueuedChangesTest {

    private static final UUID TEAM_A = UUID.randomUUID();
    private static final UUID TEAM_B = UUID.randomUUID();
    private static final String CHUNK_1 = "overworld:0:0";
    private static final String CHUNK_2 = "overworld:1:0";
    private static final String PROP_PVP = "allow_pvp";

    @Test
    void freshState_isEmpty() {
        assertTrue(new TeamQueuedChanges().isEmpty());
    }

    @Test
    void afterAddingWarDeclare_notEmpty() {
        assertFalse(new TeamQueuedChanges().withPendingWarDeclare(TEAM_A).isEmpty());
    }

    @Test
    void afterAddingAndRemovingWarDeclare_isEmpty() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingWarDeclare(TEAM_A)
                .withoutPendingWarDeclare(TEAM_A);
        assertTrue(s.isEmpty());
    }

    @Test
    void withPendingWarDeclare_removesFromWarEnds() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingWarEnd(TEAM_A)
                .withPendingWarDeclare(TEAM_A);
        assertTrue(s.isPendingWarDeclare(TEAM_A));
        assertFalse(s.isPendingWarEnd(TEAM_A));
    }

    @Test
    void withPendingWarEnd_removesFromWarDeclares() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingWarDeclare(TEAM_A)
                .withPendingWarEnd(TEAM_A);
        assertTrue(s.isPendingWarEnd(TEAM_A));
        assertFalse(s.isPendingWarDeclare(TEAM_A));
    }

    @Test
    void withPendingLandChunk_removesFromBuildPending() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingBuildChunk(CHUNK_1)
                .withPendingLandChunk(CHUNK_1);
        assertTrue(s.isPendingLandChunk(CHUNK_1));
        assertFalse(s.isPendingBuildChunk(CHUNK_1));
    }

    @Test
    void withPendingBuildChunk_removesFromLandPending() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingLandChunk(CHUNK_1)
                .withPendingBuildChunk(CHUNK_1);
        assertTrue(s.isPendingBuildChunk(CHUNK_1));
        assertFalse(s.isPendingLandChunk(CHUNK_1));
    }

    @Test
    void withPendingForceLoad_removesFromForceUnloads() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingForceUnload(CHUNK_1)
                .withPendingForceLoad(CHUNK_1);
        assertTrue(s.isPendingForceLoad(CHUNK_1));
        assertFalse(s.isPendingForceUnload(CHUNK_1));
    }

    @Test
    void withPendingForceUnload_removesFromForceLoads() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingForceLoad(CHUNK_1)
                .withPendingForceUnload(CHUNK_1);
        assertTrue(s.isPendingForceUnload(CHUNK_1));
        assertFalse(s.isPendingForceLoad(CHUNK_1));
    }

    @Test
    void withoutWarReferences_removesBothDeclareAndEnd() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingWarDeclare(TEAM_A)
                .withPendingWarEnd(TEAM_B)
                .withPendingWarDeclare(TEAM_B)  // overrides end for B
                .withoutWarReferences(TEAM_B);
        assertFalse(s.isPendingWarDeclare(TEAM_B));
        assertFalse(s.isPendingWarEnd(TEAM_B));
        assertTrue(s.isPendingWarDeclare(TEAM_A));
    }

    @Test
    void withoutWarReferences_noOpWhenAbsent() {
        TeamQueuedChanges original = new TeamQueuedChanges().withPendingWarDeclare(TEAM_A);
        TeamQueuedChanges same = original.withoutWarReferences(TEAM_B);
        assertSame(original, same);
    }

    @Test
    void withPendingProperty_thenWithout_isEmpty() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingProperty(PROP_PVP, "false")
                .withoutPendingProperty(PROP_PVP);
        assertTrue(s.isEmpty());
        assertFalse(s.hasPendingProperty(PROP_PVP));
    }

    @Test
    void withoutPendingProperty_noOpWhenAbsent() {
        TeamQueuedChanges original = new TeamQueuedChanges();
        assertSame(original, original.withoutPendingProperty(PROP_PVP));
    }

    @Test
    void copy_isIndependentOfOriginal() {
        TeamQueuedChanges original = new TeamQueuedChanges().withPendingWarDeclare(TEAM_A);
        TeamQueuedChanges copy = original.copy();
        TeamQueuedChanges extended = copy.withPendingWarDeclare(TEAM_B);

        assertFalse(original.isPendingWarDeclare(TEAM_B), "Original must not be mutated");
        assertTrue(extended.isPendingWarDeclare(TEAM_B));
        assertTrue(extended.isPendingWarDeclare(TEAM_A));
    }

    @Test
    void cleared_returnsEmptyState() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingWarDeclare(TEAM_A)
                .withPendingForceLoad(CHUNK_1)
                .withPendingProperty(PROP_PVP, "false")
                .cleared();
        assertTrue(s.isEmpty());
    }

    @Test
    void multipleChunks_dontInterfere() {
        TeamQueuedChanges s = new TeamQueuedChanges()
                .withPendingForceLoad(CHUNK_1)
                .withPendingForceLoad(CHUNK_2);
        assertTrue(s.isPendingForceLoad(CHUNK_1));
        assertTrue(s.isPendingForceLoad(CHUNK_2));

        s = s.withoutPendingForceLoad(CHUNK_1);
        assertFalse(s.isPendingForceLoad(CHUNK_1));
        assertTrue(s.isPendingForceLoad(CHUNK_2));
    }

    @Test
    void pendingProperties_returnsUnmodifiableView() {
        TeamQueuedChanges s = new TeamQueuedChanges().withPendingProperty(PROP_PVP, "false");
        assertThrows(UnsupportedOperationException.class,
                () -> s.pendingProperties().put("x", "y"));
    }

    @Test
    void pendingWarDeclares_returnsUnmodifiableView() {
        TeamQueuedChanges s = new TeamQueuedChanges().withPendingWarDeclare(TEAM_A);
        assertThrows(UnsupportedOperationException.class,
                () -> s.pendingWarDeclares().add(UUID.randomUUID()));
    }
}
