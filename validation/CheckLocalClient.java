package validation;

import com.threerings.projectx.town.data.TownSceneConfig;
import com.threerings.projectx.dungeon.data.TrainingSceneConfig;

/** Checks the effective policy loaded by the JVM, including both scene subclasses. */
public final class CheckLocalClient {
    public static void main(String[] args) throws Exception {
        int interval = Integer.parseInt(args[0]), buffer = Integer.parseInt(args[1]);
        int trainingInterval = args.length > 2 ? Integer.parseInt(args[2]) : interval;
        if (new TownSceneConfig().CU() != interval || new TrainingSceneConfig().CU() != trainingInterval) {
            throw new AssertionError("Scene input cadence does not match launcher policy");
        }
        int actual = (Integer)Class.forName("com.threerings.tudey.util.q")
            .getMethod("hd", int.class).invoke(null, 20);
        if (actual != buffer) throw new AssertionError("Unexpected render buffer: " + actual);
        System.out.println("[LOCAL] Effective scene interval=" + interval + "ms renderBuffer=" + buffer + "ms: PASSED");
    }
}
