package dev.icaro.icaruschests;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.command.IcarusChestsCommand;
import dev.icaro.icaruschests.listener.ChestBreakListener;
import dev.icaro.icaruschests.listener.ChestGuiListener;
import dev.icaro.icaruschests.listener.ChestInteractListener;
import dev.icaro.icaruschests.listener.ChestPlaceListener;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the IcarusChests plugin.
 *
 * <p>MVP scope: tiered storage chests with extra capacity, reusing the vanilla
 * chest block and differentiating tiers through a custom inventory GUI. See
 * the project plan for the full architecture and milestone roadmap.
 */
public final class IcarusChestsPlugin extends JavaPlugin {

    private ChestManager chestManager;

    @Override
    public void onEnable() {
        NamespacedKeys.init(this);
        chestManager = new ChestManager();

        registerCommands();
        registerListeners();

        getLogger().info("IcarusChests habilitado (v" + getPluginMeta().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        getLogger().info("IcarusChests desabilitado.");
    }

    private void registerCommands() {
        var command = getCommand("icaruschests");
        if (command != null) {
            IcarusChestsCommand executor = new IcarusChestsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ChestPlaceListener(chestManager), this);
        pluginManager.registerEvents(new ChestBreakListener(chestManager), this);
        pluginManager.registerEvents(new ChestInteractListener(chestManager), this);
        pluginManager.registerEvents(new ChestGuiListener(chestManager), this);
    }

    public ChestManager getChestManager() {
        return chestManager;
    }
}
