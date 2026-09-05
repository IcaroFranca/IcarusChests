package dev.icaro.icaruschests.model;

import dev.icaro.icaruschests.tier.ChestTier;

import java.util.UUID;

/**
 * In-memory representation of a single tiered chest block. One instance
 * exists per physical chest block; a double chest is two linked instances
 * (see {@link #linkedChestId}), not one instance spanning both halves.
 *
 * <p>M2 scope: identity, location and tier only. Inventory contents and
 * {@code dirty}-tracking for persistence are added in M4.
 */
public final class IcarusChest {

    private final UUID id;
    private final ChestLocation location;
    private ChestTier tier;
    private UUID linkedChestId;

    public IcarusChest(UUID id, ChestLocation location, ChestTier tier) {
        this.id = id;
        this.location = location;
        this.tier = tier;
    }

    public UUID getId() {
        return id;
    }

    public ChestLocation getLocation() {
        return location;
    }

    public ChestTier getTier() {
        return tier;
    }

    public void setTier(ChestTier tier) {
        this.tier = tier;
    }

    /** Id of the other half of this double chest, or {@code null} if this is a single chest. */
    public UUID getLinkedChestId() {
        return linkedChestId;
    }

    public void setLinkedChestId(UUID linkedChestId) {
        this.linkedChestId = linkedChestId;
    }

    public boolean isLinked() {
        return linkedChestId != null;
    }
}
