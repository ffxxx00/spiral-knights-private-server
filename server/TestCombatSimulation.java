package server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.SocketChannel;
import java.util.Map;
import com.threerings.projectx.data.PlayerObject;
import com.threerings.projectx.dungeon.config.AttackConfig;
import com.threerings.projectx.dungeon.data.DungeonInputFrame;
import com.threerings.projectx.dungeon.data.actor.*;
import com.threerings.tudey.data.TudeySceneModel;
import com.threerings.tudey.data.actor.Actor;
import com.threerings.tudey.dobj.ActorDelta;

/** Deterministic regressions against the actual client combat catalogs. */
public final class TestCombatSimulation {
    public static void main(String[] args) throws Exception {
        DObjectManager objects=new DObjectManager();
        SceneManager scene=new SceneManager(objects);
        try (SocketChannel channel=SocketChannel.open()) {
            ClientSession session=new ClientSession(channel,objects,scene,null,
                MovementConfig.load(),1,"20261006114613",0,ignored->{});
            for (String name : new String[]{"initServerObject","initPlayer"}) {
                Method method=ClientSession.class.getDeclaredMethod(name);
                method.setAccessible(true); method.invoke(session);
            }
            Field field=ClientSession.class.getDeclaredField("playerObject"); field.setAccessible(true);
            PlayerObject player=(PlayerObject)field.get(session);
            checkEquipment(scene,player);
            checkCollisions(scene,player);
            checkMelee(scene,player);
            checkMissAndCancellation(scene,player);
            checkProjectile(scene,player);
            checkBomb(scene,player);
            checkNativeControls(scene,player);
            checkSwordCharges(scene,player);
        }
        System.out.println("VANILLA COMBAT SIMULATION TEST PASSED");
    }

    private static void checkMelee(SceneManager scene,PlayerObject player) {
        CombatWorld world=newWorld(scene);
        Monster target=firstTarget(world);
        Dungeoneer pawn=pawn(scene,player,target,1);
        AttackConfig.Original attack=scene.resolveAttack(pawn.Rj(),0);
        int start=21,land=start+attack.Qh();
        pawn.aU(292,start); world.attack(pawn,attack,start);
        Monster before=(Monster)world.step(land-1,Map.of(pawn.vw(),pawn)).get(target.vw());
        check(before.RA()==1,"Melee damaged before its installed land time");
        Monster dead=(Monster)world.step(land,Map.of(pawn.vw(),pawn)).get(target.vw());
        check(dead.RA()==0 && dead.adY()==land,"Vanilla sword did not kill the 30-health training cube");
        check(target.RA()==1,"Published snapshots alias mutable combat health");
        Monster clientA=(Monster)new ActorDelta(target,dead).apply(target);
        Monster clientB=(Monster)new ActorDelta(target,dead).apply(target);
        check(clientA.RA()==0 && clientB.RA()==0 && clientA.adY()==land,
            "Monster delta lost shared health or destruction state");
        check(world.step(land+50,Map.of(pawn.vw(),pawn)).get(target.vw())!=null,
            "Death actor removed before installed death animation time");
        check(!world.step(land+100,Map.of(pawn.vw(),pawn)).containsKey(target.vw()),
            "Dead cube not removed after installed death time");
        int delay=respawnDelay(scene,target);
        check(world.step(land+100+delay-1,Map.of(pawn.vw(),pawn)).size()==10,"Cube respawned early");
        Map<Integer,Actor> respawned=world.step(land+100+delay,Map.of(pawn.vw(),pawn));
        check(respawned.size()==11 && !respawned.containsKey(target.vw()),"Cube failed to respawn with a fresh actor id");
        for (Actor actor : respawned.values()) check(((Monster)actor).RA()==1,"Respawn health or duplicate impact incorrect");
        System.out.println("[CombatTest] melee land, geometry, death, deltas, respawn passed");
    }

