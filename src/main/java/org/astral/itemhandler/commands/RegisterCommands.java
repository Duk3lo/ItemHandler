package org.astral.itemhandler.commands;

import org.astral.itemhandler.ItemHandler;
import org.bukkit.command.PluginCommand;

import java.util.Objects;

public final class RegisterCommands {

    public static void registerAll(ItemHandler plugin) {
        ItemHandlerCommand itemHandlerCommand = new ItemHandlerCommand(plugin);
        PluginCommand command = Objects.requireNonNull(plugin.getCommand("itemhandler"),
                "Error: command 'itemhandler' is not defined in plugin.yml");

        command.setExecutor(itemHandlerCommand);
        command.setTabCompleter(itemHandlerCommand);
    }
}