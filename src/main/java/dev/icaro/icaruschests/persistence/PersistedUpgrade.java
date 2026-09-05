package dev.icaro.icaruschests.persistence;

/**
 * One row of {@code chest_upgrade}: the raw {@code UpgradeType.name()} and
 * its optional serialized extra data (e.g. a Filter's accepted-materials
 * list — see {@code UpgradeRegistry}). Kept type-agnostic (plain strings, not
 * {@code UpgradeType}) since {@code persistence} must not depend on {@code
 * upgrade}, which already depends back on it.
 */
public record PersistedUpgrade(String upgradeType, String dataJson) {
}
