package com.ney.moonsalary.event;

import com.ney.moonsalary.MoonSalary;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class EventDispatcher {

    private final MoonSalary plugin;

    public EventDispatcher(MoonSalary plugin) {
        this.plugin = plugin;
    }

    public void registerEvents(Listener @NotNull ... listeners) {
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
    }
}
