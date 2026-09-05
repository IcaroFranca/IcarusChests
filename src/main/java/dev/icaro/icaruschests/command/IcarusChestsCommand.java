package dev.icaro.icaruschests.command;

import dev.icaro.icaruschests.IcarusChestsPlugin;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.gui.RecipeBookRegistry;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.upgrade.UpgradeKitRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Root command for IcarusChests: {@code ping} (health check), {@code info}
 * (debug: reports the tier of the chest the player is looking at), {@code
 * recipebook} (hands over the Recipe Book item — see {@code
 * RecipeBookRegistry}), and the admin-only {@code give}/{@code reload}.
 */
public final class IcarusChestsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("ping", "info", "give", "reload", "recipebook");
    private static final int INFO_MAX_DISTANCE = 6;
    private static final String ADMIN_PERMISSION = "icaruschests.admin";
    private static final String INFO_PERMISSION = "icaruschests.info";
    private static final String RECIPE_BOOK_PERMISSION = "icaruschests.recipebook";

    private final IcarusChestsPlugin plugin;
    private final UpgradeKitRegistry upgradeKitRegistry;
    private final RecipeBookRegistry recipeBookRegistry;

    public IcarusChestsCommand(IcarusChestsPlugin plugin, UpgradeKitRegistry upgradeKitRegistry, RecipeBookRegistry recipeBookRegistry) {
        this.plugin = plugin;
        this.upgradeKitRegistry = upgradeKitRegistry;
        this.recipeBookRegistry = recipeBookRegistry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("ping")) {
            sender.sendMessage(Component.text("IcarusChests v" + plugin.getPluginMeta().getVersion()
                    + " está online.", NamedTextColor.GOLD));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "info" -> handleInfo(sender);
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "recipebook" -> handleRecipeBook(sender, args);
            default -> {
                sender.sendMessage(Component.text("Subcomando desconhecido: " + args[0], NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleInfo(CommandSender sender) {
        if (!sender.hasPermission(INFO_PERMISSION)) {
            sender.sendMessage(Component.text("Você não tem permissão para isso.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Este comando só pode ser usado por um jogador.", NamedTextColor.RED));
            return true;
        }

        Block target = player.getTargetBlockExact(INFO_MAX_DISTANCE);
        if (target == null) {
            sender.sendMessage(Component.text("Nenhum bloco mirado a até " + INFO_MAX_DISTANCE + " blocos.",
                    NamedTextColor.RED));
            return true;
        }

        Optional<IcarusChest> chest = plugin.getChestManager().getOrLoadFromBlock(target);
        if (chest.isEmpty()) {
            sender.sendMessage(Component.text("O bloco mirado não é um baú do IcarusChests.", NamedTextColor.RED));
            return true;
        }

        IcarusChest c = chest.get();
        Location loc = c.getLocation().toBlock().getLocation();
        sender.sendMessage(Component.text("Baú IcarusChests em (" + loc.getBlockX() + ", " + loc.getBlockY() + ", "
                + loc.getBlockZ() + ")", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  Tier: " + c.getTier().displayName()
                        + (c.isDoubled() ? " (duplo)" : "")
                        + " (" + c.effectiveTotalCapacity() + " slots"
                        + (GuiFactory.isScrollable(c.effectiveTotalCapacity()) ? ", rolável" : "") + ")",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  Id: " + c.getId(), NamedTextColor.GRAY));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("Você não tem permissão para isso.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /icaruschests give <tier> [jogador]", NamedTextColor.RED));
            return true;
        }

        Optional<ChestTier> tier = parseTier(args[1]);
        if (tier.isEmpty() || tier.get().upgradeMaterial().isEmpty()) {
            sender.sendMessage(Component.text("Tier inválido (Normal não tem kit de upgrade): " + args[1],
                    NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Jogador offline ou inexistente: " + args[2], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Especifique um jogador ao usar este comando pelo console.",
                    NamedTextColor.RED));
            return true;
        }

        target.getInventory().addItem(upgradeKitRegistry.createKit(tier.get()));
        sender.sendMessage(Component.text("Kit de upgrade (" + tier.get().displayName() + ") entregue a "
                + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleRecipeBook(CommandSender sender, String[] args) {
        if (!sender.hasPermission(RECIPE_BOOK_PERMISSION)) {
            sender.sendMessage(Component.text("Você não tem permissão para isso.", NamedTextColor.RED));
            return true;
        }

        Optional<Player> target = resolveTarget(sender, args, 1);
        if (target.isEmpty()) {
            return true; // resolveTarget already messaged the sender
        }

        target.get().getInventory().addItem(RecipeBookRegistry.createBookItem());
        sender.sendMessage(Component.text("Livro de Receitas entregue a " + target.get().getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    /** {@code args[argIndex]} if present, else {@code sender} itself (must be a player). Messages the sender and returns empty on failure. */
    private Optional<Player> resolveTarget(CommandSender sender, String[] args, int argIndex) {
        if (args.length > argIndex) {
            Player target = plugin.getServer().getPlayerExact(args[argIndex]);
            if (target == null) {
                sender.sendMessage(Component.text("Jogador offline ou inexistente: " + args[argIndex], NamedTextColor.RED));
                return Optional.empty();
            }
            return Optional.of(target);
        }
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        sender.sendMessage(Component.text("Especifique um jogador ao usar este comando pelo console.", NamedTextColor.RED));
        return Optional.empty();
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("Você não tem permissão para isso.", NamedTextColor.RED));
            return true;
        }
        plugin.reloadPluginConfig();
        sender.sendMessage(Component.text("Configuração recarregada.", NamedTextColor.GREEN));
        return true;
    }

    private Optional<ChestTier> parseTier(String raw) {
        for (ChestTier tier : ChestTier.values()) {
            if (tier.name().equalsIgnoreCase(raw) || tier.displayName().equalsIgnoreCase(raw)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> tiers = new ArrayList<>();
            for (ChestTier tier : ChestTier.values()) {
                if (tier.upgradeMaterial().isPresent()) {
                    tiers.add(tier.name());
                }
            }
            return tiers;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("recipebook")) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
