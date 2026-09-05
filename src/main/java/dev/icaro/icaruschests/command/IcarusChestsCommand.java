package dev.icaro.icaruschests.command;

import dev.icaro.icaruschests.IcarusChestsPlugin;
import dev.icaro.icaruschests.model.IcarusChest;
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

import java.util.List;
import java.util.Optional;

/**
 * Root command for IcarusChests. Current subcommands: {@code ping} (health
 * check) and {@code info} (debug: reports the tier of the chest the player is
 * looking at). Future milestones will add {@code reload}, {@code give} and
 * {@code stats}.
 */
public final class IcarusChestsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("ping", "info");
    private static final int INFO_MAX_DISTANCE = 6;

    private final IcarusChestsPlugin plugin;

    public IcarusChestsCommand(IcarusChestsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("ping")) {
            sender.sendMessage(Component.text("IcarusChests v" + plugin.getPluginMeta().getVersion()
                    + " está online.", NamedTextColor.GOLD));
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            return handleInfo(sender);
        }

        sender.sendMessage(Component.text("Subcomando desconhecido: " + args[0], NamedTextColor.RED));
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
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
        Location loc = target.getLocation();
        sender.sendMessage(Component.text("Baú IcarusChests em (" + loc.getBlockX() + ", " + loc.getBlockY() + ", "
                + loc.getBlockZ() + ")", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  Tier: " + c.getTier().displayName()
                + " (" + c.getTier().totalCapacity() + " slots, " + c.getTier().pages() + " página(s))",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  Id: " + c.getId(), NamedTextColor.GRAY));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS;
        }
        return List.of();
    }
}
