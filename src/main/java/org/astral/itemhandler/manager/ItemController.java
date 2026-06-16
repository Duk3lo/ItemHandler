package org.astral.itemhandler.manager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.astral.itemhandler.ItemHandler;
import org.astral.itemhandler.config.ItemHandlerConfig;
import org.astral.itemhandler.model.ItemCycle;
import org.astral.itemhandler.model.ManagedItem;
import org.astral.itemhandler.util.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ItemController {

    private final ItemHandler plugin;
    private final ItemHandlerConfig config;
    private final ItemFactory factory;

    private final Map<UUID, PlayerState> playerStates = new HashMap<>();
    private final Map<UUID, Boolean> playersHidden = new HashMap<>();

    private ScheduledTask globalTask;
    private long globalTick = 0L;

    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public ItemController(ItemHandler plugin, ItemHandlerConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.factory = new ItemFactory(plugin);
    }

    public void applyToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyToPlayer(player);
        }
        startGlobalTaskIfNeeded();
    }

    public void applyToPlayer(Player player) {
        ensurePlayerState(player);

        player.getScheduler().run(plugin, _ -> {
            if (config.isClearInventoryOnJoin()) {
                player.getInventory().clear();
            }

            if (config.isGiveItemsOnJoin()) {
                giveStaticItems(player);
            }

            refreshVisibilityItem(player);
        }, null);

        startGlobalTaskIfNeeded();
    }

    public void reloadAndReapply() {
        shutdown();
        applyToOnlinePlayers();
    }

    public void shutdown() {
        if (globalTask != null) {
            try {
                globalTask.cancel();
            } catch (Throwable ignored) {
            }
            globalTask = null;
        }

        playerStates.clear();
        playersHidden.clear();
        globalTick = 0L;
    }

    public void removePlayer(UUID uuid) {
        playerStates.remove(uuid);
        playersHidden.remove(uuid);
    }

    private void ensurePlayerState(@NonNull Player player) {
        playerStates.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
    }

    private void startGlobalTaskIfNeeded() {
        if (globalTask != null) {
            return;
        }

        if (config.getCycles().isEmpty()) {
            return;
        }

        globalTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                _ -> tickGlobal(),
                1L,
                1L
        );
    }

    private void tickGlobal() {
        globalTick++;

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            PlayerState state = playerStates.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());

            for (ItemCycle cycle : config.getCycles().values()) {
                if (cycle == null || !cycle.enabled() || cycle.itemIds().isEmpty()) {
                    continue;
                }

                long period = Math.max(1L, cycle.periodTicks());
                long nextTick = state.nextRunTickByCycle.getOrDefault(cycle.id(), 0L);

                if (globalTick < nextTick) {
                    continue;
                }

                state.nextRunTickByCycle.put(cycle.id(), globalTick + period);

                int index = state.indexByCycle.getOrDefault(cycle.id(), 0);
                int size = cycle.itemIds().size();
                String itemId = cycle.itemIds().get(Math.floorMod(index, size));
                state.indexByCycle.put(cycle.id(), (index + 1) % size);

                schedulePlayerUpdate(player, cycle, itemId);
            }
        }
    }

    private void schedulePlayerUpdate(@NonNull Player player, @NonNull ItemCycle cycle, String itemId) {
        player.getScheduler().run(plugin, _ -> {
            if (!player.isOnline()) {
                return;
            }

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
        }, null);
    }

    private void giveStaticItems(Player player) {
        for (ManagedItem item : config.getItems().values()) {
            if (!item.giveOnJoin()) {
                continue;
            }
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

    private void refreshVisibilityItem(Player player) {
        ManagedItem visibleItem = config.getItem("visibility_on");
        ManagedItem hiddenItem = config.getItem("visibility_off");

        if (visibleItem == null && hiddenItem == null) {
            return;
        }

        Integer slot = null;

        if (visibleItem != null && visibleItem.slot() != null) {
            slot = visibleItem.slot();
        } else if (hiddenItem != null && hiddenItem.slot() != null) {
            slot = hiddenItem.slot();
        }

        if (slot == null || slot < 0 || slot >= player.getInventory().getSize()) {
            return;
        }

        boolean hidden = isPlayersHidden(player);
        ManagedItem itemToShow = hidden ? hiddenItem : visibleItem;

        if (itemToShow == null) {
            return;
        }

        player.getInventory().setItem(slot, factory.create(itemToShow));
    }

    private boolean isPlayersHidden(@NonNull Player player) {
        return playersHidden.getOrDefault(player.getUniqueId(), false);
    }

    private void setPlayersHidden(@NonNull Player player, boolean hidden) {
        playersHidden.put(player.getUniqueId(), hidden);
    }

    public void handleClick(Player player, String itemId) {
        ManagedItem item = config.getItem(itemId);
        if (item == null) return;

        int slot = item.slot() != null ? item.slot() : player.getInventory().getHeldItemSlot();

        executeCommands(
                player,
                item.clickPlayerCommands(),
                item.clickConsoleCommands(),
                item.id(),
                slot,
                null
        );

        // --- SECCIÓN DE SONIDOS CORREGIDA (Usando Registry para evitar Deprecation) ---
        if (item.clickSound() != null && !item.clickSound().isBlank()) {
            try {
                NamespacedKey key = NamespacedKey.minecraft(item.clickSound().toLowerCase(Locale.ROOT));
                Sound sound = Registry.SOUNDS.get(key);

                if (sound != null) {
                    player.playSound(player.getLocation(), sound, item.soundVolume(), item.soundPitch());
                } else {
                    plugin.getLogger().warning("Sonido inválido o no encontrado en el registro: '" + item.clickSound() + "' en el item " + item.id());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error al reproducir el sonido: '" + item.clickSound() + "' en el item " + item.id());
            }
        }
        // ------------------------------------------------------------------------------

        String toggleTo = item.toggleTo();
        if (toggleTo != null && !toggleTo.isBlank()) {
            ManagedItem targetItem = config.getItem(toggleTo);
            if (targetItem == null) {
                plugin.getLogger().warning("toggle-to inválido: " + toggleTo + " en " + item.id());
                return;
            }

            ItemStack stack = factory.create(targetItem);
            player.getInventory().setItem(slot, stack);

            if ("visibility_on".equalsIgnoreCase(item.id()) && "visibility_off".equalsIgnoreCase(toggleTo)) {
                setPlayersHidden(player, true);
            } else if ("visibility_off".equalsIgnoreCase(item.id()) && "visibility_on".equalsIgnoreCase(toggleTo)) {
                setPlayersHidden(player, false);
            }
        }
    }

    private void runCycleCommands(Player player, @NonNull ItemCycle cycle, String itemId) {
        executeCommands(player, cycle.changePlayerCommands(), cycle.changeConsoleCommands(), itemId, cycle.slot(), cycle.id());
    }

    private void executeCommands(
            @NonNull Player player,
            List<String> playerCommands,
            List<String> consoleCommands,
            String itemId,
            int slot,
            String cycleId
    ) {
        if (playerCommands != null && !playerCommands.isEmpty()) {
            for (String command : playerCommands) {
                try {
                    String parsed = parsePlaceholders(command, player, itemId, slot, cycleId);
                    String normalized = stripSlash(parsed);

                    if (handleActionBarCommand(player, normalized)) {
                        continue;
                    }

                    boolean ok = player.performCommand(normalized);
                    if (!ok) {
                        plugin.getLogger().warning("Falló el comando del jugador: " + normalized);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error ejecutando comando del jugador '" + command + "': " + e.getMessage());
                }
            }
        }

        if (consoleCommands != null && !consoleCommands.isEmpty()) {
            for (String command : consoleCommands) {
                try {
                    String parsed = parsePlaceholders(command, player, itemId, slot, cycleId);
                    String normalized = stripSlash(parsed);

                    if (handleActionBarCommand(player, normalized)) {
                        continue;
                    }

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error ejecutando comando consola '" + command + "': " + e.getMessage());
                }
            }
        }
    }

    private boolean handleActionBarCommand(@NonNull Player player, @NonNull String command) {
        String normalized = command.trim();
        if (normalized.isEmpty()) {
            return true;
        }

        String[] parts = normalized.split("\\s+", 4);
        if (parts.length < 4) {
            return false;
        }

        if (!parts[0].equalsIgnoreCase("title")) {
            return false;
        }

        if (!parts[2].equalsIgnoreCase("actionbar")) {
            return false;
        }

        Player target = resolveTargetPlayer(parts[1], player);
        if (target == null) {
            return true;
        }

        Component component = legacy.deserialize(parts[3]);
        target.sendActionBar(component);
        return true;
    }

    private Player resolveTargetPlayer(String selector, @NonNull Player contextPlayer) {
        if (selector == null || selector.isBlank()) {
            return null;
        }

        String normalized = selector.trim();

        if (normalized.equalsIgnoreCase("@s")) {
            return contextPlayer;
        }

        if (normalized.equalsIgnoreCase("@p") || normalized.equalsIgnoreCase("@a")) {
            return contextPlayer;
        }

        return Bukkit.getPlayerExact(normalized);
    }

    private @NonNull String parsePlaceholders(String text, @NonNull Player player, String itemId, int slot, String cycleId) {
        String result = text == null ? "" : text;

        result = result.replace("{player}", player.getName());
        result = result.replace("{uuid}", player.getUniqueId().toString());
        result = result.replace("{item}", itemId == null ? "" : itemId);
        result = result.replace("{slot}", slot >= 0 ? String.valueOf(slot) : "");
        result = result.replace("{cycle}", cycleId == null ? "" : cycleId);

        return result;
    }

    private String stripSlash(@NonNull String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
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

    private static final class PlayerState {
        private final Map<String, Integer> indexByCycle = new HashMap<>();
        private final Map<String, Long> nextRunTickByCycle = new HashMap<>();
    }
}