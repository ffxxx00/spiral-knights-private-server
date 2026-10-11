package validation;

import java.io.FileInputStream;
import java.lang.reflect.*;
import java.util.*;
import server.InstalledConfigManager;
import com.threerings.config.*;
import com.threerings.export.BinaryImporter;
import com.threerings.projectx.item.config.ItemConfig;
import com.threerings.projectx.dungeon.config.*;
import com.threerings.tudey.config.*;
import com.threerings.tudey.data.TudeySceneModel;

/** Read-only inventory of client combat actions, without an official connection. */
public class CombatDataAudit {
    static InstalledConfigManager configs = new InstalledConfigManager();
    static Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    public static void main(String[] args) throws Exception {
        configs.loadGroup(ActorConfig.class,"rsrc/config/actor.dat");
        configs.loadGroup(ItemConfig.class,"rsrc/config/item.dat");
        configs.loadGroup(AttackConfig.class,"rsrc/config/attack.dat");
        configs.loadGroup(FireActionConfig.class,"rsrc/config/fire_action.dat");
        configs.loadGroup(DepthScaleConfig.class,"rsrc/config/depth_scale.dat");
        if (args.length > 0 && args[0].equals("scales")) {
            for (String name : new String[]{"PC/Damage/Sword/Sword Base", "PC/Damage/Sword/Sword Base (Last Chain)", "PC/Damage/Sword/Sword Base (Charged)", "Monster/Health/Health Base -05", "Monster/Defense/Defense Base"}) {
                DepthScaleConfig scale = configs.d(DepthScaleConfig.class,name);
                dump(scale,0,name);
                System.out.println("VALUE depth0="+scale.b(configs,0)+" depth1="+scale.b(configs,1));
            }
            return;
        }
        if (args.length > 0) {
            dump(configs.d(ActorConfig.class,args[0]),0,"catalog");
            visited.clear();
            dump(configs.d(ActorConfig.class,args[0]).ai(configs),0,"actor");
            return;
        }
        for (String name : new String[]{"Weapon/Sword/Leviathan Blade","Weapon/Handgun/Polaris","Weapon/Bomb/Ash of Agni"}) {
            System.out.println("WEAPON " + name);
            ItemConfig item = configs.d(ItemConfig.class,name);
            ItemConfig.Weapon weapon = (ItemConfig.Weapon)item.w(configs);
            dump(weapon,0,"weapon");
            for (AttackConfig.AttackReference attack : weapon.attacks) dumpAttack(attack.ref);
            dumpAttack(weapon.chargedAttack);
            dumpAttack(weapon.incompleteChargedAttack);
        }
        try (FileInputStream in = new FileInputStream("scenes/294")) {
            TudeySceneModel model = (TudeySceneModel)new BinaryImporter(in).qe();
            for (TudeySceneModel.Entry entry : model.adH()) {
                if (!(entry instanceof TudeySceneModel.TileEntry)) {
                    if (entry instanceof TudeySceneModel.PlaceableEntry) {
                        TudeySceneModel.PlaceableEntry p = (TudeySceneModel.PlaceableEntry)entry;
                        if (p.placeable.getName().equals("Dynamic/Misc/Respawn Pad")) System.out.println("PAD id="+p.getKey()+" pos="+p.transform.tJ()+" rotation="+p.transform.tf());
                    }
                    System.out.println("SCENE_ENTRY"); dump(entry,0,"entry");
                }
            }
        }
    }
    static void dumpAttack(ConfigReference<AttackConfig> ref) throws Exception {
        if (ref == null) return;
        System.out.println("ATTACK " + ref);
        AttackConfig.Original original = AttackConfig.b(configs,ref);
        dump(original,0,"attack");
        for (FireActionConfig.FireActionReference fire : original.fireRefs) {
            System.out.println("FIRE " + fire.ref);
            if (fire.ref != null) dump(fire.ref.kW().get("Shape"),0,"shapeBinding");
            dump(configs.a(FireActionConfig.class,fire.ref),0,"fire");
        }
    }
    static void dump(Object value,int depth,String name) throws Exception {
        String indent = "  ".repeat(depth);
        if (value == null || value instanceof Number || value instanceof String || value instanceof Boolean || value.getClass().isEnum() || value instanceof ConfigReference) {
            System.out.println(indent+name+"="+value); return;
        }
        if (depth > 7 || !visited.add(value)) return;
        if (value.getClass().isArray()) {
            for (int i=0;i<Array.getLength(value);i++) dump(Array.get(value,i),depth+1,name+"["+i+"]");
            return;
        }
        System.out.println(indent+name+" type="+value.getClass().getName());
        for (Field field : value.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) dump(field.get(value),depth+1,field.getName());
        }
    }
}
