package icu.xdserv.mc.liquidess;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class LiquidEssentials extends JavaPlugin {

    private BedCommand bedCommand = new BedCommand();
    private BackCommand backCommand = new BackCommand();
    private ShowCommand showCommand = new ShowCommand();

    @Override
    public void onEnable() {
        LifecycleEventManager<Plugin> lifecycleManager = this.getLifecycleManager();
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(bedCommand.getCommand());
            commands.register(backCommand.getCommand());
            commands.register(showCommand.getCommand());
        });

        this.getServer().getPluginManager().registerEvents(backCommand, this);
        this.getLogger().info("LIquid Essentials is enabled");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
