package server;

import com.threerings.config.ConfigManager;
import com.threerings.config.ConfigReference;
import com.threerings.projectx.item.data.LevelItem;
import com.threerings.math.Vector2f;
import com.threerings.projectx.data.PlayerObject;
import com.threerings.projectx.dungeon.config.AttackConfig;
import com.threerings.projectx.dungeon.data.DungeonInputFrame;
import com.threerings.projectx.dungeon.data.actor.Dungeoneer;
import com.threerings.projectx.dungeon.util.i;
import com.threerings.tudey.data.TudeySceneModel;
import com.threerings.tudey.data.actor.Actor;
import com.threerings.tudey.shape.Shape;

/** Uses the frozen game's own combat state machine instead of imitation timers. */
public final class VanillaCombatController extends i {
    private final SceneManager scene;
    private final Dungeoneer pawn;
    private final PlayerObject player;
    private ConfigReference<?> observedWeapon;
    private int weaponIndex;
    private int observedActivity = Integer.MIN_VALUE, observedStart = Integer.MIN_VALUE;
    public VanillaCombatController(SceneManager scene,PlayerObject player,Dungeoneer pawn,int timestamp) {
        super(new Context(scene,player,pawn),pawn,timestamp);
        this.scene=scene; this.pawn=pawn; this.player=player;
        observedWeapon=pawn.Ri()==null?null:pawn.Ri().Mv();
    }
    public void input(DungeonInputFrame frame) {
        a(frame);
        observe();
    }
    public void tick(int timestamp) {
        gV(timestamp);
        observe();
    }
    private void observe() {
        ConfigReference<?> weapon=pawn.Ri()==null?null:pawn.Ri().Mv();
        if (!java.util.Objects.equals(observedWeapon,weapon)) {
            for (int index=0;index<4;index++) {
                LevelItem equipped=scene.getEquippedWeapon(player,index);
                if (equipped!=null && equipped.Mv().equals(weapon)) {
                    scene.equipCombatWeapon(pawn,player,index);
                    weaponIndex=index; break;
                }
            }
            observedWeapon=weapon;
        }
        int activity=pawn.adU(), started=pawn.adV();
        if (activity==observedActivity && started==observedStart) return;
        observedActivity=activity; observedStart=started;
        AttackConfig.Original attack=null;
        if (activity>=292 && activity<=299) attack=scene.resolveAttack(pawn.Rj(),activity-292);
        else if (activity==290) attack=AttackConfig.b(scene.getConfigManager(),pawn.Rj().chargedAttack);
        else if (activity==289) attack=AttackConfig.b(scene.getConfigManager(),pawn.Rj().incompleteChargedAttack);
        else if (activity==287) attack=AttackConfig.b(scene.getConfigManager(),pawn.y(scene.getConfigManager()).bash);
        if (attack!=null) scene.getCombatWorld().attack(pawn,attack,started,biC);
        System.out.println("[COMBAT] vanilla activity="+activity+" source="+pawn.vw()+" start="+started);
    }
    public int weaponIndex() { return weaponIndex; }
    private static final class Context implements i.c {
        final SceneManager scene; final PlayerObject player; final Dungeoneer pawn;
        Context(SceneManager scene,PlayerObject player,Dungeoneer pawn) { this.scene=scene; this.player=player; this.pawn=pawn; }
        public ConfigManager getConfigManager() { return scene.getConfigManager(); }
        public PlayerObject CT() { return player; }
        public TudeySceneModel Sn() { return scene.getSceneModel(SceneManager.TRAINING_SCENE_ID); }
        public Actor cY(int id) { return id==pawn.vw()?pawn:scene.getCombatWorld().snapshot(0).get(id); }
        public Actor a(Dungeoneer actor,float distance,float angle,int mask) { return null; }
        // Detailed map collision/auto-targeting is a separate, still absent layer.
        // These retain the emulator's existing unobstructed movement policy.
        public boolean a(Actor actor,Shape shape,Vector2f result) { return false; }
        public boolean a(Actor actor,Shape shape) { return false; }
        public boolean a(int mask,Shape shape) { return false; }
        public int b(Actor actor,Shape shape) { return 0; }
    }
}
