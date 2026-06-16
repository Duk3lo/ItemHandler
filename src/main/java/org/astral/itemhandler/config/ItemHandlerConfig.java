package org.astral.itemhandler.config;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.astral.itemhandler.ItemHandler;
import org.astral.itemhandler.model.ItemCycle;
import org.astral.itemhandler.model.ManagedItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemHandlerConfig {

    private final ItemHandler plugin;

    private boolean giveItemsOnJoin;
    private boolean clearInventoryOnJoin;

    private final Map<String, ManagedItem> items = new LinkedHashMap<>();
    private final Map<String, ItemCycle> cycles = new LinkedHashMap<>();

    public ItemHandlerConfig(ItemHandler plugin) {
        this.plugin = plugin;
    }

    public void load() {
        items.clear();
        cycles.clear();

        giveItemsOnJoin = plugin.getConfig().getBoolean("settings.give-items-on-join", true);
        clearInventoryOnJoin = plugin.getConfig().getBoolean("settings.clear-inventory-on-join", true);

        loadItems();
        loadCycles();
    }

    private void loadItems() {
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("items");
        if (itemsSection == null) return;

        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection sec = itemsSection.getConfigurationSection(id);
            if (sec == null) continue;

            Material material = Material.matchMaterial(sec.getString("material", "STONE"));
            if (material == null) material = Material.STONE;

            int amount = Math.max(1, sec.getInt("amount", 1));
            String name = colorize(sec.getString("name", ""));
            List<String> lore = colorizeList(sec.getStringList("lore"));

            boolean glow = sec.getBoolean("glow", false);
            boolean unbreakable = sec.getBoolean("unbreakable", false);
            boolean preventMove = sec.getBoolean("prevent-move", true);
            boolean preventDrop = sec.getBoolean("prevent-drop", true);
            boolean giveOnJoin = sec.getBoolean("give-on-join", true);
            String toggleTo = sec.getString("toggle-to", null);

            Integer slot = sec.contains("slot") ? sec.getInt("slot") : null;
            Integer customModelData = sec.contains("custom-model-data") ? sec.getInt("custom-model-data") : null;

            List<String> clickPlayerCommands = colorizeList(sec.getStringList("commands.player"));
            List<String> clickConsoleCommands = colorizeList(sec.getStringList("commands.console"));

            String clickSound = null;
            float soundVolume = 1.0f;
            float soundPitch = 1.0f;

            if (sec.contains("sound")) {
                if (sec.isConfigurationSection("sound")) {
                    clickSound = sec.getString("sound.type");
                    soundVolume = (float) sec.getDouble("sound.volume", 1.0);
                    soundPitch = (float) sec.getDouble("sound.pitch", 1.0);
                } else {
                    clickSound = sec.getString("sound");
                }
            }

            Map<Enchantment, Integer> enchants = parseEnchantments(sec.getStringList("enchants"));

            items.put(id.toLowerCase(Locale.ROOT), new ManagedItem(
                    id.toLowerCase(Locale.ROOT),
                    material,
                    amount,
                    name,
                    lore,
                    glow,
                    unbreakable,
                    preventMove,
                    preventDrop,
                    giveOnJoin,
                    toggleTo,
                    slot,
                    customModelData,
                    enchants,
                    clickPlayerCommands,
                    clickConsoleCommands,
                    clickSound,
                    soundVolume,
                    soundPitch
            ));
        }
    }

    private void loadCycles() {
        ConfigurationSection cyclesSection = plugin.getConfig().getConfigurationSection("cycles");
        if (cyclesSection == null) return;

        for (String id : cyclesSection.getKeys(false)) {
            ConfigurationSection sec = cyclesSection.getConfigurationSection(id);
            if (sec == null) continue;

            boolean enabled = sec.getBoolean("enabled", true);
            int slot = sec.getInt("slot", -1);
            long intervalMs = Math.max(50L, sec.getLong("interval-ms", 1000L));
            long periodTicks = Math.max(1L, (intervalMs + 49L) / 50L);

            List<String> itemIds = sec.getStringList("items").stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList();

            List<String> playerCommands = colorizeList(sec.getStringList("commands.player"));
            List<String> consoleCommands = colorizeList(sec.getStringList("commands.console"));

            cycles.put(id.toLowerCase(Locale.ROOT), new ItemCycle(
                    id.toLowerCase(Locale.ROOT),
                    enabled,
                    slot,
                    intervalMs,
                    periodTicks,
                    itemIds,
                    playerCommands,
                    consoleCommands
            ));
        }
    }

    private @NonNull Map<Enchantment, Integer> parseEnchantments(@NonNull List<String> raw) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();

        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

        for (String line : raw) {
            if (line == null || line.isBlank()) continue;

            String[] split = line.split(":");
            String enchName = split[0].trim().toLowerCase(Locale.ROOT);

            if (enchName.equals("durability")) enchName = "unbreaking";
            if (enchName.equals("damage_all")) enchName = "sharpness";
            if (enchName.equals("dig_speed")) enchName = "efficiency";

            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(enchName);
            Enchantment enchantment = registry.get(key);

            if (enchantment == null) {
                plugin.getLogger().warning("No se encontró el encantamiento: " + enchName);
                continue;
            }

            int level = 1;
            if (split.length > 1) {
                try {
                    level = Integer.parseInt(split[1].trim());
                } catch (NumberFormatException ignored) {
                }
            }

            result.put(enchantment, Math.max(1, level));
        }

        return result;
    }

    private @NonNull List<String> colorizeList(@NonNull List<String> input) {
        List<String> output = new ArrayList<>(input.size());
        for (String line : input) {
            output.add(colorize(line));
        }
        return output;
    }

    private @NonNull String colorize(String text) {
        if (text == null) return "";
        net.kyori.adventure.text.Component component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
    }

    public boolean isGiveItemsOnJoin() {
        return giveItemsOnJoin;
    }

    public boolean isClearInventoryOnJoin() {
        return clearInventoryOnJoin;
    }

    public Map<String, ManagedItem> getItems() {
        return Map.copyOf(items);
    }

    public Map<String, ItemCycle> getCycles() {
        return Map.copyOf(cycles);
    }

    public ManagedItem getItem(@NonNull String id) {
        return items.get(id.toLowerCase(Locale.ROOT));
    }
}