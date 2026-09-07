package dev.icaro.icaruschests.tier;

import net.kyori.adventure.text.format.TextColor;

/**
 * The handful of properties {@code GuiFactory}/{@code ChestGuiListener} actually need from "what
 * tier is this storage at" — implemented by both {@link ChestTier} (a placed chest) and {@code
 * BackpackTier} (a portable one), so the whole GUI/click-handling layer (see {@link
 * dev.icaro.icaruschests.model.StorageContainer}) can stay written once against either kind of
 * storage instead of duplicating it.
 */
public interface StorageTier {

    String displayName();

    /** Color used for this tier's name in the GUI title, matching its ore/material. */
    TextColor titleColor();

    /** Total item capacity at this tier alone. Always a multiple of 9 (a Minecraft inventory row). */
    int totalCapacity();

    /** Number of pluggable-upgrade slots (Filter, Stack, etc.) at this tier. Never decreases between tiers. */
    int upgradeSlotCount();
}
