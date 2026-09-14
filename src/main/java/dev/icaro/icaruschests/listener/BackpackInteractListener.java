package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.BackpackManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.model.IcarusBackpack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
 * <p>A backpack is always a custom-head-textured item ({@code BackpackRegistry}) — never a
 * vanilla {@code Material.BUNDLE} — so there's no bundle insert/extract mechanic to guard
 * against here at all; a plain right-click on a head has no special vanilla behavior.
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
