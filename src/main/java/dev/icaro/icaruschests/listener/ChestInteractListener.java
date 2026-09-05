package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.model.IcarusChest;
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

/**
 * Opens the custom tiered GUI when a player right-clicks an IcarusChest, or
 * applies an upgrade kit instead when they shift-right-click while holding
 * one that matches the chest's next tier.
 */
public final class ChestInteractListener implements Listener {

    private final ChestManager chestManager;
    private final TierUpgradeService tierUpgradeService;

    public ChestInteractListener(ChestManager chestManager, TierUpgradeService tierUpgradeService) {
        this.chestManager = chestManager;
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

        chestManager.getOrLoadFromBlock(block).ifPresent(chest -> {
            event.setCancelled(true);
            Player player = event.getPlayer();
            ItemStack inHand = player.getInventory().getItemInMainHand();

            if (player.isSneaking() && UpgradeKitRegistry.targetTierOf(inHand).isPresent()) {
                handleUpgradeAttempt(player, chest, inHand);
                return;
            }

            // Always opens scrolled to the top; remembering a player's last
            // scroll position is a nice-to-have left for later polish.
            GuiFactory.open(player, chest);
        });
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
