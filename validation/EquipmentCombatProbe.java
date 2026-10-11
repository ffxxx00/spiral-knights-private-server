package validation;
import server.*;
import java.lang.reflect.*;
import java.nio.channels.SocketChannel;
import com.threerings.projectx.data.PlayerObject;
import com.threerings.projectx.item.config.ItemConfig;
import com.threerings.projectx.item.data.*;
import com.threerings.projectx.dungeon.data.DungeonInputFrame;
import com.threerings.projectx.dungeon.data.actor.Dungeoneer;
public class EquipmentCombatProbe {
 public static void main(String[] args) throws Exception {
  SceneManager scene=new SceneManager(new DObjectManager());
  try(SocketChannel ch=SocketChannel.open()) {
   ClientSession session=new ClientSession(ch,new DObjectManager(),scene,null,MovementConfig.load(),1,"20261006114613",0,x->{});
   for(String name:new String[]{"initServerObject","initPlayer"}) {Method m=ClientSession.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(session);}
   Field f=ClientSession.class.getDeclaredField("playerObject");f.setAccessible(true);PlayerObject p=(PlayerObject)f.get(session);
   System.out.println("RANK LIMITS "+ItemCodes.bwB+" MAX="+com.threerings.projectx.mission.data.MissionCodes.bAR);
   for(ItemCodes.Group g:ItemCodes.Group.values())if(g.getSlots()!=null)System.out.println("SLOTS "+g+" "+java.util.Arrays.toString(g.getSlots()));
   for(String name:new String[]{"Gear/Armor/Chaos Cloak","Gear/Helm/Chaos Cowl","Weapon/Sword/Combuster"}) {
    ItemConfig c=scene.getConfigManager().d(ItemConfig.class,name);ItemConfig.Original o=c.w(scene.getConfigManager());
    System.out.println("ITEM "+name+" id="+c.getConfigId()+" creatable="+c.creatable+" class="+o.getClass()+" UZ="+o.UZ()+" allowed="+o.a(p,ItemCatalog.createItem(90909,name,10,scene.getConfigManager())));
   }
   p.equipment[6]=p.equipment[7];
   Dungeoneer pawn=(Dungeoneer)scene.createPlayerKnight(p,1,294,5.5f,-12.5f);pawn.aU(0,1);
   VanillaCombatController control=new VanillaCombatController(scene,p,pawn,1);
   System.out.println("MOVE LIMITS radius="+pawn.aeb()+" square="+pawn.aec()+" shape="+pawn.adW().shape);
   for(int time=21;time<=401;time+=20) {control.input(new DungeonInputFrame(time,0,0,1,21)); if(time%100==21)System.out.println("MOVE t="+time+" pos="+pawn.tz()+" moving="+pawn.bR(2)+" can="+control.QI()+" speed="+pawn.getSpeed()+" status="+pawn.Rq().getSpeedModifier());}
   for(int time=421;time<=7001;time+=20) {int flags=time<=4801?4:0;control.input(new DungeonInputFrame(time,0,0,flags,flags==0?4821:421)); scene.getCombatWorld().step(time,java.util.Map.of(pawn.vw(),pawn)); if(time%200==21)System.out.println("CHARGE t="+time+" activity="+pawn.adU());}
   validation.CombatDataAudit.configs=scene.getConfigManager();
   validation.CombatDataAudit.dumpAttack(pawn.Rj().chargedAttack);
  }
 }
}
