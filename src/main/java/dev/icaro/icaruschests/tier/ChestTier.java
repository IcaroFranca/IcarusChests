package dev.icaro.icaruschests.tier;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.Optional;

/**
 * A tier of IcarusChest, ordered from lowest to highest capacity. The ordinal
 * of each constant is persisted (PDC + SQLite) as the tier identifier, so
 * constants must never be reordered — only appended before {@link #NETHERITE}
 * would require a migration, so new tiers should be appended at the end.
 *
 * <p>A vanilla {@code Inventory} of chest type is capped at 54 slots (6 rows)
 * by the client, so tiers above that capacity are split across multiple
 * navigable pages of up to {@link #MAX_SLOTS_PER_PAGE} slots instead of one
 * oversized inventory.
 */
public enum ChestTier {

    NORMAL("Normal", 27, 1, null, 0),
    COPPER("Cobre", 36, 1, Material.COPPER_INGOT, 8),
    IRON("Ferro", 45, 1, Material.IRON_INGOT, 8),
    GOLD("Ouro", 54, 1, Material.GOLD_INGOT, 8),
    DIAMOND("Diamante", 54, 2, Material.DIAMOND, 8),
    NETHERITE("Netherite", 54, 3, Material.NETHERITE_INGOT, 4);

    public static final int MAX_SLOTS_PER_PAGE = 54;

    private final String displayName;
    private final int slotsPerPage;
    private final int pages;
    private final Material upgradeMaterial;
    private final int upgradeAmount;

    ChestTier(String displayName, int slotsPerPage, int pages, Material upgradeMaterial, int upgradeAmount) {
        if (slotsPerPage <= 0 || slotsPerPage % 9 != 0 || slotsPerPage > MAX_SLOTS_PER_PAGE) {
            throw new IllegalArgumentException("slotsPerPage must be a positive multiple of 9, at most "
                    + MAX_SLOTS_PER_PAGE + ": " + slotsPerPage);
        }
        this.displayName = displayName;
        this.slotsPerPage = slotsPerPage;
        this.pages = pages;
        this.upgradeMaterial = upgradeMaterial;
        this.upgradeAmount = upgradeAmount;
    }

    public String displayName() {
        return displayName;
    }

    /** Number of slots in each page's GUI (a multiple of 9, at most {@link #MAX_SLOTS_PER_PAGE}). */
    public int slotsPerPage() {
        return slotsPerPage;
    }

    /** Number of navigable pages this tier's inventory is split across. */
    public int pages() {
        return pages;
    }

    /** Total item capacity across all pages. */
    public int totalCapacity() {
        return slotsPerPage * pages;
    }

    /** Material consumed by this tier's upgrade kit recipe, absent for {@link #NORMAL}. */
    public Optional<Material> upgradeMaterial() {
        return Optional.ofNullable(upgradeMaterial);
    }

    /** Amount of {@link #upgradeMaterial()} consumed by this tier's upgrade kit recipe. */
    public int upgradeAmount() {
        return upgradeAmount;
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
