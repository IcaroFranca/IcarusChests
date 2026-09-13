package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeSlots;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Lets a hopper (or hopper minecart, or a dropper feeding one) push items INTO a tiered chest —
 * the one direction {@code ChestProtectionListener} otherwise leaves entirely alone, since the
 * block's real vanilla inventory is always empty by design and knows nothing about {@code
 * IcarusChest#getContents()}. Every push here is cancelled at the vanilla level either way (that
 * fake inventory must stay untouched) and re-done by hand against the chest's real, custom
 * storage instead. Pulling items back OUT of a tiered chest via hopper is a deliberate non-goal
 * for now (see {@code ChestProtectionListener}, which keeps blocking that direction).
 *
 * <p>Every insertion respects whatever Filter/Stack upgrades are installed, exactly like a
 * shift-click deposit in the chest's own GUI — a Filter rejects an item outright (the hopper
 * simply never manages to move it, same as vanilla against a full or comparator-locked
 * container), and a Stack upgrade lets a slot accept more than one item's normal max. Both share
 * {@link UpgradeSlots#insertRespectingStackCap} with {@code ChestGuiListener#handleShiftDeposit}
 * so the one rule for "how much fits right now" never drifts between the two.
 */
public final class ChestHopperListener implements Listener {

    private final ChestManager chestManager;

    public ChestHopperListener(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Optional<IcarusChest> destination = resolveTaggedChest(event.getDestination());
        if (destination.isEmpty()) {
            return; // not a tiered chest — nothing for this listener to do
        }
        event.setCancelled(true); // the real vanilla inventory must stay untouched either way
        insert(event, destination.get());
    }

    private void insert(InventoryMoveItemEvent event, IcarusChest chest) {
        if (!chestManager.isReady(chest.getId())) {
            // Still hydrating from SQLite (see ChestManager#whenReady) — inserting now risks
            // getting silently overwritten the instant hydration's own setContents() lands.
            // Extremely narrow window (right after a server restart); the hopper simply tries
            // again next tick, same as if the chest looked full for a moment.
            return;
        }
        ItemStack incoming = event.getItem();
        if (incoming == null || incoming.getType() == Material.AIR) {
            return;
        }
        Optional<ItemStack> filterItem = UpgradeSlots.filterItem(chest.getUpgrades());
        if (filterItem.isPresent()) {
            List<Material> accepted = UpgradeRegistry.filterMaterials(filterItem.get());
            if (!accepted.isEmpty() && !accepted.contains(incoming.getType())) {
                return; // filtered out — same as a vanilla hopper against a comparator-locked container
            }
        }
        // If someone has this chest open right now, flush their live view into the array first —
        // see GuiFactory#syncIfOpen — before mutating it and (below) repainting from it.
        GuiFactory.syncIfOpen(chest);
        double stackMultiplier = UpgradeSlots.bestStackMultiplier(chest.getUpgrades());
        int insertedAmount = UpgradeSlots.insertRespectingStackCap(chest.getContents(), stackMultiplier, incoming);
        if (insertedAmount <= 0) {
            return; // full even at whatever cap applies — nothing to take from the hopper
        }
        event.getSource().removeItem(withAmount(incoming, insertedAmount));
        chest.setDirty(true);
        GuiFactory.refreshIfOpen(chest);
    }

    private static ItemStack withAmount(ItemStack base, int amount) {
        ItemStack copy = base.clone();
        copy.setAmount(amount);
        return copy;
    }

    private Optional<IcarusChest> resolveTaggedChest(Inventory inventory) {
        return inventory == null ? Optional.empty() : resolveTaggedChest(inventory.getHolder());
    }

    /**
     * {@link DoubleChest#getLeftSide()}/{@code getRightSide()} return the side's {@link
     * InventoryHolder} directly (each a plain {@link Chest}), not an {@link Inventory} — so the
     * double-chest recursion has to branch on holders, not inventories. Mirrors {@code
     * ChestProtectionListener}'s own traversal for the exact same reason.
     */
    private Optional<IcarusChest> resolveTaggedChest(InventoryHolder holder) {
        if (holder instanceof DoubleChest doubleChest) {
            Optional<IcarusChest> left = resolveTaggedChest(doubleChest.getLeftSide());
            return left.isPresent() ? left : resolveTaggedChest(doubleChest.getRightSide());
        }
        if (holder instanceof Chest chestHolder) {
            Block block = chestHolder.getBlock();
            if (chestManager.isTaggedChest(block)) {
                return chestManager.getOrLoadFromBlock(block);
            }
        }
        return Optional.empty();
    }
}
