package dev.voidpulsar.lc_claim_economy.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeamQueuedChanges {
    private final Map<String, String> queuedPropertyEdits;
    private final Set<String> queuedForceLoadKeys;
    private final Set<String> queuedForceUnloadKeys;
    private final Set<String> queuedLandChunkKeys;
    private final Set<String> queuedBuildChunkKeys;
    private final Set<UUID> queuedWarDeclareTargets;
    private final Set<UUID> queuedWarEndTargets;

    public TeamQueuedChanges() {
        this(new HashMap<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    public TeamQueuedChanges(
            Map<String, String> propertyEdits,
            Set<String> forceLoadKeys,
            Set<String> forceUnloadKeys
    ) {
        this(propertyEdits, forceLoadKeys, forceUnloadKeys, new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    public TeamQueuedChanges(
            Map<String, String> propertyEdits,
            Set<String> forceLoadKeys,
            Set<String> forceUnloadKeys,
            Set<UUID> warDeclareTargets,
            Set<UUID> warEndTargets
    ) {
        this(propertyEdits, forceLoadKeys, forceUnloadKeys, new HashSet<>(), new HashSet<>(), warDeclareTargets, warEndTargets);
    }

    public TeamQueuedChanges(
            Map<String, String> propertyEdits,
            Set<String> forceLoadKeys,
            Set<String> forceUnloadKeys,
            Set<String> landChunkKeys,
            Set<String> buildChunkKeys,
            Set<UUID> warDeclareTargets,
            Set<UUID> warEndTargets
    ) {
        this.queuedPropertyEdits = new HashMap<>(propertyEdits);
        this.queuedForceLoadKeys = new HashSet<>(forceLoadKeys);
        this.queuedForceUnloadKeys = new HashSet<>(forceUnloadKeys);
        this.queuedLandChunkKeys = new HashSet<>(landChunkKeys);
        this.queuedBuildChunkKeys = new HashSet<>(buildChunkKeys);
        this.queuedWarDeclareTargets = new HashSet<>(warDeclareTargets);
        this.queuedWarEndTargets = new HashSet<>(warEndTargets);
    }

    public Map<String, String> pendingProperties() {
        return Collections.unmodifiableMap(queuedPropertyEdits);
    }

    public Set<String> pendingForceLoads() {
        return Collections.unmodifiableSet(queuedForceLoadKeys);
    }

    public Set<String> pendingForceUnloads() {
        return Collections.unmodifiableSet(queuedForceUnloadKeys);
    }

    public Set<String> pendingLandChunks() {
        return Collections.unmodifiableSet(queuedLandChunkKeys);
    }

    public Set<String> pendingBuildChunks() {
        return Collections.unmodifiableSet(queuedBuildChunkKeys);
    }

    public Set<UUID> pendingWarDeclares() {
        return Collections.unmodifiableSet(queuedWarDeclareTargets);
    }

    public Set<UUID> pendingWarEnds() {
        return Collections.unmodifiableSet(queuedWarEndTargets);
    }

    public boolean isEmpty() {
        return queuedPropertyEdits.isEmpty()
                && queuedForceLoadKeys.isEmpty()
                && queuedForceUnloadKeys.isEmpty()
                && queuedLandChunkKeys.isEmpty()
                && queuedBuildChunkKeys.isEmpty()
                && queuedWarDeclareTargets.isEmpty()
                && queuedWarEndTargets.isEmpty();
    }

    public boolean hasPendingProperty(String propertyId) {
        return queuedPropertyEdits.containsKey(propertyId);
    }

    public boolean isPendingForceLoad(String chunkKey) {
        return queuedForceLoadKeys.contains(chunkKey);
    }

    public boolean isPendingForceUnload(String chunkKey) {
        return queuedForceUnloadKeys.contains(chunkKey);
    }

    public boolean isPendingLandChunk(String chunkKey) {
        return queuedLandChunkKeys.contains(chunkKey);
    }

    public boolean isPendingBuildChunk(String chunkKey) {
        return queuedBuildChunkKeys.contains(chunkKey);
    }

    public boolean isPendingWarDeclare(UUID targetTeamId) {
        return queuedWarDeclareTargets.contains(targetTeamId);
    }

    public boolean isPendingWarEnd(UUID targetTeamId) {
        return queuedWarEndTargets.contains(targetTeamId);
    }

    public TeamQueuedChanges withPendingProperty(String propertyId, String value) {
        TeamQueuedChanges next = copy();
        next.queuedPropertyEdits.put(propertyId, value);
        return next;
    }

    public TeamQueuedChanges withoutPendingProperty(String propertyId) {
        if (!queuedPropertyEdits.containsKey(propertyId)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedPropertyEdits.remove(propertyId);
        return next;
    }

    public TeamQueuedChanges withPendingForceLoad(String chunkKey) {
        TeamQueuedChanges next = copy();
        next.queuedForceLoadKeys.add(chunkKey);
        next.queuedForceUnloadKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withoutPendingForceLoad(String chunkKey) {
        if (!queuedForceLoadKeys.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedForceLoadKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withPendingForceUnload(String chunkKey) {
        TeamQueuedChanges next = copy();
        next.queuedForceUnloadKeys.add(chunkKey);
        next.queuedForceLoadKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withoutPendingForceUnload(String chunkKey) {
        if (!queuedForceUnloadKeys.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedForceUnloadKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withPendingLandChunk(String chunkKey) {
        TeamQueuedChanges next = copy();
        next.queuedLandChunkKeys.add(chunkKey);
        next.queuedBuildChunkKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withoutPendingLandChunk(String chunkKey) {
        if (!queuedLandChunkKeys.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedLandChunkKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withPendingBuildChunk(String chunkKey) {
        TeamQueuedChanges next = copy();
        next.queuedBuildChunkKeys.add(chunkKey);
        next.queuedLandChunkKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withoutPendingBuildChunk(String chunkKey) {
        if (!queuedBuildChunkKeys.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedBuildChunkKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withoutChunkTypePending(String chunkKey) {
        if (!queuedLandChunkKeys.contains(chunkKey) && !queuedBuildChunkKeys.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedLandChunkKeys.remove(chunkKey);
        next.queuedBuildChunkKeys.remove(chunkKey);
        return next;
    }

    public TeamQueuedChanges withPendingWarDeclare(UUID targetTeamId) {
        TeamQueuedChanges next = copy();
        next.queuedWarDeclareTargets.add(targetTeamId);
        next.queuedWarEndTargets.remove(targetTeamId);
        return next;
    }

    public TeamQueuedChanges withoutPendingWarDeclare(UUID targetTeamId) {
        if (!queuedWarDeclareTargets.contains(targetTeamId)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedWarDeclareTargets.remove(targetTeamId);
        return next;
    }

    public TeamQueuedChanges withPendingWarEnd(UUID targetTeamId) {
        TeamQueuedChanges next = copy();
        next.queuedWarEndTargets.add(targetTeamId);
        next.queuedWarDeclareTargets.remove(targetTeamId);
        return next;
    }

    public TeamQueuedChanges withoutPendingWarEnd(UUID targetTeamId) {
        if (!queuedWarEndTargets.contains(targetTeamId)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedWarEndTargets.remove(targetTeamId);
        return next;
    }

    public TeamQueuedChanges withoutWarReferences(UUID teamId) {
        if (!queuedWarDeclareTargets.contains(teamId) && !queuedWarEndTargets.contains(teamId)) {
            return this;
        }
        TeamQueuedChanges next = copy();
        next.queuedWarDeclareTargets.remove(teamId);
        next.queuedWarEndTargets.remove(teamId);
        return next;
    }

    public TeamQueuedChanges cleared() {
        return new TeamQueuedChanges();
    }

    public TeamQueuedChanges copy() {
        return new TeamQueuedChanges(
                queuedPropertyEdits,
                queuedForceLoadKeys,
                queuedForceUnloadKeys,
                queuedLandChunkKeys,
                queuedBuildChunkKeys,
                queuedWarDeclareTargets,
                queuedWarEndTargets
        );
    }
}