    private static void checkMissAndCancellation(SceneManager scene,PlayerObject player) {
        for (int scenario=0;scenario<4;scenario++) {
            CombatWorld world=newWorld(scene); Monster target=firstTarget(world);
            Dungeoneer pawn=pawn(scene,player,target,scenario==0?10:1);
            if (scenario==1) pawn.ah((float)Math.PI);
            AttackConfig.Original attack=scene.resolveAttack(pawn.Rj(),0);
            pawn.aU(292,21); world.attack(pawn,attack,21);
            world.step(22,Map.of(pawn.vw(),pawn));
            if (scenario==2) pawn.aU(286,30);
            Map<Integer,Actor> actors=world.step(21+attack.Qh(),
                scenario==3?Map.of():Map.of(pawn.vw(),pawn));
            for (Actor actor : actors.values()) check(((Monster)actor).RA()==1,
                "Out-of-range, wrong-facing, cancelled or departed attack caused damage: "+scenario);
        }
        System.out.println("[CombatTest] range, facing, interruption, departure passed");
    }

    private static void checkProjectile(SceneManager scene,PlayerObject player) {
        CombatWorld world=newWorld(scene); Monster target=firstTarget(world);
        Dungeoneer pawn=pawn(scene,player,target,2);
        scene.equipCombatWeapon(pawn,player,2);
        AttackConfig.Original attack=scene.resolveAttack(pawn.Rj(),0);
        pawn.aU(292,21); world.attack(pawn,attack,21);
        int land=21+attack.Qh();
        Map<Integer,Actor> actors=world.step(land,Map.of(pawn.vw(),pawn));
        Actor bullet=actors.values().stream().filter(a->a instanceof Bullet).findFirst().orElseThrow(
            ()->new AssertionError("Polaris only animated: no bullet actor"));
        check(((Monster)actors.get(target.vw())).RA()==1,"Gun damaged before projectile contact");
        boolean hit=false;
        for (int time=land+20;time<land+600;time+=20) {
            actors=world.step(time,Map.of(pawn.vw(),pawn));
            Actor victim=actors.get(target.vw());
            if (victim==null || ((Monster)victim).RA()<1) { hit=true; break; }
        }
        check(hit,"Projectile flight did not damage the cube");
        check(actors.get(bullet.vw()).adY()<Integer.MAX_VALUE,"Bullet did not detonate on contact");
        CombatWorld missed=newWorld(scene);
        Monster distant=firstTarget(missed);
        Dungeoneer shooter=pawn(scene,player,distant,50);
        scene.equipCombatWeapon(shooter,player,2);
        AttackConfig.Original shot=scene.resolveAttack(shooter.Rj(),0);
        shooter.aU(292,21); missed.attack(shooter,shot,21);
        int fired=21+shot.Qh();
        Bullet expired=(Bullet)missed.step(fired,Map.of(shooter.vw(),shooter)).values().stream()
            .filter(a->a instanceof Bullet).findFirst().orElseThrow();
        int expiry=fired+expired.Rg();
        Map<Integer,Actor> fizzled=missed.step(expiry,Map.of(shooter.vw(),shooter));
        check(((Bullet)fizzled.get(expired.vw())).Rh(),"Expired projectile did not use vanilla fizzle flag");
        for (Actor actor : fizzled.values()) if (actor instanceof Monster)
            check(((Monster)actor).RA()==1,"Missed projectile caused damage on expiration");
        System.out.println("[CombatTest] Polaris spawn, flight, contact damage passed");
    }

    private static void checkBomb(SceneManager scene,PlayerObject player) {
        CombatWorld world=newWorld(scene); Monster target=firstTarget(world);
        Dungeoneer pawn=pawn(scene,player,target,1);
        scene.equipCombatWeapon(pawn,player,3);
        AttackConfig.Original attack=AttackConfig.b(scene.getConfigManager(),pawn.Rj().chargedAttack);
        pawn.aU(290,21); world.attack(pawn,attack,21);
        Map<Integer,Actor> actors=world.step(21,Map.of(pawn.vw(),pawn));
        Bomb bomb=(Bomb)actors.values().stream().filter(a->a instanceof Bomb).findFirst().orElseThrow(
            ()->new AssertionError("Charged bomb only animated: no bomb actor"));
        int fuse=((com.threerings.projectx.dungeon.config.DungeonActorConfig.Bomb)bomb.adW()).fuse;
        check(((Monster)world.step(20+fuse,Map.of(pawn.vw(),pawn)).get(target.vw())).RA()==1,
            "Bomb damaged before its configured fuse");
        actors=world.step(21+fuse,Map.of(pawn.vw(),pawn));
        check(((Monster)actors.get(target.vw())).RA()<1,"Charged bomb blast did not damage a cube in its radius");
        check(actors.get(bomb.vw()).adY()==21+fuse,"Bomb did not expire at its installed fuse");
        for (int time=21+fuse+20;time<=21+fuse+5_040;time+=20)
            actors=world.step(time,Map.of(pawn.vw(),pawn));
        for (Actor actor : actors.values()) check(!(actor instanceof Bomb),"Chemical cloud leaked beyond configured lifespan");
        System.out.println("[CombatTest] Ash of Agni spawn, fuse, blast passed");
    }

