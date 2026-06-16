package org.astral.itemhandler;

import org.astral.itemhandler.commands.RegisterCommands;
import org.astral.itemhandler.config.ItemHandlerConfig;
import org.astral.itemhandler.events.RegisterEvents;
import org.astral.itemhandler.manager.ItemController;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemHandler extends JavaPlugin {

    private ItemHandlerConfig configManager;
    private ItemController itemController;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ItemHandlerConfig(this);
        configManager.load();

        itemController = new ItemController(this, configManager);

        RegisterEvents.registerAll(this);
        RegisterCommands.registerAll(this);

        itemController.applyToOnlinePlayers();
    }

    @Override
    public void onDisable() {
        if (itemController != null) {
            itemController.shutdown();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        configManager.load();
        itemController.reloadAndReapply();
    }

    public ItemController getItemController() {
        return itemController;
    }
}