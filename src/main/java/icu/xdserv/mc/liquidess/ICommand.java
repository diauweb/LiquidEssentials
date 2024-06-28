package icu.xdserv.mc.liquidess;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface ICommand {
    LiteralCommandNode<CommandSourceStack> getCommand();
}