    private static void checkNativeControls(SceneManager scene,PlayerObject player) {
        Dungeoneer pawn=(Dungeoneer)scene.createPlayerKnight(player,1,294,SceneManager.TRAINING_SPAWN_X,SceneManager.TRAINING_SPAWN_Y);
        pawn.aU(0,1);
        VanillaCombatController controller=new VanillaCombatController(scene,player,pawn,1);
        controller.input(new DungeonInputFrame(21,0f,0f,4,21));
        check(pawn.adU()==292,"Client primary state machine did not start attack");
        controller.input(new DungeonInputFrame(41,0f,0f,DungeonInputFrame.au(0,2),41));
        check(pawn.Ri().Mv().getName().equals("Weapon/Sword/Leviathan Blade"),"Weapon switch bypassed native recovery");
        for (int time=61;time<=1001;time+=20) controller.input(new DungeonInputFrame(time,0f,0f,0,41));
        controller.input(new DungeonInputFrame(1021,0f,0f,DungeonInputFrame.au(0,2),1021));
        check(pawn.Ri().Mv().getName().equals("Weapon/Handgun/Polaris"),"Weapon switch failed after native recovery");
        controller.input(new DungeonInputFrame(1041,0f,0f,DungeonInputFrame.au(1,2),1021));
        float beforeMovement=pawn.tz().x;
        controller.tick(1061);
        check(pawn.tz().x>beforeMovement,"Native combat controller marked walking without moving");
        float stopped=pawn.tz().x;
        controller.input(new DungeonInputFrame(1081,0f,0f,DungeonInputFrame.au(0,2),1081));
        stopped=pawn.tz().x;
        controller.tick(1101); controller.tick(1121);
        check(pawn.tz().x==stopped,"Native combat movement kept sliding after key release");
        System.out.println("[CombatTest] native recovery, weapon switching, movement stop passed");
    }

