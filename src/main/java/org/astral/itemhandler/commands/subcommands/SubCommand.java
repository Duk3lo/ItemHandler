package org.astral.itemhandler.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.List;

public interface SubCommand {

    void execute(@NonNull CommandSender sender, String[] args);

    List<String> tabComplete(@NonNull CommandSender sender, String[] args);

    @NonNull String getName();
}