package de.cubbossa.pathfinder;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;

public class CommandRegistry {

    public void loadCommands() {
        CommandAPI.onLoad(new CommandAPIBukkitConfig(PathFinderPlugin.getInstance())
            .shouldHookPaperReload(true)
            .usePluginNamespace()
            .missingExecutorImplementationMessage("Wrong command usage, use /help."));
    }
    
}