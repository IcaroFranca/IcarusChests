package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.BackpackManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.model.IcarusBackpack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Opens a backpack on literally any click while holding it — left or right, on a block, on
 * nothing (air), or on an entity — a deliberate choice (see the project's own planning notes):
 * holding a backpack means you're about to check it, full stop, so it takes over mining and
 * attacking too rather than only right-click. {@code SpecialItemProtectionListener} separately
 * keeps it from ever being placed as a block in the first place.
 *
 * <p>{@link #onBundleClickAttempt} is what actually makes {@code BackpackRegistry#refreshPreview}
 * safe to give the item real, vanilla-visible bundle contents at all: without it, right-clicking
 * a bundle-shaped backpack in *any* inventory screen (this plugin's own GUI, the player's own
 * inventory, a chest, anywhere) would trigger Minecraft's own bundle insert/extract mechanic —
 * completely outside this plugin, and a real, confirmed duplication vector in an earlier version
 * (see that method's own Javadoc). Every other click type (left-click, shift-click, drag, drop,
 * hotbar swap) is untouched and still moves the item around an inventory completely normally.
 */
public final class BackpackInteractListener implements Listener {

    private final BackpackManager backpackManager;

    public BackpackInteractListener(BackpackManager backpackManager) {
        this.backpackManager = backpackManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return; // stepping on a pressure plate/tripwire isn't a "click" — never open for that
        }
        ItemStack item = event.getItem();
        if (BackpackManager.idOf(item).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        openBackpack(event.getPlayer(), item);
    }

    /** Attacking with a backpack in hand opens it instead of dealing damage — the same "any click" rule extended to combat. */
    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (BackpackManager.idOf(item).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        openBackpack(player, item);
    }

    /**
     * Cancels a plain right-click ({@code ClickType.RIGHT} — vanilla's own bundle logic isn't
     * gated behind any other click type: not shift-click, drag, drop, or a hotbar-number swap)
     * touching a bundle-shaped backpack in *any* inventory, as either the clicked slot's item or
     * the item held on the cursor — that covers both directions vanilla's mechanic supports
     * (right-clicking a slotted bundle to extract/insert with an empty/held cursor, and
     * right-clicking *any other* slot while holding a bundle on the cursor to insert that slot's
     * stack into it). A custom-head-textured backpack is never checked for this at all — a head
     * has no bundle mechanic to protect against, and blocking its right-click would only get in
     * the way of otherwise-normal inventory management.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBundleClickAttempt(InventoryClickEvent event) {
        if (event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (isBundleBackpack(event.getCurrentItem()) || isBundleBackpack(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    private boolean isBundleBackpack(ItemStack item) {
        if (item == null || BackpackManager.idOf(item).isEmpty()) {
            return false;
        }
        Material type = item.getType();
        return type == Material.BUNDLE || type.name().endsWith("_BUNDLE");
    }

    private void openBackpack(Player player, ItemStack item) {
        Optional<IcarusBackpack> maybeBackpack = backpackManager.getOrLoad(item);
        if (maybeBackpack.isEmpty()) {
            return;
        }
        IcarusBackpack backpack = maybeBackpack.get();
        // First touch after a server restart may still be hydrating from SQLite — see
        // BackpackManager#whenReady; every other time this runs immediately.
        backpackManager.whenReady(backpack.getId(), () -> {
            if (backpack.isContentsLoadFailed()) {
                // The saved contents couldn't be read back (see StorageContainer's docs) — refusing
                // to open keeps anyone from treating the blank placeholder array as real, empty
                // contents (which a later save would then make permanent).
                player.sendMessage(Component.text(
                        "Esta mochila nao pode ser aberta: houve um erro ao carregar seu conteudo salvo. "
                                + "Nada foi apagado; chame um administrador.", NamedTextColor.RED));
                return;
            }
            GuiFactory.open(player, backpack);
        });
    }
}
