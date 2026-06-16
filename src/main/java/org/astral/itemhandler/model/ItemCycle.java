package org.astral.itemhandler.model;

import java.util.List;

public record ItemCycle(
        String id,
        boolean enabled,
        int slot,
        long intervalMs,
        long periodTicks,
        List<String> itemIds,
        List<String> changePlayerCommands,
        List<String> changeConsoleCommands
) {
}