package net.kojacoord.connector.bukkit;

import net.kojacoord.connector.core.OrchestratorConnector;
import net.kojacoord.connector.core.PlatformHooks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * Spigot/Paper/Bukkit adapter. Compiled against an old Bukkit API and using only
 * long-stable calls, so this one jar runs on every Minecraft version.
 */
public final class BukkitConnectorPlugin extends JavaPlugin implements PlatformHooks {
    private OrchestratorConnector connector;

    @Override
    public void onEnable() {
        connector = new OrchestratorConnector(this);
        connector.onServerStarted();
    }

    @Override
    public void onDisable() {
        if (connector != null) connector.onServerStopping();
    }

    @Override public int getPlayerCount() { return Bukkit.getOnlinePlayers().size(); }
    @Override public int getMaxPlayers() { return Bukkit.getMaxPlayers(); }

    /** The server root (where server.properties / data.yml / game.json live). */
    @Override public File getDataDir() { return getServer().getWorldContainer(); }

    @Override
    public void scheduleRepeating(int seconds, Runnable task) {
        long ticks = Math.max(1, seconds) * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, task, ticks, ticks);
    }

    @Override public void info(String msg) { getLogger().info(msg); }
    @Override public void warn(String msg, Throwable t) { getLogger().log(Level.WARNING, msg, t); }
}
