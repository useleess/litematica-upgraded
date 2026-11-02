package com.useleess.litematicaupgraded.selftest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.useleess.litematicaupgraded.malilib.overlay.message.Message;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageUtils;
import com.useleess.litematicaupgraded.malilib.overlay.widget.MessageRendererWidget;
import com.useleess.litematicaupgraded.malilib.render.text.StyledText;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;

/**
 * messages/: on-screen messages sent from another thread (the 3D preview reading a schematic) while the game draws the
 * message list. They used to change the list in the middle of drawing it: ConcurrentModificationException, game crash (#5).
 */
final class MessageTests {
    private static final int SENT = 3000;
    private static volatile Throwable senderError;
    private static volatile boolean senderDone;
    private static int ticksAfter;

    private MessageTests() {
    }

    /** The text of every message on screen now. */
    private static List<String> shownMessages() throws ReflectiveOperationException {
        Field messages = MessageRendererWidget.class.getDeclaredField("messages");
        Field text = Message.class.getDeclaredField("message");
        messages.setAccessible(true);
        text.setAccessible(true);
        List<String> shown = new ArrayList<>();
        for (Object message : (List<?>) messages.get(MessageUtils.getMessageRendererWidget(null, null))) {
            for (StyledTextLine line : ((StyledText) text.get(message)).lines) shown.add(line.displayText);
        }
        return shown;
    }

    static void register(Runner.Registry r) {
        r.waitFor("messages/from-another-thread-while-drawn", 2400, c -> {
            senderError = null;
            senderDone = false;
            ticksAfter = 0;
            Thread sender = new Thread(() -> {
                try {
                    for (int i = 0; i < SENT; i++) {
                        MessageDispatcher.generic().time(5000).send("selftest message " + i);
                        if (i % 20 == 0) Thread.sleep(1);
                    }
                } catch (Throwable e) {
                    senderError = e;
                }
                senderDone = true;
            }, "LU selftest message sender");
            sender.start();
        }, c -> {
            // the messages arrive on the game thread: a few ticks after the sender finished, the last one is in the list
            if (!senderDone || ++ticksAfter < 5) return false;
            c.check(senderError == null, "sending messages from another thread failed: " + senderError);
            c.check(shownMessages().contains("selftest message " + (SENT - 1)), "the last message sent from the other thread is not on screen");
            return true;
        });
    }
}
