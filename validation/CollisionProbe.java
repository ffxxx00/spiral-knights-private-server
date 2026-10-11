package validation;
import server.*;
import java.lang.reflect.*;
import java.nio.channels.SocketChannel;
import com.threerings.math.*;
import com.threerings.projectx.data.PlayerObject;
import com.threerings.tudey.data.actor.Actor;
import com.threerings.tudey.data.TudeySceneModel;
public class CollisionProbe {
 public static void main(String[] args)throws Exception {
  DObjectManager objects=new DObjectManager();SceneManager scene=new SceneManager(objects);
  try(SocketChannel ch=SocketChannel.open()) {
   ClientSession s=new ClientSession(ch,objects,scene,null,MovementConfig.load(),1,"20261006114613",0,x->{});
   for(String name:new String[]{"initServerObject","initPlayer"}) {Method m=ClientSession.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(s);}
   Field f=ClientSession.class.getDeclaredField("playerObject");f.setAccessible(true);PlayerObject p=(PlayerObject)f.get(s);
   for(int id:new int[]{1,294}) {
    Actor a=scene.createPlayerKnight(p,1,id,id==1?1:5.5f,id==1?-30.5f:-12.5f);
    TudeySceneModel model=scene.getSceneModel(id);
    System.out.println("PAWN scene="+id+" collision="+a.adW().collisionFlags+" mask="+a.adW().collisionMask);
    for(int y=-32;y<=20;y+=2) {
     StringBuilder row=new StringBuilder("MAP "+id+" y="+y+" ");
     for(int x=-25;x<=45;x++) {
      a.tz().s(x+0.5f,y+0.5f);
      row.append(model.a(a,a.adW().p(model.getConfigManager()).acP().d(new Transform2D(a.tz(),0)))?'#':'.');
     }
     System.out.println(row);
    }
   }
   for(Actor a:scene.getCombatWorld().snapshot(1).values())System.out.println("ENEMY pos="+a.tz()+" flags="+a.adW().collisionFlags+" mask="+a.adW().collisionMask+" shape="+a.adW().shape);
  }
 }
}
