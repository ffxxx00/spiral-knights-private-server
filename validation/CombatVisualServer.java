package validation;

import server.*;
import java.lang.reflect.Field;
import java.util.Map;
import com.threerings.projectx.dungeon.data.actor.Dungeoneer;

/** Isolated graphical smoke test; never attaches to the normal game server. */
public final class CombatVisualServer {
    public static void main(String[] args) throws Exception {
        SpiralServer server=new SpiralServer(48024);
        Field sessionsField=SpiralServer.class.getDeclaredField("sessions"); sessionsField.setAccessible(true);
        Field sceneField=SpiralServer.class.getDeclaredField("sceneMgr"); sceneField.setAccessible(true);
        Field pawnField=ClientSession.class.getDeclaredField("authoritativeActor"); pawnField.setAccessible(true);
        Field clockField=ClientSession.class.getDeclaredField("sceneTimestamp"); clockField.setAccessible(true);
        SceneManager scene=(SceneManager)sceneField.get(server);
        boolean injected=false;
        try {
            server.start();
            long deadline=System.nanoTime()+300_000_000_000L;
            while (System.nanoTime()<deadline) {
                if (!injected) {
                    @SuppressWarnings("unchecked") Map<Integer,ClientSession> sessions=(Map<Integer,ClientSession>)sessionsField.get(server);
                    for (ClientSession session : sessions.values()) synchronized (session) {
                        Object actor=pawnField.get(session);
                        if (!(actor instanceof Dungeoneer)) continue;
                        Dungeoneer pawn=(Dungeoneer)actor;
                        int time=clockField.getInt(session);
                        if (time<12_001 || !java.nio.file.Files.isRegularFile(
                                java.nio.file.Path.of("validation/combat-visual.ready"))) continue;
                        pawn.tz().s(37.5f,-3.5f); pawn.ah(0); pawn.aU(292,time);
                        scene.getCombatWorld().attack(pawn,scene.resolveAttack(pawn.Rj(),0),time);
                        injected=true;
                        System.out.println("[VISUAL-COMBAT] injected sword against frozen cube time="+time);
                    }
                }
                Thread.sleep(50L);
            }
        } finally { server.stop(); }
    }
}
