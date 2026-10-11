package server;

import java.io.FileInputStream;
import java.util.HashMap;
import com.threerings.config.ConfigGroup;
import com.threerings.config.ConfigManager;
import com.threerings.config.ManagedConfig;
import com.threerings.export.BinaryImporter;

/** In-memory view of the game's installed binary configuration catalog. */
public final class InstalledConfigManager extends ConfigManager {
    public InstalledConfigManager() {
        this._type = "global";
        this._resources = new HashMap<>();
        this._classes = new HashMap<>();
        this._classes.put("global", new Class<?>[0]);
        this._classes.put("resource", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    public <T extends ManagedConfig> int loadGroup(Class<T> type, String path) throws Exception {
        T[] configs;
        try (FileInputStream input = new FileInputStream(path)) {
            configs = (T[])new BinaryImporter(input).qf();
        }
        ConfigGroup<T> group = new ConfigGroup<>(type);
        group.init(this);
        this._groups.put(type, group);
        for (T config : configs) {
            group.a(config, false);
        }
        return configs.length;
    }
}
