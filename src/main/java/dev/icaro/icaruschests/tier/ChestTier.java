package dev.icaro.icaruschests.tier;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Optional;

/**
 * A tier of IcarusChest, ordered from lowest to highest capacity. The ordinal
 * of each constant is persisted (PDC + SQLite) as the tier identifier, so
 * constants must never be reordered — only appended before {@link #NETHERITE}
 * would require a migration, so new tiers should be appended at the end.
 *
 * <p>{@link #totalCapacity()} must always be a multiple of 9 (a Minecraft
 * chest-type inventory row) since the GUI scrolls through it a row at a
 * time — see {@code GuiFactory}. Linking two chests of the same tier into a
 * double chest doubles whatever single-chest capacity applies at the time —
 * that doubling is per-chest-instance state (see {@code IcarusChest}), not
 * part of this enum.
 */
public enum ChestTier {

    NORMAL("Normal", 27, TextColor.color(0xB6, 0x86, 0x55), null, 0, 1),
    COPPER("Cobre", 45, TextColor.color(0xC8, 0x71, 0x37), Material.COPPER_INGOT, 8, 1),
    IRON("Ferro", 54, TextColor.color(0xDC, 0xDC, 0xDC), Material.IRON_INGOT, 8, 2),
    GOLD("Ouro", 81, TextColor.color(0xFF, 0xD9, 0x66), Material.GOLD_INGOT, 8, 2),
    DIAMOND("Diamante", 108, TextColor.color(0x4A, 0xED, 0xD9), Material.DIAMOND, 8, 3),
    NETHERITE("Netherite", 135, TextColor.color(0x6E, 0x5A, 0x61), Material.NETHERITE_INGOT, 4, 4);

    /** How many of {@link #upgradeSlotCount()}'s reserved columns actually exist in the control row — see {@code GuiFactory}. */
    public static final int MAX_UPGRADE_SLOTS = 6;

    private final String displayName;
    private final int totalCapacity;
    private final TextColor titleColor;
    private final Material upgradeMaterial;
    private final int upgradeAmount;
    private final int upgradeSlotCount;

    ChestTier(String displayName, int totalCapacity, TextColor titleColor, Material upgradeMaterial,
              int upgradeAmount, int upgradeSlotCount) {
        if (totalCapacity <= 0 || totalCapacity % 9 != 0) {
            throw new IllegalArgumentException("totalCapacity must be a positive multiple of 9: " + totalCapacity);
        }
        if (upgradeSlotCount < 0 || upgradeSlotCount > MAX_UPGRADE_SLOTS) {
            throw new IllegalArgumentException("upgradeSlotCount must be between 0 and " + MAX_UPGRADE_SLOTS + ": " + upgradeSlotCount);
        }
        this.displayName = displayName;
        this.totalCapacity = totalCapacity;
        this.titleColor = titleColor;
        this.upgradeMaterial = upgradeMaterial;
        this.upgradeAmount = upgradeAmount;
        this.upgradeSlotCount = upgradeSlotCount;
    }

    public String displayName() {
        return displayName;
    }

    /** Color used for this tier's name in the GUI title, matching its ore/material. */
    public TextColor titleColor() {
        return titleColor;
    }

    /** Total item capacity at this tier alone (not doubled). Always a multiple of 9. */
    public int totalCapacity() {
        return totalCapacity;
    }

    /** Material consumed by this tier's upgrade kit recipe, absent for {@link #NORMAL}. */
    public Optional<Material> upgradeMaterial() {
        return Optional.ofNullable(upgradeMaterial);
    }

    /** Amount of {@link #upgradeMaterial()} consumed by this tier's upgrade kit recipe. */
    public int upgradeAmount() {
        return upgradeAmount;
    }

    /** Number of pluggable-upgrade slots (Filter, Stack, etc.) a chest at this tier has. Never decreases between tiers. */
    public int upgradeSlotCount() {
        return upgradeSlotCount;
    }

    /**
     * The 3x3 crafting grid shape for this tier's upgrade kit recipe: a
     * chest ({@code 'C'}) in the center, surrounded by {@link #upgradeAmount()}
     * units of {@link #upgradeMaterial()} ({@code 'M'}). New upgrade amounts
     * need a new case here — deliberately not derived automatically, so an
     * unsupported amount fails loudly instead of crafting a silently wrong
     * shape.
     */
    public String[] recipeShape() {
        return switch (upgradeAmount) {
            case 8 -> new String[]{"MMM", "MCM", "MMM"};
            case 4 -> new String[]{" M ", "MCM", " M "};
            default -> throw new IllegalStateException(
                    "No crafting shape defined for upgrade amount " + upgradeAmount + " (tier " + this + ")");
        };
    }

    /** The tier reached by applying an upgrade kit to a chest currently at this tier, if any. */
    public Optional<ChestTier> next() {
        ChestTier[] values = values();
        int nextOrdinal = ordinal() + 1;
        return nextOrdinal < values.length ? Optional.of(values[nextOrdinal]) : Optional.empty();
    }

    public static Optional<ChestTier> byOrdinal(int ordinal) {
        ChestTier[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return Optional.empty();
        }
        return Optional.of(values[ordinal]);
    }

    /** Namespaced key suffix conventionally used for this tier's upgrade kit item, e.g. {@code "upgrade_kit_copper"}. */
    public String upgradeKitKey() {
        return "upgrade_kit_" + name().toLowerCase();
    }
}
