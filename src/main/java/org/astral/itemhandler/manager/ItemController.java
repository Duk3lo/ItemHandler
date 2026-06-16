package org.astral.itemhandler.manager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.astral.itemhandler.ItemHandler;
import org.astral.itemhandler.config.ItemHandlerConfig;
import org.astral.itemhandler.model.ItemCycle;
import org.astral.itemhandler.model.ManagedItem;
import org.astral.itemhandler.util.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ItemController {

    private final ItemHandler plugin;
    private final ItemHandlerConfig config;
    private final ItemFactory factory;

    private final Map<UUID, List<ScheduledTask>> cycleTasks = new HashMap<>();

    public ItemController(ItemHandler plugin, ItemHandlerConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.factory = new ItemFactory(plugin);
    }

    public void applyToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyToPlayer(player);
        }
    }

    public void applyToPlayer(Player player) {
        if (!config.isGiveItemsOnJoin()) {
            return;
        }

        player.getScheduler().run(plugin, _ -> {
            if (config.isClearInventoryOnJoin()) {
                player.getInventory().clear();
            }

            giveStaticItems(player);
            startCycles(player);
        }, null);
    }

    public void reloadAndReapply() {
        shutdown();
        applyToOnlinePlayers();
    }

    public void shutdown() {
        for (List<ScheduledTask> tasks : cycleTasks.values()) {
            for (ScheduledTask task : tasks) {
                try {
                    task.cancel();
                } catch (Throwable ignored) {
                }
            }
        }
        cycleTasks.clear();
    }

    public void removePlayer(UUID uuid) {
        List<ScheduledTask> tasks = cycleTasks.remove(uuid);
        if (tasks == null) return;

        for (ScheduledTask task : tasks) {
            try {
                task.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    private void giveStaticItems(Player player) {
        for (ManagedItem item : config.getItems().values()) {
            if (item.slot() == null) {
                continue;
            }

            ItemStack stack = factory.create(item);
            int slot = item.slot();

            if (slot >= 0 && slot < player.getInventory().getSize()) {
                player.getInventory().setItem(slot, stack);
            }
        }
    }

    private void startCycles(@NonNull Player player) {
        removePlayer(player.getUniqueId());

        List<ScheduledTask> tasks = new ArrayList<>();

        for (ItemCycle cycle : config.getCycles().values()) {
            if (!cycle.enabled() || cycle.itemIds().isEmpty()) {
                continue;
            }

            AtomicInteger index = new AtomicInteger(0);

            ScheduledTask task = player.getScheduler().runAtFixedRate(
                    plugin,
                    _ -> {
                        if (!player.isOnline()) {
                            return;
                        }

                        int current = Math.floorMod(index.getAndIncrement(), cycle.itemIds().size());
                        String itemId = cycle.itemIds().get(current);
                        ManagedItem item = config.getItem(itemId);
                        if (item == null) {
                            return;
                        }

                        ItemStack stack = factory.create(item);

                        if (cycle.slot() >= 0 && cycle.slot() < player.getInventory().getSize()) {
                            player.getInventory().setItem(cycle.slot(), stack);
                        } else {
                            player.getInventory().addItem(stack);
                        }

                        runCycleCommands(player, cycle, itemId);
                    },
                    null,
                    1L,
                    cycle.periodTicks()
            );

            if (task != null) {
                tasks.add(task);
            }
        }

        if (!tasks.isEmpty()) {
            cycleTasks.put(player.getUniqueId(), tasks);
        }
    }

    public void handleClick(Player player, String itemId) {
        ManagedItem item = config.getItem(itemId);
        if (item == null) {
            return;
        }
        executeCommands(player, item.clickPlayerCommands(), item.clickConsoleCommands(), item.id(), -1, null);
    }

    private void runCycleCommands(Player player, @NonNull ItemCycle cycle, String itemId) {
        executeCommands(player, cycle.changePlayerCommands(), cycle.changeConsoleCommands(), itemId, cycle.slot(), cycle.id());
    }

    private void executeCommands(
            Player player,
            List<String> playerCommands,
            List<String> consoleCommands,
            String itemId,
            int slot,
            String cycleId
    ) {
        if (playerCommands != null && !playerCommands.isEmpty()) {
            player.getScheduler().run(plugin, _ -> {
                for (String command : playerCommands) {
                    String parsed = parsePlaceholders(command, player, itemId, slot, cycleId);
                    player.performCommand(stripSlash(parsed));
                }
            }, null);
        }

        if (consoleCommands != null && !consoleCommands.isEmpty()) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                for (String command : consoleCommands) {
                    String parsed = parsePlaceholders(command, player, itemId, slot, cycleId);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(parsed));
                }
            });
        }
    }

    private @NonNull String parsePlaceholders(String text, @NonNull Player player, String itemId, int slot, String cycleId) {
        String result = text;
        result = result.replace("{player}", player.getName());
        result = result.replace("{uuid}", player.getUniqueId().toString());
        result = result.replace("{item}", itemId == null ? "" : itemId);
        result = result.replace("{slot}", slot >= 0 ? String.valueOf(slot) : "");
        result = result.replace("{cycle}", cycleId == null ? "" : cycleId);
        return result;
    }

    private String stripSlash(@NonNull String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    public boolean isManaged(ItemStack stack) {
        return factory.isManaged(stack);
    }

    public String getManagedId(ItemStack stack) {
        return factory.getManagedId(stack);
    }

    public boolean isProtectedByConfig(String itemId) {
        ManagedItem item = config.getItem(itemId);
        return item != null && (item.preventMove() || item.preventDrop() || item.slot() != null);
    }
}