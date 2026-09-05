package dev.icaro.icaruschests.upgrade;

import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Applies a validated upgrade kit to a chest: resizes its contents (never
 * losing an item, since the new capacity is always >= the old one), bumps
 * its tier, retags the block's PDC, and persists both.
 *
 * <p>Deliberately takes only the {@link IcarusChest} domain object, not a
 * {@code Block} — its own {@link IcarusChest#getLocation()} is always the
 * correct block to retag, regardless of which physical half of a double
 * chest the player actually clicked (see {@code ChestManager}'s pointer
 * resolution), so there is no double-chest special-casing needed here.
 */
public final class TierUpgradeService {

    private final ChestRepository chestRepository;
    private final Plugin plugin;

    public TierUpgradeService(ChestRepository chestRepository, Plugin plugin) {
        this.chestRepository = chestRepository;
        this.plugin = plugin;
    }

    /**
     * @return {@code true} if {@code kit} matched this chest's next tier and the upgrade was applied
     *         (caller is responsible for consuming one item from the kit stack); {@code false} if the
     *         chest is already at max tier or the kit doesn't match the required next tier.
     */
    public boolean tryUpgrade(IcarusChest chest, ItemStack kit) {
        Optional<ChestTier> next = chest.getTier().next();
        if (next.isEmpty()) {
            return false;
        }
        Optional<ChestTier> kitTarget = UpgradeKitRegistry.targetTierOf(kit);
        if (kitTarget.isEmpty() || kitTarget.get() != next.get()) {
            return false;
        }

        apply(chest, next.get());
        return true;
    }

    private void apply(IcarusChest chest, ChestTier newTier) {
        chest.setContents(Arrays.copyOf(chest.getContents(), newTier.totalCapacity()));
        chest.setTier(newTier);
        chest.setDirty(true);

        retag(chest.getLocation().toBlock(), newTier);

        chestRepository.insert(chest).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir upgrade de tier do bau " + chest.getId(), ex);
            return null;
        });
        chestRepository.saveContents(chest).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir conteudo pos-upgrade do bau " + chest.getId(), ex);
            return null;
        });
    }

    private void retag(Block block, ChestTier newTier) {
        if (!(block.getState() instanceof TileState state)) {
            return;
        }
        state.getPersistentDataContainer().set(NamespacedKeys.TIER, PersistentDataType.INTEGER, newTier.ordinal());
        state.update(true);
    }
}
