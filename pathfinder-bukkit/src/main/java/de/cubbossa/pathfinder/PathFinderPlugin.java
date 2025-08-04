package de.cubbossa.pathfinder;

import de.cubbossa.pathfinder.dump.DumpWriter;
import de.cubbossa.pathfinder.dump.DumpWriterProvider;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class PathFinderPlugin extends JavaPlugin {

  @Getter
  private static PathFinderPlugin instance;

  private final BukkitPathFinder pathFinder;

  public PathFinderPlugin() {
    instance = this;
    pathFinder = new BukkitPathFinder(this);
  }

private DumpWriter dumpWriter;

  @Override
  public void onLoad() {
    pathFinder.onLoad();

    DumpWriter dumpWriter = DumpWriterProvider.get();
    if (dumpWriter != null) {
        dumpWriter.addProperty("plugin-version", () -> getDescription().getVersion());
        dumpWriter.addProperty("bukkit-version", () -> getServer().getBukkitVersion());
        dumpWriter.addProperty("java-version", () -> System.getProperty("java.version"));
        dumpWriter.addProperty("os", () -> System.getProperty("os.name") + " " + System.getProperty("os.version"));
        dumpWriter.addProperty("mc-version", () -> getServer().getVersion());
        dumpWriter.addProperty("active-plugins", () -> Arrays.stream(getServer().getPluginManager().getPlugins())
            .map(Plugin::getName).collect(Collectors.toCollection(TreeSet::new)));
        dumpWriter.addProperty("worlds", () -> Bukkit.getWorlds().stream()
            .collect(Collectors.toMap(World::getUID, World::getName, (s, s2) -> s, TreeMap::new)));
    } else {
        getLogger().warning("DumpWriterProvider.get() returned null! Skipping dumpWriter properties setup.");
    }
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();
    pathFinder.onEnable();
  }

  @Override
  public void onDisable() {
    pathFinder.onDisable();
  }
}
