package server;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import com.threerings.config.ConfigReference;
import com.threerings.crowd.chat.data.ChatMarshaller;
import com.threerings.crowd.data.PlaceConfig;
import com.threerings.io.r;
import com.threerings.io.s;
import com.threerings.io.v;
import com.threerings.io.w;
import com.threerings.media.image.ColorPository;
import com.threerings.presents.data.InvocationMarshaller;
import com.threerings.presents.client.InvocationReceiver;
import com.threerings.presents.dobj.CompoundEvent;
import com.threerings.presents.dobj.DEvent;
import com.threerings.presents.dobj.DObject;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.f;
import com.threerings.presents.dobj.InvocationNotificationEvent;
import com.threerings.presents.dobj.InvocationRequestEvent;
import com.threerings.presents.dobj.InvocationResponseEvent;
import com.threerings.presents.net.AuthRequest;
import com.threerings.presents.net.AuthResponse;
import com.threerings.presents.net.BootstrapNotification;
import com.threerings.presents.net.EventNotification;
import com.threerings.presents.net.ForwardEventRequest;
import com.threerings.presents.net.Message;
import com.threerings.presents.net.ObjectResponse;
import com.threerings.presents.net.PingRequest;
import com.threerings.presents.net.PongResponse;
import com.threerings.presents.net.SubscribeRequest;
import com.threerings.presents.net.Transport;
import com.threerings.projectx.data.PlayerObject;
import com.threerings.projectx.data.ProjectXBootstrapData;
import com.threerings.projectx.data.ProjectXOccupantInfo;
import com.threerings.projectx.data.ProjectXSceneObject;
import com.threerings.projectx.dungeon.data.DungeonInputFrame;
import com.threerings.projectx.dungeon.data.PartyObject;
import com.threerings.projectx.dungeon.data.TrainingSceneConfig;
import com.threerings.projectx.dungeon.data.actor.Dungeoneer;
import com.threerings.projectx.item.data.Item;
import com.threerings.projectx.town.data.TownSceneObject;
import com.threerings.projectx.town.data.TownSceneConfig;
import com.threerings.tudey.data.TudeySceneModel;
import com.threerings.tudey.data.InputEnqueueEvent;
import com.threerings.tudey.data.InputFrame;
import com.threerings.tudey.data.actor.Actor;
import com.threerings.tudey.dobj.ActorDelta;
import com.threerings.tudey.dobj.SceneDeltaEvent;
import com.threerings.projectx.data.actor.Knight;
import com.threerings.whirled.data.SceneMarshaller;
import com.threerings.whirled.zone.data.ZoneMarshaller;
import com.threerings.whirled.zone.data.ZoneSummary;

/** End-to-end protocol test for the minimal private server handshake. */
public class TestEmulatorProtocol {
    private static final long READ_TIMEOUT_MS = 10_000L;

    public static void main(String[] args) throws Exception {
        System.setProperty("com.threerings.io.enumPolicy", "ORDINAL");
        int testPort = 47625;

        System.out.println("[Test] Starting test server instance on port " + testPort + "...");
        SpiralServer server = new SpiralServer(testPort);
        SocketChannel client = null;
        SocketChannel friendClient = null;
        try {
            server.start();
            Thread.sleep(150L);

            client = SocketChannel.open(new InetSocketAddress("localhost", testPort));
            client.configureBlocking(false);
            Wire wire = new Wire(client);

            System.out.println("[Test] 1. Authenticating...");
            wire.send(new AuthRequest(null, "20260828143805", new String[]{"presents", "projectx"}));
            AuthResponse auth = expect(wire.read(), AuthResponse.class, "AuthResponse");
            check("success".equals(auth.yf().code), "Authentication did not return success");

            BootstrapNotification bootstrapMessage = expect(
                wire.read(), BootstrapNotification.class, "BootstrapNotification");
            ProjectXBootstrapData bootstrap = (ProjectXBootstrapData)bootstrapMessage.yg();
            check(bootstrap.services != null, "Bootstrap services are null");
            checkService(bootstrap.services, ChatMarshaller.class, "ChatMarshaller");
            checkService(bootstrap.services, SceneMarshaller.class, "SceneMarshaller");
            checkService(bootstrap.services, ZoneMarshaller.class, "ZoneMarshaller");
            System.out.println("[Test] Bootstrap has all required global services (count="
                + bootstrap.services.size() + ").");

            check(wire.tryRead(250L) == null,
                "Server sent an unsolicited object before the client subscription");

            System.out.println("[Test] 2. Subscribing to PlayerObject OID=" + bootstrap.clientOid + "...");
            wire.send(new SubscribeRequest(bootstrap.clientOid));
            ObjectResponse<?> playerResponse = expect(
                wire.read(), ObjectResponse.class, "PlayerObject ObjectResponse");
            DObject object = playerResponse.yj();
            check(object instanceof PlayerObject,
                "Expected PlayerObject, got " + object.getClass().getName());
            PlayerObject player = (PlayerObject)object;
            check(player.color == SceneManager.DEFAULT_PLAYER_COLOR,
                "PlayerObject does not use the installed starter player color");
            checkInstalledColor(player.color);
            check(player.availableKnights != null && player.availableKnights.length == 1,
                "PlayerObject does not expose one selectable knight");
            check("character/pc/model_level.dat".equals(
                    player.availableKnights[0].model.getName()),
                "Character card does not use the complete animated PC model");
            check(player.availableKnights[0].model.kX().containsKey("Face Texture"),
                "Character card model omitted the configured eye texture");
            check(player.availableKnights[0].model.kX().size() > 3,
                "Character card model omitted equipped armor/helm/shield parameters");
            check(player.items != null && !player.items.isEmpty(), "Player inventory is empty");
            for (Item item : player.items) {
                check(item != null, "Player inventory contains a null item");
            }
            check(player.partyOid > 0, "PlayerObject does not reference a combat PartyObject");
            wire.send(new SubscribeRequest(player.partyOid));
            ObjectResponse<?> partyResponse = expect(
                wire.read(), ObjectResponse.class, "PartyObject ObjectResponse");
            check(partyResponse.yj() instanceof PartyObject,
                "Combat party subscription did not return PartyObject");
            PartyObject party = (PartyObject)partyResponse.yj();
            check(party.partyService != null && party.speakService != null,
                "Combat PartyObject omitted required services");
            check(party.members.size() == 1 && party.health.size() == 1
                    && party.status.size() == 1,
                "Combat PartyObject omitted member, health or status state");
            check(party.members.iterator().next().weapons.length == 4,
                "Combat party member does not expose four weapon slots");

            short registeredZoneReceiverId = 7;
            EntryAddedEvent<InvocationReceiver.Registration> receiverRegistration =
                new EntryAddedEvent<>(bootstrap.clientOid, "receivers",
                    new InvocationReceiver.Registration(
                        "2d900cf54355111b4bb4befcdff42b82", registeredZoneReceiverId));
            receiverRegistration.cF(bootstrap.clientOid);
            f testObjectManager = (f)Proxy.newProxyInstance(
                f.class.getClassLoader(), new Class<?>[]{ f.class },
                (proxy, method, args_) -> method.getReturnType() == boolean.class ? false : null);
            CompoundEvent receiverBatch = new CompoundEvent(player, testObjectManager);
            receiverBatch.a(receiverRegistration);
            receiverBatch.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(receiverBatch));

            System.out.println("[Test] 3. Selecting knight " + player.knight + "...");
            InvocationMarshaller.ConfirmMarshaller selectListener =
                new InvocationMarshaller.ConfirmMarshaller();
            selectListener.requestId = 41;
            InvocationRequestEvent selectRequest = new InvocationRequestEvent(
                bootstrap.clientOid,
                ClientSession.KNIGHT_SERVICE_CODE,
                3,
                new Object[]{ player.knight, selectListener });
            selectRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(selectRequest));

