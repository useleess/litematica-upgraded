package com.useleess.litematicaupgraded.malilib.action;

import com.useleess.litematicaupgraded.malilib.action.builtin.EventAction;
import com.useleess.litematicaupgraded.malilib.action.util.ActionUtils;
import com.useleess.litematicaupgraded.malilib.input.ActionResult;
import com.useleess.litematicaupgraded.malilib.listener.EventListener;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;

public class SimpleNamedAction extends NamedAction
{
    protected Action action;

    public SimpleNamedAction(String name,
                             String translationKey,
                             ModInfo mod,
                             Action action)
    {
        super(ActionType.SIMPLE, name, translationKey, mod);

        this.action = action;
    }

    @Override
    public boolean isUserAdded()
    {
        return false;
    }

    @Override
    public ActionResult execute(ActionContext ctx)
    {
        if (this.checkIsAllowedOrPrintMessage() == false)
        {
            return ActionResult.FAIL;
        }

        return this.action.execute(ctx);
    }

    public static SimpleNamedAction of(ModInfo mod, String name, Action action)
    {
        String translationKey = ActionUtils.createTranslationKeyFor(mod, name);
        return new SimpleNamedAction(name, translationKey, mod, action);
    }

    public static SimpleNamedAction of(ModInfo mod, String name, Action action, String commentTranslationKey)
    {
        SimpleNamedAction namedAction = of(mod, name, action);
        namedAction.setCommentTranslationKey(commentTranslationKey);
        return namedAction;
    }

    public static SimpleNamedAction of(ModInfo mod, String name, EventListener listener)
    {
        String translationKey = ActionUtils.createTranslationKeyFor(mod, name);
        return new SimpleNamedAction(name, translationKey, mod, EventAction.of(listener));
    }
}
