package dev.icaro.icaruschests.upgrade;

import java.util.Optional;

/**
 * A pluggable upgrade a chest's dedicated upgrade slots can hold. New types
 * go here; {@link UpgradeRegistry} builds each one's item/recipe and {@code
 * ChestGuiListener}/{@code GuiFactory} wire in whatever gameplay effect they
 * have.
 *
 * <p>Stack upgrades come in five tiers (one {@code UpgradeType} constant
 * each, not a single type with a configurable level) so a chest can carry at
 * most one of a given tier at a time, same as any other upgrade item — see
 * {@code UpgradeSlots#bestStackMultiplier} for how multiple installed tiers
 * (e.g. in a high-tier chest with several upgrade slots) combine: only the
 * best one applies, they don't stack multiplicatively.
 */
public enum UpgradeType {

    /**
     * Only accepts item types explicitly configured on the installed item
     * itself (right-click the air while holding it — see {@code
     * FilterConfigListener}); accepts anything if none configured yet.
     */
    FILTER("Filtro", 0),

    STACK_COPPER("Stack de Cobre", 1.5),
    STACK_IRON("Stack de Ferro", 2.0),
    STACK_GOLD("Stack de Ouro", 4.0),
    STACK_DIAMOND("Stack de Diamante", 8.0),
    STACK_NETHERITE("Stack de Netherite", 16.0);

    private final String displayName;
    private final double stackMultiplier;

    UpgradeType(String displayName, double stackMultiplier) {
        this.displayName = displayName;
        this.stackMultiplier = stackMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    /** {@code true} for the {@code STACK_*} tiers, {@code false} for {@link #FILTER}. */
    public boolean isStackUpgrade() {
        return stackMultiplier > 0;
    }

    /** How much this tier multiplies a slot's normal max stack size by. Only meaningful when {@link #isStackUpgrade()}. */
    public double stackMultiplier() {
        return stackMultiplier;
    }

    /** Namespaced key suffix conventionally used for this upgrade's recipe/config entries, e.g. {@code "upgrade_stack_copper"}. */
    public String key() {
        return "upgrade_" + name().toLowerCase();
    }

    /**
     * The Stack tier one step below this one, if any — {@link UpgradeRegistry}
     * requires it (instead of a Hopper) as this tier's crafting ingredient, so
     * upgrading a chest's Stack upgrade consumes the one it replaces. Copper
     * has no previous tier (it's the entry point) and Filter isn't a Stack
     * tier at all, so both return empty.
     */
    public Optional<UpgradeType> previousStackTier() {
        return switch (this) {
            case STACK_IRON -> Optional.of(STACK_COPPER);
            case STACK_GOLD -> Optional.of(STACK_IRON);
            case STACK_DIAMOND -> Optional.of(STACK_GOLD);
            case STACK_NETHERITE -> Optional.of(STACK_DIAMOND);
            default -> Optional.empty();
        };
    }
}
