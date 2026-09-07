package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.backpack.BackpackRegistry;
import dev.icaro.icaruschests.chest.BackpackManager;
import dev.icaro.icaruschests.model.IcarusBackpack;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.tier.BackpackTier;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Registers the backpack crafting recipes and computes their actual result on the fly — unlike an
 * upgrade kit's recipe (a fixed, shared item), a backpack's result carries an id unique to that
 * physical instance, which a plain {@link ShapedRecipe}'s static result item can't express:
 * <ul>
 *   <li>the base recipe (8 Leather + 1 Chest → the first, {@link BackpackTier#LEATHER} backpack)
 *       needs a brand-new random id every time — two backpacks crafted this way must never share
 *       a storage row;</li>
 *   <li>every later tier's recipe (the previous backpack + 8 of that tier's ore → the next
 *       backpack) needs the exact opposite: the *same* id the consumed backpack already had, so
 *       its stored contents carry over — see {@code BackpackManager}/{@code
 *       ChestRepository#updateBackpackTier} for how that's persisted without needing to read the
 *       old contents back first.</li>
 * </ul>
 * Both are handled by recomputing the recipe's result in {@link #onPrepareCraft} (whenever the
 * crafting grid changes, mirroring {@link UpgradeRecipeValidationListener}'s own "recompute or
 * clear the result" approach for tier-locked kits) and reading that same computed result back off
 * the slot in {@link #onCraft} to know what to actually register/persist.
 */
public final class BackpackRecipeListener implements Listener {

    private static final String BASE_RECIPE_KEY = "backpack_leather";

    private final Plugin plugin;
    private final BackpackRegistry backpackRegistry;
    private final BackpackManager backpackManager;
    private final ChestRepository chestRepository;

    public BackpackRecipeListener(Plugin plugin, BackpackRegistry backpackRegistry,
                                   BackpackManager backpackManager, ChestRepository chestRepository) {
        this.plugin = plugin;
        this.backpackRegistry = backpackRegistry;
        this.backpackManager = backpackManager;
        this.chestRepository = chestRepository;
    }

    /** Registers the base recipe and every tier-up recipe. One recipe's problem is logged and skipped, same reasoning as {@code UpgradeKitRegistry}. */
    public void registerRecipes() {
        try {
            registerBaseRecipe();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao registrar a receita da mochila de couro", e);
        }
        for (BackpackTier tier : BackpackTier.values()) {
            tier.upgradeMaterial().ifPresent(ore -> {
                try {
                    registerTierUpRecipe(tier, ore);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Falha ao registrar a receita da mochila de " + tier.displayName(), e);
                }
            });
        }
    }

    private void registerBaseRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, BASE_RECIPE_KEY);
        ShapedRecipe recipe = new ShapedRecipe(key, placeholderResult(BackpackTier.LEATHER));
        recipe.shape("MMM", "MCM", "MMM");
        recipe.setIngredient('M', Material.LEATHER);
        recipe.setIngredient('C', Material.CHEST);
        plugin.getServer().addRecipe(recipe);
    }

    private void registerTierUpRecipe(BackpackTier tier, Material ore) {
        NamespacedKey key = tierUpRecipeKey(plugin, tier);
        ShapedRecipe recipe = new ShapedRecipe(key, placeholderResult(tier));
        recipe.shape("MMM", "MCM", "MMM");
        recipe.setIngredient('M', ore);
        // Broad by material only (a backpack's exact id can't be expressed as a RecipeChoice) —
        // onPrepareCraft below does the real "is this actually the right previous tier" check,
        // same pattern as UpgradeRecipeValidationListener's stack-tier kits.
        recipe.setIngredient('C', new RecipeChoice.MaterialChoice(Material.BUNDLE, Material.PLAYER_HEAD));
        plugin.getServer().addRecipe(recipe);
    }

    /**
     * Never actually handed to a player as-is: {@link #onPrepareCraft} always overwrites the
     * output slot with the real computed result before it can be taken out. Bukkit still requires
     * a fixed template item to register a {@link ShapedRecipe} at all, so this only ever satisfies
     * that — the nil UUID makes it obvious in a debugger/log if it ever leaked out somehow.
     */
    private ItemStack placeholderResult(BackpackTier tier) {
        return backpackRegistry.createBackpack(tier, new UUID(0L, 0L));
    }

    private static NamespacedKey tierUpRecipeKey(Plugin plugin, BackpackTier tier) {
        return new NamespacedKey(plugin, "backpack_tier_" + tier.name().toLowerCase());
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !plugin.getName().equalsIgnoreCase(keyed.getKey().getNamespace())) {
            return;
        }
        String recipeName = keyed.getKey().getKey();
        CraftingInventory inventory = event.getInventory();

        if (recipeName.equals(BASE_RECIPE_KEY)) {
            inventory.setResult(backpackRegistry.createBackpack(BackpackTier.LEATHER, UUID.randomUUID()));
            return;
        }

        Optional<BackpackTier> targetTier = parseTierUpKey(recipeName);
        if (targetTier.isEmpty()) {
            return; // not one of ours
        }
        Optional<BackpackTier> requiredPreviousTier = BackpackTier.byOrdinal(targetTier.get().ordinal() - 1);
        if (requiredPreviousTier.isEmpty()) {
            return; // shouldn't happen: every tier-up recipe's target has a previous tier by construction
        }

        Optional<UUID> previousBackpackId = findMatchingBackpack(inventory, requiredPreviousTier.get());
        if (previousBackpackId.isEmpty()) {
            inventory.setResult(null); // wrong/missing previous-tier backpack in the grid: no result
            return;
        }
        inventory.setResult(backpackRegistry.createBackpack(targetTier.get(), previousBackpackId.get()));
    }

    private Optional<UUID> findMatchingBackpack(CraftingInventory inventory, BackpackTier requiredTier) {
        for (ItemStack item : inventory.getMatrix()) {
            if (BackpackRegistry.tierOf(item).filter(found -> found == requiredTier).isPresent()) {
                Optional<UUID> id = BackpackManager.idOf(item);
                if (id.isPresent()) {
                    return id;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<BackpackTier> parseTierUpKey(String recipeName) {
        String prefix = "backpack_tier_";
        if (!recipeName.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(BackpackTier.valueOf(recipeName.substring(prefix.length()).toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The output slot at this point holds exactly what {@link #onPrepareCraft} last computed —
     * carrying either a fresh random id (base recipe) or the previous backpack's preserved id
     * (a tier-up) — so this never needs to re-scan the crafting grid itself.
     */
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        Optional<BackpackTier> tier = BackpackRegistry.tierOf(result);
        if (tier.isEmpty()) {
            return;
        }
        Optional<UUID> id = BackpackManager.idOf(result);
        if (id.isEmpty()) {
            return;
        }

        if (tier.get() == BackpackTier.LEATHER) {
            IcarusBackpack backpack = new IcarusBackpack(id.get(), BackpackTier.LEATHER);
            backpackManager.register(backpack);
            chestRepository.insertBackpack(backpack).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "Falha ao persistir mochila recem-criada " + id.get(), ex);
                return null;
            });
        } else {
            backpackManager.bumpTierIfCached(id.get(), tier.get());
            chestRepository.updateBackpackTier(id.get(), tier.get().ordinal()).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "Falha ao persistir upgrade de tier da mochila " + id.get(), ex);
                return null;
            });
        }
    }
}
