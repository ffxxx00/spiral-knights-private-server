package validation;
import server.*;
import java.lang.reflect.*;
import java.nio.channels.SocketChannel;
import com.threerings.projectx.data.PlayerObject;
import com.threerings.projectx.dungeon.data.DungeonInputFrame;
import com.threerings.projectx.dungeon.data.actor.Dungeoneer;

public class VanillaCombatProbe {
    public static void main(String[] args) throws Exception {
        DObjectManager objects=new DObjectManager();
        SceneManager scene=new SceneManager(objects);
        try (SocketChannel channel=SocketChannel.open()) {
            ClientSession session=new ClientSession(channel,objects,scene,null,MovementConfig.load(),1,"20261006114613",0,ignored->{});
            for (String method : new String[]{"initServerObject","initPlayer"}) {
                Method init=ClientSession.class.getDeclaredMethod(method); init.setAccessible(true); init.invoke(session);
            }
            Field field=ClientSession.class.getDeclaredField("playerObject"); field.setAccessible(true);
            PlayerObject player=(PlayerObject)field.get(session);
            Dungeoneer pawn=(Dungeoneer)scene.createPlayerKnight(player,1,294,37.5f,-3.5f);
            pawn.aU(0,1);
            VanillaCombatController controller=new VanillaCombatController(scene,player,pawn,1);
            for (int time=21;time<=2001;time+=20) {
                int flags=time==21?4:0;
                controller.input(new DungeonInputFrame(time,0f,0f,flags,21));
                scene.getCombatWorld().step(time,java.util.Map.of(pawn.vw(),pawn));
                if(time%100==21) System.out.println("PROBE t="+time+" activity="+pawn.adU()+" pos="+pawn.tz()+" shield="+pawn.QG());
            }
        }
    }
}
