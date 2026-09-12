package dev.icaro.icaruschests.model;

import dev.icaro.icaruschests.tier.StorageTier;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * What {@code GuiFactory} and {@code ChestGuiListener} actually need from "a thing with tiered,
 * upgradeable storage" — implemented by both {@link IcarusChest} (tied to a placed block) and
 * {@code IcarusBackpack} (tied to a carried item instead). The whole GUI/click-handling layer is
 * written once against this interface; a chest and a backpack differ only in how their identity
 * is anchored (a block's PDC vs. an item's PDC) and how they're persisted (see {@code
 * ChestManager}/{@code BackpackManager}), never in how their contents/upgrades behave once open.
 */
public interface StorageContainer {

    UUID getId();

    StorageTier getTier();

    /** This tier's capacity, plus whatever this specific instance adds on top of it (e.g. a chest doubled with a linked secondary). */
    int effectiveTotalCapacity();

    /** Full, globally-indexed backing array (size {@link #effectiveTotalCapacity()}). Mutated in place by the GUI layer. */
    ItemStack[] getContents();

    /** Replaces the entire backing array, e.g. after hydrating from SQLite or resizing on a tier upgrade. */
    void setContents(ItemStack[] contents);

    /** One entry per upgrade slot (size {@code getTier().upgradeSlotCount()}); {@code null} means the slot is empty. */
    ItemStack[] getUpgrades();

    /** Replaces the entire upgrades array, e.g. after hydrating from SQLite or resizing on a tier upgrade. */
    void setUpgrades(ItemStack[] upgrades);

    boolean isDirty();

    void setDirty(boolean dirty);

    /**
     * Set once, permanently, if {@link #setContents} was never called because SQLite's stored blob
     * couldn't be deserialized (corrupted row, incompatible format after a downgrade, etc.) — see
     * {@code ChestManager}/{@code BackpackManager}'s {@code hydrateContentsAsync}. A container in
     * this state keeps whatever blank placeholder array it started with, and every caller that
     * would read, mutate, or persist it (GUI open, tier-upgrade application, {@code AutosaveTask})
     * must check this first and refuse instead — treating the blank placeholder as real, empty
     * contents would let a normal save silently overwrite the still-recoverable corrupted row in
     * SQLite with actual (wrong) emptiness, destroying it for good.
     */
    boolean isContentsLoadFailed();

    void setContentsLoadFailed(boolean failed);

    /** The noun the GUI title ends with, e.g. {@code "Baú"}/{@code "Baú Duplo"} for a chest, {@code "Mochila"} for a backpack. */
    String noun();
}
