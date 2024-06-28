package icu.xdserv.mc.liquidess;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
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

import java.util.*;
import java.util.logging.Level;

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

    // TODO: support plumbing towards every direction
    private static int plumb(Location location /* , BlockFace towards */) {
        Location cloned = location.clone();

        int offset = 0;
        while (cloned.add(0, -1, 0).getBlock().isEmpty()) {
            ++offset;
        }

        return offset;
    }

    private class Walker {
        public final List<Location> accepted = new ArrayList<>();
        public final Set<Location> walked = new HashSet<>();
        public final int maxSteps;

        public final Queue<Location> queue = new ArrayDeque<>();

        public Walker (int maxSteps) {
            this.maxSteps = maxSteps;
        }

        public void walk(Location location) {
            dfs(location, 0, false);
        }

        // TODO: support flying onto the ceiling, and rewrite the whole thing in BFS.
        private void dfs(Location location, int steps, boolean climbing) {
            if (walked.contains(location.toBlockLocation()) || steps > maxSteps) {
                return;
            }

            walked.add(location.toBlockLocation());

            Block block = location.getBlock();
            if (block.isSolid()) {
                return;
            }

            Block up = block.getRelative(BlockFace.UP);
            Block down = block.getRelative(BlockFace.DOWN);
            Block east = block.getRelative(BlockFace.EAST);
            Block west = block.getRelative(BlockFace.WEST);
            Block north = block.getRelative(BlockFace.NORTH);
            Block south = block.getRelative(BlockFace.SOUTH);

            // Solid ground with at least 2 blocks of vertical space
            if (down.isSolid() && up.isEmpty()) {
                Bukkit.getLogger().log(Level.INFO, String.format("Accepted: %s", location));
                accepted.add(location.toBlockLocation());
            }

            if (down.isEmpty()) {
                if (!climbing) {
                    // Downward stairs
                    int drop = plumb(location);
                    dfs(location.clone().add(0, -drop, 0), steps + 1, false);
                } else {
                    // Extending platform
                    dfs(location.clone().add(1, 0, 0), steps + 1, true);
                    dfs(location.clone().add(0, 0, 1), steps + 1, true);
                    dfs(location.clone().add(-1, 0, 0), steps + 1, true);
                    dfs(location.clone().add(0, 0, -1), steps + 1, true);
                }

                // Vertical wall
                if (!east.isEmpty() || !north.isEmpty() || !west.isEmpty() || !south.isEmpty()) {
                    dfs(location.clone().add(0, 1, 0), steps + 1, true);
                }
            } else {
                // Flat surface
                dfs(location.clone().add(1, 0, 0), steps + 1, false);
                dfs(location.clone().add(0, 0, 1), steps + 1, false);
                dfs(location.clone().add(-1, 0, 0), steps + 1, false);
                dfs(location.clone().add(0, 0, -1), steps + 1, false);

                // Upward stairs
                if (up.isEmpty()) {
                    if (!east.isEmpty()) {
                        dfs(location.clone().add(1, 1, 0), steps + 1, false);
                    }
                    if (!north.isEmpty()) {
                        dfs(location.clone().add(0, 1, 1), steps + 1, false);
                    }
                    if (!west.isEmpty()) {
                        dfs(location.clone().add(-1, 1, 0), steps + 1, false);
                    }
                    if (!south.isEmpty()) {
                        dfs(location.clone().add(0, 1, -1), steps + 1, false);
                    }
                }
            }
        }
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

        final double boundFrom = 50;
        final double boundTo = 100;

        Walker walker = new Walker((int) (boundTo * 2));
        walker.walk(locRespawn.clone().add(0, 2, 0));
        // If we use BFS this could probably be simplified to O(1), since the farther the accepted location is, the
        // nearer to the end of the list will it be. Just simple random access is enough then.
        var accepted = walker.accepted.stream()
                .filter(l -> l.distance(locRespawn) >= boundFrom)
                .toList();
        if (!accepted.isEmpty()) {
            p.teleport(accepted.get((int) (rand.nextDouble() * accepted.size())).add(.5, .5, .5));
            p.sendMessage(ChatColor.GREEN + "已返回到与死亡地点连通的区域内");
            p.playSound(p.getLocation(), "liquid:sfx/teleport_waypoint", SoundCategory.MASTER, 1.0F, 1.0F);
            return 0;
        }

        // Poor one... They must've been buried by sand or gravel.
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
