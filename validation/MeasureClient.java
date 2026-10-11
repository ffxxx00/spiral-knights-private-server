package validation;

import java.util.Arrays;
import java.util.Locale;
import com.threerings.projectx.client.ProjectXApp;
import java.lang.reflect.Method;
import com.threerings.tudey.data.TudeySceneModel;
import org.lwjgl.opengl.GL11;

/** Optional graphical probe; has no changes to the client's timing policy. */
public final class MeasureClient extends ProjectXApp {
    private long windowStarted;
    private long lastFrame;
    private final double[] frames = new double[6000];
    private int count;
    private Object measuredView;
    private static final Class<?> DIRECTOR;
    static { try { DIRECTOR = Class.forName("com.threerings.tudey.a.i"); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    private static Object read(Object view, String name) { try { return DIRECTOR.getMethod(name).invoke(view); } catch(Exception e) { throw new IllegalStateException(e); } }
    private static int number(Object view, String name) { return ((Number)read(view,name)).intValue(); }
    private static Object field(Object target, Class<?> owner, String name) {
        try { java.lang.reflect.Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static com.threerings.tudey.data.actor.Actor spriteActor(Object sprite) {
        try { return (com.threerings.tudey.data.actor.Actor)sprite.getClass().getMethod("acU").invoke(sprite); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private int pendingMessages() {
        try {
            Object communicator = field(apT, Class.forName("com.threerings.presents.client.Client"), "aps");
            Object dispatch = field(communicator, Class.forName("com.threerings.presents.client.u"), "aqm");
            Object queue = field(dispatch, Class.forName("com.threerings.presents.client.p"), "apV");
            return (Integer)queue.getClass().getMethod("size").invoke(queue);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private long sceneStarted;
    private boolean rendererReported;
    private long benchmarkStarted;
    private boolean trainingRequested;
    private boolean sawCombatCube, sawDeadCube, sawRespawnedCube;
    private long combatObservedAt;

    public MeasureClient() { super("HeroKnight", "password", false, "HeroKnight", null, null, null, null); }
    public static void main(String[] args) {
        System.setProperty("com.threerings.io.enumPolicy", "ORDINAL");
        String backend = org.lwjgl.system.MemoryUtil.memBackend().getClass().getName();
        System.out.println("[LOCAL][MEMORY] backend=" + backend);
        if ("ffm".equals(System.getProperty("org.lwjgl.system.memoryBackend"))
                && !backend.startsWith("org.lwjgl.system.MemoryBackendFFM")) {
            throw new AssertionError("FFM memory backend did not load");
        }
        MeasureClient client=new MeasureClient();
        client.startup();
        System.exit(Boolean.getBoolean("measurement.combat")
            && !(client.sawDeadCube && client.sawRespawnedCube)?1:0);
    }
    @Override protected void updateFrame() {
        super.updateFrame();
        long now = System.nanoTime();
        if (!DIRECTOR.isInstance(Gu())) { lastFrame = 0; return; }
        Object view = Gu();
        if (Boolean.getBoolean("measurement.combat")) {
            @SuppressWarnings("unchecked") java.util.Map<Integer,?> sprites =
                (java.util.Map<Integer,?>)field(view,DIRECTOR,"bPw");
            if (sawCombatCube && !sawDeadCube && sprites!=null && !sprites.containsKey(2018)
                    && read(view,"Sn")!=null && ((TudeySceneModel)read(view,"Sn")).sceneId==294) {
                sawDeadCube=true; System.out.println("[LOCAL][COMBAT] rendered cube removed actor=2018");
            }
            for (Object sprite : sprites==null ? java.util.Collections.emptyList() : sprites.values()) {
                com.threerings.tudey.data.actor.Actor actor=spriteActor(sprite);
                if (!(actor instanceof com.threerings.projectx.dungeon.data.actor.Monster)
                        || actor.tz().x!=38.5f || actor.tz().y!=-3.5f) continue;
                float health=((com.threerings.projectx.dungeon.data.actor.Monster)actor).RA();
                if (!sawCombatCube && actor.vw()==2018 && read(view,"Sn")!=null
                        && ((TudeySceneModel)read(view,"Sn")).sceneId==294) {
                    sawCombatCube=true; System.out.println("[LOCAL][COMBAT] received original cube actor="+actor.vw()+" health="+health);
                    combatObservedAt=now;
                    try { java.nio.file.Files.writeString(java.nio.file.Path.of("validation/combat-visual.ready"),"ready"); }
                    catch (java.io.IOException error) { throw new IllegalStateException(error); }
                }
                if (health==0 && !sawDeadCube) {
                    sawDeadCube=true; System.out.println("[LOCAL][COMBAT] received cube death actor="+actor.vw()+" time="+actor.adY());
                }
                if (sawDeadCube && actor.vw()!=2018 && health==1 && !sawRespawnedCube) {
                    sawRespawnedCube=true; System.out.println("[LOCAL][COMBAT] received respawn actor="+actor.vw());
                }
            }
        }
        if (benchmarkStarted == 0) benchmarkStarted = now;
        if (view != measuredView) {
            measuredView = view;
            sceneStarted = windowStarted = now;
            lastFrame = 0;
            count = 0;
        }
        if (lastFrame != 0 && count < frames.length) frames[count++] = (now-lastFrame)/1e6;
        lastFrame = now;
        if (!rendererReported) {
            rendererReported = true;
            System.out.println("[LOCAL][GPU] " + GL11.glGetString(GL11.GL_RENDERER));
        }
        if (now-windowStarted >= 5_000_000_000L && count > 0) {
            double[] ordered = Arrays.copyOf(frames,count);
            Arrays.sort(ordered);
            System.out.printf(Locale.ROOT,
                "[LOCAL][PERF] scene=%d fps=%.1f frameP50Ms=%.2f frameP95Ms=%.2f renderBufferMs=%d renderBehindClockMs=%d predictionLeadMs=%d pendingMessages=%d sceneClock=%d%n",
                read(view,"Sn") == null ? -1 : ((TudeySceneModel)read(view,"Sn")).sceneId,
                count*1e9/(now-windowStarted), ordered[count/2], ordered[(count-1)*95/100],
                number(view,"act"), number(view,"acw")-number(view,"acs"), number(view,"acv")-number(view,"acw"), pendingMessages(), number(view,"acw"));
            windowStarted = now; count = 0;
        }
        if (Boolean.getBoolean("measurement.visitTraining") && !trainingRequested
                && now-benchmarkStarted > Long.getLong("measurement.trainingAfterSeconds",45L)*1_000_000_000L) {
            trainingRequested = true;
            try {
                System.out.println("[LOCAL][PERF] Requesting Training Hall through SceneDirector.bl(294)");
                GF().getClass().getMethod("bl",int.class).invoke(GF(),294);
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        boolean combatProbe=Boolean.getBoolean("measurement.combat");
        boolean complete=combatProbe
            ? (combatObservedAt!=0 && now-combatObservedAt>25_000_000_000L)
                || now-benchmarkStarted>120_000_000_000L
            : now-benchmarkStarted>Long.getLong("measurement.durationSeconds",90L)*1_000_000_000L;
        if (complete) {
            if (Boolean.getBoolean("measurement.combat") && !(sawDeadCube && sawRespawnedCube))
                System.err.println("[LOCAL][COMBAT] FAILED: rendered cube death and respawn not verified");
            shutdown();
        }
    }
}
