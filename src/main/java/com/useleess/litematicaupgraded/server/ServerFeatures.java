// What the server we are connected to supports, as the client knows it (set by the capabilities message).
package com.useleess.litematicaupgraded.server;

import com.useleess.litematicaupgraded.server.net.MessageCapabilities;

/**
 * Without the mod on the server nothing arrives, and everything stays at the defaults: the printer is allowed (it is a
 * client-side feature), every server feature is off and the range is the vanilla one. Holds plain fields only, so the
 * server can load it too.
 */
public final class ServerFeatures {
    /** Whether a capabilities message arrived for the current connection (the server runs the mod). */
    public static volatile boolean received;
    public static volatile boolean printerAllowed = true;
    public static volatile boolean accuratePlacement;
    public static volatile boolean airPlacement;
    public static volatile boolean shulkerRestock;
    /** Server-side saving, and server-side paste / fill / replace / delete / move. */
    public static volatile boolean saving, edits;
    /** The server's largest edit upload in KiB (0 = unknown). */
    public static volatile int uploadKiB;
    /** The server's extended range (eye distance); 0 = vanilla reach only. */
    public static volatile double range;

    private static volatile MessageCapabilities pending;

    private ServerFeatures() {}

    /** Called on the network thread; applied on the client thread by {@link #takePending()}. */
    public static void offer(MessageCapabilities message) {
        pending = message;
    }

    /** The capabilities that arrived since the last call and are now in effect, or null. */
    public static MessageCapabilities takePending() {
        MessageCapabilities message = pending;
        if (message == null) return null;
        pending = null;
        received = true;
        printerAllowed = message.printerAllowed;
        accuratePlacement = message.accuratePlacement;
        airPlacement = message.airPlacement;
        shulkerRestock = message.shulkerRestock;
        range = message.range;
        saving = message.saving;
        edits = message.edits;
        uploadKiB = message.uploadKiB;
        return message;
    }

    /** Back to "no server part": a new connection or none. */
    public static void reset() {
        pending = null;
        received = false;
        printerAllowed = true;
        accuratePlacement = false;
        airPlacement = false;
        shulkerRestock = false;
        range = 0;
        saving = false;
        edits = false;
        uploadKiB = 0;
    }
}
