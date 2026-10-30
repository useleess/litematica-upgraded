// The network channel of the printer support, after Schematica Upgraded's PacketHandler (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server.net;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.useleess.litematicaupgraded.LitematicaUpgraded;

public final class Net {
    /** Channel names are at most 20 characters long. */
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(LitematicaUpgraded.MOD_ID);

    private Net() {}

    public static void init() {
        CHANNEL.registerMessage(MessageCapabilities.class, MessageCapabilities.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(MessagePlacementIntent.class, MessagePlacementIntent.class, 1, Side.SERVER);
        CHANNEL.registerMessage(MessageAirPlace.class, MessageAirPlace.class, 2, Side.SERVER);
        CHANNEL.registerMessage(MessageRangedClick.class, MessageRangedClick.class, 3, Side.SERVER);
        CHANNEL.registerMessage(MessageRangedDig.class, MessageRangedDig.class, 4, Side.SERVER);
        CHANNEL.registerMessage(MessageShulkerTake.class, MessageShulkerTake.class, 5, Side.SERVER);
        CHANNEL.registerMessage(MessageSaveRequest.class, MessageSaveRequest.class, 6, Side.SERVER);
        CHANNEL.registerMessage(MessageEditUpload.class, MessageEditUpload.class, 7, Side.SERVER);
        CHANNEL.registerMessage(MessageEditCancel.class, MessageEditCancel.class, 8, Side.SERVER);
        CHANNEL.registerMessage(MessageSaveData.class, MessageSaveData.class, 9, Side.CLIENT);
        CHANNEL.registerMessage(MessageEditStatus.class, MessageEditStatus.class, 10, Side.CLIENT);
    }
}
