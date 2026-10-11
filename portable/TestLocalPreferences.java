package portable;
import java.util.prefs.Preferences;
public final class TestLocalPreferences {
    public static void main(String[] args) throws Exception {
        Preferences node = Preferences.userRoot().node("copy-isolation-test/child");
        if (args[0].equals("write")) {
            if (node.get("token", null) != null) throw new AssertionError("Expected fresh profile");
            node.put("token", "private-only");
            node.flush();
        } else {
            if (!"private-only".equals(node.get("token", null))) throw new AssertionError("Preferences did not persist across JVMs");
            if (Preferences.userRoot().node("copy-isolation-test").childrenNames().length != 1) throw new AssertionError("Missing child");
            node.removeNode();
            Preferences.userRoot().node("copy-isolation-test").removeNode();
        }
        System.out.println("PORTABLE PREFERENCES OK: " + args[0]);
    }
}