package icu.xdserv.mc.liquidess;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;

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

    private static int plumb(Location location, boolean upwards, boolean toAir) {
        Location cloned = location.clone();

        int offset = 0;
        while (toAir ^ cloned.add(0, upwards ? 1 : -1, 0).getBlock().isEmpty()) {
            ++offset;
        }

        return offset;
    }

    private class Walker {
        public final List<Location> accepted = new ArrayList<>();
        public final Set<Location> walked = new HashSet<>();
        public final int maxSteps;

        public Walker (int maxSteps) {
            this.maxSteps = maxSteps;
        }

        public void walk(Location location) {
            Queue<Triple<Location, Integer, Boolean>> queue = new ArrayDeque<>();
            queue.add(Triple.of(location, 0, false));

            while (!queue.isEmpty()) {
                var triple = queue.poll();

                // State arguments
                Location loc = triple.getLeft();
                int steps = triple.getMiddle();
                boolean climbing = triple.getRight();

                if (walked.contains(loc) || steps > maxSteps) {
                    continue;
                }
                walked.add(loc);

                Block block = loc.getBlock();
                if (block.isSolid()) {
                    continue;
                }

                Block east = block.getRelative(BlockFace.EAST);
                Block west = block.getRelative(BlockFace.WEST);
                Block up = block.getRelative(BlockFace.UP);
                Block down = block.getRelative(BlockFace.DOWN);
                Block north = block.getRelative(BlockFace.NORTH);
                Block south = block.getRelative(BlockFace.SOUTH);

                // Solid ground with at least 2 blocks of vertical space
                if (down.isSolid() && up.isEmpty()) {
                    accepted.add(loc.toBlockLocation());
                }

                // Flat surface or extending platform
                if (climbing ^ down.isSolid()) {
                    queue.add(Triple.of(loc.clone().add(1, 0, 0), steps + 1, false));
                    queue.add(Triple.of(loc.clone().add(0, 0, 1), steps + 1, false));
                    queue.add(Triple.of(loc.clone().add(-1, 0, 0), steps + 1, false));
                    queue.add(Triple.of(loc.clone().add(0, 0, -1), steps + 1, false));
                }

                // Vertical wall
                if (east.isSolid() || north.isSolid() || west.isSolid() || south.isSolid()) {
                    queue.add(Triple.of(loc.clone().add(0, 1, 0), steps + 1, true));
                }

                // Special cases
                if (!climbing || down.isEmpty()) {
                    // Striking downward
                    int drop = plumb(loc, false, false);
                    queue.add(Triple.of(loc.clone().add(0, -drop, 0), steps + drop, false));
                } else if (block.isLiquid()) {
                    // Getting out of the water
                    int fly = plumb(loc, true, true);
                    queue.add(Triple.of(loc.clone().add(0, fly + 1, 0), steps + fly + 1, false));
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
            sender.sendMessage(ChatColor.RED + "[Back] 没有找到上一个死亡地点");
            return 1;
        }
        lastLocations.remove(p.getUniqueId());

        final double boundFrom = 32;
        final double boundTo = 64;

        // Multiply by sqrt(2) because our moves have no antialiasing.
        Walker walker = new Walker((int) (boundTo * Math.sqrt(2)));
        walker.walk(locRespawn.clone());
        if (!walker.accepted.isEmpty()) {
            // Randomly choose one from the end of the list, making use of the nature of BFS.
            int i = (int) (rand.nextDouble(.9, 1) * walker.accepted.size());
            p.teleport(walker.accepted.get(i).add(.5, .5, .5));
            p.sendMessage(ChatColor.GREEN + "[Back] 已返回到与死亡地点连通的区域内");
            p.playSound(p.getLocation(), "liquid:sfx/teleport_waypoint", SoundCategory.MASTER, 1.0F, 1.0F);
            return 0;
        }

        // Poor one... They must've been buried by sand and gravel, or drown in endless sea and lava pool
        outer: for (int tries = 0; tries < 10; tries++) {
            double tpOffsetX = (rand.nextInt(2) == 0 ? 1 : -1) * (rand.nextDouble() * (boundTo - boundFrom) + boundFrom);
            double tpOffsetZ = (rand.nextInt(2) == 0 ? 1 : -1) * (rand.nextDouble() * (boundTo - boundFrom) + boundFrom);
            Location offsetLoc = locRespawn.clone().add(tpOffsetX, 0, tpOffsetZ);
            World world = locRespawn.getWorld();

            int i = Math.min(locRespawn.getBlockY(), world.getHighestBlockYAt(offsetLoc));
            for (; i <= world.getHighestBlockYAt(locRespawn); i++) {
                offsetLoc.setY(i);
                if (isSafeLocation(offsetLoc)) {
                    // Prevent teleporting onto bedrock platform in Nether
                    if (world.getEnvironment() == World.Environment.NETHER && offsetLoc.getBlockY() > 120) {
                        continue outer;
                    }

                    p.teleport(offsetLoc.add(0, 1, 0));
                    p.sendMessage(ChatColor.YELLOW + "[Back] 已返回到死亡地点附近的随机区域内");
                    p.playSound(p.getLocation(), "liquid:sfx/teleport_waypoint", SoundCategory.MASTER, 1.0F, 1.0F);
                    return 0;
                }
            }
        }

        sender.sendMessage(ChatColor.RED + "[Back] 没有合适的地点返回");
        return 1;
    }
}
