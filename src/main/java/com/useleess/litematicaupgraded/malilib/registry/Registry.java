package com.useleess.litematicaupgraded.malilib.registry;

import com.useleess.litematicaupgraded.malilib.action.ActionRegistry;
import com.useleess.litematicaupgraded.malilib.command.ClientCommandHandler;
import com.useleess.litematicaupgraded.malilib.config.ConfigManager;
import com.useleess.litematicaupgraded.malilib.config.ConfigManagerImpl;
import com.useleess.litematicaupgraded.malilib.config.serialization.JsonConfigSerializerRegistry;
import com.useleess.litematicaupgraded.malilib.event.dispatch.ClientWorldChangeEventDispatcher;
import com.useleess.litematicaupgraded.malilib.event.dispatch.ClientWorldChangeEventDispatcherImpl;
import com.useleess.litematicaupgraded.malilib.event.dispatch.InitializationDispatcher;
import com.useleess.litematicaupgraded.malilib.event.dispatch.InitializationDispatcherImpl;
import com.useleess.litematicaupgraded.malilib.event.dispatch.RenderEventDispatcher;
import com.useleess.litematicaupgraded.malilib.event.dispatch.RenderEventDispatcherImpl;
import com.useleess.litematicaupgraded.malilib.event.dispatch.TickEventDispatcher;
import com.useleess.litematicaupgraded.malilib.event.dispatch.TickEventDispatcherImpl;
import com.useleess.litematicaupgraded.malilib.gui.config.indicator.ConfigStatusWidgetRegistry;
import com.useleess.litematicaupgraded.malilib.gui.config.registry.ConfigScreenRegistry;
import com.useleess.litematicaupgraded.malilib.gui.config.registry.ConfigTabExtensionRegistry;
import com.useleess.litematicaupgraded.malilib.gui.config.registry.ConfigTabRegistry;
import com.useleess.litematicaupgraded.malilib.gui.config.registry.ConfigTabRegistryImpl;
import com.useleess.litematicaupgraded.malilib.gui.config.registry.ConfigWidgetRegistry;
import com.useleess.litematicaupgraded.malilib.gui.icon.IconRegistry;
import com.useleess.litematicaupgraded.malilib.input.HotkeyManager;
import com.useleess.litematicaupgraded.malilib.input.HotkeyManagerImpl;
import com.useleess.litematicaupgraded.malilib.input.InputDispatcher;
import com.useleess.litematicaupgraded.malilib.input.InputDispatcherImpl;
import com.useleess.litematicaupgraded.malilib.interoperation.BlockPlacementPositionHandler;
import com.useleess.litematicaupgraded.malilib.network.ClientPacketChannelHandler;
import com.useleess.litematicaupgraded.malilib.network.ClientPacketChannelHandlerImpl;
import com.useleess.litematicaupgraded.malilib.overlay.InfoOverlay;
import com.useleess.litematicaupgraded.malilib.overlay.InfoWidgetManager;
import com.useleess.litematicaupgraded.malilib.overlay.InfoWidgetRegistry;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageRedirectManager;
import com.useleess.litematicaupgraded.malilib.util.text.TranslationOverrideManager;

public class Registry
{
    // Registries
    public static final ActionRegistry ACTION_REGISTRY = new ActionRegistry();
    public static final ConfigStatusWidgetRegistry CONFIG_STATUS_WIDGET = new ConfigStatusWidgetRegistry();
    public static final ConfigScreenRegistry CONFIG_SCREEN = new ConfigScreenRegistry();
    public static final ConfigTabRegistry CONFIG_TAB = new ConfigTabRegistryImpl();
    public static final ConfigTabExtensionRegistry CONFIG_TAB_EXTENSION = new ConfigTabExtensionRegistry();
    public static final ConfigWidgetRegistry CONFIG_WIDGET = new ConfigWidgetRegistry();
    public static final IconRegistry ICON = new IconRegistry();
    public static final InfoWidgetRegistry INFO_WIDGET = new InfoWidgetRegistry();
    public static final JsonConfigSerializerRegistry JSON_CONFIG_SERIALIZER = new JsonConfigSerializerRegistry();

    // Various "managers" or "handlers"
    public static final ClientCommandHandler CLIENT_COMMAND_HANDLER = new ClientCommandHandler();
    public static final ConfigManager CONFIG_MANAGER = new ConfigManagerImpl();
    public static final HotkeyManager HOTKEY_MANAGER = new HotkeyManagerImpl();
    public static final InfoOverlay INFO_OVERLAY = new InfoOverlay();
    public static final InfoWidgetManager INFO_WIDGET_MANAGER = new InfoWidgetManager(INFO_OVERLAY);
    public static final MessageRedirectManager MESSAGE_REDIRECT_MANAGER = new MessageRedirectManager();
    public static final TranslationOverrideManager TRANSLATION_OVERRIDE_MANAGER = new TranslationOverrideManager();

    // Event dispatchers and handlers
    public static final BlockPlacementPositionHandler BLOCK_PLACEMENT_POSITION_HANDLER = new BlockPlacementPositionHandler();
    public static final ClientPacketChannelHandler CLIENT_PACKET_CHANNEL_HANDLER = new ClientPacketChannelHandlerImpl();
    public static final ClientWorldChangeEventDispatcher CLIENT_WORLD_CHANGE_EVENT_DISPATCHER = new ClientWorldChangeEventDispatcherImpl();
    public static final InitializationDispatcher INITIALIZATION_DISPATCHER = new InitializationDispatcherImpl();
    public static final InputDispatcher INPUT_DISPATCHER = new InputDispatcherImpl();
    public static final RenderEventDispatcher RENDER_EVENT_DISPATCHER = new RenderEventDispatcherImpl();
    public static final TickEventDispatcher TICK_EVENT_DISPATCHER = new TickEventDispatcherImpl();
}
