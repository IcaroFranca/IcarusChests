package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.backpack.BackpackRegistry;
import dev.icaro.icaruschests.chest.BackpackManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.model.IcarusBackpack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Opens a backpack on literally any click while holding it — left or right, on a block, on
 * nothing (air), or on an entity — a deliberate choice (see the project's own planning notes):
 * holding a backpack means you're about to check it, full stop, so it takes over mining and
 * attacking too rather than only right-click. {@code SpecialItemProtectionListener} separately
 * keeps it from ever being placed as a block in the first place.
 *
 * <p>Also the safety net for a real duplication bug in an earlier version of this plugin, which
 * briefly gave backpacks a genuine (vanilla-functional, right-click-extractable/insertable)
 * bundle content list for tooltip preview purposes — see {@code BackpackRegistry#sanitize} for
 * the fix itself. Every touch here ({@link #onInteract}, {@link #onAttack}, and — since a player
 * could be sitting on an already-poisoned backpack from before this fix ever gets applied — every
 * {@link #onJoin} and the equivalent sweep {@code IcarusChestsPlugin} runs at {@code onEnable} for
 * whoever's already online) strips any such contents on sight, before the item could ever be
 * clicked in some other inventory screen and exploited.
 */
public final class BackpackInteractListener implements Listener {

    private final BackpackManager backpackManager;
    private final BackpackRegistry backpackRegistry;

    public BackpackInteractListener(BackpackManager backpackManager, BackpackRegistry backpackRegistry) {
        this.backpackManager = backpackManager;
        this.backpackRegistry = backpackRegistry;
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
        backpackRegistry.sanitize(item);
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
        backpackRegistry.sanitize(item);
        event.setCancelled(true);
        openBackpack(player, item);
    }

    /** Catches a player who logs in already carrying a backpack poisoned by an earlier plugin version — see the class Javadoc. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sanitizeInventory(event.getPlayer());
    }

    /** Strips any real bundle contents off every backpack in {@code player}'s inventory (main + hotbar + offhand). Safe to call unconditionally. */
    public void sanitizeInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (BackpackManager.idOf(item).isPresent()) {
                backpackRegistry.sanitize(item);
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (BackpackManager.idOf(offHand).isPresent()) {
            backpackRegistry.sanitize(offHand);
        }
    }

    private void openBackpack(Player player, ItemStack item) {
        Optional<IcarusBackpack> maybeBackpack = backpackManager.getOrLoad(item);
        if (maybeBackpack.isEmpty()) {
            return;
        }
        IcarusBackpack backpack = maybeBackpack.get();
        // First touch after a server restart may still be hydrating from SQLite — see
        // BackpackManager#whenReady; every other time this runs immediately.
        backpackManager.whenReady(backpack.getId(), () -> GuiFactory.open(player, backpack));
    }
}