            InvocationResponseEvent selectResponse = expectEvent(
                wire.read(), InvocationResponseEvent.class, "selectKnight response");
            check(selectResponse.ye() == 41 && selectResponse.yb() == 1,
                "Unexpected selectKnight confirmation");

            InvocationNotificationEvent moveNotification = expectEvent(
                wire.read(), InvocationNotificationEvent.class, "zone move notification");
            check(moveNotification.yb() == 1, "Unexpected zone notification method");
            check(moveNotification.ya() == registeredZoneReceiverId,
                "Server ignored the client's registered ZoneDirector receiver id");
            check(moveNotification.yc().length == 2,
                "Zone notification did not contain zone and scene ids");
            Object[] moveArgs = moveNotification.yc();
            check(moveArgs[0].equals(Integer.valueOf(1)),
                "Zone notification contained the wrong zone id");
            check(moveArgs[1].equals(Integer.valueOf(SceneManager.DEFAULT_SCENE_ID)),
                "Zone notification confused the place OID with the scene id");

            System.out.println("[Test] 4. Resolving zoned move with SceneModel...");
            ZoneMarshaller.ZoneMoveMarshaller zoneListener =
                new ZoneMarshaller.ZoneMoveMarshaller();
            zoneListener.requestId = 42;
            InvocationRequestEvent zoneRequest = new InvocationRequestEvent(
                bootstrap.clientOid,
                ClientSession.ZONE_SERVICE_CODE,
                1,
                new Object[]{ moveArgs[0], moveArgs[1],
                    Integer.valueOf(0), zoneListener });
            zoneRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(zoneRequest));

            InvocationResponseEvent zoneResponse = expectEvent(
                wire.read(), InvocationResponseEvent.class, "ZoneMarshaller response");
            check(zoneResponse.ye() == 42 && zoneResponse.yb() == 3,
                "Zoned move did not return gotPlace with a SceneModel");
            Object[] zoneArgs = zoneResponse.yc();
            check(zoneArgs.length == 4, "Zoned move response has the wrong argument count");
            check(zoneArgs[0].equals(Integer.valueOf(SceneManager.SCENE_OID)),
                "Zoned move returned the wrong place OID");
            check(zoneArgs[1] instanceof PlaceConfig, "Zoned move omitted PlaceConfig");
            check(zoneArgs[1] instanceof TownSceneConfig,
                "Zoned move did not return Haven's embedded TownSceneConfig");
            check(zoneArgs[2] instanceof ZoneSummary, "Zoned move omitted ZoneSummary");
            check(zoneArgs[3] instanceof TudeySceneModel, "Zoned move omitted TudeySceneModel");
            check(((TudeySceneModel)zoneArgs[3]).sceneId == SceneManager.DEFAULT_SCENE_ID,
                "Zoned move returned a SceneModel with a mismatched scene id");
            check(((TudeySceneModel)zoneArgs[3]).adr().size() > 1_000,
                "Haven SceneModel contains no usable map geometry");

            System.out.println("[Test] 5. Subscribing to TownSceneObject...");
            wire.send(new SubscribeRequest(SceneManager.SCENE_OID));
            ObjectResponse<?> sceneResponse = expect(
                wire.read(), ObjectResponse.class, "TownSceneObject ObjectResponse");
            DObject sceneObject = sceneResponse.yj();
            check(sceneObject instanceof TownSceneObject,
                "Expected TownSceneObject, got " + sceneObject.getClass().getName());
            TownSceneObject scene = (TownSceneObject)sceneObject;
            check(scene.speakService != null, "speakService is null");
            check(scene.tudeySceneService != null, "tudeySceneService is null");
            check(scene.projectXSceneService != null, "projectXSceneService is null");
            check(scene.townSceneService != null, "townSceneService is null");
            check(scene.occupants.aH(bootstrap.clientOid),
                "Scene occupants do not contain the selected player");
            check(scene.occupantInfo != null && !scene.occupantInfo.isEmpty(),
                "Scene occupantInfo is empty");

            System.out.println("[Test] 6. Requesting initial Tudey scene state...");
            InvocationRequestEvent stateRequest = new InvocationRequestEvent(
                SceneManager.SCENE_OID,
                SceneManager.TUDEY_SCENE_SERVICE_CODE,
                1,
                new Object[0]);
            stateRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(stateRequest));

            SceneDeltaEvent initialState = expectEvent(
                wire.read(), SceneDeltaEvent.class, "initial Tudey SceneDeltaEvent");
            check(initialState.xG() == bootstrap.clientOid,
                "Initial scene state targeted the wrong ClientObject");
            check(initialState.adO() == SceneManager.SCENE_OID,
                "Initial scene state contained the wrong scene object OID");
            check(initialState.adR() == 0,
                "First scene delta did not reference the client's initial record");
            check(initialState.QU() > initialState.adR(),
                "Initial scene state timestamp did not advance");
            check(initialState.adT() != null && initialState.adT().length == 1,
                "Initial scene state did not contain the player actor");
            check(initialState.adT()[0] instanceof Knight,
                "Initial scene actor is not a ProjectX Knight");
            Knight knightActor = (Knight)initialState.adT()[0];
            check(knightActor.vx() == bootstrap.clientOid,
                "Knight actor id does not match occupant pawnId");
            check(SceneManager.PLAYER_ACTOR_CONFIG.equals(knightActor.Mt().getName()),
                "Knight actor does not reference the installed player model");
            check(knightActor.Mt().kX().get("Model") instanceof ConfigReference,
                "Knight actor config omitted its composed player Model parameter");
            ConfigReference<?> actorModel =
                (ConfigReference<?>)knightActor.Mt().kX().get("Model");
            check("character/pc/model_level.dat".equals(actorModel.getName()),
                "Knight actor does not use the complete animated PC model");
            check(actorModel.kX().containsKey("Face Texture")
                    && actorModel.kX().containsKey("Height"),
                "Knight actor model omitted appearance parameters");
            check(actorModel.kX().size() > 3,
                "Knight actor model omitted equipped armor/helm/shield parameters");
            check(Math.abs(knightActor.tA().x - SceneManager.HAVEN_SPAWN_X) < 0.001f
                    && Math.abs(knightActor.tA().y - SceneManager.HAVEN_SPAWN_Y) < 0.001f,
                "Knight actor is not positioned at Haven's default entrance");

            System.out.println("[Test] 7. Sending original-client movement frames...");
            SceneDeltaEvent preloadTick = expectSceneDelta(
                wire, 0, "fixed-rate preload SceneDeltaEvent");
            check(preloadTick.adR() == initialState.QU()
                    && preloadTick.QU() > initialState.QU()
                    && preloadTick.QU() - preloadTick.adR() <= 100,
                "Authoritative scene ticker did not advance in a small fixed step");
            check(preloadTick.adP() == 0,
                "Preload tick acknowledged an input that was never received");
            check(preloadTick.adU() == null,
                "Preload tick invented an authoritative actor update");

            System.out.println("[Test] 7a. Verifying ordered SceneDelta chain without ACK gating...");
            List<SceneDeltaEvent> chainedDeltas = collectSceneDeltasFor(wire, 350L);
            check(chainedDeltas.size() >= 2,
                "Server stopped its scene clock while the client ACK was delayed; deltas="
                    + chainedDeltas.size());
            SceneDeltaEvent latestChainedDelta = preloadTick;
            for (SceneDeltaEvent chainedDelta : chainedDeltas) {
                check(chainedDelta.adR() == latestChainedDelta.QU(),
                    "SceneDelta chain lost its exact previous reference: expected="
                        + latestChainedDelta.QU() + " actual=" + chainedDelta.adR());
                check(chainedDelta.QU() - chainedDelta.adR() == 100,
                    "SceneDelta chain did not retain the installed 100 ms cadence");
                check(chainedDelta.adP() == 0,
                    "SceneDelta acknowledged input that the server never received");
                latestChainedDelta = chainedDelta;
            }

            int firstInputTimestamp = 1_000;
            InputEnqueueEvent establishInputClock = new InputEnqueueEvent(
                SceneManager.SCENE_OID,
                latestChainedDelta.QU(),
                firstInputTimestamp,
                // The graphical client sends unacknowledged history newest-first.
                new InputFrame[]{
                    new InputFrame(firstInputTimestamp, 0.0f, 0.0f, 1),
                    new InputFrame(900, 0.0f, 0.0f, 1)
                });
            establishInputClock.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(establishInputClock));
            SceneDeltaEvent firstMovement = expectSceneDelta(
                wire, firstInputTimestamp, "first movement SceneDeltaEvent");
            check(firstMovement.adP() == firstInputTimestamp,
                "Server did not acknowledge the first input frame");
            check(firstMovement.adR() >= latestChainedDelta.QU(),
                "Movement delta went backwards relative to the ordered scene chain");
            check(firstMovement.QU() > firstMovement.adR(),
                "Movement delta did not advance the shared scene clock");
            check(firstMovement.QU() - firstMovement.adR() == 100,
                "Movement delta did not retain the installed 100 ms cadence");
            check(firstMovement.adU() != null && firstMovement.adU().length == 1,
                "First movement delta omitted the authoritative Knight update");
            Actor firstAuthoritativeActor =
                (Actor)firstMovement.adU()[0].apply(knightActor);
            check(Math.abs(firstAuthoritativeActor.tA().x
                    - (SceneManager.HAVEN_SPAWN_X + 0.5f)) < 0.001f,
                "Authoritative movement does not match installed actor speed=5.0; x="
                    + firstAuthoritativeActor.tA().x);
            check(((Knight)firstAuthoritativeActor).adE() == 0,
                "Knight did not leave its creation activity for idle/walk animation");
            check(((Knight)firstAuthoritativeActor).adF() <= firstMovement.QU(),
                "Actor activity was scheduled on the future predictive input clock");

            int secondInputTimestamp = 1_100;
            InputEnqueueEvent moveInput = new InputEnqueueEvent(
                SceneManager.SCENE_OID,
                firstMovement.QU(),
                secondInputTimestamp,
                new InputFrame[]{ new InputFrame(secondInputTimestamp, 0.0f, 0.0f, 1) });
            moveInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(moveInput));
            SceneDeltaEvent movement = expectSceneDelta(
                wire, secondInputTimestamp, "movement SceneDeltaEvent");
            check(movement.adP() == secondInputTimestamp,
                "Movement delta did not acknowledge the latest input frame");
            check(movement.QU() - firstMovement.QU() <= 300,
                "Rate-limited actor state exceeded the 200 ms target plus one tick");
            check(movement.adU() != null && movement.adU().length == 1,
                "Movement delta omitted the authoritative Knight update");
            Actor movedActor = (Actor)movement.adU()[0].apply(firstAuthoritativeActor);
            check(movedActor.tA().x > SceneManager.HAVEN_SPAWN_X,
                "Moving east did not advance the authoritative X position");
            check(Math.abs(movedActor.tA().y - SceneManager.HAVEN_SPAWN_Y) < 0.001f,
                "Moving east unexpectedly changed the authoritative Y position: "
                    + movedActor.tA().y);
            check(movedActor.bR(2),
                "Movement input did not set the actor's moving flag");

            int stopInputTimestamp = 1_150;
            InputEnqueueEvent stopInput = new InputEnqueueEvent(
                SceneManager.SCENE_OID,
                movement.QU(),
                stopInputTimestamp,
                new InputFrame[]{ new InputFrame(stopInputTimestamp, 0.0f, 0.0f, 0) });
            stopInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(stopInput));
            SceneDeltaEvent stopped = expectSceneDelta(
                wire, stopInputTimestamp, "stop movement SceneDeltaEvent");
            Actor stoppedActor = (Actor)stopped.adU()[0].apply(movedActor);
            check(!stoppedActor.bR(2),
                "Releasing movement did not clear the actor's moving flag");
            check(Math.abs(stoppedActor.tA().x - movedActor.tA().x) < 0.001f
                    && Math.abs(stoppedActor.tA().y - movedActor.tA().y) < 0.001f,
                "Stop input unexpectedly moved the authoritative actor");

            System.out.println("[Test] 8. Resolving Haven <-> Training Hall portals...");
            check(SceneManager.getPortalDestination(
                    SceneManager.DEFAULT_SCENE_ID, -23.0f, 11.5f)
                    == SceneManager.TRAINING_SCENE_ID,
                "Haven Training Hall doorway is not mapped to scene 294");
            check(SceneManager.getPortalDestination(
                    SceneManager.TRAINING_SCENE_ID, 5.5f, -21.5f)
                    == SceneManager.DEFAULT_SCENE_ID,
                "Training Hall exit is not mapped back to Haven");
            check(SceneManager.getPortalDestination(
                    SceneManager.DEFAULT_SCENE_ID, 0.0f, 0.0f) == -1,
                "A non-portal Haven position unexpectedly triggers a transition");
            check(SceneManager.getPortalDestination(
                    SceneManager.DEFAULT_SCENE_ID, -23.0f, 5.0f, -23.0f, 16.0f)
                    == SceneManager.TRAINING_SCENE_ID,
                "Fast movement can skip across the Haven Training Hall portal");

            ZoneMarshaller.ZoneMoveMarshaller trainingListener =
                new ZoneMarshaller.ZoneMoveMarshaller();
            trainingListener.requestId = 43;
            InvocationRequestEvent trainingRequest = new InvocationRequestEvent(
                bootstrap.clientOid,
                ClientSession.ZONE_SERVICE_CODE,
                1,
                new Object[]{ Integer.valueOf(1),
                    Integer.valueOf(SceneManager.TRAINING_SCENE_ID),
                    Integer.valueOf(0), trainingListener });
            trainingRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(trainingRequest));

            InvocationResponseEvent trainingResponse = readUntilEvent(
                wire, InvocationResponseEvent.class, "Training Hall ZoneMarshaller response");
            check(trainingResponse.ye() == 43 && trainingResponse.yb() == 3,
                "Training Hall move did not return gotPlace with a SceneModel");
            Object[] trainingArgs = trainingResponse.yc();
            check(trainingArgs[0].equals(Integer.valueOf(SceneManager.TRAINING_PLACE_OID)),
                "Training Hall move returned the wrong place OID");
            check(trainingArgs[1] instanceof TrainingSceneConfig,
                "Training Hall move omitted its embedded TrainingSceneConfig");
            check(trainingArgs[2] instanceof ZoneSummary,
                "Training Hall move omitted ZoneSummary");
            check(trainingArgs[3] instanceof TudeySceneModel,
                "Training Hall move omitted TudeySceneModel");
            TudeySceneModel trainingModel = (TudeySceneModel)trainingArgs[3];
            check(trainingModel.sceneId == SceneManager.TRAINING_SCENE_ID,
                "Training Hall SceneModel has a mismatched scene id");
            check(trainingModel.adr().size() > 1_000,
                "Training Hall SceneModel contains no usable map geometry");

            wire.send(new SubscribeRequest(SceneManager.TRAINING_PLACE_OID));
            ObjectResponse<?> trainingObjectResponse = expect(
                wire.read(), ObjectResponse.class, "Training Hall scene ObjectResponse");
            DObject trainingObject = trainingObjectResponse.yj();
            check(trainingObject instanceof ProjectXSceneObject,
                "Expected ProjectXSceneObject for Training Hall, got "
                    + trainingObject.getClass().getName());
            ProjectXSceneObject trainingScene = (ProjectXSceneObject)trainingObject;
            check(trainingScene.speakService != null,
                "Training Hall speakService is null");
            check(trainingScene.tudeySceneService != null,
                "Training Hall tudeySceneService is null");
            check(trainingScene.projectXSceneService != null,
                "Training Hall projectXSceneService is null");
            check(trainingScene.occupants.aH(bootstrap.clientOid),
                "Training Hall occupants do not contain the player");
            ProjectXOccupantInfo trainingOccupant =
                (ProjectXOccupantInfo)trainingScene.occupantInfo.iterator().next();
            check(trainingOccupant.color == player.color,
                "Training Hall occupant color differs from PlayerObject color");
            checkInstalledColor(trainingOccupant.color);

            InvocationRequestEvent trainingStateRequest = new InvocationRequestEvent(
                SceneManager.TRAINING_PLACE_OID,
                SceneManager.TUDEY_SCENE_SERVICE_CODE,
                1,
                new Object[0]);
            trainingStateRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(trainingStateRequest));
            SceneDeltaEvent trainingState = expectEvent(
                wire.read(), SceneDeltaEvent.class, "Training Hall initial SceneDeltaEvent");
            check(trainingState.adO() == SceneManager.TRAINING_PLACE_OID,
                "Training Hall state contained the wrong scene object OID");
            check(trainingState.adT() != null && trainingState.adT().length == 1,
                "Training Hall initial state omitted the player actor");
            check(trainingState.adT()[0] instanceof Dungeoneer,
                "Training Hall player actor is not combat-capable Dungeoneer");
            Dungeoneer trainingKnight = (Dungeoneer)trainingState.adT()[0];
            check(Math.abs(trainingKnight.tA().x - SceneManager.TRAINING_SPAWN_X) < 0.001f
                    && Math.abs(trainingKnight.tA().y - SceneManager.TRAINING_SPAWN_Y) < 0.001f,
                "Knight did not spawn at the Training Hall entrance");
            check(SceneManager.COMBAT_PLAYER_ACTOR_CONFIG.equals(trainingKnight.Mt().getName()),
                "Training Hall actor does not use Character/PC/Level");
            check(trainingKnight.Re() != null
                    && "Weapon/Sword/Leviathan Blade".equals(
                        trainingKnight.Re().Mt().getName()),
                "Training Hall actor did not equip the first weapon");
            ConfigReference<?> trainingModelRef =
                (ConfigReference<?>)trainingKnight.Mt().kX().get("Model");
            check(trainingModelRef != null && trainingModelRef.kX().containsKey("Weapon"),
                "Training Hall actor model omitted its visible weapon");

            SceneDeltaEvent trainingPreloadTick = expectSceneDelta(
                wire, 0, "Training Hall preload SceneDeltaEvent");
            check(trainingPreloadTick.adR() == trainingState.QU(),
                "Training Hall preload did not reference its initial state");
            check(trainingPreloadTick.QU() - trainingPreloadTick.adR() == 200,
                "Training Hall did not use its client-safe 200 ms cadence");

            System.out.println("[Test] 8a. Verifying Training Hall emits at 5 Hz...");
            List<SceneDeltaEvent> trainingRateDeltas =
                collectSceneDeltasFor(wire, 450L);
            check(trainingRateDeltas.size() >= 1 && trainingRateDeltas.size() <= 3,
                "Training Hall did not stay near five reliable deltas per second; deltas="
                    + trainingRateDeltas.size());
            for (SceneDeltaEvent trainingRateDelta : trainingRateDeltas) {
                check(trainingRateDelta.adR() == trainingPreloadTick.QU(),
                    "Training Hall rate sample broke the ordered delta chain");
                check(trainingRateDelta.QU() - trainingRateDelta.adR() == 200,
                    "Training Hall rate sample did not advance 200 ms");
                trainingPreloadTick = trainingRateDelta;
            }

            System.out.println("[Test] 9. Exercising authoritative combat controls...");
            int swordAttackTimestamp = 2_000;
            DungeonInputFrame swordAttackFrame = new DungeonInputFrame(
                swordAttackTimestamp, 0.0f, 0.0f, 4, swordAttackTimestamp);
            InputEnqueueEvent swordAttackInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                trainingPreloadTick.QU(),
                swordAttackTimestamp,
                new InputFrame[]{ swordAttackFrame });
            swordAttackInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(swordAttackInput));
            SceneDeltaEvent swordAttackDelta = expectSceneDelta(
                wire, swordAttackTimestamp, "Leviathan attack SceneDeltaEvent");
            Dungeoneer attackingKnight = (Dungeoneer)swordAttackDelta.adU()[0].apply(trainingKnight);
            check(attackingKnight.adE() == 292,
                "Primary sword attack did not start ks_attack activity 292");
            check(attackingKnight.Re() != null,
                "Primary attack delta lost the equipped weapon");

            int weaponSwitchTimestamp = 4_000;
            int polarisFlags = DungeonInputFrame.at(0, 2);
            DungeonInputFrame weaponSwitchFrame = new DungeonInputFrame(
                weaponSwitchTimestamp, 0.0f, 0.0f, polarisFlags, swordAttackTimestamp);
            InputEnqueueEvent weaponSwitchInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                swordAttackDelta.QU(),
                weaponSwitchTimestamp,
                new InputFrame[]{ weaponSwitchFrame });
            weaponSwitchInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(weaponSwitchInput));
            SceneDeltaEvent weaponSwitchDelta = expectSceneDelta(
                wire, weaponSwitchTimestamp, "weapon-switch SceneDeltaEvent");
            Dungeoneer polarisKnight =
                (Dungeoneer)weaponSwitchDelta.adU()[0].apply(attackingKnight);
            check(polarisKnight.Re() != null
                    && "Weapon/Handgun/Polaris".equals(polarisKnight.Re().Mt().getName()),
                "Weapon slot 3 did not equip Polaris");
            ConfigReference<?> polarisModel =
                (ConfigReference<?>)polarisKnight.Mt().kX().get("Model");
            check(polarisModel != null && polarisModel.kX().get("Weapon") != null,
                "Weapon switch did not update the visible weapon model");

            int handgunAttackTimestamp = 4_100;
            DungeonInputFrame handgunAttackFrame = new DungeonInputFrame(
                handgunAttackTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.at(4, 2), handgunAttackTimestamp);
            InputEnqueueEvent handgunAttackInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                weaponSwitchDelta.QU(),
                handgunAttackTimestamp,
                new InputFrame[]{ handgunAttackFrame });
            handgunAttackInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(handgunAttackInput));
            SceneDeltaEvent handgunAttackDelta = expectSceneDelta(
                wire, handgunAttackTimestamp, "Polaris attack SceneDeltaEvent");
            Dungeoneer handgunAttackKnight =
                (Dungeoneer)handgunAttackDelta.adU()[0].apply(polarisKnight);
            check(handgunAttackKnight.adE() == 292,
                "Primary handgun attack did not start ks_attack activity 292");

            Thread.sleep(1_000L);
            int defendTimestamp = 6_000;
            DungeonInputFrame defendFrame = new DungeonInputFrame(
                defendTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.at(64, 2), defendTimestamp);
            InputEnqueueEvent defendInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                handgunAttackDelta.QU(),
                defendTimestamp,
                new InputFrame[]{ defendFrame });
            defendInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(defendInput));
            SceneDeltaEvent defendDelta = expectSceneDelta(
                wire, defendTimestamp, "shield defense SceneDeltaEvent");
            Dungeoneer defendingKnight =
                (Dungeoneer)defendDelta.adU()[0].apply(handgunAttackKnight);
            check(defendingKnight.adE() == 284,
                "Defend input did not start ks_defense activity 284");
            check(defendingKnight.adF() <= defendDelta.QU(),
                "Coalesced defense activity was stamped in the client's future");

            int bombSwitchTimestamp = 8_000;
            DungeonInputFrame bombSwitchFrame = new DungeonInputFrame(
                bombSwitchTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.at(0, 3), defendTimestamp);
            InputEnqueueEvent bombSwitchInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                defendDelta.QU(),
                bombSwitchTimestamp,
                new InputFrame[]{ bombSwitchFrame });
            bombSwitchInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(bombSwitchInput));
            SceneDeltaEvent bombSwitchDelta = expectSceneDelta(
                wire, bombSwitchTimestamp, "bomb-switch SceneDeltaEvent");
            Dungeoneer bombKnight =
                (Dungeoneer)bombSwitchDelta.adU()[0].apply(defendingKnight);
            check(bombKnight.Re() != null
                    && "Weapon/Bomb/Ash of Agni".equals(bombKnight.Re().Mt().getName()),
                "Weapon slot 4 did not equip Ash of Agni");

            int bombChargeTimestamp = 8_100;
            DungeonInputFrame bombChargeFrame = new DungeonInputFrame(
                bombChargeTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.at(4, 3), bombChargeTimestamp);
            InputEnqueueEvent bombChargeInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                bombSwitchDelta.QU(),
                bombChargeTimestamp,
                new InputFrame[]{ bombChargeFrame });
            bombChargeInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(bombChargeInput));
            SceneDeltaEvent bombChargeDelta = expectSceneDelta(
                wire, bombChargeTimestamp, "bomb charge SceneDeltaEvent");
            Dungeoneer chargingKnight =
                (Dungeoneer)bombChargeDelta.adU()[0].apply(bombKnight);
            check(chargingKnight.adE() == 283,
                "Bomb attack did not start ks_charge activity 283");

            Thread.sleep(3_200L);
            int bombHeldTimestamp = 11_200;
            DungeonInputFrame bombHeldFrame = new DungeonInputFrame(
                bombHeldTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.at(4, 3), bombChargeTimestamp);
            InputEnqueueEvent bombHeldInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                bombChargeDelta.QU(),
                bombHeldTimestamp,
                new InputFrame[]{ bombHeldFrame });
            bombHeldInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(bombHeldInput));
            SceneDeltaEvent bombHeldDelta = expectSceneDelta(
                wire, bombHeldTimestamp, "held bomb charge SceneDeltaEvent");
            Dungeoneer heldBombKnight = bombHeldDelta.adU() == null
                ? chargingKnight
                : (Dungeoneer)bombHeldDelta.adU()[0].apply(chargingKnight);

            int bombReleaseTimestamp = 11_300;
            DungeonInputFrame bombReleaseFrame = new DungeonInputFrame(
                bombReleaseTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.at(0, 3), bombReleaseTimestamp);
            InputEnqueueEvent bombReleaseInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                bombHeldDelta.QU(),
                bombReleaseTimestamp,
                new InputFrame[]{ bombReleaseFrame });
            bombReleaseInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(bombReleaseInput));
            SceneDeltaEvent bombReleaseDelta = expectSceneDelta(
                wire, bombReleaseTimestamp, "charged bomb release SceneDeltaEvent");
            Dungeoneer releasedBombKnight =
                (Dungeoneer)bombReleaseDelta.adU()[0].apply(heldBombKnight);
            check(releasedBombKnight.adE() == 290,
                "Fully charged bomb did not start ks_charge_attack activity 290");
            check(releasedBombKnight.adF() <= bombReleaseDelta.QU(),
                "Coalesced charged attack was stamped in the client's future");

            int dodgeTimestamp = 12_300;
            DungeonInputFrame dodgeFrame = new DungeonInputFrame(
                dodgeTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.at(1024, 3), dodgeTimestamp);
            InputEnqueueEvent dodgeInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                bombReleaseDelta.QU(),
                dodgeTimestamp,
                new InputFrame[]{ dodgeFrame });
            dodgeInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(dodgeInput));
            SceneDeltaEvent dodgeDelta = expectSceneDelta(
                wire, dodgeTimestamp, "dodge SceneDeltaEvent");
            Dungeoneer dodgingKnight =
                (Dungeoneer)dodgeDelta.adU()[0].apply(releasedBombKnight);
            check(dodgingKnight.adE() == 286,
                "Dodge input did not start ks_dodge activity 286");
            check(dodgingKnight.adF() <= dodgeDelta.QU(),
                "Coalesced dodge was stamped in the client's future");

            int shieldBashTimestamp = 13_000;
            DungeonInputFrame shieldBashFrame = new DungeonInputFrame(
                shieldBashTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.at(2048, 3), shieldBashTimestamp);
            InputEnqueueEvent shieldBashInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                dodgeDelta.QU(),
                shieldBashTimestamp,
                new InputFrame[]{ shieldBashFrame });
            shieldBashInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(shieldBashInput));
            SceneDeltaEvent shieldBashDelta = expectSceneDelta(
                wire, shieldBashTimestamp, "shield-bash SceneDeltaEvent");
            Dungeoneer bashingKnight =
                (Dungeoneer)shieldBashDelta.adU()[0].apply(dodgingKnight);
            check(bashingKnight.adE() == 287,
                "Shield-bash input did not start ks_bash activity 287");

            ZoneMarshaller.ZoneMoveMarshaller returnListener =
                new ZoneMarshaller.ZoneMoveMarshaller();
            returnListener.requestId = 44;
            InvocationRequestEvent returnRequest = new InvocationRequestEvent(
                bootstrap.clientOid,
                ClientSession.ZONE_SERVICE_CODE,
                1,
                new Object[]{ Integer.valueOf(1),
                    Integer.valueOf(SceneManager.DEFAULT_SCENE_ID),
                    Integer.valueOf(0), returnListener });
            returnRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(returnRequest));
            InvocationResponseEvent returnResponse = readUntilEvent(
                wire, InvocationResponseEvent.class, "return-to-Haven ZoneMarshaller response");
            check(returnResponse.ye() == 44 && returnResponse.yb() == 3,
                "Return from Training Hall did not resolve Haven");
            check(returnResponse.yc()[0].equals(Integer.valueOf(SceneManager.SCENE_OID)),
                "Return from Training Hall used the wrong Haven place OID");
            check(returnResponse.yc()[1] instanceof TownSceneConfig,
                "Return from Training Hall omitted Haven's TownSceneConfig");

            InvocationRequestEvent returnStateRequest = new InvocationRequestEvent(
                SceneManager.SCENE_OID,
                SceneManager.TUDEY_SCENE_SERVICE_CODE,
                1,
                new Object[0]);
            returnStateRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(returnStateRequest));
            SceneDeltaEvent returnState = expectEvent(
                wire.read(), SceneDeltaEvent.class, "return-to-Haven SceneDeltaEvent");
            Knight returnedKnight = (Knight)returnState.adT()[0];
            check(Math.abs(returnedKnight.tA().x - SceneManager.HAVEN_TRAINING_RETURN_X) < 0.001f
                    && Math.abs(returnedKnight.tA().y
                        - SceneManager.HAVEN_TRAINING_RETURN_Y) < 0.001f,
                "Knight did not return beside Haven's Training Hall doorway");

            System.out.println("[Test] 10. Joining and synchronizing a second LAN client...");
            JoinedClient friend = joinHaven(testPort, 90);
            friendClient = friend.channel;
            check(friend.bootstrap.clientOid != bootstrap.clientOid,
                "Two sessions received the same client/pawn OID");
            check(friend.initialState.adT() != null
                    && friend.initialState.adT().length == 2,
                "Second client did not receive both Haven actors");
            Knight friendActor = findAddedActor(
                friend.initialState, friend.bootstrap.clientOid);
            check(friendActor != null,
                "Second client initial state omitted its own unique actor");

            SceneDeltaEvent friendAddedForHost = expectAddedActor(
                wire, bootstrap.clientOid, SceneManager.SCENE_OID,
                friend.bootstrap.clientOid, "friend actor broadcast");
            check(findAddedActor(friendAddedForHost, friend.bootstrap.clientOid) != null,
                "Existing client did not receive the joining friend actor");

            int friendInputTimestamp = 20_000;
            InputEnqueueEvent friendMovement = new InputEnqueueEvent(
                SceneManager.SCENE_OID,
                friend.preloadState.QU(),
                friendInputTimestamp,
                new InputFrame[]{
                    new InputFrame(friendInputTimestamp, 0.0f, 0.0f, 1),
                    new InputFrame(friendInputTimestamp - 100, 0.0f, 0.0f, 1)
                });
            friendMovement.cF(friend.bootstrap.clientOid);
            friend.wire.send(new ForwardEventRequest(friendMovement));
            SceneDeltaEvent friendMovementOwn = expectSceneDelta(
                friend.wire, friendInputTimestamp, "friend authoritative movement");
            ActorDelta ownFriendDelta = findActorDelta(
                friendMovementOwn, friend.bootstrap.clientOid);
            check(ownFriendDelta != null,
                "Moving friend did not receive its authoritative actor delta");
            Knight movedFriend = (Knight)ownFriendDelta.apply(friendActor);
            check(movedFriend.tA().x > friendActor.tA().x,
                "Friend movement did not advance its authoritative position");

            SceneDeltaEvent friendMovementForHost = expectUpdatedActor(
                wire, bootstrap.clientOid, SceneManager.SCENE_OID,
                friend.bootstrap.clientOid, "friend movement broadcast");
            check(findActorDelta(friendMovementForHost, friend.bootstrap.clientOid) != null,
                "Existing client did not receive the friend's movement");

            friendClient.close();
            friendClient = null;
            expectRemovedActor(wire, bootstrap.clientOid, SceneManager.SCENE_OID,
                friend.bootstrap.clientOid,
                "friend disconnect broadcast");

            System.out.println("[Test] 11. Checking keepalive...");
            PingRequest ping = new PingRequest(Transport.ars);
            ping.c(Transport.ars);
            wire.send(ping);
            readUntilMessage(wire, PongResponse.class, "PongResponse");

            System.out.println("==========================================================");
            System.out.println("   PRIVATE SERVER WORLD-ENTRY PROTOCOL TEST PASSED        ");
            System.out.println("==========================================================");
        } finally {
            if (client != null) {
                client.close();
            }
            if (friendClient != null) {
                friendClient.close();
            }
            server.stop();
        }
    }

    /** Connects another stock-protocol client directly to Haven for multiplayer tests. */
    private static JoinedClient joinHaven(int port, int requestBase) throws Exception {
        SocketChannel channel = SocketChannel.open(
            new InetSocketAddress("localhost", port));
        channel.configureBlocking(false);
        Wire wire = new Wire(channel);
        try {
            wire.send(new AuthRequest(
                null, "20260828143805", new String[]{"presents", "projectx"}));
            AuthResponse auth = readUntilMessage(
                wire, AuthResponse.class, "second-client AuthResponse");
            check("success".equals(auth.yf().code),
                "Second-client authentication did not return success");
            BootstrapNotification bootstrapMessage = readUntilMessage(
                wire, BootstrapNotification.class, "second-client BootstrapNotification");
            ProjectXBootstrapData bootstrap =
                (ProjectXBootstrapData)bootstrapMessage.yg();

            wire.send(new SubscribeRequest(bootstrap.clientOid));
            ObjectResponse<?> playerResponse = readUntilMessage(
                wire, ObjectResponse.class, "second-client PlayerObject");
            check(playerResponse.yj() instanceof PlayerObject,
                "Second client did not receive its PlayerObject");
            PlayerObject player = (PlayerObject)playerResponse.yj();

            ZoneMarshaller.ZoneMoveMarshaller zoneListener =
                new ZoneMarshaller.ZoneMoveMarshaller();
            zoneListener.requestId = (short)requestBase;
            InvocationRequestEvent zoneRequest = new InvocationRequestEvent(
                bootstrap.clientOid,
                ClientSession.ZONE_SERVICE_CODE,
                1,
                new Object[]{ Integer.valueOf(1),
                    Integer.valueOf(SceneManager.DEFAULT_SCENE_ID),
                    Integer.valueOf(0), zoneListener });
            zoneRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(zoneRequest));
            InvocationResponseEvent zoneResponse = readUntilEvent(
                wire, InvocationResponseEvent.class,
                "second-client Haven ZoneMarshaller response");
            check(zoneResponse.ye() == (short)requestBase && zoneResponse.yb() == 3,
                "Second-client Haven move did not include its SceneModel");

            wire.send(new SubscribeRequest(SceneManager.SCENE_OID));
            ObjectResponse<?> sceneResponse = readUntilMessage(
                wire, ObjectResponse.class, "second-client TownSceneObject");
            check(sceneResponse.yj() instanceof TownSceneObject,
                "Second client did not receive Haven's TownSceneObject");

            InvocationRequestEvent stateRequest = new InvocationRequestEvent(
                SceneManager.SCENE_OID,
                SceneManager.TUDEY_SCENE_SERVICE_CODE,
                1,
                new Object[0]);
            stateRequest.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(stateRequest));
            SceneDeltaEvent initialState = readUntilEvent(
                wire, SceneDeltaEvent.class, "second-client initial scene state");
            SceneDeltaEvent preloadState = expectSceneDelta(
                wire, 0, "second-client preload scene state");
            check(preloadState.adR() == initialState.QU(),
                "Second-client preload did not reference its initial state");
            return new JoinedClient(
                channel, wire, bootstrap, player, initialState, preloadState);
        } catch (Exception error) {
            channel.close();
            throw error;
        }
    }

    private static Knight findAddedActor(SceneDeltaEvent event, int actorId) {
        Actor[] actors = event.adT();
        if (actors != null) {
            for (Actor actor : actors) {
                if (actor instanceof Knight && actor.vx() == actorId) {
                    return (Knight)actor;
                }
            }
        }
        return null;
    }

    private static ActorDelta findActorDelta(SceneDeltaEvent event, int actorId) {
        ActorDelta[] deltas = event.adU();
        if (deltas != null) {
            for (ActorDelta delta : deltas) {
                if (delta != null && delta.vx() == actorId) {
                    return delta;
                }
            }
        }
        return null;
    }

    private static SceneDeltaEvent expectAddedActor(
            Wire wire, int clientOid, int sceneOid, int actorId,
            String description) throws Exception {
        for (int attempts = 0; attempts < 512; attempts++) {
            SceneDeltaEvent event = readUntilEvent(
                wire, SceneDeltaEvent.class, description);
            acknowledgeSceneDelta(wire, clientOid, sceneOid, event);
            if (findAddedActor(event, actorId) != null) {
                return event;
            }
        }
        throw new AssertionError("Timed out waiting for " + description
            + " actorId=" + actorId);
    }

    private static SceneDeltaEvent expectUpdatedActor(
            Wire wire, int clientOid, int sceneOid, int actorId,
            String description) throws Exception {
        for (int attempts = 0; attempts < 512; attempts++) {
            SceneDeltaEvent event = readUntilEvent(
                wire, SceneDeltaEvent.class, description);
            acknowledgeSceneDelta(wire, clientOid, sceneOid, event);
            if (findActorDelta(event, actorId) != null) {
                return event;
            }
        }
        throw new AssertionError("Timed out waiting for " + description
            + " actorId=" + actorId);
    }

    private static SceneDeltaEvent expectRemovedActor(
            Wire wire, int clientOid, int sceneOid, int actorId,
            String description) throws Exception {
        for (int attempts = 0; attempts < 512; attempts++) {
            SceneDeltaEvent event = readUntilEvent(
                wire, SceneDeltaEvent.class, description);
            acknowledgeSceneDelta(wire, clientOid, sceneOid, event);
            int[] removed = event.adV();
            if (removed != null) {
                for (int removedId : removed) {
                    if (removedId == actorId) {
                        return event;
                    }
                }
            }
        }
        throw new AssertionError("Timed out waiting for " + description
            + " actorId=" + actorId);
    }

    private static void acknowledgeSceneDelta(
            Wire wire, int clientOid, int sceneOid, SceneDeltaEvent event)
            throws Exception {
        InputEnqueueEvent acknowledge = new InputEnqueueEvent(
            sceneOid, event.QU(), event.QU(), new InputFrame[0]);
        acknowledge.cF(clientOid);
        wire.send(new ForwardEventRequest(acknowledge));
    }

    private static void checkService(
            List<InvocationMarshaller<?>> services, Class<?> contract, String description) {
        for (InvocationMarshaller<?> service : services) {
            if (contract.isInstance(service)) {
                return;
            }
        }
        throw new AssertionError("Bootstrap is missing " + description);
    }

    private static final class JoinedClient {
        final SocketChannel channel;
        final Wire wire;
        final ProjectXBootstrapData bootstrap;
        final PlayerObject player;
        final SceneDeltaEvent initialState;
        final SceneDeltaEvent preloadState;

        JoinedClient(
                SocketChannel channel, Wire wire, ProjectXBootstrapData bootstrap,
                PlayerObject player, SceneDeltaEvent initialState,
                SceneDeltaEvent preloadState) {
            this.channel = channel;
            this.wire = wire;
            this.bootstrap = bootstrap;
            this.player = player;
            this.initialState = initialState;
            this.preloadState = preloadState;
        }
    }

    /** Verifies the graphical client can resolve a packed color id. */
    private static void checkInstalledColor(int colorId) throws Exception {
        try (JarFile configJar = new JarFile("code/projectx-config.jar")) {
            JarEntry entry = configJar.getJarEntry("rsrc/config/media/colordefs.dat");
            check(entry != null, "Installed color definitions are missing");
            try (InputStream input = configJar.getInputStream(entry)) {
                ColorPository colors = ColorPository.g(input);
                check(colors.cc(colorId) != null,
                    "ColorPository cannot resolve player color " + colorId);
            }
        }
    }

    private static <T> T expect(Object value, Class<T> type, String description) {
        check(type.isInstance(value), "Expected " + description + ", got "
            + (value == null ? "null" : value.getClass().getName()));
        return type.cast(value);
    }

    private static SceneDeltaEvent expectSceneDelta(
            Wire wire, int minimumAcknowledge, String description) throws Exception {
        for (int attempts = 0; attempts < 512; attempts++) {
            Object message = wire.read();
            if (!(message instanceof EventNotification)) {
                continue;
            }
            DEvent event = ((EventNotification)message).yi();
            if (event instanceof SceneDeltaEvent
                    && ((SceneDeltaEvent)event).adP() >= minimumAcknowledge) {
                SceneDeltaEvent delta = (SceneDeltaEvent)event;
                int expectedIntervalMs = delta.adO() == SceneManager.TRAINING_PLACE_OID
                    ? 200 : 100;
                check(delta.QU() - delta.adR() == expectedIntervalMs,
                    "SceneDeltaEvent broke the configured scene cadence for "
                        + description + ": " + (delta.QU() - delta.adR()) + "ms");
                return delta;
            }
        }
        throw new AssertionError("Timed out waiting for " + description
            + " acknowledge=" + minimumAcknowledge);
    }

    private static <T extends DEvent> T readUntilEvent(
            Wire wire, Class<T> type, String description) throws Exception {
        for (int attempts = 0; attempts < 512; attempts++) {
            Object message = wire.read();
            if (message instanceof EventNotification) {
                DEvent event = ((EventNotification)message).yi();
                if (type.isInstance(event)) {
                    return type.cast(event);
                }
            }
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static <T> T readUntilMessage(
            Wire wire, Class<T> type, String description) throws Exception {
        for (int attempts = 0; attempts < 512; attempts++) {
            Object message = wire.read();
            if (type.isInstance(message)) {
                return type.cast(message);
            }
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static List<SceneDeltaEvent> collectSceneDeltasFor(
            Wire wire, long durationMs) throws Exception {
        long deadline = System.currentTimeMillis() + durationMs;
        List<SceneDeltaEvent> deltas = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            long remaining = Math.max(1L, deadline - System.currentTimeMillis());
            Object message = wire.tryRead(remaining);
            if (message instanceof EventNotification
                    && ((EventNotification)message).yi() instanceof SceneDeltaEvent) {
                deltas.add((SceneDeltaEvent)((EventNotification)message).yi());
            }
        }
        return deltas;
    }

    private static <T extends DEvent> T expectEvent(
            Object value, Class<T> type, String description) {
        EventNotification notification = expect(value, EventNotification.class, description);
        return expect(notification.yi(), type, description);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Wire {
        private final SocketChannel channel;
        private final s sink = new s();
        private final w output = new w(sink);
        private final r source = new r();
        private final v input = new v(source);

        Wire(SocketChannel channel) {
            this.channel = channel;
        }

        void send(Message message) throws Exception {
            output.bg(message);
            output.flush();
            ByteBuffer buffer = sink.sI();
            long deadline = System.currentTimeMillis() + READ_TIMEOUT_MS;
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) == 0) {
                    if (System.currentTimeMillis() >= deadline) {
                        throw new AssertionError("Timed out writing " + message.getClass().getSimpleName());
                    }
                    Thread.sleep(2L);
                }
            }
            sink.sJ();
        }

        Object read() throws Exception {
            Object message = tryRead(READ_TIMEOUT_MS);
            if (message == null) {
                throw new AssertionError("Timed out waiting for a server message");
            }
            return message;
        }

        Object tryRead(long timeoutMs) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutMs;
            do {
                if (source.a(channel)) {
                    return input.qf();
                }
                Thread.sleep(2L);
            } while (System.currentTimeMillis() < deadline);
            return null;
        }
    }
}
