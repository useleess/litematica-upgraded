package com.useleess.litematicaupgraded.malilib.gui.callback;

import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;

public interface SliderCallbackWithText extends SliderCallback
{
    /**
     * @return the formatted display text for the current value. This is used in the slider widget in the GUI.
     */
    StyledTextLine getDisplayText();

    /**
     * Updates the display text, if the value changes externally
     */
    void updateDisplayText();
}
