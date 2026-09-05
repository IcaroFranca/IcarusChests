package dev.icaro.icaruschests.tier;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Optional;

/**
 * A tier of IcarusChest, ordered from lowest to highest capacity. The ordinal
 * of each constant is persisted (PDC + SQLite) as the tier identifier, so
 * constants must never be reordered — only appended before {@link #NETHERITE}
 * would require a migration, so new tiers should be appended at the end.
 *
 * <p>A vanilla {@code Inventory} of chest type is capped at 54 slots (6 rows)
 * by the client, so tiers above that capacity are split across multiple
 * navigable pages instead of one oversized inventory. Pages aren't
 * necessarily uniform in size — {@link #pageSizes} lists each page's exact
 * size (front-loaded: earlier pages are as large as possible), and they
 * always sum to {@link #totalCapacity()}.
 *
 * <p>Linking two chests of the same tier into a double chest doubles
 * whatever single-chest capacity applies at the time (see {@code
 * IcarusChest#effectivePageSizes()}) — that doubling is per-chest-instance
 * state, not part of this enum.
 */
public enum ChestTier {

    NORMAL("Normal", new int[]{27}, null, 0),
    COPPER("Cobre", new int[]{45}, Material.COPPER_INGOT, 8),
    IRON("Ferro", new int[]{54}, Material.IRON_INGOT, 8),
    GOLD("Ouro", new int[]{54, 27}, Material.GOLD_INGOT, 8),
    DIAMOND("Diamante", new int[]{54, 54}, Material.DIAMOND, 8),
    NETHERITE("Netherite", new int[]{54, 54, 27}, Material.NETHERITE_INGOT, 4);

    public static final int MAX_SLOTS_PER_PAGE = 54;

    private final String displayName;
    private final int[] pageSizes;
    private final Material upgradeMaterial;
    private final int upgradeAmount;

    ChestTier(String displayName, int[] pageSizes, Material upgradeMaterial, int upgradeAmount) {
        for (int size : pageSizes) {
            if (size <= 0 || size % 9 != 0 || size > MAX_SLOTS_PER_PAGE) {
                throw new IllegalArgumentException("each page size must be a positive multiple of 9, at most "
                        + MAX_SLOTS_PER_PAGE + ": " + size);
            }
        }
        this.displayName = displayName;
        this.pageSizes = pageSizes;
        this.upgradeMaterial = upgradeMaterial;
        this.upgradeAmount = upgradeAmount;
    }

    public String displayName() {
        return displayName;
    }

    /** Size of each page in slots, front-loaded (earlier pages are as large as possible), always summing to {@link #totalCapacity()}. */
    public int[] pageSizes() {
        return pageSizes.clone();
    }

    /** Number of navigable pages this tier's inventory is split across. */
    public int pages() {
        return pageSizes.length;
    }

    /** Total item capacity across all pages, at this tier alone (not doubled). */
    public int totalCapacity() {
        return Arrays.stream(pageSizes).sum();
    }

    /** Material consumed by this tier's upgrade kit recipe, absent for {@link #NORMAL}. */
    public Optional<Material> upgradeMaterial() {
        return Optional.ofNullable(upgradeMaterial);
    }

    /** Amount of {@link #upgradeMaterial()} consumed by this tier's upgrade kit recipe. */
    public int upgradeAmount() {
        return upgradeAmount;
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
