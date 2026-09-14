package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.chest.ChestTaggingService;
import dev.icaro.icaruschests.chest.StarterChestRegistry;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.upgrade.TierUpgradeService;
import dev.icaro.icaruschests.upgrade.UpgradeKitRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Opens the custom tiered GUI when a player right-clicks an IcarusChest, applies an upgrade kit
 * instead when they shift-right-click while holding one that matches the chest's next tier, or —
 * for a plain, not-yet-tagged chest — turns it into a brand-new IcarusChest at {@link
 * ChestTier#NORMAL} when they shift-right-click it while holding a {@link StarterChestRegistry}
 * kit. The starter kit is a custom head, never itself placed as a block — same interaction shape
 * as any tier upgrade kit, just applied to a plain chest instead of an already-tagged one.
 */
public final class ChestInteractListener implements Listener {

    private final ChestManager chestManager;
    private final ChestTaggingService taggingService;
    private final TierUpgradeService tierUpgradeService;

    public ChestInteractListener(ChestManager chestManager, ChestTaggingService taggingService,
                                  TierUpgradeService tierUpgradeService) {
        this.chestManager = chestManager;
        this.taggingService = taggingService;
        this.tierUpgradeService = tierUpgradeService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            // The hand check avoids handling this event twice per click
            // (Bukkit fires it once per hand for a single right-click).
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();

        if (!chestManager.isTaggedChest(block)) {
            if (player.isSneaking() && StarterChestRegistry.isStarterChest(inHand)) {
                event.setCancelled(true);
                handleStarterKitApply(player, block, inHand);
            }
            return; // a plain chest otherwise: let vanilla open its own GUI normally
        }

        chestManager.getOrLoadFromBlock(block).ifPresent(chest -> {
            event.setCancelled(true);

            // Waits for contents/upgrades hydration to finish before touching the chest at all —
            // a no-op wait except right after a fresh server start (see ChestManager's docs); this
            // is what stops a same-tick GUI open or kit application from racing that async load.
            chestManager.whenReady(chest.getId(), () -> {
                if (chest.isContentsLoadFailed()) {
                    // The saved contents couldn't be read back (see StorageContainer's docs) — this
                    // chest is locked out of the GUI and out of kit upgrades entirely rather than
                    // letting anyone treat its blank placeholder array as real, empty contents.
                    player.sendMessage(Component.text(
                            "Este bau nao pode ser aberto: houve um erro ao carregar seu conteudo salvo. "
                                    + "Nada foi apagado; chame um administrador.", NamedTextColor.RED));
                    return;
                }
                if (player.isSneaking() && UpgradeKitRegistry.targetTierOf(inHand).isPresent()) {
                    handleUpgradeAttempt(player, chest, inHand);
                    return;
                }

                // Always opens scrolled to the top; remembering a player's last
                // scroll position is a nice-to-have left for later polish.
                GuiFactory.open(player, chest);
            });
        });
    }

    /**
     * Turns {@code block} — a plain, not-yet-tagged chest — into a brand-new IcarusChest at {@link
     * ChestTier#NORMAL}, consuming one starter kit. Rejects outright (no kit consumed) if {@code
     * block} is directly adjacent to a plain chest that isn't one of ours ({@code
     * ChestPlaceListener} already keeps this from arising when the plain chest is placed second,
     * but the starter kit can still be the one to introduce it if applied carelessly) or to an
     * IcarusChest of a different tier — vanilla would still visually merge an incompatible pair
     * either way.
     */
    private void handleStarterKitApply(Player player, Block block, ItemStack kit) {
        if (taggingService.hasUntaggedChestNeighbor(block)) {
            player.sendMessage(Component.text(
                    "Nao e possivel transformar um bau encostado a um bau comum.", NamedTextColor.RED));
            return;
        }
        Optional<IcarusChest> neighborPrimary = taggingService.findAdjacentPrimary(block);
        if (neighborPrimary.isPresent() && neighborPrimary.get().getTier() != ChestTier.NORMAL) {
            player.sendMessage(Component.text(
                    "Nao e possivel unir a um bau de tier " + neighborPrimary.get().getTier().displayName() + ".",
                    NamedTextColor.RED));
            return;
        }

        taggingService.tagChestBlock(block, neighborPrimary);
        kit.setAmount(kit.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage(Component.text("Bau transformado num IcarusChest!", NamedTextColor.GREEN));
    }

    private void handleUpgradeAttempt(Player player, IcarusChest chest, ItemStack kit) {
        if (!tierUpgradeService.tryUpgrade(chest, kit)) {
            player.sendMessage(Component.text("Este kit nao serve para o proximo tier deste bau.", NamedTextColor.RED));
            return;
        }

        kit.setAmount(kit.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage(Component.text("Bau evoluido para " + chest.getTier().displayName() + "!", NamedTextColor.GREEN));
    }
}
