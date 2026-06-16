package org.astral.itemhandler.model;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

public record ManagedItem(
        String id,
        Material material,
        int amount,
        String name,
        List<String> lore,
        boolean glow,
        boolean unbreakable,
        boolean preventMove,
        boolean preventDrop,
        Integer slot,
        Integer customModelData,
        Map<Enchantment, Integer> enchantments,
        List<String> clickPlayerCommands,
        List<String> clickConsoleCommands
) {
}