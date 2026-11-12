package com.ctrcraft.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's copycat blocks and Copycats+ (stairs, slabs, slopes, beams, panels, ...) look like another block. For the kart, they
 * ARE that block: a copycat of a boost block boosts, a copycat of ice is ice. Their shape (collision) already works, since the
 * terrain model reads collision boxes. This reads what a copycat copies, by reflection, so neither mod is a dependency: with
 * neither installed it does nothing.
 *
 * <p>Three kinds of block entity, tried in this order:
 * <ol>
 *   <li>Copycats+ blocks with several parts (bytes, panels...): {@code IMultiStateCopycatBlockEntity#getMaterialItemStorage()
 *       .getMaterialMap()}, one material per part;</li>
 *   <li>every other Copycats+ copycat (slopes, stairs, slabs, beams, shafts, doors...): its own {@code ICopycatBlockEntity#getMaterial()}.
 *       These do NOT extend Create's copycat entity (they use {@code CCCopycatBlockEntity} and friends), and Create's own entity gets
 *       the interface by mixin, so this one check covers both;</li>
 *   <li>Create's {@code CopycatBlockEntity#getMaterial()}, when Copycats+ is not installed.</li>
 * </ol>
 */
public final class CopycatCompat {
    private CopycatCompat() {}

    private static boolean looked;
    private static Class<?> singleBe;
    private static Class<?> multiBe;
    private static Class<?> ccBe;
    private static Method getMaterial;
    private static Method getCcMaterial;
    private static Method getStorage;
    private static Method getMaterialMap;

    private static synchronized void init() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            singleBe = Class.forName("com.simibubi.create.content.decoration.copycat.CopycatBlockEntity");
            getMaterial = singleBe.getMethod("getMaterial");
        } catch (ReflectiveOperationException | LinkageError e) {
            singleBe = null;
        }
        try {
            ccBe = Class.forName("com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity");
            getCcMaterial = ccBe.getMethod("getMaterial");
        } catch (ReflectiveOperationException | LinkageError e) {
            ccBe = null;
        }
        try {
            multiBe = Class.forName("com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity");
            getStorage = multiBe.getMethod("getMaterialItemStorage");
            Class<?> storage = Class.forName("com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage");
            getMaterialMap = storage.getMethod("getMaterialMap");
        } catch (ReflectiveOperationException | LinkageError e) {
            multiBe = null;
        }
        com.ctrcraft.CtrCraftMod.LOGGER.info("Copycat support: Create copycats {}, Copycats+ copycats {}, Copycats+ multi-part copycats {}",
                singleBe != null ? "on" : "off", ccBe != null ? "on" : "off", multiBe != null ? "on" : "off");
    }

    /** What the copycat at {@code pos} copies (one state, or one per part), empty if it is not a copycat or copies nothing. */
    public static List<BlockState> materials(Level level, BlockPos pos) {
        init();
        if (singleBe == null && multiBe == null && ccBe == null) {
            return List.of();
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return List.of();
        }
        List<BlockState> out = new ArrayList<>(2);
        try {
            if (multiBe != null && multiBe.isInstance(be)) {
                Object storage = getStorage.invoke(be);
                if (storage != null) {
                    Object map = getMaterialMap.invoke(storage);
                    if (map instanceof Map<?, ?> m) {
                        collect(m.values(), out);
                    }
                }
            } else if (ccBe != null && ccBe.isInstance(be)) {
                collect(java.util.Collections.singletonList(getCcMaterial.invoke(be)), out);
            } else if (singleBe != null && singleBe.isInstance(be)) {
                collect(java.util.Collections.singletonList(getMaterial.invoke(be)), out);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
        return out;
    }

    private static void collect(Collection<?> states, List<BlockState> out) {
        for (Object o : states) {
            if (o instanceof BlockState s && !s.isAir() && !s.is(Blocks.BARRIER)) {
                out.add(s);
            }
        }
    }
}
