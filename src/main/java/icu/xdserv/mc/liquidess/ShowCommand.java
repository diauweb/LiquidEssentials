package icu.xdserv.mc.liquidess;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Function;

public class ShowCommand implements ICommand {

    @Override
    public LiteralCommandNode<CommandSourceStack> getCommand() {
        return Commands.literal("show")
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

        ItemStack item = p.getInventory().getItemInMainHand();

        if (item.isEmpty()) {
            sender.sendMessage("你没有手持物品。");
            return 1;
        }

        String message = "<player_name> 展示了一个物品: <item> x<item_count>";
        Component itemComp = item.displayName().hoverEvent(item.asHoverEvent());

        Component c = MiniMessage.miniMessage().deserialize(message,
                Placeholder.unparsed("player_name", p.getName()),
                Placeholder.component("item", itemComp),
                Placeholder.unparsed("item_count", String.valueOf(item.getAmount())));
        Bukkit.getServer().broadcast(c);
        return 0;
    }
}
