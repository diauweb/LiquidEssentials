package icu.xdserv.mc.liquidess;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class BackCommand implements ICommand, Listener {

    HashMap<UUID, Location> lastLocations = new HashMap<>();
    Random rand = new Random();

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        Location location = event.getEntity().getLocation();
        lastLocations.put(uuid, location);
        event.getEntity().sendMessage(ChatColor.GRAY + "使用 /back 回到你的死亡地点附近");
    }


    @Override
    public LiteralCommandNode<CommandSourceStack> getCommand() {
        return Commands.literal("back")
                .requires(cs -> cs.getSender() instanceof Player)
                .executes(this::onCommand)
                .build();
    }

    public static boolean isSafeLocation(Location location) {
        try {
            Block feet = location.getBlock();
            if (!feet.getType().isTransparent() && !feet.getLocation().add(0, 1, 0).getBlock().getType().isTransparent()) {
                return false; // not transparent (will suffocate)
            }
            Block head = feet.getRelative(BlockFace.UP);
            if (!head.getType().isTransparent()) {
                return false; // not transparent (will suffocate)
            }
            Block ground = feet.getRelative(BlockFace.DOWN);
            // returns if the ground is solid or not.
            return ground.getType().isSolid();
        } catch (Exception err) {
            err.printStackTrace();
        }
        return false;
    }

    private int onCommand(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only player can use this command!");
            return 1;
        }
        Player p = (Player) sender;

        Location locRespawn = lastLocations.get(p.getUniqueId());
        if (locRespawn == null) {
            sender.sendMessage(ChatColor.RED + "没有找到上一个死亡地点");
            return 1;
        }
        lastLocations.remove(p.getUniqueId());

        final double boundFrom = 80;
        final double boundTo = 100;

        for (int tries = 0; tries < 10; tries++) {
            double tpOffsetX = (rand.nextInt(2) == 0 ? 1 : -1) * (rand.nextDouble() * (boundTo - boundFrom) + boundFrom);
            double tpOffsetZ = (rand.nextInt(2) == 0 ? 1 : -1) * (rand.nextDouble() * (boundTo - boundFrom) + boundFrom);
            Location offsetLoc = locRespawn.clone().add(tpOffsetX, 0, tpOffsetZ);

            int i = Math.min(locRespawn.getBlockY(), locRespawn.getWorld().getHighestBlockYAt(offsetLoc));
            for (; i <= locRespawn.getWorld().getHighestBlockYAt(locRespawn); i++) {
                offsetLoc.setY(i);
                if (isSafeLocation(offsetLoc)) {
                    p.teleport(offsetLoc.add(0, 1, 0));
                    p.sendMessage(ChatColor.GREEN + "已返回到死亡地点附近");
                    p.playSound(p.getLocation(), "liquid:sfx/teleport_waypoint", SoundCategory.MASTER, 1.0F, 1.0F);
                    return 0;
                }
            }
        }


        sender.sendMessage(ChatColor.RED + "没有合适的地点返回");
        return 1;
    }
}
