package dev.voidpulsar.lc_claim_economy.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeamQueuedChanges {
    private final Map<String, String> pendingProperties;
    private final Set<String> pendingForceLoads;
    private final Set<String> pendingForceUnloads;
    private final Set<String> pendingLandChunks;
    private final Set<String> pendingBuildChunks;
    private final Set<UUID> pendingWarDeclares;
    private final Set<UUID> pendingWarEnds;

    public TeamQueuedChanges() {
        this(new HashMap<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    public TeamQueuedChanges(
            Map<String, String> pendingProperties,
            Set<String> pendingForceLoads,
            Set<String> pendingForceUnloads
    ) {
        this(pendingProperties, pendingForceLoads, pendingForceUnloads, new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    public TeamQueuedChanges(
            Map<String, String> pendingProperties,
            Set<String> pendingForceLoads,
            Set<String> pendingForceUnloads,
            Set<UUID> pendingWarDeclares,
            Set<UUID> pendingWarEnds
    ) {
        this(pendingProperties, pendingForceLoads, pendingForceUnloads, new HashSet<>(), new HashSet<>(), pendingWarDeclares, pendingWarEnds);
    }

    public TeamQueuedChanges(
            Map<String, String> pendingProperties,
            Set<String> pendingForceLoads,
            Set<String> pendingForceUnloads,
            Set<String> pendingLandChunks,
            Set<String> pendingBuildChunks,
            Set<UUID> pendingWarDeclares,
            Set<UUID> pendingWarEnds
    ) {
        this.pendingProperties = new HashMap<>(pendingProperties);
        this.pendingForceLoads = new HashSet<>(pendingForceLoads);
        this.pendingForceUnloads = new HashSet<>(pendingForceUnloads);
        this.pendingLandChunks = new HashSet<>(pendingLandChunks);
        this.pendingBuildChunks = new HashSet<>(pendingBuildChunks);
        this.pendingWarDeclares = new HashSet<>(pendingWarDeclares);
        this.pendingWarEnds = new HashSet<>(pendingWarEnds);
    }

    public Map<String, String> pendingProperties() {
        return Collections.unmodifiableMap(pendingProperties);
    }

    public Set<String> pendingForceLoads() {
        return Collections.unmodifiableSet(pendingForceLoads);
    }

    public Set<String> pendingForceUnloads() {
        return Collections.unmodifiableSet(pendingForceUnloads);
    }

    public Set<String> pendingLandChunks() {
        return Collections.unmodifiableSet(pendingLandChunks);
    }

    public Set<String> pendingBuildChunks() {
        return Collections.unmodifiableSet(pendingBuildChunks);
    }

    public Set<UUID> pendingWarDeclares() {
        return Collections.unmodifiableSet(pendingWarDeclares);
    }

    public Set<UUID> pendingWarEnds() {
        return Collections.unmodifiableSet(pendingWarEnds);
    }

    public boolean isEmpty() {
        return pendingProperties.isEmpty()
                && pendingForceLoads.isEmpty()
                && pendingForceUnloads.isEmpty()
                && pendingLandChunks.isEmpty()
                && pendingBuildChunks.isEmpty()
                && pendingWarDeclares.isEmpty()
                && pendingWarEnds.isEmpty();
    }

    public boolean hasPendingProperty(String propertyId) {
        return pendingProperties.containsKey(propertyId);
    }

    public boolean isPendingForceLoad(String chunkKey) {
        return pendingForceLoads.contains(chunkKey);
    }

    public boolean isPendingForceUnload(String chunkKey) {
        return pendingForceUnloads.contains(chunkKey);
    }

    public boolean isPendingLandChunk(String chunkKey) {
        return pendingLandChunks.contains(chunkKey);
    }

    public boolean isPendingBuildChunk(String chunkKey) {
        return pendingBuildChunks.contains(chunkKey);
    }

    public boolean isPendingWarDeclare(UUID targetTeamId) {
        return pendingWarDeclares.contains(targetTeamId);
    }

    public boolean isPendingWarEnd(UUID targetTeamId) {
        return pendingWarEnds.contains(targetTeamId);
    }

    public TeamQueuedChanges withPendingProperty(String propertyId, String value) {
        TeamQueuedChanges copy = copy();
        copy.pendingProperties.put(propertyId, value);
        return copy;
    }

    public TeamQueuedChanges withoutPendingProperty(String propertyId) {
        if (!pendingProperties.containsKey(propertyId)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingProperties.remove(propertyId);
        return copy;
    }

    public TeamQueuedChanges withPendingForceLoad(String chunkKey) {
        TeamQueuedChanges copy = copy();
        copy.pendingForceLoads.add(chunkKey);
        copy.pendingForceUnloads.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withoutPendingForceLoad(String chunkKey) {
        if (!pendingForceLoads.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingForceLoads.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withPendingForceUnload(String chunkKey) {
        TeamQueuedChanges copy = copy();
        copy.pendingForceUnloads.add(chunkKey);
        copy.pendingForceLoads.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withoutPendingForceUnload(String chunkKey) {
        if (!pendingForceUnloads.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingForceUnloads.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withPendingLandChunk(String chunkKey) {
        TeamQueuedChanges copy = copy();
        copy.pendingLandChunks.add(chunkKey);
        copy.pendingBuildChunks.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withoutPendingLandChunk(String chunkKey) {
        if (!pendingLandChunks.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingLandChunks.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withPendingBuildChunk(String chunkKey) {
        TeamQueuedChanges copy = copy();
        copy.pendingBuildChunks.add(chunkKey);
        copy.pendingLandChunks.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withoutPendingBuildChunk(String chunkKey) {
        if (!pendingBuildChunks.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingBuildChunks.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withoutChunkTypePending(String chunkKey) {
        if (!pendingLandChunks.contains(chunkKey) && !pendingBuildChunks.contains(chunkKey)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingLandChunks.remove(chunkKey);
        copy.pendingBuildChunks.remove(chunkKey);
        return copy;
    }

    public TeamQueuedChanges withPendingWarDeclare(UUID targetTeamId) {
        TeamQueuedChanges copy = copy();
        copy.pendingWarDeclares.add(targetTeamId);
        copy.pendingWarEnds.remove(targetTeamId);
        return copy;
    }

    public TeamQueuedChanges withoutPendingWarDeclare(UUID targetTeamId) {
        if (!pendingWarDeclares.contains(targetTeamId)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingWarDeclares.remove(targetTeamId);
        return copy;
    }

    public TeamQueuedChanges withPendingWarEnd(UUID targetTeamId) {
        TeamQueuedChanges copy = copy();
        copy.pendingWarEnds.add(targetTeamId);
        copy.pendingWarDeclares.remove(targetTeamId);
        return copy;
    }

    public TeamQueuedChanges withoutPendingWarEnd(UUID targetTeamId) {
        if (!pendingWarEnds.contains(targetTeamId)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingWarEnds.remove(targetTeamId);
        return copy;
    }

    public TeamQueuedChanges withoutWarReferences(UUID teamId) {
        if (!pendingWarDeclares.contains(teamId) && !pendingWarEnds.contains(teamId)) {
            return this;
        }
        TeamQueuedChanges copy = copy();
        copy.pendingWarDeclares.remove(teamId);
        copy.pendingWarEnds.remove(teamId);
        return copy;
    }

    public TeamQueuedChanges cleared() {
        return new TeamQueuedChanges();
    }

    public TeamQueuedChanges copy() {
        return new TeamQueuedChanges(
                pendingProperties,
                pendingForceLoads,
                pendingForceUnloads,
                pendingLandChunks,
                pendingBuildChunks,
                pendingWarDeclares,
                pendingWarEnds
        );
    }
}
