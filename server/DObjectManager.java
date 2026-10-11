package server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.threerings.presents.dobj.DObject;

public class DObjectManager {
    private final Map<Integer, DObject> registry = new ConcurrentHashMap<>();

    public void register(DObject obj) {
        if (obj != null) {
            registry.put(obj.xK(), obj);
        }
    }

    public void registerIfAbsent(DObject obj) {
        if (obj != null) {
            registry.putIfAbsent(obj.xK(), obj);
        }
    }

    public void unregister(int oid) {
        registry.remove(oid);
    }

    public DObject get(int oid) {
        return registry.get(oid);
    }

    public boolean contains(int oid) {
        return registry.containsKey(oid);
    }
}
