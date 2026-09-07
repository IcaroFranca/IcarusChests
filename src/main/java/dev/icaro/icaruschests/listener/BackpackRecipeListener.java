package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.backpack.BackpackRegistry;
import dev.icaro.icaruschests.chest.BackpackManager;
import dev.icaro.icaruschests.model.IcarusBackpack;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.tier.BackpackTier;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
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
    private static final String RECOLOR_RECIPE_KEY = "backpack_recolor";

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
        try {
            registerRecolorRecipe();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao registrar a receita de colorir a mochila", e);
        }
    }

    /**
     * A backpack + any single dye → the same backpack, recolored — deliberately our own recipe
     * rather than relying on vanilla's own bundle-recoloring (unconfirmed whether/how it behaves
     * on the server versions this plugin targets, and what it would do to {@code BACKPACK_ID}/
     * {@code BACKPACK_TIER} if it rebuilt the item's meta from scratch): this way the exact
     * behavior — same id, same tier, same bundle-content preview, only the {@link Material}
     * itself changes — is fully ours to guarantee. {@code Material.valueOf} (not a direct enum
     * constant) for every colored bundle variant, since those are a newer addition than this
     * plugin's minimum Paper API target — a server whose Bukkit build predates them just never
     * registers that color, rather than failing to compile against it at all.
     */
    private void registerRecolorRecipe() {
        List<Material> backpackMaterials = new ArrayList<>();
        backpackMaterials.add(Material.BUNDLE);
        backpackMaterials.add(Material.PLAYER_HEAD);
        List<Material> dyeMaterials = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            bundleMaterialFor(color).ifPresent(backpackMaterials::add);
            dyeMaterials.add(dyeMaterialFor(color));
        }
        NamespacedKey key = new NamespacedKey(plugin, RECOLOR_RECIPE_KEY);
        ShapelessRecipe recipe = new ShapelessRecipe(key, placeholderResult(BackpackTier.LEATHER));
        recipe.addIngredient(new RecipeChoice.MaterialChoice(backpackMaterials));
        recipe.addIngredient(new RecipeChoice.MaterialChoice(dyeMaterials));
        plugin.getServer().addRecipe(recipe);
    }

    /** The colored bundle variant for {@code color}, if this server's Bukkit API declares one (added after this plugin's minimum target version). */
    private Optional<Material> bundleMaterialFor(DyeColor color) {
        try {
            return Optional.of(Material.valueOf(color.name() + "_BUNDLE"));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Dye materials have existed since long before colored bundles, so unlike {@link #bundleMaterialFor} this never needs to be optional. */
    private Material dyeMaterialFor(DyeColor color) {
        return Material.valueOf(color.name() + "_DYE");
    }

    private Optional<DyeColor> dyeColorOf(Material material) {
        for (DyeColor color : DyeColor.values()) {
            if (dyeMaterialFor(color) == material) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
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

        if (recipeName.equals(RECOLOR_RECIPE_KEY)) {
            inventory.setResult(computeRecolorResult(inventory).orElse(null));
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

    /** {@code inventory}'s 2-item grid recolored, if it's a valid (backpack, dye) pair — empty if either slot doesn't match, the backpack is a custom head (no color to change), or this server's API predates that color's bundle variant. */
    private Optional<ItemStack> computeRecolorResult(CraftingInventory inventory) {
        ItemStack backpackItem = null;
        DyeColor chosenColor = null;
        for (ItemStack item : inventory.getMatrix()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (BackpackManager.idOf(item).isPresent()) {
                backpackItem = item;
            } else {
                Optional<DyeColor> color = dyeColorOf(item.getType());
                if (color.isPresent()) {
                    chosenColor = color.get();
                }
            }
        }
        if (backpackItem == null || chosenColor == null || backpackItem.getType() == Material.PLAYER_HEAD) {
            return Optional.empty();
        }
        return bundleMaterialFor(chosenColor).map(material -> {
            ItemStack recolored = backpackItem.clone();
            recolored.setType(material); // BundleMeta carries over unchanged across every bundle color — id/tier/preview all survive
            return recolored;
        });
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
     *
     * <p>A recolor craft is deliberately excluded up front: its result keeps the exact same id
     * and tier the consumed backpack already had, so letting it fall through into the logic below
     * would either wrongly re-register it as a brand-new (blank!) backpack — if it happened to be
     * {@link BackpackTier#LEATHER} — or issue a harmless but pointless "tier bump" to the tier it's
     * already at. Recoloring changes nothing this plugin persists at all: the chosen {@link
     * Material} lives entirely on the physical item itself, the same way any other vanilla item
     * property would.
     */
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed && plugin.getName().equalsIgnoreCase(keyed.getKey().getNamespace())
                && keyed.getKey().getKey().equals(RECOLOR_RECIPE_KEY)) {
            return;
        }

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
            // Only reachable if this exact backpack was already cached this session (see
            // bumpTierIfCached) — otherwise there's no in-memory contents to preview from yet, and
            // the next real open/close cycle fills the preview in normally.
            backpackManager.get(id.get()).ifPresent(cached -> {
                backpackRegistry.refreshPreview(result, cached.getContents());
                event.setCurrentItem(result);
            });
            chestRepository.updateBackpackTier(id.get(), tier.get().ordinal()).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "Falha ao persistir upgrade de tier da mochila " + id.get(), ex);
                return null;
            });
        }
    }
}
