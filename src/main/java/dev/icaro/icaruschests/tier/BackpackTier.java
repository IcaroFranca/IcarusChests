package dev.icaro.icaruschests.tier;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Optional;

/**
 * A tier of portable backpack, ordered from lowest to highest capacity — the carried counterpart
 * to {@link ChestTier}, deliberately its own enum rather than reusing {@code ChestTier} directly:
 * a backpack's progression is smaller (so placed storage stays worth building), starts from a
 * craftable base instead of a free starting tier, and upgrades by recrafting the previous backpack
 * together with ore (see {@code BackpackRegistry}) rather than by consuming a kit against a placed
 * block (see {@code TierUpgradeService}) — different enough mechanics that folding it into {@code
 * ChestTier} would mean carrying fields that don't apply to one kind or the other.
 *
 * <p>The ordinal of each constant is persisted (PDC + SQLite, same {@code tier} column a chest
 * uses — see {@code kind} for how a row picks which of the two enums its ordinal means), so
 * constants must never be reordered — only appended before {@link #NETHERITE}.
 */
public enum BackpackTier implements StorageTier {

    LEATHER("Couro", 9, TextColor.color(0xA0, 0x6A, 0x42), null, 1),
    COPPER("Cobre", 18, TextColor.color(0xC8, 0x71, 0x37), Material.COPPER_INGOT, 1),
    IRON("Ferro", 27, TextColor.color(0xDC, 0xDC, 0xDC), Material.IRON_INGOT, 2),
    GOLD("Ouro", 36, TextColor.color(0xFF, 0xD9, 0x66), Material.GOLD_INGOT, 2),
    DIAMOND("Diamante", 45, TextColor.color(0x4A, 0xED, 0xD9), Material.DIAMOND, 3),
    NETHERITE("Netherite", 54, TextColor.color(0x6E, 0x5A, 0x61), Material.NETHERITE_INGOT, 4);

    /** How many ore units the recipe to reach this tier from the previous one consumes (irrelevant for {@link #LEATHER}, the base tier). */
    private static final int UPGRADE_ORE_AMOUNT = 8;

    private final String displayName;
    private final int totalCapacity;
    private final TextColor titleColor;
    private final Material upgradeMaterial;
    private final int upgradeSlotCount;

    BackpackTier(String displayName, int totalCapacity, TextColor titleColor, Material upgradeMaterial, int upgradeSlotCount) {
        if (totalCapacity <= 0 || totalCapacity % 9 != 0) {
            throw new IllegalArgumentException("totalCapacity must be a positive multiple of 9: " + totalCapacity);
        }
        if (upgradeSlotCount < 0 || upgradeSlotCount > ChestTier.MAX_UPGRADE_SLOTS) {
            throw new IllegalArgumentException("upgradeSlotCount must be between 0 and " + ChestTier.MAX_UPGRADE_SLOTS + ": " + upgradeSlotCount);
        }
        this.displayName = displayName;
        this.totalCapacity = totalCapacity;
        this.titleColor = titleColor;
        this.upgradeMaterial = upgradeMaterial;
        this.upgradeSlotCount = upgradeSlotCount;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public TextColor titleColor() {
        return titleColor;
    }

    @Override
    public int totalCapacity() {
        return totalCapacity;
    }

    @Override
    public int upgradeSlotCount() {
        return upgradeSlotCount;
    }

    /** The ore this tier's own recipe consumes to reach it *from the previous tier* — absent for {@link #LEATHER} (crafted from scratch, not upgraded into). */
    public Optional<Material> upgradeMaterial() {
        return Optional.ofNullable(upgradeMaterial);
    }

    /** Amount of {@link #upgradeMaterial()} the recipe to reach this tier consumes. */
    public int upgradeAmount() {
        return UPGRADE_ORE_AMOUNT;
    }

    /** The tier reached by recrafting a backpack currently at this tier with its ore, if any. */
    public Optional<BackpackTier> next() {
        BackpackTier[] values = values();
        int nextOrdinal = ordinal() + 1;
        return nextOrdinal < values.length ? Optional.of(values[nextOrdinal]) : Optional.empty();
    }

    public static Optional<BackpackTier> byOrdinal(int ordinal) {
        BackpackTier[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return Optional.empty();
        }
        return Optional.of(values[ordinal]);
    }
}
