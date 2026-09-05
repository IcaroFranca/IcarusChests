package dev.icaro.icaruschests.upgrade;

/**
 * A pluggable upgrade a chest's dedicated upgrade slots can hold. New types
 * go here; {@link UpgradeRegistry} builds each one's item/recipe and {@code
 * ChestGuiListener}/{@code GuiFactory} wire in whatever gameplay effect they
 * have.
 */
public enum UpgradeType {

    /** While installed, the chest only accepts one item type at a time (whichever is already stored). */
    FILTER("Filtro"),

    /** While installed, a slot already holding an item can keep accepting more of the same type past the normal stack limit. */
    STACK("Stack");

    private final String displayName;

    UpgradeType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Namespaced key suffix conventionally used for this upgrade's recipe/config entries, e.g. {@code "upgrade_filter"}. */
    public String key() {
        return "upgrade_" + name().toLowerCase();
    }
}
