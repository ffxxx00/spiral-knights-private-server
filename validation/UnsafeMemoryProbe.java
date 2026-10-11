package validation;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;

/** Checks the client LWJGL backend with the client's bundled Java runtime. */
public final class UnsafeMemoryProbe {
    public static void main(String[] args) {
        String backend = MemoryUtil.memBackend().getClass().getName();
        System.out.println("LWJGL memory backend=" + backend);
        if ("ffm".equals(System.getProperty("org.lwjgl.system.memoryBackend"))
                && !backend.startsWith("org.lwjgl.system.MemoryBackendFFM")) {
            throw new AssertionError("FFM selection fell back to another backend");
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(64);
        try {
            buffer.putInt(0, 123456);
            if (buffer.getInt(0) != 123456) throw new AssertionError("Native memory read failed");
            long address = MemoryUtil.memAddress(buffer);
            MemoryUtil.memPutInt(address+4,987654);
            if (buffer.getInt(4) != 987654 || MemoryUtil.memGetInt(address+4) != 987654) {
                throw new AssertionError("Native address round trip failed");
            }
            ByteBuffer wrapper = MemoryUtil.memByteBuffer(address,64);
            wrapper.putInt(8,2468);
            if (buffer.getInt(8) != 2468) throw new AssertionError("Wrapped buffer does not alias memory");
            MemoryUtil.memCopy(new byte[]{11,22,33},address+12);
            byte[] copy = new byte[3];
            MemoryUtil.memCopy(address+12,copy);
            if (!java.util.Arrays.equals(copy,new byte[]{11,22,33})) throw new AssertionError("Array copy failed");
            buffer.asFloatBuffer().put(4,1.25f);
            if (MemoryUtil.memGetFloat(address+16) != 1.25f) throw new AssertionError("Typed view failed");
        } finally {
            MemoryUtil.memFree(buffer);
        }
        ByteBuffer text = MemoryUtil.memUTF8("Caballero ñ",true);
        try {
            if (!"Caballero ñ".equals(MemoryUtil.memUTF8(MemoryUtil.memAddress(text)))) throw new AssertionError("UTF8 round trip failed");
        } finally { MemoryUtil.memFree(text); }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer ints = stack.ints(7,8,9);
            if (MemoryUtil.memGetInt(MemoryUtil.memAddress(ints)+4) != 8) throw new AssertionError("Stack allocation failed");
            org.lwjgl.PointerBuffer pointers = stack.mallocPointer(2);
            pointers.put(0,MemoryUtil.memAddress(ints));
            if (pointers.get(0) != MemoryUtil.memAddress(ints)) throw new AssertionError("Pointer buffer failed");
        }
        System.out.println("Client LWJGL allocation, addresses, views, arrays, UTF8, stack, pointers and free: PASSED");
    }
}
