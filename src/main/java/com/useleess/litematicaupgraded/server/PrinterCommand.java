// The admin switches of the server part: /litematicaupgraded printer|saving|edits on|off|status, edits level|limit <n>, status
package com.useleess.litematicaupgraded.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Operators (permission level 2) switch the printer, server-side saving and server-side edits on or off for everybody, set the
 * permission level edits need and the upload cap; the options are saved and every client is told within a tick.
 */
public final class PrinterCommand extends CommandBase {
    public static final String NAME = "litematicaupgraded";

    @Override public String getName() { return NAME; }

    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override public String getUsage(ICommandSender sender) { return "litematica_upgraded.command.usage"; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) throw new WrongUsageException(getUsage(sender));
        switch (args[0]) {
            case "status":
                status(sender);
                return;
            case "printer":
                if (args.length < 2) throw new WrongUsageException(getUsage(sender));
                switch (args[1]) {
                    case "on":
                    case "off":
                        boolean allowed = "on".equals(args[1]);
                        ServerConfig.setPrinterAllowed(allowed);
                        notifyCommandListener(sender, this, allowed ? "litematica_upgraded.command.printer.on" : "litematica_upgraded.command.printer.off");
                        return;
                    case "status":
                        sender.sendMessage(new TextComponentTranslation(ServerConfig.printerAllowed
                            ? "litematica_upgraded.command.printer.status.on" : "litematica_upgraded.command.printer.status.off"));
                        return;
                    default:
                        throw new WrongUsageException(getUsage(sender));
                }
            case "saving":
                if (args.length < 2) throw new WrongUsageException(getUsage(sender));
                switch (args[1]) {
                    case "on":
                    case "off":
                        boolean allowed = "on".equals(args[1]);
                        ServerConfig.setSaving(allowed);
                        notifyCommandListener(sender, this, allowed ? "litematica_upgraded.command.saving.on" : "litematica_upgraded.command.saving.off");
                        return;
                    case "status":
                        sender.sendMessage(new TextComponentTranslation(ServerConfig.saving
                            ? "litematica_upgraded.command.saving.status.on" : "litematica_upgraded.command.saving.status.off"));
                        return;
                    default:
                        throw new WrongUsageException(getUsage(sender));
                }
            case "edits":
                if (args.length < 2) throw new WrongUsageException(getUsage(sender));
                switch (args[1]) {
                    case "on":
                    case "off":
                        boolean allowed = "on".equals(args[1]);
                        ServerConfig.setEdits(allowed);
                        notifyCommandListener(sender, this, allowed ? "litematica_upgraded.command.edits.on" : "litematica_upgraded.command.edits.off");
                        return;
                    case "level":
                        if (args.length < 3) throw new WrongUsageException(getUsage(sender));
                        ServerConfig.setEditLevel(parseInt(args[2], 0, 4));
                        notifyCommandListener(sender, this, "litematica_upgraded.command.edits.level", ServerConfig.editLevel);
                        return;
                    case "limit":
                        if (args.length < 3) throw new WrongUsageException(getUsage(sender));
                        ServerConfig.setUploadKiB(parseInt(args[2], 1, 262144));
                        notifyCommandListener(sender, this, "litematica_upgraded.command.edits.limit", ServerConfig.uploadKiB);
                        return;
                    case "rate":
                        if (args.length < 3) throw new WrongUsageException(getUsage(sender));
                        ServerConfig.setBlocksPerTick(parseInt(args[2], 100, 1000000));
                        notifyCommandListener(sender, this, "litematica_upgraded.command.edits.rate", ServerConfig.blocksPerTick);
                        return;
                    case "status":
                        sender.sendMessage(new TextComponentTranslation(ServerConfig.edits
                            ? "litematica_upgraded.command.edits.status.on" : "litematica_upgraded.command.edits.status.off"));
                        return;
                    default:
                        throw new WrongUsageException(getUsage(sender));
                }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    /** All the options in one place. */
    private static void status(ICommandSender sender) {
        sender.sendMessage(new TextComponentTranslation("litematica_upgraded.command.status",
            onOff(ServerConfig.printerAllowed), onOff(ServerConfig.saving), onOff(ServerConfig.edits), ServerConfig.editLevel,
            ServerConfig.uploadKiB, ServerJobs.INSTANCE.running()));
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, Arrays.asList("printer", "saving", "edits", "status"));
        if (args.length == 2 && "edits".equals(args[0])) return getListOfStringsMatchingLastWord(args, Arrays.asList("on", "off", "status", "level", "limit", "rate"));
        if (args.length == 2 && !"status".equals(args[0])) return getListOfStringsMatchingLastWord(args, Arrays.asList("on", "off", "status"));
        return Collections.emptyList();
    }
}
