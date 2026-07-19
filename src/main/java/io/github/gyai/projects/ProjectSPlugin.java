package io.github.gyai.projects;

import io.github.gyai.projects.command.ProjectCommand;
import io.github.gyai.projects.manager.ItemManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProjectSPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        ItemManager.initialize();

        if (getCommand("projects") != null) {
            getCommand("projects").setExecutor(new ProjectCommand());
        }

        getLogger().info("ProjectS has started!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ProjectS has stopped!");
    }
}