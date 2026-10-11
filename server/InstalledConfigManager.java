package server;

import java.io.FileInputStream;
import java.util.HashMap;
import com.threerings.config.ConfigGroup;
import com.threerings.config.ConfigManager;
import com.threerings.config.DerivedConfig;
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
            Object installed = new BinaryImporter(input).qe();
            // Some frozen catalogs (notably fire_action.dat) use the newer
            // ConfigGroup.Data envelope instead of a bare typed array.
            configs = (T[])(installed instanceof ConfigGroup.Data
                ? ((ConfigGroup.Data)installed).array : installed);
        }
        ConfigGroup<T> group = new ConfigGroup<>(type);
        group.init(this);
        this._groups.put(type, group);
        for (T config : configs) {
            if (!type.isInstance(config) && !(config instanceof DerivedConfig)) {
                throw new IllegalArgumentException("Unexpected config type in " + path
                    + ": " + (config == null ? "null" : config.getClass().getName()));
            }
            group.a(config, false);
        }
        return configs.length;
    }
}
