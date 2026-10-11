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
import com.threerings.presents.net.UpdateThrottleMessage;
import com.threerings.projectx.data.PlayerObject;
import com.threerings.projectx.data.Pref;
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
    private static final MovementConfig TEST_CONFIG = MovementConfig.load();

    public static void main(String[] args) throws Exception {
        System.setProperty("com.threerings.io.enumPolicy", "ORDINAL");
        int testPort = 47625;

        System.out.println("[Test] Starting test server instance on port " + testPort + "...");
        SpiralServer server = new SpiralServer(testPort);
        SocketChannel client = null;
        SocketChannel friendClient = null;
        try {
            checkSnapshotAcknowledgments(server);
            server.start();
            Thread.sleep(150L);

            client = SocketChannel.open(new InetSocketAddress("localhost", testPort));
            client.configureBlocking(false);
            Wire wire = new Wire(client);

            System.out.println("[Test] 1. Authenticating...");
            wire.send(new AuthRequest(null, "20261006114613", new String[]{"presents", "projectx"}));
            AuthResponse auth = expect(wire.read(), AuthResponse.class, "AuthResponse");
            check("success".equals(auth.yg().code), "Authentication did not return success");

            BootstrapNotification bootstrapMessage = expect(
                wire.read(), BootstrapNotification.class, "BootstrapNotification");
            ProjectXBootstrapData bootstrap = (ProjectXBootstrapData)bootstrapMessage.yh();
            UpdateThrottleMessage throttle = expect(wire.read(),
                UpdateThrottleMessage.class, "client send budget negotiation");
            check(throttle.messagesPerSec >= 1000 / Math.min(TEST_CONFIG.deltaIntervalMs,
                    TEST_CONFIG.trainingDeltaIntervalMs) + 20,
                "Client send budget cannot sustain input cadence and other requests");
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
            DObject object = playerResponse.yk();
            check(object instanceof PlayerObject,
                "Expected PlayerObject, got " + object.getClass().getName());
            PlayerObject player = (PlayerObject)object;
            check(player.color == SceneManager.DEFAULT_PLAYER_COLOR,
                "PlayerObject does not use the installed starter player color");
            checkInstalledColor(player.color);
            check(player.MO() == ((Boolean)Pref.PARTY_WITH_ANY.defValue).booleanValue(),
                "OptionsDialog cannot read the frozen client's default party preference");
            check(player.availableKnights != null && player.availableKnights.length == 1,
                "PlayerObject does not expose one selectable knight");
            check("character/pc/model_level.dat".equals(
                    player.availableKnights[0].model.getName()),
                "Character card does not use the complete animated PC model");
            check(player.availableKnights[0].model.kW().containsKey("Face Texture"),
                "Character card model omitted the configured eye texture");
            check(player.availableKnights[0].model.kW().size() > 3,
                "Character card model omitted equipped armor/helm/shield parameters");
            check(player.items != null && !player.items.isEmpty(), "Player inventory is empty");
            for (Item item : player.items) {
                check(item != null, "Player inventory contains a null item");
            }
            check(player.partyOid > 0, "PlayerObject does not reference a combat PartyObject");
            wire.send(new SubscribeRequest(player.partyOid));
            ObjectResponse<?> partyResponse = expect(
                wire.read(), ObjectResponse.class, "PartyObject ObjectResponse");
            check(partyResponse.yk() instanceof PartyObject,
                "Combat party subscription did not return PartyObject");
            PartyObject party = (PartyObject)partyResponse.yk();
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
            check(selectResponse.yf() == 41 && selectResponse.yc() == 1,
                "Unexpected selectKnight confirmation");

            InvocationNotificationEvent moveNotification = expectEvent(
                wire.read(), InvocationNotificationEvent.class, "zone move notification");
            check(moveNotification.yc() == 1, "Unexpected zone notification method");
            check(moveNotification.yb() == registeredZoneReceiverId,
                "Server ignored the client's registered ZoneDirector receiver id");
            check(moveNotification.yd().length == 2,
                "Zone notification did not contain zone and scene ids");
            Object[] moveArgs = moveNotification.yd();
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
            check(zoneResponse.yf() == 42 && zoneResponse.yc() == 3,
                "Zoned move did not return gotPlace with a SceneModel");
            Object[] zoneArgs = zoneResponse.yd();
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
            check(((TudeySceneModel)zoneArgs[3]).adH().size() > 1_000,
                "Haven SceneModel contains no usable map geometry");

            System.out.println("[Test] 5. Subscribing to TownSceneObject...");
            wire.send(new SubscribeRequest(SceneManager.SCENE_OID));
            ObjectResponse<?> sceneResponse = expect(
                wire.read(), ObjectResponse.class, "TownSceneObject ObjectResponse");
            DObject sceneObject = sceneResponse.yk();
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
            check(initialState.xH() == bootstrap.clientOid,
                "Initial scene state targeted the wrong ClientObject");
            check(initialState.aee() == SceneManager.SCENE_OID,
                "Initial scene state contained the wrong scene object OID");
            check(initialState.aeh() == 0,
                "First scene delta did not reference the client's initial record");
            check(initialState.QY() > initialState.aeh(),
                "Initial scene state timestamp did not advance");
            check(initialState.aej() != null && initialState.aej().length == 1,
                "Initial scene state did not contain the player actor");
            check(initialState.aej()[0] instanceof Knight,
                "Initial scene actor is not a ProjectX Knight");
            Knight knightActor = (Knight)initialState.aej()[0];
            check(knightActor.vw() == bootstrap.clientOid,
                "Knight actor id does not match occupant pawnId");
            check(SceneManager.PLAYER_ACTOR_CONFIG.equals(knightActor.Mv().getName()),
                "Knight actor does not reference the installed player model");
            check(knightActor.Mv().kW().get("Model") instanceof ConfigReference,
                "Knight actor config omitted its composed player Model parameter");
            ConfigReference<?> actorModel =
                (ConfigReference<?>)knightActor.Mv().kW().get("Model");
            check("character/pc/model_level.dat".equals(actorModel.getName()),
                "Knight actor does not use the complete animated PC model");
            check(actorModel.kW().containsKey("Face Texture")
                    && actorModel.kW().containsKey("Height"),
                "Knight actor model omitted appearance parameters");
            check(actorModel.kW().size() > 3,
                "Knight actor model omitted equipped armor/helm/shield parameters");
            check(Math.abs(knightActor.tz().x - SceneManager.HAVEN_SPAWN_X) < 0.001f
                    && Math.abs(knightActor.tz().y - SceneManager.HAVEN_SPAWN_Y) < 0.001f,
                "Knight actor is not positioned at Haven's default entrance");

            System.out.println("[Test] 7. Sending original-client movement frames...");
            SceneDeltaEvent preloadTick = expectSceneDelta(
                wire, 0, "fixed-rate preload SceneDeltaEvent");
            check(preloadTick.aeh() == initialState.QY()
                    && preloadTick.QY() > initialState.QY()
                    && preloadTick.QY() - preloadTick.aeh() == TEST_CONFIG.deltaIntervalMs,
                "Authoritative scene ticker did not advance in a small fixed step");
            check(preloadTick.aef() == 0,
                "Preload tick acknowledged an input that was never received");
            check(preloadTick.aek() == null,
                "Preload tick invented an authoritative actor update");

            System.out.println("[Test] 7a. Verifying ordered SceneDelta chain without ACK gating...");
            List<SceneDeltaEvent> chainedDeltas = collectSceneDeltasFor(wire, 350L);
            check(chainedDeltas.size() >= 2,
                "Server stopped its scene clock while the client ACK was delayed; deltas="
                    + chainedDeltas.size());
            SceneDeltaEvent latestChainedDelta = preloadTick;
            for (SceneDeltaEvent chainedDelta : chainedDeltas) {
                check(chainedDelta.aeh() == latestChainedDelta.QY(),
                    "SceneDelta chain lost its exact previous reference: expected="
                        + latestChainedDelta.QY() + " actual=" + chainedDelta.aeh());
                check(chainedDelta.QY() - chainedDelta.aeh() == TEST_CONFIG.deltaIntervalMs,
                    "SceneDelta chain did not retain the configured Haven cadence");
                check(chainedDelta.aef() == 0,
                    "SceneDelta acknowledged input that the server never received");
                latestChainedDelta = chainedDelta;
            }

            int firstInputTimestamp = 1_000;
            InputEnqueueEvent establishInputClock = new InputEnqueueEvent(
                SceneManager.SCENE_OID,
                latestChainedDelta.QY(),
                firstInputTimestamp,
                // The graphical client sends unacknowledged history newest-first.
                new InputFrame[]{
                    new InputFrame(firstInputTimestamp, 0.75f, 0.0f, 1),
                    new InputFrame(900, 0.75f, 0.0f, 1)
                });
            establishInputClock.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(establishInputClock));
            SceneDeltaEvent firstMovement = expectSceneDelta(
                wire, firstInputTimestamp, "first movement SceneDeltaEvent");
            check(firstMovement.aef() == firstInputTimestamp,
                "Server did not acknowledge the first input frame");
            check(firstMovement.aeh() >= latestChainedDelta.QY(),
                "Movement delta went backwards relative to the ordered scene chain");
            check(firstMovement.QY() > firstMovement.aeh(),
                "Movement delta did not advance the shared scene clock");
            check(firstMovement.QY() - firstMovement.aeh() == TEST_CONFIG.deltaIntervalMs,
                "Movement delta did not retain the configured Haven cadence");
            check(firstMovement.aek() != null && firstMovement.aek().length == 1,
                "First movement delta omitted the authoritative Knight update");
            Actor firstAuthoritativeActor =
                (Actor)firstMovement.aek()[0].apply(knightActor);
            check(Math.abs(firstAuthoritativeActor.tz().x
                    - (SceneManager.HAVEN_SPAWN_X + 0.5f)) < 0.001f,
                "Authoritative movement does not match installed actor speed=5.0; x="
                    + firstAuthoritativeActor.tz().x);
            check(((Knight)firstAuthoritativeActor).adU() == 0,
                "Knight did not leave its creation activity for idle/walk animation");
            check(Math.abs(firstAuthoritativeActor.tA() - 0.75f) < 0.001f,
                "Actor rotation was confused with translation or movement direction");
            check(((Knight)firstAuthoritativeActor).adV() <= firstMovement.QY(),
                "Actor activity was scheduled on the future predictive input clock");

            int secondInputTimestamp = 1_100;
            InputEnqueueEvent moveInput = new InputEnqueueEvent(
                SceneManager.SCENE_OID,
                firstMovement.QY(),
                secondInputTimestamp,
                new InputFrame[]{ new InputFrame(secondInputTimestamp, 0.0f, 0.0f, 1) });
            moveInput.cF(bootstrap.clientOid);
            long movementRequestStarted = System.nanoTime();
            wire.send(new ForwardEventRequest(moveInput));
            SceneDeltaEvent movement = expectSceneDelta(
                wire, secondInputTimestamp, "movement SceneDeltaEvent");
            check(movement.aef() == secondInputTimestamp,
                "Movement delta did not acknowledge the latest input frame");
            // The shared clock keeps ticking while the test inspects the prior
            // actor. Measure from sending this request, not that earlier tick.
            long movementResponseMs = (System.nanoTime() - movementRequestStarted) / 1_000_000L;
            check(movementResponseMs <= Math.max(250,
                    TEST_CONFIG.actorStateIntervalMs + 2 * TEST_CONFIG.deltaIntervalMs),
                "Authoritative movement response arrived late: " + movementResponseMs + "ms");
            System.out.println("[Test][LATENCY] movement response=" + movementResponseMs + "ms");
            check(movement.aek() != null && movement.aek().length == 1,
                "Movement delta omitted the authoritative Knight update");
            Actor movedActor = (Actor)movement.aek()[0].apply(firstAuthoritativeActor);
            check(movedActor.tz().x > SceneManager.HAVEN_SPAWN_X,
                "Moving east did not advance the authoritative X position");
            check(Math.abs(movedActor.tz().y - SceneManager.HAVEN_SPAWN_Y) < 0.001f,
                "Moving east unexpectedly changed the authoritative Y position: "
                    + movedActor.tz().y);
            check(movedActor.bR(2),
                "Movement input did not set the actor's moving flag");

            int stopInputTimestamp = 1_150;
            InputEnqueueEvent stopInput = new InputEnqueueEvent(
                SceneManager.SCENE_OID,
                movement.QY(),
                stopInputTimestamp,
                new InputFrame[]{ new InputFrame(stopInputTimestamp, 0.0f, 0.0f, 0) });
            stopInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(stopInput));
            SceneDeltaEvent stopped = expectSceneDelta(
                wire, stopInputTimestamp, "stop movement SceneDeltaEvent");
            Actor stoppedActor = (Actor)stopped.aek()[0].apply(movedActor);
            check(!stoppedActor.bR(2),
                "Releasing movement did not clear the actor's moving flag");
            check(Math.abs(stoppedActor.tz().x - movedActor.tz().x) < 0.001f
                    && Math.abs(stoppedActor.tz().y - movedActor.tz().y) < 0.001f,
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
            check(trainingResponse.yf() == 43 && trainingResponse.yc() == 3,
                "Training Hall move did not return gotPlace with a SceneModel");
            Object[] trainingArgs = trainingResponse.yd();
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
            check(trainingModel.adH().size() > 1_000,
                "Training Hall SceneModel contains no usable map geometry");

            wire.send(new SubscribeRequest(SceneManager.TRAINING_PLACE_OID));
            ObjectResponse<?> trainingObjectResponse = expect(
                wire.read(), ObjectResponse.class, "Training Hall scene ObjectResponse");
            DObject trainingObject = trainingObjectResponse.yk();
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
            check(trainingState.aee() == SceneManager.TRAINING_PLACE_OID,
                "Training Hall state contained the wrong scene object OID");
            check(trainingState.aej() != null && trainingState.aej().length == 12,
                "Training Hall initial state omitted the player actor");
            check(trainingState.aej()[0] instanceof Dungeoneer,
                "Training Hall player actor is not combat-capable Dungeoneer");
            Dungeoneer trainingKnight = (Dungeoneer)trainingState.aej()[0];
            check(Math.abs(trainingKnight.tz().x - SceneManager.TRAINING_SPAWN_X) < 0.001f
                    && Math.abs(trainingKnight.tz().y - SceneManager.TRAINING_SPAWN_Y) < 0.001f,
                "Knight did not spawn at the Training Hall entrance");
            check(SceneManager.COMBAT_PLAYER_ACTOR_CONFIG.equals(trainingKnight.Mv().getName()),
                "Training Hall actor does not use Character/PC/Level");
            check(trainingKnight.Ri() != null
                    && "Weapon/Sword/Leviathan Blade".equals(
                        trainingKnight.Ri().Mv().getName()),
                "Training Hall actor did not equip the first weapon");
            ConfigReference<?> trainingModelRef =
                (ConfigReference<?>)trainingKnight.Mv().kW().get("Model");
            check(trainingModelRef != null && trainingModelRef.kW().containsKey("Weapon"),
                "Training Hall actor model omitted its visible weapon");

            SceneDeltaEvent trainingPreloadTick = expectSceneDelta(
                wire, 0, "Training Hall preload SceneDeltaEvent");
            check(trainingPreloadTick.aeh() == trainingState.QY(),
                "Training Hall preload did not reference its initial state");
            check(trainingPreloadTick.QY() - trainingPreloadTick.aeh() == TEST_CONFIG.trainingDeltaIntervalMs,
                "Training Hall did not use its configured cadence");

            System.out.println("[Test] 8a. Verifying Training Hall configured tick cadence...");
            // The initial state now serializes the map's eleven NPCs. Consume
            // that preload backlog before measuring arrival cadence.
            for (SceneDeltaEvent preload : collectSceneDeltasFor(wire,250L)) {
                check(preload.aeh()==trainingPreloadTick.QY(),"Training preload broke delta chain");
                check(preload.QY()-preload.aeh()==TEST_CONFIG.trainingDeltaIntervalMs,
                    "Training preload changed the configured tick step");
                trainingPreloadTick=preload;
            }
            SceneNetworkManager measuredNetwork=(SceneNetworkManager)privateValue(server,"sceneNetwork");
            int measuredClockStart=measuredNetwork.currentTimestamp(SceneManager.TRAINING_SCENE_ID);
            long measuredWallStart=System.nanoTime();
            List<SceneDeltaEvent> trainingRateDeltas =
                collectSceneDeltasFor(wire, 450L);
            long measuredWallMs=(System.nanoTime()-measuredWallStart)/1_000_000L;
            int measuredClockAdvance=measuredNetwork.currentTimestamp(SceneManager.TRAINING_SCENE_ID)-measuredClockStart;
            int expectedTrainingDeltas = (int)(measuredWallMs / TEST_CONFIG.trainingDeltaIntervalMs);
            check(Math.abs(measuredClockAdvance-measuredWallMs)<=TEST_CONFIG.trainingDeltaIntervalMs*3L,
                "Authoritative Training clock did not follow elapsed wall time");
            check(trainingRateDeltas.size() >= Math.max(1, expectedTrainingDeltas * 3 / 4),
                "Training Hall did not sustain its configured cadence; deltas="
                    + trainingRateDeltas.size());
            for (SceneDeltaEvent trainingRateDelta : trainingRateDeltas) {
                check(trainingRateDelta.aeh() == trainingPreloadTick.QY(),
                    "Training Hall rate sample broke the ordered delta chain");
                check(trainingRateDelta.QY() - trainingRateDelta.aeh() == TEST_CONFIG.trainingDeltaIntervalMs,
                    "Training Hall rate sample did not advance its configured interval");
                trainingPreloadTick = trainingRateDelta;
            }

            CombatReader combatReader = new CombatReader(trainingKnight);
            System.out.println("[Test] 9. Exercising authoritative combat controls...");
            int swordAttackTimestamp = 2_000;
            DungeonInputFrame swordAttackFrame = new DungeonInputFrame(
                swordAttackTimestamp, 0.0f, 0.0f, 4, swordAttackTimestamp);
            InputEnqueueEvent swordAttackInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                trainingPreloadTick.QY(),
                swordAttackTimestamp,
                new InputFrame[]{ swordAttackFrame });
            swordAttackInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(swordAttackInput));
            SceneDeltaEvent swordAttackDelta = combatReader.expect(
                wire, swordAttackTimestamp, "Leviathan attack SceneDeltaEvent");
            Dungeoneer attackingKnight = combatReader.pawn;
            check(attackingKnight.adU() == 292,
                "Primary sword attack did not start ks_attack activity 292");
            check(attackingKnight.Ri() != null,
                "Primary attack delta lost the equipped weapon");

            InputEnqueueEvent releaseSwordInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID, swordAttackDelta.QY(), 2_100,
                new InputFrame[]{new DungeonInputFrame(2_100,0f,0f,0,2_100)});
            releaseSwordInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(releaseSwordInput));
            combatReader.expect(wire,2_100,"sword release");

            // Input timestamps acknowledge client frames; they do not fast-forward
            // the authoritative scene or bypass vanilla attack recovery.
            Thread.sleep(900L);
            int weaponSwitchTimestamp = 4_000;
            int polarisFlags = DungeonInputFrame.au(0, 2);
            DungeonInputFrame weaponSwitchFrame = new DungeonInputFrame(
                weaponSwitchTimestamp, 0.0f, 0.0f, polarisFlags, swordAttackTimestamp);
            InputEnqueueEvent weaponSwitchInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                swordAttackDelta.QY(),
                weaponSwitchTimestamp,
                new InputFrame[]{ weaponSwitchFrame });
            weaponSwitchInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(weaponSwitchInput));
            SceneDeltaEvent weaponSwitchDelta = combatReader.expect(
                wire, weaponSwitchTimestamp, "weapon-switch SceneDeltaEvent");
            Dungeoneer polarisKnight =
                combatReader.pawn;
            check(polarisKnight.Ri() != null
                    && "Weapon/Handgun/Polaris".equals(polarisKnight.Ri().Mv().getName()),
                "Weapon slot 3 did not equip Polaris");
            ConfigReference<?> polarisModel =
                (ConfigReference<?>)polarisKnight.Mv().kW().get("Model");
            check(polarisModel != null && polarisModel.kW().get("Weapon") != null,
                "Weapon switch did not update the visible weapon model");

            Thread.sleep(400L);
            int handgunAttackTimestamp = 4_100;
            DungeonInputFrame handgunAttackFrame = new DungeonInputFrame(
                handgunAttackTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.au(4, 2), handgunAttackTimestamp);
            InputEnqueueEvent handgunAttackInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                weaponSwitchDelta.QY(),
                handgunAttackTimestamp,
                new InputFrame[]{ handgunAttackFrame });
            handgunAttackInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(handgunAttackInput));
            SceneDeltaEvent handgunAttackDelta = combatReader.expect(
                wire, handgunAttackTimestamp, "Polaris attack SceneDeltaEvent");
            Dungeoneer handgunAttackKnight =
                combatReader.pawn;
            check(handgunAttackKnight.adU() == 292,
                "Primary handgun attack did not start ks_attack activity 292");

            InputEnqueueEvent releaseHandgunInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID, handgunAttackDelta.QY(), 4_200,
                new InputFrame[]{new DungeonInputFrame(4_200,0f,0f,
                    DungeonInputFrame.au(0,2),4_200)});
            releaseHandgunInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(releaseHandgunInput));
            combatReader.expect(wire,4_200,"handgun release");

            Thread.sleep(1_000L);
            int defendTimestamp = 6_000;
            DungeonInputFrame defendFrame = new DungeonInputFrame(
                defendTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.au(64, 2), defendTimestamp);
            InputEnqueueEvent defendInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                handgunAttackDelta.QY(),
                defendTimestamp,
                new InputFrame[]{ defendFrame });
            defendInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(defendInput));
            SceneDeltaEvent defendDelta = combatReader.expect(
                wire, defendTimestamp, "shield defense SceneDeltaEvent");
            Dungeoneer defendingKnight =
                combatReader.pawn;
            check(defendingKnight.adU() == 284,
                "Defend input did not start ks_defense activity 284");
            check(defendingKnight.adV() <= defendDelta.QY(),
                "Coalesced defense activity was stamped in the client's future");

            int bombSwitchTimestamp = 8_000;
            DungeonInputFrame bombSwitchFrame = new DungeonInputFrame(
                bombSwitchTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.au(0, 3), defendTimestamp);
            InputEnqueueEvent bombSwitchInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                defendDelta.QY(),
                bombSwitchTimestamp,
                new InputFrame[]{ bombSwitchFrame });
            bombSwitchInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(bombSwitchInput));
            SceneDeltaEvent bombSwitchDelta = combatReader.expect(
                wire, bombSwitchTimestamp, "bomb-switch SceneDeltaEvent");
            Dungeoneer bombKnight =
                combatReader.pawn;
            check(bombKnight.Ri() != null
                    && "Weapon/Bomb/Ash of Agni".equals(bombKnight.Ri().Mv().getName()),
                "Weapon slot 4 did not equip Ash of Agni");

            Thread.sleep(400L);
            int bombChargeTimestamp = 8_100;
            DungeonInputFrame bombChargeFrame = new DungeonInputFrame(
                bombChargeTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.au(4, 3), bombChargeTimestamp);
            InputEnqueueEvent bombChargeInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                bombSwitchDelta.QY(),
                bombChargeTimestamp,
                new InputFrame[]{ bombChargeFrame });
            bombChargeInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(bombChargeInput));
            SceneDeltaEvent bombChargeDelta = combatReader.expect(
                wire, bombChargeTimestamp, "bomb charge SceneDeltaEvent");
            Dungeoneer chargingKnight =
                combatReader.pawn;
            check(chargingKnight.adU() == 283,
                "Bomb attack did not start ks_charge activity 283");

            Thread.sleep(3_200L);
            int bombHeldTimestamp = 11_200;
            DungeonInputFrame bombHeldFrame = new DungeonInputFrame(
                bombHeldTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.au(4, 3), bombChargeTimestamp);
            InputEnqueueEvent bombHeldInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                bombChargeDelta.QY(),
                bombHeldTimestamp,
                new InputFrame[]{ bombHeldFrame });
            bombHeldInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(bombHeldInput));
            SceneDeltaEvent bombHeldDelta = combatReader.expect(
                wire, bombHeldTimestamp, "held bomb charge SceneDeltaEvent");
            Dungeoneer heldBombKnight = combatReader.pawn;

            int bombReleaseTimestamp = 11_300;
            DungeonInputFrame bombReleaseFrame = new DungeonInputFrame(
                bombReleaseTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.au(0, 3), bombReleaseTimestamp);
            InputEnqueueEvent bombReleaseInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                bombHeldDelta.QY(),
                bombReleaseTimestamp,
                new InputFrame[]{ bombReleaseFrame });
            bombReleaseInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(bombReleaseInput));
            SceneDeltaEvent bombReleaseDelta = combatReader.expect(
                wire, bombReleaseTimestamp, "charged bomb release SceneDeltaEvent");
            Dungeoneer releasedBombKnight =
                combatReader.pawn;
            check(releasedBombKnight.adU() == 290,
                "Fully charged bomb did not start ks_charge_attack activity 290");
            check(releasedBombKnight.adV() <= bombReleaseDelta.QY(),
                "Coalesced charged attack was stamped in the client's future");

            Thread.sleep(1_000L);
            int dodgeTimestamp = 12_300;
            DungeonInputFrame dodgeFrame = new DungeonInputFrame(
                dodgeTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.au(1024, 3), dodgeTimestamp);
            InputEnqueueEvent dodgeInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                bombReleaseDelta.QY(),
                dodgeTimestamp,
                new InputFrame[]{ dodgeFrame });
            dodgeInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(dodgeInput));
            SceneDeltaEvent dodgeDelta = combatReader.expect(
                wire, dodgeTimestamp, "dodge SceneDeltaEvent");
            Dungeoneer dodgingKnight =
                combatReader.pawn;
            check(dodgingKnight.adU() == 286,
                "Dodge input did not start ks_dodge activity 286");
            check(dodgingKnight.adV() <= dodgeDelta.QY(),
                "Coalesced dodge was stamped in the client's future");

            Thread.sleep(800L);
            int shieldBashTimestamp = 13_000;
            DungeonInputFrame shieldBashFrame = new DungeonInputFrame(
                shieldBashTimestamp, 0.0f, 0.0f,
                DungeonInputFrame.au(2048, 3), shieldBashTimestamp);
            InputEnqueueEvent shieldBashInput = new InputEnqueueEvent(
                SceneManager.TRAINING_PLACE_OID,
                dodgeDelta.QY(),
                shieldBashTimestamp,
                new InputFrame[]{ shieldBashFrame });
            shieldBashInput.cF(bootstrap.clientOid);
            wire.send(new ForwardEventRequest(shieldBashInput));
            SceneDeltaEvent shieldBashDelta = combatReader.expect(
                wire, shieldBashTimestamp, "shield-bash SceneDeltaEvent");
            Dungeoneer bashingKnight =
                combatReader.pawn;
            check(bashingKnight.adU() == 287,
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
            check(returnResponse.yf() == 44 && returnResponse.yc() == 3,
                "Return from Training Hall did not resolve Haven");
            check(returnResponse.yd()[0].equals(Integer.valueOf(SceneManager.SCENE_OID)),
                "Return from Training Hall used the wrong Haven place OID");
            check(returnResponse.yd()[1] instanceof TownSceneConfig,
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
            Knight returnedKnight = (Knight)returnState.aej()[0];
            check(Math.abs(returnedKnight.tz().x - SceneManager.HAVEN_TRAINING_RETURN_X) < 0.001f
                    && Math.abs(returnedKnight.tz().y
                        - SceneManager.HAVEN_TRAINING_RETURN_Y) < 0.001f,
                "Knight did not return beside Haven's Training Hall doorway");

            System.out.println("[Test] 10. Joining and synchronizing a second LAN client...");
            JoinedClient friend = joinHaven(testPort, 90);
            friendClient = friend.channel;
            check(friend.bootstrap.clientOid != bootstrap.clientOid,
                "Two sessions received the same client/pawn OID");
            check(friend.initialState.aej() != null
                    && friend.initialState.aej().length == 2,
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
                friend.preloadState.QY(),
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
            check(movedFriend.tz().x > friendActor.tz().x,
                "Friend movement did not advance its authoritative position");

            SceneDeltaEvent friendMovementForHost = expectUpdatedActor(
                wire, bootstrap.clientOid, SceneManager.SCENE_OID,
                friend.bootstrap.clientOid, "friend movement broadcast");
            check(findActorDelta(friendMovementForHost, friend.bootstrap.clientOid) != null,
                "Existing client did not receive the friend's movement");

            System.out.println("[Test] 10a. Verifying shared combat damage for two TCP clients...");
            SceneDeltaEvent hostCombatState=enterTraining(wire,bootstrap.clientOid,80);
            SceneDeltaEvent friendCombatState=enterTraining(friend.wire,friend.bootstrap.clientOid,81);
            com.threerings.projectx.dungeon.data.actor.Monster hostCube=null,friendCube=null;
            for (Actor actor : hostCombatState.aej()) {
                if (actor instanceof com.threerings.projectx.dungeon.data.actor.Monster
                        && actor.tz().x==38.5f && actor.tz().y==-3.5f)
                    hostCube=(com.threerings.projectx.dungeon.data.actor.Monster)actor;
            }
            check(hostCube!=null,"Host omitted shared training cube");
            for (Actor actor : friendCombatState.aej()) if (actor.vw()==hostCube.vw())
                friendCube=(com.threerings.projectx.dungeon.data.actor.Monster)actor;
            check(friendCube!=null && friendCube.RA()==1,"Friend did not join the same combat world");
            // Place a deterministic fixture beside the cube. The ordinary scene
            // ticker and TCP serialization must publish its damage to both peers.
            @SuppressWarnings("unchecked") java.util.Map<Integer,ClientSession> testSessions=
                (java.util.Map<Integer,ClientSession>)privateValue(server,"sessions");
            ClientSession hostSession=testSessions.get(1);
            SceneManager combatScene=(SceneManager)privateValue(server,"sceneMgr");
            synchronized (hostSession) {
                Dungeoneer pawn=(Dungeoneer)privateValue(hostSession,"authoritativeActor");
                int time=(Integer)privateValue(hostSession,"sceneTimestamp");
                pawn.tz().s(37.5f,-3.5f); pawn.ah(0); pawn.aU(292,time);
                combatScene.getCombatWorld().attack(pawn,combatScene.resolveAttack(pawn.Rj(),0),time);
            }
            SceneDeltaEvent hostHit=expectUpdatedActor(wire,bootstrap.clientOid,
                SceneManager.TRAINING_PLACE_OID,hostCube.vw(),"host shared damage");
            SceneDeltaEvent friendHit=expectUpdatedActor(friend.wire,friend.bootstrap.clientOid,
                SceneManager.TRAINING_PLACE_OID,friendCube.vw(),"friend shared damage");
            com.threerings.projectx.dungeon.data.actor.Monster hostDead=
                (com.threerings.projectx.dungeon.data.actor.Monster)findActorDelta(hostHit,hostCube.vw()).apply(hostCube);
            com.threerings.projectx.dungeon.data.actor.Monster friendDead=
                (com.threerings.projectx.dungeon.data.actor.Monster)findActorDelta(friendHit,friendCube.vw()).apply(friendCube);
            check(hostDead.RA()==0 && friendDead.RA()==0 && hostDead.adY()==friendDead.adY(),
                "Two clients diverged in authoritative cube health or death time");

            friendClient.shutdownOutput();
            expectRemovedActor(wire, bootstrap.clientOid, SceneManager.TRAINING_PLACE_OID,
                friend.bootstrap.clientOid,
                "friend disconnect broadcast");
            friendClient.close();
            friendClient = null;

            System.out.println("[Test] 11. Checking keepalive...");
            PingRequest ping = new PingRequest(Transport.arz);
            ping.c(Transport.arz);
            wire.send(ping);
            PongResponse pong = readUntilMessage(wire, PongResponse.class, "PongResponse");
            check(pong.yl() >= ping.yl() && pong.ym() >= pong.yl(),
                "Keepalive omitted the frozen client's send/receive timestamps");
            check(pong.yn() >= 0 && pong.yn() <= pong.ym() - ping.yl(),
                "Keepalive process delay does not fit within the round trip");

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

    /** Reproduces input arriving between the shared snapshot and publication. */
    private static void checkSnapshotAcknowledgments(SpiralServer server) throws Exception {
        java.lang.reflect.Field managerField = SpiralServer.class.getDeclaredField("sceneMgr");
        managerField.setAccessible(true);
        try (SocketChannel channel = SocketChannel.open()) {
            ClientSession session = new ClientSession(channel, null,
                (SceneManager)managerField.get(server), null, TEST_CONFIG,
                1, "20261006114613", 0, ignored -> {});
            Knight actor = new Knight(new ConfigReference<>("Knight"), 101, 1,
                new com.threerings.math.Vector2f(1f, -30.5f), 0f);
            setSessionField(session,"authoritativeActor",actor);
            setSessionField(session,"sceneReady",true);
            setSessionField(session,"sceneTimestamp",1);
            setSessionField(session,"lastInputTimestamp",100);
            java.lang.reflect.Field sentField = ClientSession.class.getDeclaredField("lastSentActors");
            sentField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Integer,Knight> sent = (java.util.Map<Integer,Knight>)sentField.get(session);
            sent.put(actor.vw(),(Knight)actor.copy(null));
            java.lang.reflect.Field queueField = ClientSession.class.getDeclaredField("outbound");
            queueField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.concurrent.BlockingQueue<Object> queue =
                (java.util.concurrent.BlockingQueue<Object>)queueField.get(session);

            int step = Math.max(TEST_CONFIG.deltaIntervalMs,TEST_CONFIG.actorStateIntervalMs);
            ClientSession.TickSnapshot beforeMove = session.snapshotForTick(SceneManager.DEFAULT_SCENE_ID,1+step);
            actor.tz().x += 0.5f; actor.k(2,true);
            setSessionField(session,"lastInputTimestamp",200);
            session.publishSceneTick(SceneManager.DEFAULT_SCENE_ID,1+step,
                java.util.Map.of(actor.vw(),beforeMove.actor),beforeMove.inputTimestamp);
            SceneDeltaEvent oldState = (SceneDeltaEvent)((EventNotification)queue.remove()).yj();
            check(oldState.aef() == 100,
                "Snapshot acknowledged movement received after capture");

            ClientSession.TickSnapshot beforeStop = session.snapshotForTick(SceneManager.DEFAULT_SCENE_ID,1+2*step);
            actor.k(2,false); setSessionField(session,"lastInputTimestamp",300);
            session.publishSceneTick(SceneManager.DEFAULT_SCENE_ID,1+2*step,
                java.util.Map.of(actor.vw(),beforeStop.actor),beforeStop.inputTimestamp);
            SceneDeltaEvent movingState = (SceneDeltaEvent)((EventNotification)queue.remove()).yj();
            check(movingState.aef() == 200 && findActorDelta(movingState,actor.vw()) != null,
                "Moving snapshot acknowledged release before delivering stopped state");

            ClientSession.TickSnapshot stopped = session.snapshotForTick(SceneManager.DEFAULT_SCENE_ID,1+3*step);
            session.publishSceneTick(SceneManager.DEFAULT_SCENE_ID,1+3*step,
                java.util.Map.of(actor.vw(),stopped.actor),stopped.inputTimestamp);
            SceneDeltaEvent stopState = (SceneDeltaEvent)((EventNotification)queue.remove()).yj();
            ActorDelta stoppedDelta = findActorDelta(stopState,actor.vw());
            check(stopState.aef() == 300 && stoppedDelta != null
                    && !((Actor)stoppedDelta.apply(beforeStop.actor)).bR(2),
                "Release acknowledgment omitted the stopped actor state");
            System.out.println("[Test] Snapshot/input acknowledgment race, including key release: PASSED");
        }
    }

    private static void setSessionField(ClientSession session, String name, Object value) throws Exception {
        java.lang.reflect.Field field = ClientSession.class.getDeclaredField(name);
        field.setAccessible(true); field.set(session,value);
    }

    /** Connects another stock-protocol client directly to Haven for multiplayer tests. */
    private static JoinedClient joinHaven(int port, int requestBase) throws Exception {
        SocketChannel channel = SocketChannel.open(
            new InetSocketAddress("localhost", port));
        channel.configureBlocking(false);
        Wire wire = new Wire(channel);
        try {
            wire.send(new AuthRequest(
                null, "20261006114613", new String[]{"presents", "projectx"}));
            AuthResponse auth = readUntilMessage(
                wire, AuthResponse.class, "second-client AuthResponse");
            check("success".equals(auth.yg().code),
                "Second-client authentication did not return success");
            BootstrapNotification bootstrapMessage = readUntilMessage(
                wire, BootstrapNotification.class, "second-client BootstrapNotification");
            ProjectXBootstrapData bootstrap =
                (ProjectXBootstrapData)bootstrapMessage.yh();

            wire.send(new SubscribeRequest(bootstrap.clientOid));
            ObjectResponse<?> playerResponse = readUntilMessage(
                wire, ObjectResponse.class, "second-client PlayerObject");
            check(playerResponse.yk() instanceof PlayerObject,
                "Second client did not receive its PlayerObject");
            PlayerObject player = (PlayerObject)playerResponse.yk();

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
            check(zoneResponse.yf() == (short)requestBase && zoneResponse.yc() == 3,
                "Second-client Haven move did not include its SceneModel");

            wire.send(new SubscribeRequest(SceneManager.SCENE_OID));
            ObjectResponse<?> sceneResponse = readUntilMessage(
                wire, ObjectResponse.class, "second-client TownSceneObject");
            check(sceneResponse.yk() instanceof TownSceneObject,
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
            check(preloadState.aeh() == initialState.QY(),
                "Second-client preload did not reference its initial state");
            return new JoinedClient(
                channel, wire, bootstrap, player, initialState, preloadState);
        } catch (Exception error) {
            channel.close();
            throw error;
        }
    }

    private static Knight findAddedActor(SceneDeltaEvent event, int actorId) {
        Actor[] actors = event.aej();
        if (actors != null) {
            for (Actor actor : actors) {
                if (actor instanceof Knight && actor.vw() == actorId) {
                    return (Knight)actor;
                }
            }
        }
        return null;
    }

    private static ActorDelta findActorDelta(SceneDeltaEvent event, int actorId) {
        ActorDelta[] deltas = event.aek();
        if (deltas != null) {
            for (ActorDelta delta : deltas) {
                if (delta != null && delta.vw() == actorId) {
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
            int[] removed = event.ael();
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
            sceneOid, event.QY(), event.QY(), new InputFrame[0]);
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
        return expectSceneDelta(wire,minimumAcknowledge,description,event->{});
    }

    private static final class CombatReader {
        Dungeoneer pawn;
        CombatReader(Dungeoneer pawn) { this.pawn=pawn; }
        SceneDeltaEvent expect(Wire wire,int minimumAcknowledge,String description) throws Exception {
            return expectSceneDelta(wire,minimumAcknowledge,description,event->{
                if (event.aee()!=SceneManager.TRAINING_PLACE_OID) return;
                ActorDelta delta=findActorDelta(event,pawn.vw());
                if (delta!=null) pawn=(Dungeoneer)delta.apply(pawn);
            });
        }
    }

    private static Object privateValue(Object object,String name) throws Exception {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(object);
    }

    private static SceneDeltaEvent enterTraining(Wire wire,int clientOid,int requestId) throws Exception {
        ZoneMarshaller.ZoneMoveMarshaller listener=new ZoneMarshaller.ZoneMoveMarshaller();
        listener.requestId=(short)requestId;
        InvocationRequestEvent move=new InvocationRequestEvent(clientOid,ClientSession.ZONE_SERVICE_CODE,1,
            new Object[]{1,SceneManager.TRAINING_SCENE_ID,0,listener});
        move.cF(clientOid); wire.send(new ForwardEventRequest(move));
        InvocationResponseEvent response=readUntilEvent(wire,InvocationResponseEvent.class,"shared combat scene move");
        check(response.yf()==requestId && response.yc()==3,"Shared combat scene move failed");
        wire.send(new SubscribeRequest(SceneManager.TRAINING_PLACE_OID));
        readUntilMessage(wire,ObjectResponse.class,"shared combat scene subscription");
        InvocationRequestEvent state=new InvocationRequestEvent(SceneManager.TRAINING_PLACE_OID,
            SceneManager.TUDEY_SCENE_SERVICE_CODE,1,new Object[0]);
        state.cF(clientOid); wire.send(new ForwardEventRequest(state));
        for (int attempt=0;attempt<512;attempt++) {
            SceneDeltaEvent event=readUntilEvent(wire,SceneDeltaEvent.class,"shared combat initial state");
            if (event.aee()!=SceneManager.TRAINING_PLACE_OID || event.aej()==null) continue;
            InputEnqueueEvent ready=new InputEnqueueEvent(SceneManager.TRAINING_PLACE_OID,event.QY(),0,new InputFrame[0]);
            ready.cF(clientOid); wire.send(new ForwardEventRequest(ready));
            return event;
        }
        throw new AssertionError("Shared combat initial state missing");
    }

    private static SceneDeltaEvent expectSceneDelta(Wire wire,int minimumAcknowledge,
            String description,java.util.function.Consumer<SceneDeltaEvent> consume) throws Exception {
        for (int attempts = 0; attempts < 512; attempts++) {
            Object message = wire.read();
            if (!(message instanceof EventNotification)) {
                continue;
            }
            DEvent event = ((EventNotification)message).yj();
            if (event instanceof SceneDeltaEvent) consume.accept((SceneDeltaEvent)event);
            if (event instanceof SceneDeltaEvent
                    && ((SceneDeltaEvent)event).aef() >= minimumAcknowledge) {
                SceneDeltaEvent delta = (SceneDeltaEvent)event;
                int expectedIntervalMs = delta.aee() == SceneManager.TRAINING_PLACE_OID
                    ? TEST_CONFIG.trainingDeltaIntervalMs : TEST_CONFIG.deltaIntervalMs;
                check(delta.QY() - delta.aeh() == expectedIntervalMs,
                    "SceneDeltaEvent broke the configured scene cadence for "
                        + description + ": " + (delta.QY() - delta.aeh()) + "ms");
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
                DEvent event = ((EventNotification)message).yj();
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
                    && ((EventNotification)message).yj() instanceof SceneDeltaEvent) {
                deltas.add((SceneDeltaEvent)((EventNotification)message).yj());
            }
        }
        return deltas;
    }

    private static <T extends DEvent> T expectEvent(
            Object value, Class<T> type, String description) {
        EventNotification notification = expect(value, EventNotification.class, description);
        return expect(notification.yj(), type, description);
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
            output.bf(message);
            output.flush();
            ByteBuffer buffer = sink.sH();
            long deadline = System.currentTimeMillis() + READ_TIMEOUT_MS;
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) == 0) {
                    if (System.currentTimeMillis() >= deadline) {
                        throw new AssertionError("Timed out writing " + message.getClass().getSimpleName());
                    }
                    Thread.sleep(2L);
                }
            }
            sink.sI();
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
                    return input.qe();
                }
                Thread.sleep(2L);
            } while (System.currentTimeMillis() < deadline);
            return null;
        }
    }
}