    private static void checkEquipment(SceneManager scene,PlayerObject player) {
        check(player.rank.equals(com.threerings.projectx.mission.data.MissionCodes.bAR),"Maximum native rank missing");
        for(int rarity=0;rarity<=5;rarity++)
            check(player.dN(rarity),"Native client refuses equipment rarity "+rarity);
        java.util.Set<String> names=new java.util.HashSet<>();
        for(com.threerings.projectx.item.data.Item item:player.items) {
            check(names.add(item.Mv().getName()),"Duplicate arsenal item");
            check(scene.resolveItemConfig(item)!=null,"Unresolved inventory configuration");
            check(player.dN(scene.resolveItemConfig(item).rarity),"Client rank gate blocks "+item.Mv());
        }
        for(com.threerings.config.ManagedConfig entry:scene.getConfigManager().bd(com.threerings.projectx.item.config.ItemConfig.class).kL()) {
            com.threerings.projectx.item.config.ItemConfig config=scene.getConfigManager().d(com.threerings.projectx.item.config.ItemConfig.class,entry.getName());
            if(config==null || !config.creatable) continue;
            com.threerings.projectx.item.config.ItemConfig.Original original=config.w(scene.getConfigManager());
            if(original instanceof com.threerings.projectx.item.config.ItemConfig.Sword
                    || original instanceof com.threerings.projectx.item.config.ItemConfig.Handgun
                    || original instanceof com.threerings.projectx.item.config.ItemConfig.Bomb)
                check(names.contains(entry.getName()),"Client weapon missing: "+entry.getName());
        }
        long helmet=player.equipment[0],armor=player.equipment[1];
        check(scene.resolveItemConfig(findItem(player,helmet)) instanceof com.threerings.projectx.item.config.ItemConfig.Helm,"Helmet slot reversed");
        check(scene.resolveItemConfig(findItem(player,armor)) instanceof com.threerings.projectx.item.config.ItemConfig.Armor,"Armor slot reversed");
        check("True".equals(scene.createPlayerModel(player).kW().get("Shield Away")),"Town shield policy changed");
        check("False".equals(scene.createPlayerModel(player,0).kW().get("Shield Away")),"Combat shield still on back");
        check(!scene.createPlayerModel(player,0).kW().containsKey("Shield"),"Base combat model duplicates the sprite shield");
        scene.moveEquipment(player,helmet,com.threerings.projectx.item.data.ItemCodes.Location.ARSENAL,-1);
        check(player.equipment[0]==0,"Helmet unequip failed");
        scene.moveEquipment(player,helmet,com.threerings.projectx.item.data.ItemCodes.Location.EQUIPMENT,0);
        try {
            scene.moveEquipment(player,armor,com.threerings.projectx.item.data.ItemCodes.Location.EQUIPMENT,0);
            throw new AssertionError("Armor accepted in helmet slot");
        } catch(IllegalArgumentException expected) { }
        check(player.equipment[1]==armor && player.equipment[0]==helmet,"Rejected equip mutated slots");
        Dungeoneer pawn=(Dungeoneer)scene.createPlayerKnight(player,1,294,0,0);
        scene.equipCombatWeapon(pawn,player,2);
        VanillaCombatController controller=new VanillaCombatController(scene,player,pawn,1);
        check(controller.weaponIndex()==2,"Controller lost the equipped weapon slot");
        long shield=player.equipment[2],weapon=player.equipment[8];
        pawn.aU(292,21);
        scene.moveEquipment(player,shield,com.threerings.projectx.item.data.ItemCodes.Location.ARSENAL,-1);
        scene.refreshCombatEquipment(pawn,player,2,41);
        check(pawn.Rk()==null && pawn.adU()==0 && pawn.adV()!=21,"Shield removal kept stale shield or attack state");
        scene.moveEquipment(player,shield,com.threerings.projectx.item.data.ItemCodes.Location.EQUIPMENT,2);
        scene.moveEquipment(player,weapon,com.threerings.projectx.item.data.ItemCodes.Location.ARSENAL,-1);
        scene.refreshCombatEquipment(pawn,player,2,61);
        check(pawn.Ri()==null && pawn.Rk()!=null,"Unarmed combat equipment did not refresh");
        scene.moveEquipment(player,weapon,com.threerings.projectx.item.data.ItemCodes.Location.EQUIPMENT,8);
        scene.refreshCombatEquipment(pawn,player,2,81);
        check(pawn.Ri()!=null && pawn.Ri().VA()==weapon,"Active combat weapon could not be reequipped");
        System.out.println("[CombatTest] complete arsenal, native slots, max rank, shield and equipment passed");
    }

