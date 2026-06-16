package org.astral.itemhandler.events.listener;

import org.astral.itemhandler.ItemHandler;
import org.astral.itemhandler.manager.ItemController;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

public final class ItemHandlerListener implements Listener {

    private final ItemController controller;

    public ItemHandlerListener(@NonNull ItemHandler plugin) {
        this.controller = plugin.getItemController();
    }

    @EventHandler
    public void onJoin(@NonNull PlayerJoinEvent event) {
        controller.applyToPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        controller.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(@NonNull PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!controller.isManaged(stack)) return;

        String id = controller.getManagedId(stack);
        if (id != null && controller.isProtectedByConfig(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(@NonNull PlayerSwapHandItemsEvent event) {
        if (controller.isManaged(event.getMainHandItem()) || controller.isManaged(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(@NonNull InventoryClickEvent event) {
        if (controller.isManaged(event.getCurrentItem()) || controller.isManaged(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(@NonNull InventoryDragEvent event) {
        if (controller.isManaged(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(@NonNull PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!controller.isManaged(item)) return;

        String id = controller.getManagedId(item);
        if (id != null) {
            controller.handleClick(event.getPlayer(), id);
        }
    }
}