package validation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import com.threerings.projectx.client.ProjectXApp;
import com.threerings.tudey.data.actor.Actor;

/** Exercises the client's real keyboard-dispatch path in this diagnostic JVM. */
public final class MeasureStop extends ProjectXApp {
    private static final Class<?> DIRECTOR = type("com.threerings.tudey.a.i");
    private static final Class<?> INPUT = type("com.threerings.tudey.a.a");
    private static final Class<?> SPRITE = type("com.threerings.tudey.a.b.a");
    private Object view, controller;
    private long started, pressed, released, lastSample;
    private int stage, key, trials;
    private float releaseX, releaseY, directionX, directionY, pressX, pressY;
    private double maxForward;
    public MeasureStop() { super("HeroKnight", "password", false, "HeroKnight", null, null, null, null); }
    public static void main(String[] args) {
        System.setProperty("com.threerings.io.enumPolicy","ORDINAL");
        System.out.println("[STOP] inputClass=" + INPUT.getProtectionDomain().getCodeSource().getLocation());
        new MeasureStop().startup();
    }
    private static Class<?> type(String name) { try { return Class.forName(name); } catch(Exception e) { throw new IllegalStateException(e); } }
    private static Object field(Object object, Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static Object invoke(Object object, Class<?> owner, String name) {
        try { return owner.getMethod(name).invoke(object); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private Actor visible() { return (Actor)invoke(invoke(view,DIRECTOR,"PO"),SPRITE,"acU"); }
    private Actor predicted() {
        Object predictor = field(controller,INPUT,"bOJ");
        return (Actor)invoke(predictor,type("com.threerings.tudey.util.b"),"acU");
    }
    private void key(boolean down) {
        try {
            Object root = field(this,ProjectXApp.class,"aLD");
            Method method = type("com.threerings.opengl.gui.P").getDeclaredMethod(
                down ? "keyPressed" : "keyReleased",long.class,char.class,int.class,boolean.class);
            method.setAccessible(true);
            long stamp = (Long)root.getClass().getMethod("getTickStamp").invoke(root);
            method.invoke(root,stamp,'\0',key,false);
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    @Override protected void updateFrame() {
        super.updateFrame(); long now = System.nanoTime();
        if (!DIRECTOR.isInstance(Gu())) return;
        view = Gu(); controller = invoke(view,DIRECTOR,"acn");
        if (started == 0) started = now;
        if (invoke(view,DIRECTOR,"PO") == null || field(controller,INPUT,"bOJ") == null) return;
        if (stage == 0 && now-started > 25_000_000_000L) {
            key = ((Number)field(controller,INPUT,"bOy")).intValue() > 0 ? 87 : 265;
            pressX = visible().tz().x; pressY = visible().tz().y;
            key(true); pressed = now; stage = 1;
            System.out.println("[STOP] trial=" + (trials+1) + " pressed key=" + key);
        } else if (stage == 1 && now-pressed > 150_000_000L) {
            Actor actor = visible(); releaseX = actor.tz().x; releaseY = actor.tz().y;
            double distance = Math.hypot(releaseX-pressX,releaseY-pressY);
            directionX = (float)((releaseX-pressX)/distance);
            directionY = (float)((releaseY-pressY)/distance);
            key(false); released = now; stage = 2;
            System.out.printf(Locale.ROOT,"[STOP] released inputFlags=%s heldMs=%.1f moved=%.5f visualGap=%.5f%n",
                field(controller,INPUT,"_flags"),(now-pressed)/1e6,Math.hypot(releaseX-pressX,releaseY-pressY),
                Math.hypot(releaseX-predicted().tz().x,releaseY-predicted().tz().y));
            if (distance < 0.001) throw new AssertionError("Key did not move the pawn");
            if (Boolean.getBoolean("measurement.requireImmediateStop") &&
                    Math.hypot(releaseX-predicted().tz().x,releaseY-predicted().tz().y) > 0.0001) {
                throw new AssertionError("Visible pawn still lagging behind prediction at release");
            }
        } else if (stage == 2) {
            Actor actor = visible(), prediction = predicted();
            if (prediction.bR(2)) throw new AssertionError("Prediction still moving after key release");
            double forward = (actor.tz().x-releaseX)*directionX + (actor.tz().y-releaseY)*directionY;
            maxForward = Math.max(maxForward,forward);
            if (now-lastSample > 45_000_000L) {
                System.out.printf(Locale.ROOT,"[STOP] elapsedMs=%.1f visual=(%.5f,%.5f) predicted=(%.5f,%.5f) forward=%.5f moving=%s%n",
                    (now-released)/1e6,actor.tz().x,actor.tz().y,prediction.tz().x,prediction.tz().y,forward,prediction.bR(2));
                lastSample = now;
            }
            if (now-released > 600_000_000L) {
                System.out.printf(Locale.ROOT,"[STOP] maxPostReleaseForward=%.5f%n",maxForward);
                if (Boolean.getBoolean("measurement.requireImmediateStop") && maxForward > 0.0001) {
                    throw new AssertionError("Visible pawn continued moving after release: " + maxForward);
                }
                if (++trials == 5) { System.out.println("[STOP] Five key-release trials: PASSED"); stage = 3; shutdown(); }
                else { stage = 0; maxForward = 0; }
            }
        }
        if (now-started > 50_000_000_000L) throw new AssertionError("Keyboard movement probe did not finish");
    }
}
