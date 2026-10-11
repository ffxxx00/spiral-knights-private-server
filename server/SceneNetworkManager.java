package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import com.threerings.projectx.data.actor.Knight;

/**
 * Fixed-rate authoritative scene clocks and participant broadcaster.
 *
 * Input frequency never drives output frequency: each scene advances in small,
 * shared steps and every connected client receives the same actor snapshot.
 */
public final class SceneNetworkManager implements AutoCloseable {
    private final int schedulerIntervalMs;
    private final Map<Integer, SceneChannel> scenes = new LinkedHashMap<>();
    private final ScheduledExecutorService ticker;
    private volatile boolean running;

    public SceneNetworkManager(int havenIntervalMs, int trainingIntervalMs) {
        this.schedulerIntervalMs = havenIntervalMs;
        this.scenes.put(SceneManager.DEFAULT_SCENE_ID,
            new SceneChannel(SceneManager.DEFAULT_SCENE_ID, havenIntervalMs));
        this.scenes.put(SceneManager.TRAINING_SCENE_ID,
            new SceneChannel(SceneManager.TRAINING_SCENE_ID, trainingIntervalMs));
        ThreadFactory threads = task -> {
            Thread thread = new Thread(task, "SceneNetwork-Ticker");
            thread.setDaemon(true);
            thread.setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1));
            return thread;
        };
        this.ticker = Executors.newSingleThreadScheduledExecutor(threads);
    }

    public void start() {
        if (this.running) {
            return;
        }
        this.running = true;
        // The scene timestamp is game time in milliseconds. The scheduler runs
        // at Haven's 100 ms input cadence, while each channel decides when to
        // emit. Training deliberately emits one reliable record every 200 ms:
        // graphical-client telemetry showed that its Dungeoneer controller can
        // consume about five records per second even when no ActorDelta exists.
        this.ticker.scheduleAtFixedRate(
            this::tickSafely, this.schedulerIntervalMs, this.schedulerIntervalMs,
            TimeUnit.MILLISECONDS);
        System.out.println("[NETWORK] authoritative scene ticker started baseIntervalMs="
            + this.schedulerIntervalMs
            + " havenIntervalMs=" + intervalMs(SceneManager.DEFAULT_SCENE_ID)
            + " trainingIntervalMs=" + intervalMs(SceneManager.TRAINING_SCENE_ID));
    }

    public int currentTimestamp(int sceneId) {
        SceneChannel scene = requireScene(sceneId);
        synchronized (scene) {
            return scene.timestamp;
        }
    }

    public int intervalMs(int sceneId) {
        return requireScene(sceneId).tickIntervalMs;
    }

    public void join(int sceneId, ClientSession session) {
        SceneChannel scene = requireScene(sceneId);
        synchronized (scene) {
            boolean wasEmpty = scene.participants.isEmpty();
            if (!scene.participants.contains(session)) {
                scene.participants.add(session);
            }
            if (wasEmpty) {
                scene.pendingElapsedMs = 0;
                scene.resetMetrics(System.nanoTime());
            }
            System.out.println("[NETWORK] join scene=" + sceneId
                + " clientOid=" + session.getClientOid()
                + " players=" + scene.participants.size()
                + " timestamp=" + scene.timestamp);
        }
    }

    public void leave(int sceneId, ClientSession session) {
        SceneChannel scene = this.scenes.get(sceneId);
        if (scene == null) {
            return;
        }
        synchronized (scene) {
            if (scene.participants.remove(session)) {
                System.out.println("[NETWORK] leave scene=" + sceneId
                    + " clientOid=" + session.getClientOid()
                    + " players=" + scene.participants.size());
            }
        }
    }

    public Map<Integer, Knight> snapshotActors(int sceneId) {
        SceneChannel scene = requireScene(sceneId);
        List<ClientSession> participants;
        int timestamp;
        synchronized (scene) {
            participants = new ArrayList<>(scene.participants);
            timestamp = scene.timestamp;
        }
        return snapshotActors(sceneId, timestamp, participants);
    }

    public int participantCount(int sceneId) {
        SceneChannel scene = requireScene(sceneId);
        synchronized (scene) {
            return scene.participants.size();
        }
    }

    public void broadcastOccupants(int sceneId) {
        SceneChannel scene = this.scenes.get(sceneId);
        if (scene == null) {
            return;
        }
        List<ClientSession> participants;
        synchronized (scene) {
            participants = new ArrayList<>(scene.participants);
        }
        for (ClientSession session : participants) {
            try {
                session.publishSceneOccupants(sceneId);
            } catch (IOException error) {
                System.out.println("[NETWORK][WARNING] failed occupant broadcast"
                    + " scene=" + sceneId + " clientOid=" + session.getClientOid()
                    + " error=" + error.getMessage());
                session.close();
            }
        }
    }

    private void tickSafely() {
        if (!this.running) {
            return;
        }
        try {
            for (SceneChannel scene : this.scenes.values()) {
                tick(scene);
            }
        } catch (Throwable error) {
            System.out.println("[NETWORK][ERROR] scene tick failed: " + error);
            error.printStackTrace(System.out);
        }
    }

    private void tick(SceneChannel scene) {
        List<ClientSession> participants;
        int timestamp;
        String metrics = null;
        synchronized (scene) {
            if (scene.participants.isEmpty()) {
                scene.pendingElapsedMs = 0;
                return;
            }
            scene.pendingElapsedMs += this.schedulerIntervalMs;
            if (scene.pendingElapsedMs < scene.tickIntervalMs) {
                return;
            }
            scene.pendingElapsedMs -= scene.tickIntervalMs;
            long now = System.nanoTime();
            if (scene.lastTickNanos != 0L) {
                scene.maxTickGapNanos = Math.max(
                    scene.maxTickGapNanos, now - scene.lastTickNanos);
            }
            scene.lastTickNanos = now;
            scene.timestamp += scene.tickIntervalMs;
            timestamp = scene.timestamp;
            participants = new ArrayList<>(scene.participants);
            scene.metricsTicks++;
            if (scene.metricsTicks >= 100) {
                long wallNanos = Math.max(1L, now - scene.metricsStartedNanos);
                double rateHz = scene.metricsTicks * 1_000_000_000.0 / wallNanos;
                long wallMs = TimeUnit.NANOSECONDS.toMillis(wallNanos);
                long maxGapMs = TimeUnit.NANOSECONDS.toMillis(scene.maxTickGapNanos);
                metrics = String.format(java.util.Locale.ROOT,
                    "[NETWORK][TICK] scene=%d players=%d timestamp=%d rateHz=%.2f"
                        + " wallWindowMs=%d maxGapMs=%d",
                    scene.sceneId, participants.size(), timestamp, rateHz,
                    wallMs, maxGapMs);
                scene.resetMetrics(now);
            }
        }

        if (metrics != null) {
            System.out.println(metrics);
        }

        Map<Integer, Knight> actors = snapshotActors(
            scene.sceneId, timestamp, participants);
        for (ClientSession session : participants) {
            try {
                session.publishSceneTick(scene.sceneId, timestamp, actors);
            } catch (Exception error) {
                System.out.println("[NETWORK][WARNING] failed scene delta"
                    + " scene=" + scene.sceneId
                    + " clientOid=" + session.getClientOid()
                    + " error=" + error.getMessage());
                session.close();
            }
        }
    }

    private static Map<Integer, Knight> snapshotActors(
            int sceneId, int timestamp, List<ClientSession> participants) {
        Map<Integer, Knight> actors = new LinkedHashMap<>();
        for (ClientSession session : participants) {
            Knight actor = session.snapshotActorForTick(sceneId, timestamp);
            if (actor != null) {
                actors.put(actor.vw(), actor);
            }
        }
        return actors;
    }

    private SceneChannel requireScene(int sceneId) {
        SceneChannel scene = this.scenes.get(sceneId);
        if (scene == null) {
            throw new IllegalArgumentException("Unknown network sceneId=" + sceneId);
        }
        return scene;
    }

    @Override
    public void close() {
        this.running = false;
        this.ticker.shutdownNow();
        System.out.println("[NETWORK] authoritative scene ticker stopped");
    }

    private static final class SceneChannel {
        final int sceneId;
        final int tickIntervalMs;
        final List<ClientSession> participants = new ArrayList<>();
        int timestamp = 1;
        int pendingElapsedMs;
        long lastTickNanos;
        long metricsStartedNanos;
        long maxTickGapNanos;
        int metricsTicks;

        SceneChannel(int sceneId, int tickIntervalMs) {
            this.sceneId = sceneId;
            this.tickIntervalMs = tickIntervalMs;
        }

        void resetMetrics(long now) {
            this.lastTickNanos = now;
            this.metricsStartedNanos = now;
            this.maxTickGapNanos = 0L;
            this.metricsTicks = 0;
        }
    }
}
