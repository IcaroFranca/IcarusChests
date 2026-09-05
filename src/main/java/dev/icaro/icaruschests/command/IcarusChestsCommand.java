package dev.icaro.icaruschests.command;

import dev.icaro.icaruschests.IcarusChestsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Root command for IcarusChests. M1 only implements {@code /icaruschests ping},
 * used to verify the plugin loaded correctly. Future milestones will add
 * {@code info}, {@code reload}, {@code give} and {@code stats} subcommands.
 */
public final class IcarusChestsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("ping");

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

        sender.sendMessage(Component.text("Subcomando desconhecido: " + args[0], NamedTextColor.RED));
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
