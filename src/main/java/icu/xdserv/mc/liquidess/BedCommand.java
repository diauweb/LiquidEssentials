package icu.xdserv.mc.liquidess;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BedCommand implements ICommand {

    public LiteralCommandNode<CommandSourceStack> getCommand() {
        return Commands.literal("bed")
                .requires(cs -> cs.getSender() instanceof Player)
                .executes(this::onCommand)
                .build();
    }

    private int onCommand(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only player can use this command!");
            return 1;
        }
        Player p = (Player) sender;

        Location locRespawn = p.getRespawnLocation();
        if (locRespawn == null) {
            sender.sendMessage(ChatColor.RED + "没有找到有效的重生点");
            return 1;
        }
        p.teleport(locRespawn);
        p.sendMessage(ChatColor.GREEN + "已返回到床的位置");
        p.playSound(p.getLocation(), "liquid:sfx/teleport_waypoint", SoundCategory.MASTER, 1.0F, 1.0F);
        return 0;
    }

}
