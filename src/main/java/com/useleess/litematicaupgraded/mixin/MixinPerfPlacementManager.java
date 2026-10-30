package com.useleess.litematicaupgraded.mixin;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.WorldClient;

import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacementManager;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.WorldWrap;
import com.useleess.litematicaupgraded.malilib.util.position.PositionUtils;
import com.useleess.litematicaupgraded.perf.CompleteCopy;
import com.useleess.litematicaupgraded.perf.GhostMove;
import com.useleess.litematicaupgraded.perf.PerfPlacementManager;

/** Holds the copying while a placement is moved (ghost move). From Litematica Turbo. */
@Mixin(value = SchematicPlacementManager.class, remap = false)
public abstract class MixinPerfPlacementManager implements PerfPlacementManager
{
    @Shadow @Final protected LongSet chunksToRebuild;

    @Override
    public boolean perf$hasCopyableChunks()
    {
        if (this.chunksToRebuild.isEmpty())
        {
            return false;
        }

        WorldClient world = GameWrap.getClientWorld();

        if (world == null || Configs.Generic.LOAD_ENTIRE_SCHEMATICS.getBooleanValue())
        {
            return true;
        }

        LongIterator it = this.chunksToRebuild.iterator();

        while (it.hasNext())
        {
            long pos = it.nextLong();

            if (WorldWrap.isClientChunkLoaded(PositionUtils.getChunkPosX(pos), PositionUtils.getChunkPosZ(pos), world))
            {
                return true;
            }
        }

        return false;
    }

    /** While a placement is being moved nothing is copied; the ghosts are shown instead. */
    @Inject(method = "processQueuedChunks", at = @At("HEAD"), cancellable = true)
    private void perf$holdWhileMoving(CallbackInfoReturnable<Boolean> cir)
    {
        if (GhostMove.isHoldingWork())
        {
            cir.setReturnValue(false);
        }
        else if (GhostMove.isActive())
        {
            // the real copy is about to change the schematic world: complete-copy pieces still building would read it
            CompleteCopy.beforeWorldChange();
        }
    }

    @Inject(method = "setOrigin", at = @At("HEAD"))
    private void perf$beforeMove(SchematicPlacement placement, com.useleess.litematicaupgraded.malilib.util.position.BlockPos origin, CallbackInfo ci)
    {
        GhostMove.beforeSetOrigin(placement);
    }

    @Inject(method = "setOrigin", at = @At("RETURN"))
    private void perf$afterMove(SchematicPlacement placement, com.useleess.litematicaupgraded.malilib.util.position.BlockPos origin, CallbackInfo ci)
    {
        GhostMove.afterSetOrigin(placement);
    }

    /** Any change other than moving (rotate, mirror, sub-regions, ...) goes the normal way. */
    @Inject(method = "onPrePlacementChange", at = @At("HEAD"))
    private void perf$otherChange(SchematicPlacement placement, CallbackInfo ci)
    {
        if (GhostMove.isInSetOrigin() == false)
        {
            GhostMove.endAndDropCopy();
        }
    }

    @Inject(method = "removeSchematicPlacement(Lcom/useleess/litematicaupgraded/litematica/schematic/placement/SchematicPlacement;Z)Z", at = @At("HEAD"))
    private void perf$placementRemoved(SchematicPlacement placement, boolean update, CallbackInfoReturnable<Boolean> cir)
    {
        GhostMove.endAndDropCopy();
    }

    @Inject(method = "addSchematicPlacement", at = @At("HEAD"))
    private void perf$placementAdded(SchematicPlacement placement, boolean printMessages, CallbackInfo ci)
    {
        GhostMove.endAndDropCopy();
    }
}
