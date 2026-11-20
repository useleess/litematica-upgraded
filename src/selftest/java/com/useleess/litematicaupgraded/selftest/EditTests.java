package com.useleess.litematicaupgraded.selftest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.util.SchematicEditUtils;
import com.useleess.litematicaupgraded.litematica.util.LitematicaDirectories;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.Direction;

/** schematic/edit/: Schematic Edit mode replacing keeps how blocks were turned and set (#14). */
final class EditTests {
    private static final IBlockState DOOR = Blocks.OAK_DOOR.getDefaultState().withProperty(BlockDoor.FACING, EnumFacing.EAST)
            .withProperty(BlockDoor.OPEN, true).withProperty(BlockDoor.HINGE, BlockDoor.EnumHingePosition.RIGHT);
    private static final IBlockState GATE_OPEN = Blocks.OAK_FENCE_GATE.getDefaultState()
            .withProperty(BlockFenceGate.FACING, EnumFacing.SOUTH).withProperty(BlockFenceGate.OPEN, true);
    private static final IBlockState GATE_WEST = Blocks.OAK_FENCE_GATE.getDefaultState().withProperty(BlockFenceGate.FACING, EnumFacing.WEST);
    private static final IBlockState STAIRS = Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH)
            .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);

    private static LoadedSchematic loaded;
    private static SchematicPlacement placement;
    private static BlockPos origin;

    private EditTests() {}

    static void register(Runner.Registry r) {
        r.waitFor("schematic/edit/replace-keeps-facing-open-and-both-door-halves", 600, c -> {
            Map<net.minecraft.util.math.BlockPos, IBlockState> blocks = new LinkedHashMap<>();
            blocks.put(new net.minecraft.util.math.BlockPos(0, 0, 0), DOOR.withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER));
            blocks.put(new net.minecraft.util.math.BlockPos(0, 1, 0), DOOR.withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER));
            blocks.put(new net.minecraft.util.math.BlockPos(2, 0, 0), GATE_OPEN);
            blocks.put(new net.minecraft.util.math.BlockPos(3, 0, 0), GATE_WEST);
            blocks.put(new net.minecraft.util.math.BlockPos(4, 0, 0), Blocks.PLANKS.getDefaultState());
            blocks.put(new net.minecraft.util.math.BlockPos(5, 0, 0), STAIRS);
            Path dir = LitematicaDirectories.getSchematicsBaseDirectory().resolve("lu-edit-tests");
            Files.createDirectories(dir);
            Path file = dir.resolve("edit-keep-state.litematic");
            LitematicWriter.write(file, blocks);
            loaded = LoadedSchematic.tryLoadSchematic(file).orElseThrow(() -> new IllegalStateException("cannot load " + file));
            origin = new BlockPos((int) Math.floor(c.mc.player.posX) + 3, (int) Math.floor(c.mc.player.posY) + 2, (int) Math.floor(c.mc.player.posZ) + 3);
            placement = SchematicPlacement.create(loaded, origin, "selftest-edit", true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
        }, c -> {
            World world = SchematicWorldHandler.getSchematicWorld(); // the vanilla type: its method is remapped
            if (world == null || world.getBlockState(origin.add(5, 0, 0).toVanillaPos()).getBlock() != Blocks.OAK_STAIRS) return false;
            long total = loaded.schematic.getMetadata().getTotalBlocks();

            c.check(SchematicEditUtils.replaceAllIdentical(origin.add(2, 0, 0), Blocks.SPRUCE_FENCE_GATE.getDefaultState()), "replacing the open gate was refused");
            c.check(at(2) == Blocks.SPRUCE_FENCE_GATE.getDefaultState().withProperty(BlockFenceGate.FACING, EnumFacing.SOUTH)
                    .withProperty(BlockFenceGate.OPEN, true), "the replaced gate is " + at(2) + ", not spruce, south, open");
            c.check(at(3) == GATE_WEST, "the gate facing another way was replaced too: " + at(3));

            c.check(SchematicEditUtils.replaceAllIdentical(origin, Blocks.IRON_DOOR.getDefaultState()), "replacing the door was refused");
            IBlockState iron = Blocks.IRON_DOOR.getDefaultState().withProperty(BlockDoor.FACING, EnumFacing.EAST)
                    .withProperty(BlockDoor.OPEN, true).withProperty(BlockDoor.HINGE, BlockDoor.EnumHingePosition.RIGHT);
            c.check(at(0, 0) == iron.withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER), "the door's lower half is " + at(0, 0));
            c.check(at(0, 1) == iron.withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER), "the door's upper half is " + at(0, 1));

            IBlockState spruce = Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.SPRUCE);
            c.check(SchematicEditUtils.replaceAllIdentical(origin.add(4, 0, 0), spruce), "replacing the planks was refused");
            c.check(at(4) == spruce, "the material was not swapped: " + at(4));

            c.check(SchematicEditUtils.replaceInDirection(origin.add(5, 0, 0), Direction.UP, Blocks.STONE_BRICK_STAIRS.getDefaultState()),
                    "replacing the stairs was refused");
            c.check(at(5) == Blocks.STONE_BRICK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH)
                    .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP), "the replaced stairs are " + at(5));

            c.check(loaded.schematic.getMetadata().getTotalBlocks() == total, "the block count changed to "
                    + loaded.schematic.getMetadata().getTotalBlocks() + " from " + total);
            DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement, true);
            return true;
        });
    }

    private static IBlockState at(int x) {
        return at(x, 0);
    }

    private static IBlockState at(int x, int y) {
        BlockContainer container = loaded.schematic.getRegions().values().iterator().next().getBlockContainer();
        return container.getBlockState(x, y, 0).vanillaState();
    }
}
