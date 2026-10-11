package portable;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.prefs.*;

/** Keeps the private client's preferences in this copy, outside the Windows registry. */
public final class LocalPreferencesFactory implements PreferencesFactory {
    private final Preferences user = new Node(null, "", root("user"));
    private final Preferences system = new Node(null, "", root("system"));
    private static Path root(String kind) {
        return Paths.get(System.getProperty("private.preferences.dir"), kind);
    }
    public Preferences userRoot() { return user; }
    public Preferences systemRoot() { return system; }

    private static final class Node extends AbstractPreferences {
        private final Path directory;
        private final Properties values = new Properties();
        Node(Node parent, String name, Path directory) {
            super(parent, name);
            this.directory = directory;
            try { syncSpi(); } catch (BackingStoreException e) { throw new IllegalStateException(e); }
        }
        protected void putSpi(String key, String value) {
            values.setProperty(key, value);
            save();
        }
        protected String getSpi(String key) { return values.getProperty(key); }
        protected void removeSpi(String key) { values.remove(key); save(); }
        private void save() {
            try { flushSpi(); } catch (BackingStoreException e) { throw new IllegalStateException(e); }
        }
        protected String[] keysSpi() { return values.stringPropertyNames().toArray(new String[0]); }
        protected String[] childrenNamesSpi() throws BackingStoreException {
            if (!Files.isDirectory(directory)) return new String[0];
            List<String> names = new ArrayList<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                for (Path entry : entries) if (Files.isDirectory(entry)) {
                    names.add(new String(Base64.getUrlDecoder().decode(entry.getFileName().toString()), java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException | IllegalArgumentException e) { throw new BackingStoreException(e); }
            return names.toArray(new String[0]);
        }
        protected AbstractPreferences childSpi(String name) {
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new Node(this, name, directory.resolve(encoded));
        }
        protected void removeNodeSpi() throws BackingStoreException {
            try {
                Files.deleteIfExists(directory.resolve("values.properties"));
                Files.deleteIfExists(directory);
            } catch (IOException e) { throw new BackingStoreException(e); }
        }
        protected void syncSpi() throws BackingStoreException {
            Path file = directory.resolve("values.properties");
            values.clear();
            if (Files.exists(file)) try (InputStream in = Files.newInputStream(file)) {
                values.load(in);
            } catch (IOException e) { throw new BackingStoreException(e); }
        }
        protected void flushSpi() throws BackingStoreException {
            try {
                Files.createDirectories(directory);
                Path temporary = Files.createTempFile(directory, "prefs-", ".tmp");
                try {
                    try (OutputStream out = Files.newOutputStream(temporary)) {
                        values.store(out, "Private Spiral Knights preferences");
                    }
                    Files.move(temporary, directory.resolve("values.properties"), StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temporary); }
            } catch (IOException e) { throw new BackingStoreException(e); }
        }
    }
}
