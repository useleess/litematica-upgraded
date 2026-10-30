package com.useleess.litematicaupgraded.perf;

import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/** Free graphics memory as the card reports it (NVIDIA: GL_NVX_gpu_memory_info, AMD: GL_ATI_meminfo), in KB; -1 when it cannot. Render thread only. */
public final class GpuMemory {
    private static final int GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
    private static final int TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    private GpuMemory() {}

    public static long nvxFreeKb() {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps.GL_NVX_gpu_memory_info == false) return -1;
        IntBuffer b = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX, b);
        GL11.glGetError();
        return b.get(0);
    }

    public static long atiFreeKb() {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps.GL_ATI_meminfo == false) return -1;
        IntBuffer b = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(TEXTURE_FREE_MEMORY_ATI, b);
        GL11.glGetError();
        return b.get(0);
    }
}
