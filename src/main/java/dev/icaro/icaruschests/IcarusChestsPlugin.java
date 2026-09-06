package dev.icaro.icaruschests;

import dev.icaro.icaruschests.chest.AutosaveTask;
import dev.icaro.icaruschests.chest.ChestDestructionHandler;
import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.command.IcarusChestsCommand;
import dev.icaro.icaruschests.config.ConfigManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.gui.IcarusChestHolder;
import dev.icaro.icaruschests.gui.RecipeBookRegistry;
import dev.icaro.icaruschests.listener.ChestBreakListener;
import dev.icaro.icaruschests.listener.ChestGuiListener;
import dev.icaro.icaruschests.listener.ChestInteractListener;
import dev.icaro.icaruschests.listener.ChestPlaceListener;
import dev.icaro.icaruschests.listener.ChestProtectionListener;
import dev.icaro.icaruschests.listener.FilterConfigListener;
import dev.icaro.icaruschests.listener.RecipeBookListener;
import dev.icaro.icaruschests.listener.UpgradeRecipeValidationListener;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.persistence.Database;
import dev.icaro.icaruschests.upgrade.TierUpgradeService;
import dev.icaro.icaruschests.upgrade.UpgradeKitRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletionException;

/**
 * Entry point for the IcarusChests plugin.
 *
 * <p>MVP scope: tiered storage chests with extra capacity, reusing the vanilla
 * chest block and differentiating tiers through a custom inventory GUI. See
 * the project plan for the full architecture and milestone roadmap.
 */
public final class IcarusChestsPlugin extends JavaPlugin {

    private static final long SHUTDOWN_FLUSH_TIMEOUT_SECONDS = 5;

    private Database database;
    private ChestRepository chestRepository;
    private ChestManager chestManager;
    private ConfigManager configManager;
    private AutosaveTask autosaveTask;
    private BukkitTask autosaveTaskHandle;
    private UpgradeKitRegistry upgradeKitRegistry;
    private UpgradeRegistry upgradeRegistry;
    private RecipeBookRegistry recipeBookRegistry;
    private TierUpgradeService tierUpgradeService;
    private ChestDestructionHandler destructionHandler;

    @Override
    public void onEnable() {
        NamespacedKeys.init(this);
        configManager = new ConfigManager(this);
        configManager.load();
        GuiFactory.init(configManager);

        if (!openDatabase()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        chestRepository = new ChestRepository(database);
        upgradeKitRegistry = new UpgradeKitRegistry(this, configManager);
        upgradeRegistry = new UpgradeRegistry(this, configManager);
        recipeBookRegistry = new RecipeBookRegistry(upgradeKitRegistry, upgradeRegistry);
        chestManager = new ChestManager(chestRepository, upgradeRegistry, this);
        autosaveTask = new AutosaveTask(chestManager, chestRepository, getLogger());
        destructionHandler = new ChestDestructionHandler(chestManager, chestRepository, upgradeKitRegistry, this);
        tierUpgradeService = new TierUpgradeService(chestRepository, this);

        // Commands/listeners are registered before recipes: recipe registration reaches out to
        // config.yml-driven custom head textures and is the riskiest step here, and each recipe
        // is already isolated against throwing (see registerRecipes()) — but registering it last
        // means the plugin's core functionality is guaranteed to come up regardless either way.
        registerCommands();
        registerListeners();
        rescheduleAutosave();
        upgradeKitRegistry.registerRecipes();
        upgradeRegistry.registerRecipes();

        getLogger().info("IcarusChests habilitado (v" + getPluginMeta().getVersion() + ").");
    }

    /** Reloads {@code config.yml} and applies the (possibly changed) autosave interval immediately. */
    public void reloadPluginConfig() {
        configManager.load();
        rescheduleAutosave();
    }

    private void rescheduleAutosave() {
        if (autosaveTaskHandle != null) {
            autosaveTaskHandle.cancel();
        }
        long period = configManager.autosaveIntervalTicks();
        autosaveTaskHandle = getServer().getScheduler().runTaskTimer(this, autosaveTask, period, period);
    }

    @Override
    public void onDisable() {
        closeOpenChestGuis();
        if (autosaveTask != null) {
            autosaveTask.flush(SHUTDOWN_FLUSH_TIMEOUT_SECONDS);
        }
        if (database != null) {
            database.close(SHUTDOWN_FLUSH_TIMEOUT_SECONDS);
        }
        getLogger().info("IcarusChests desabilitado.");
    }

    /** Blocks briefly at startup so the schema is guaranteed ready before any listener runs. */
    private boolean openDatabase() {
        database = new Database(this);
        try {
            database.open().join();
            return true;
        } catch (CompletionException e) {
            getLogger().severe("Nao foi possivel abrir o banco SQLite do IcarusChests: " + e.getCause());
            return false;
        }
    }

    /** Forces any player currently viewing a tiered chest GUI to close it, so its edits are synced before the final flush. */
    private void closeOpenChestGuis() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof IcarusChestHolder) {
                player.closeInventory();
            }
        }
    }

    private void registerCommands() {
        var command = getCommand("icaruschests");
        if (command != null) {
            IcarusChestsCommand executor = new IcarusChestsCommand(this, upgradeKitRegistry, recipeBookRegistry);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ChestPlaceListener(chestManager, chestRepository, this), this);
        pluginManager.registerEvents(new ChestBreakListener(chestManager, destructionHandler, chestRepository, this), this);
        pluginManager.registerEvents(new ChestInteractListener(chestManager, tierUpgradeService), this);
        pluginManager.registerEvents(new ChestGuiListener(chestManager, chestRepository, this), this);
        pluginManager.registerEvents(new ChestProtectionListener(chestManager, destructionHandler), this);
        pluginManager.registerEvents(new FilterConfigListener(), this);
        pluginManager.registerEvents(new RecipeBookListener(recipeBookRegistry), this);
        pluginManager.registerEvents(new UpgradeRecipeValidationListener(), this);
    }

    public ChestManager getChestManager() {
        return chestManager;
    }
}
