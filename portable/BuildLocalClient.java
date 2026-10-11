package portable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.Map;
import java.util.Arrays;

/** Generates a local class overlay while preserving the frozen JAR byte-for-byte. */
public final class BuildLocalClient {
    private enum Patch { INTERVAL, PRELOAD, LOCAL_PAWN }
    public static void main(String[] args) throws Exception {
        int interval = Integer.parseInt(args[2]);
        if (interval < 10 || interval > 100) throw new IllegalArgumentException("Local interval must be 10..100 ms");
        int preloadBudget = args.length > 3 ? Integer.parseInt(args[3]) : 8;
        if (preloadBudget < 1 || preloadBudget > 50) throw new IllegalArgumentException("Preload budget must be 1..50 ms");
        patchInterval(args[0], args[1], "com/threerings/tudey/data/TudeySceneConfig.class", 100, interval);
        patchInterval(args[0], args[1], "com/threerings/projectx/dungeon/data/LevelSceneConfig.class", 33, interval);
        patchClass(args[0], args[1], "com/threerings/tudey/a/i.class", 50, preloadBudget, Patch.PRELOAD);
        patchClass(args[0], args[1], "com/threerings/tudey/a/a.class", 0, 0, Patch.LOCAL_PAWN);
        System.out.println("[LOCAL] Client input interval=" + interval + "ms; frozen JAR unchanged");
        System.out.println("[LOCAL] Scene preload budget=" + preloadBudget + "ms per stage");
        System.out.println("[LOCAL] Local pawn uses predicted position immediately; visual easing removed");
    }
    private static void patchInterval(String jarPath, String outputDirectory,
            String entry, int originalInterval, int interval) throws Exception {
        patchClass(jarPath, outputDirectory, entry, originalInterval, interval, Patch.INTERVAL);
    }
    private static void patchClass(String jarPath, String outputDirectory,
            String entry, int originalInterval, int interval, Patch patch) throws Exception {
        boolean loading = patch == Patch.PRELOAD;
        byte[] bytes;
        try (JarFile jar = new JarFile(jarPath)) {
            bytes = jar.getInputStream(jar.getJarEntry(entry)).readAllBytes();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != 0xCAFEBABE) throw new IllegalStateException("Invalid class file");
        buffer.getInt(); // minor + major
        String[] utf8 = new String[u2(buffer)];
        int budgetOffset = -1, budgetConstant = -1;
        for (int index = 1; index < utf8.length; index++) {
            int tag = buffer.get() & 255;
            switch (tag) {
                case 1: {
                    byte[] value = new byte[u2(buffer)]; buffer.get(value);
                    utf8[index] = new String(value, StandardCharsets.UTF_8); break;
                }
                case 3: case 4: buffer.position(buffer.position()+4); break;
                case 5: {
                    int offset = buffer.position(); long value = buffer.getLong();
                    if (loading && value == 50L) {
                        if (budgetOffset != -1) throw new IllegalStateException("Ambiguous preload constant");
                        budgetOffset = offset; budgetConstant = index;
                    }
                    index++; break;
                }
                case 6: buffer.position(buffer.position()+8); index++; break;
                case 7: case 8: case 16: case 19: case 20: buffer.position(buffer.position()+2); break;
                case 9: case 10: case 11: case 12: case 17: case 18: buffer.position(buffer.position()+4); break;
                case 15: buffer.position(buffer.position()+3); break;
                default: throw new IllegalStateException("Unsupported constant pool tag " + tag);
            }
        }
        buffer.position(buffer.position()+6); // access, this, super
        int interfaces = u2(buffer); buffer.position(buffer.position()+2*interfaces);
        int fields = u2(buffer);
        for (int index = 0; index < fields; index++) skipMember(buffer);
        int methods = u2(buffer), patched = 0;
        for (int index = 0; index < methods; index++) {
            int access = u2(buffer);
            String name = utf8[u2(buffer)], descriptor = utf8[u2(buffer)];
            int attributes = u2(buffer);
            for (int attr = 0; attr < attributes; attr++) {
                String kind = utf8[u2(buffer)]; int length = buffer.getInt(); int start = buffer.position();
                if (patch == Patch.INTERVAL && name.equals("CU") && descriptor.equals("()I") && kind.equals("Code")) {
                    buffer.getInt(); // max_stack + max_locals
                    int codeLength = buffer.getInt(), offset = buffer.position();
                    // javap: base CU() returns 100; Level CU() returns 33. Preserve instruction size,
                    // stack maps, serialization, constructors and every other method.
                    if ((access & 1) == 0 || codeLength != 3 || bytes[offset] != 0x10
                            || bytes[offset+1] != originalInterval || (bytes[offset+2] & 255) != 0xac) {
                        throw new IllegalStateException("Frozen CU() contract changed; refusing overlay");
                    }
                    bytes[offset+1] = (byte)interval; patched++;
                }
                if (patch == Patch.LOCAL_PAWN && name.equals("ai") && descriptor.equals("(F)V") && kind.equals("Code")) {
                    buffer.getInt(); int codeLength = buffer.getInt(), offset = buffer.position();
                    // Frozen ai(float), offsets 397..429: render the controlled pawn
                    // toward bOJ's predicted position with alpha=1-exp(bOR*dt).
                    // Keep prediction, key flags, speed and reconciliation unchanged.
                    byte[] tail = {
                        0x19, 0x04, (byte)0xb6, 0x00, (byte)0xeb, (byte)0xb6, 0x01, 0x15,
                        0x2a, (byte)0xb4, 0x00, 0x76, (byte)0xb6, 0x01, 0x1e, (byte)0xb6, 0x01, 0x15,
                        0x0c, (byte)0xb2, 0x00, 0x7e, 0x23, 0x6a, (byte)0xb8, 0x00, (byte)0xa0, 0x66,
                        (byte)0xb6, 0x00, (byte)0x99, 0x57, (byte)0xb1
                    };
                    if (codeLength != 430 || !Arrays.equals(tail, Arrays.copyOfRange(bytes,offset+397,offset+430))) {
                        throw new IllegalStateException("Frozen local pawn easing contract changed; refusing overlay");
                    }
                    // Retain fconst_1 (alpha=1) and replace the remaining expression
                    // with nops. Instruction offsets and stack maps remain valid.
                    Arrays.fill(bytes,offset+416,offset+425,(byte)0);
                    patched++;
                }
                if (loading && kind.equals("Code")) {
                    // The frozen director shares one long 50 across exactly
                    // these four verified resource-loading stages. No gameplay
                    // clock, input, simulation or animation method is changed.
                    Map<String, Integer> sites = Map.of("tick(F)V",21,"acF()F",80,"acG()F",52,"acH()F",64);
                    Integer site = sites.get(name + descriptor);
                    if (site != null) {
                        buffer.getInt(); int codeLength = buffer.getInt(), offset = buffer.position();
                        if (budgetConstant < 0 || codeLength < site+3 || (bytes[offset+site] & 255) != 0x14
                                || buffer.getShort(offset+site+1) != budgetConstant) {
                            throw new IllegalStateException("Frozen preload contract changed: " + name);
                        }
                        patched++;
                    }
                }
                buffer.position(start+length);
            }
        }
        if (patched != (loading ? 4 : 1)) throw new IllegalStateException("Unexpected number of frozen patch sites: " + patched);
        if (loading) buffer.putLong(budgetOffset, interval);
        Path output = Path.of(outputDirectory).resolve(entry);
        Files.createDirectories(output.getParent()); Files.write(output, bytes);
    }
    private static int u2(ByteBuffer buffer) { return buffer.getShort() & 65535; }
    private static void skipMember(ByteBuffer buffer) {
        buffer.position(buffer.position()+6); int attributes = u2(buffer);
        for (int index = 0; index < attributes; index++) {
            buffer.getShort(); int length = buffer.getInt(); buffer.position(buffer.position()+length);
        }
    }
}
