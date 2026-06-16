package org.astral.itemhandler.events;

import org.astral.itemhandler.ItemHandler;
import org.astral.itemhandler.events.listener.ItemHandlerListener;
import org.bukkit.plugin.PluginManager;
import org.jspecify.annotations.NonNull;

public final class RegisterEvents {

    public static void registerAll(@NonNull ItemHandler plugin) {
        PluginManager manager = plugin.getServer().getPluginManager();
        manager.registerEvents(new ItemHandlerListener(plugin), plugin);
    }
}