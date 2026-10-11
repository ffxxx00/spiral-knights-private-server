package validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.Arrays;

/** Ensures the overlay preserves all bytes outside the intended patch sites. */
public final class CheckFrozenOverlay {
    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile("code/projectx-pcode.jar")) {
            check(jar,"com/threerings/tudey/data/TudeySceneConfig.class",100,20);
            check(jar,"com/threerings/projectx/dungeon/data/LevelSceneConfig.class",33,20);
            check(jar,"com/threerings/tudey/a/i.class",50,8);
            checkPawn(jar);
        }
    }
    private static void checkPawn(JarFile jar) throws Exception {
        String entry = "com/threerings/tudey/a/a.class";
        byte[] original = jar.getInputStream(jar.getJarEntry(entry)).readAllBytes();
        byte[] overlay = Files.readAllBytes(Path.of("client-local/classes").resolve(entry));
        byte[] expression = {0x0c,(byte)0xb2,0x00,0x7e,0x23,0x6a,(byte)0xb8,0x00,(byte)0xa0,0x66,(byte)0xb6,0x00,(byte)0x99};
        int site = -1;
        for (int i = 0; i <= original.length-expression.length; i++) {
            if (Arrays.equals(expression,Arrays.copyOfRange(original,i,i+expression.length))) {
                if (site >= 0) throw new AssertionError("Ambiguous pawn expression");
                site = i;
            }
        }
        if (site < 0 || original.length != overlay.length) throw new AssertionError("Pawn contract changed");
        byte[] expected = original.clone();
        Arrays.fill(expected,site+1,site+10,(byte)0);
        if (!Arrays.equals(expected,overlay)) throw new AssertionError("Unexpected pawn bytecode change");
        System.out.println("[LOCAL] " + entry + ": easing alpha=1; all other bytes intact: PASSED");
    }
    private static void check(JarFile jar, String entry, int before, int after) throws Exception {
        byte[] original = jar.getInputStream(jar.getJarEntry(entry)).readAllBytes();
        byte[] overlay = Files.readAllBytes(Path.of("client-local/classes").resolve(entry));
        if (original.length != overlay.length) throw new AssertionError("Class structure changed: " + entry);
        int changes = 0;
        for (int index = 0; index < original.length; index++) {
            if (original[index] != overlay[index]) {
                changes++;
                if ((original[index]&255) != before || (overlay[index]&255) != after) {
                    throw new AssertionError("Unexpected byte changed: " + entry + ":" + index);
                }
            }
        }
        if (changes != 1) throw new AssertionError("Expected one constant operand change: " + entry);
        System.out.println("[LOCAL] " + entry + ": exactly one constant changed; all other bytes intact: PASSED");
    }
}
