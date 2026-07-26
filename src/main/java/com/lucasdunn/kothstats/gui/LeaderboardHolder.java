package com.lucasdunn.kothstats.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LeaderboardHolder implements InventoryHolder {
    private final String kothName;
    private final int page;
    private Inventory inventory;

    public LeaderboardHolder(String kothName, int page) {
        this.kothName = kothName;
        this.page = page;
    }

    public String getKothName() {
        return kothName;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
