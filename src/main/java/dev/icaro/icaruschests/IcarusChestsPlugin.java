package dev.icaro.icaruschests;

import dev.icaro.icaruschests.command.IcarusChestsCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the IcarusChests plugin.
 *
 * <p>MVP scope: tiered storage chests with extra capacity, reusing the vanilla
 * chest block and differentiating tiers through a custom inventory GUI. See
 * the project plan for the full architecture and milestone roadmap.
 */
public final class IcarusChestsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        var pingCommand = getCommand("icaruschests");
        if (pingCommand != null) {
            IcarusChestsCommand executor = new IcarusChestsCommand(this);
            pingCommand.setExecutor(executor);
            pingCommand.setTabCompleter(executor);
        }

        getLogger().info("IcarusChests habilitado (v" + getPluginMeta().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        getLogger().info("IcarusChests desabilitado.");
    }
}