    private static void checkCollisions(SceneManager scene,PlayerObject player) {
        float trainingWall=0;
        for(int id:new int[]{1,294}) {
            float x=id==1?SceneManager.HAVEN_SPAWN_X:SceneManager.TRAINING_SPAWN_X;
            float y=id==1?SceneManager.HAVEN_SPAWN_Y:SceneManager.TRAINING_SPAWN_Y;
            Actor pawn=scene.createPlayerKnight(player,1,id,x,y);
            TudeySceneModel model=scene.getSceneModel(id);
            check(!model.a(pawn,collisionShape(pawn,model)),"Spawn intersects client map: "+id);
            float blockedX=x;
            for(int step=1;step<=800;step++) {
                pawn.tz().s(x+step*0.1f,y);
                if(model.a(pawn,collisionShape(pawn,model))) { blockedX=pawn.tz().x;break; }
            }
            check(blockedX>x,"No client map wall found on test ray: "+id);
            if(id==294)trainingWall=blockedX;
            pawn.tz().s(x,y);
            scene.getCollision(id).move(pawn,blockedX+2,y);
            check(pawn.tz().x>x && pawn.tz().x<blockedX,"Town movement tunneled through map wall: "+id+" start="+x+","+y+" blockedX="+blockedX+" end="+pawn.tz());
            check(!model.a(pawn,collisionShape(pawn,model)),"Map collision left pawn penetrating: "+id);
            if(pawn instanceof Dungeoneer) {
                pawn.tz().s(x,y);
                ((Dungeoneer)pawn).aU(0,1);
                VanillaCombatController controller=new VanillaCombatController(scene,player,(Dungeoneer)pawn,1);
                int duration=(int)Math.ceil((blockedX+2-x)/5f*1000);
                for(int time=21;time<duration+100;time+=20)
                    controller.input(new DungeonInputFrame(time,0,0,1,21));
                check(pawn.tz().x>x && pawn.tz().x<blockedX,"Native map movement failed: start="+x+" wall="+blockedX+" end="+pawn.tz());
                check(!model.a(pawn,collisionShape(pawn,model)),"Native controller left map penetration");
            }
        }
        CombatWorld world=newWorld(scene);
        Monster target=(Monster)world.snapshot(1).values().stream()
            .filter(a->a.tz().x==31.5f && a.tz().y==-0.5f).findFirst().orElseThrow();
        Dungeoneer pawn=pawn(scene,player,target,1.5f);
        TudeySceneModel model=scene.getSceneModel(294);
        SceneCollision collision=new SceneCollision(model,world);
        check(!collision.intersects(pawn,collisionShape(pawn,model)),"Enemy test starts obstructed");
        VanillaCombatController controller=new VanillaCombatController(scene,player,pawn,1,world);
        float start=pawn.tz().x;
        for(int time=21;time<=801;time+=20)controller.input(new DungeonInputFrame(time,0,0,1,21));
        check(pawn.tz().x>start && pawn.tz().x<target.tz().x,"Walking crossed a living cube");
        check(!collision.intersects(pawn,collisionShape(pawn,model)),"Enemy collision left penetration");
        float stopped=pawn.tz().x;
        AttackConfig.Original attack=scene.resolveAttack(pawn.Rj(),0);
        pawn.aU(292,821);world.attack(pawn,attack,821);
        world.step(821+attack.Qh(),Map.of(pawn.vw(),pawn));
        check(((Monster)world.snapshot(0).get(target.vw())).RA()==0,"Collision release test failed to kill cube");
        collision.move(pawn,target.tz().x+1,target.tz().y);
        check(pawn.tz().x>target.tz().x && pawn.tz().x>stopped,"Dead enemy still blocks movement");
        CombatWorld shots=newWorld(scene);
        Dungeoneer shooter=(Dungeoneer)scene.createPlayerKnight(player,1,294,trainingWall-2,SceneManager.TRAINING_SPAWN_Y);
        scene.equipCombatWeapon(shooter,player,2);
        AttackConfig.Original shot=scene.resolveAttack(shooter.Rj(),0);
        shooter.aU(292,21);shots.attack(shooter,shot,21);
        int fired=21+shot.Qh();
        Bullet bullet=(Bullet)shots.step(fired,Map.of(shooter.vw(),shooter)).values().stream()
            .filter(a->a instanceof Bullet).findFirst().orElseThrow();
        // Native projectile mask differs from the player's: low barriers may
        // stop walking while allowing shots. Find the bullet's actual wall.
        com.threerings.math.Vector2f bulletSpawn=new com.threerings.math.Vector2f(bullet.tz());
        float bulletWall=Float.NaN;
        for(int step=1;step<500;step++) {
            bullet.tz().s(bulletSpawn.x+step*0.1f,bulletSpawn.y);
            if(model.a(bullet,collisionShape(bullet,model))) {bulletWall=bullet.tz().x;break;}
        }
        bullet.tz().q(bulletSpawn);
        check(Float.isFinite(bulletWall),"No native projectile wall found");
        boolean wallHit=false;
        for(int time=fired+20;time<fired+bullet.Rg();time+=20) {
            Actor updated=shots.step(time,Map.of(shooter.vw(),shooter)).get(bullet.vw());
            if(updated!=null && updated.adY()<Integer.MAX_VALUE) {
                check(Math.abs(updated.tz().x-bulletWall)<0.15f,"Projectile did not stop at its native map boundary: "+updated.tz()+" wall="+bulletWall);
                wallHit=true;break;
            }
        }
        check(wallHit,"Projectile crossed client wall or only expired at fuse time");
        System.out.println("[CombatTest] Haven/Training walls, native movement, live/dead enemy and projectile collision passed");
    }

    private static com.threerings.tudey.shape.Shape collisionShape(Actor actor,TudeySceneModel model) {
        return actor.adW().p(model.getConfigManager()).acP()
            .d(new com.threerings.math.Transform2D(actor.tz(),actor.tA()));
    }

    private static com.threerings.projectx.item.data.Item findItem(PlayerObject player,long id) {
        for(com.threerings.projectx.item.data.Item item:player.items) if(item.VA()==id)return item;
        throw new AssertionError("Inventory item absent");
    }

    private static void checkSwordCharges(SceneManager scene,PlayerObject player) {
        long original=player.equipment[6];
        for(String name:new String[]{"Weapon/Sword/Leviathan Blade","Weapon/Sword/Combuster"}) {
            for(com.threerings.projectx.item.data.Item item:player.items)
                if(item.Mv().getName().equals(name))player.equipment[6]=item.VA();
            CombatWorld world=newWorld(scene);
            // Use the open gun range; the old five-unit charge fixture stood
            // outside the west room, shooting through its wall.
            Monster target=(Monster)world.snapshot(1).values().stream()
                .filter(a->a.tz().x==38.5f && a.tz().y==-3.5f).findFirst().orElseThrow();
            Dungeoneer pawn=pawn(scene,player,target,name.endsWith("Combuster")?5f:1f);
            check(!scene.getSceneModel(294).a(pawn,collisionShape(pawn,scene.getSceneModel(294))),"Sword charge test starts inside map geometry");
            VanillaCombatController controller=new VanillaCombatController(scene,player,pawn,1,world);
            boolean charging=false,charged=false,chargedDamage=false;
            java.util.Set<Integer> burstIds=new java.util.HashSet<>();
            for(int time=21;time<=7001;time+=20) {
                int flags=time<=4201?4:0;
                controller.input(new DungeonInputFrame(time,0,0,flags,flags==0?4221:21));
                charging|=pawn.adU()==283;charged|=pawn.adU()==290;
                for(Actor actor:world.step(time,Map.of(pawn.vw(),pawn)).values()) {
                    if(actor.Mv().getName().equals("Bullet/Sword/Parts/Burst Trail")) {
                        burstIds.add(actor.vw());
                        check(java.util.Objects.equals(actor.Mv().kW().get("Color"),
                            pawn.Rj().chargedAttack.kW().get("Burst Color")),"Charged burst lost native weapon color: actual="
                                +actor.Mv().kW().get("Color")+" expected="+pawn.Rj().chargedAttack.kW().get("Burst Color"));
                    }
                    if(time>4221 && actor.vw()==target.vw() && actor instanceof Monster
                            && ((Monster)actor).RA()<1)chargedDamage=true;
                }
            }
            check(charging && charged,"Sword charge did not hold and release: "+name);
            if(name.endsWith("Combuster")) {
                check(burstIds.size()==5,"Combuster did not publish five charged bursts: "+burstIds.size());
                check(chargedDamage,"Combuster charged wave did not damage a cube beyond primary melee reach");
            }
        }
        player.equipment[6]=original;
        System.out.println("[CombatTest] Leviathan and Combuster charged attacks, five burst actors passed");
    }

    private static CombatWorld newWorld(SceneManager scene) {
        return new CombatWorld(scene.getConfigManager(),scene.getSceneModel(SceneManager.TRAINING_SCENE_ID));
    }
    private static Monster firstTarget(CombatWorld world) {
        Map<Integer,Actor> snapshot=world.snapshot(1);
        check(snapshot.size()==11,"Client training target count changed");
        return (Monster)snapshot.values().iterator().next();
    }
    private static Dungeoneer pawn(SceneManager scene,PlayerObject player,Monster target,float distance) {
        Dungeoneer pawn=(Dungeoneer)scene.createPlayerKnight(player,1,294,target.tz().x-distance,target.tz().y);
        pawn.aU(0,1); return pawn;
    }
    private static int respawnDelay(SceneManager scene,Monster target) {
        for (TudeySceneModel.Entry entry : scene.getSceneModel(294).adH()) {
            if (!(entry instanceof TudeySceneModel.PlaceableEntry)) continue;
            TudeySceneModel.PlaceableEntry pad=(TudeySceneModel.PlaceableEntry)entry;
            if (!pad.placeable.getName().equals("Dynamic/Misc/Respawn Pad")) continue;
            if (pad.transform.tJ().x==target.tz().x && pad.transform.tJ().y==target.tz().y)
                return ((Number)pad.placeable.kW().get("Delay")).intValue();
        }
        throw new AssertionError("Training respawn pad missing");
    }
    private static void check(boolean result,String message) {
        if (!result) throw new AssertionError(message);
    }
}
