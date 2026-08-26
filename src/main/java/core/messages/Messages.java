package core.messages;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Centralised message manager. Loads messages from three properties files
 * (one per category) and formats them with {@link MessageFormat} parameters.
 *
 * <p>Categories:
 * <ul>
 *   <li>{@link Category#CONSOLE} — diagnostic/log output written to stdout/stderr</li>
 *   <li>{@link Category#GUI} — status messages shown in the GUI status bar</li>
 *   <li>{@link Category#SERVER} — feedback sent to the connected Minecraft client
 *       (boss bar / action bar)</li>
 * </ul>
 *
 * <p>Message keys are dot-separated, prefixed by category, e.g.
 * {@code server.selection.enabled}, {@code gui.auth.getting_details},
 * {@code console.chunk.parse_error}.
 */
public final class Messages {
    private static final Map<Category, Properties> BUNDLES = new EnumMap<>(Category.class);

    static {
        for (Category cat : Category.values()) {
            Properties props = new Properties();
            String file = "messages-" + cat.name().toLowerCase() + ".properties";
            try (InputStream in = Messages.class.getClassLoader().getResourceAsStream(file)) {
                if (in != null) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                // fall through — missing bundle means keys resolve to themselves
            }
            BUNDLES.put(cat, props);
        }
    }

    private Messages() { }

    /**
     * Resolve a message key for the given category with no parameters.
     * If the key is not found, the key itself is returned (fail-safe).
     */
    public static String get(Category category, String key) {
        return get(category, key, (Object[]) null);
    }

    /**
     * Resolve a message key for the given category, substituting parameters
     * via {@link MessageFormat}. If the key is not found, the key itself is
     * returned (fail-safe).
     *
     * @param category the message category
     * @param key      the dot-separated message key
     * @param args     parameters for {@code {0}}, {@code {1}}, … placeholders
     */
    public static String get(Category category, String key, Object... args) {
        Properties props = BUNDLES.get(category);
        String template = props != null ? props.getProperty(key) : null;
        if (template == null) {
            return key;
        }
        if (template.isEmpty()) {
            return "";
        }
        if (args == null || args.length == 0) {
            return template;
        }
        return MessageFormat.format(template, args);
    }

    /** Convenience for {@link Category#CONSOLE}. */
    public static String console(String key) {
        return get(Category.CONSOLE, key);
    }

    /** Convenience for {@link Category#CONSOLE} with parameters. */
    public static String console(String key, Object... args) {
        return get(Category.CONSOLE, key, args);
    }

    /** Convenience for {@link Category#GUI}. */
    public static String gui(String key) {
        return get(Category.GUI, key);
    }

    /** Convenience for {@link Category#GUI} with parameters. */
    public static String gui(String key, Object... args) {
        return get(Category.GUI, key, args);
    }

    /** Convenience for {@link Category#SERVER}. */
    public static String server(String key) {
        return get(Category.SERVER, key);
    }

    /** Convenience for {@link Category#SERVER} with parameters. */
    public static String server(String key, Object... args) {
        return get(Category.SERVER, key, args);
    }

    /**
     * Returns the GUI messages as a {@link ResourceBundle} for use with
     * {@link javafx.fxml.FXMLLoader#setResources(ResourceBundle)}.
     * FXML files can then use {@code %gui.key} syntax for translatable text.
     */
    public static ResourceBundle guiBundle() {
        return new ResourceBundle() {
            private final Properties props = BUNDLES.get(Category.GUI);

            @Override
            protected Object handleGetObject(String key) {
                return props != null ? props.getProperty(key) : null;
            }

            @Override
            public java.util.Enumeration<String> getKeys() {
                if (props == null) {
                    return java.util.Collections.emptyEnumeration();
                }
                return java.util.Collections.enumeration(
                    props.stringPropertyNames());
            }
        };
    }

    /** Message category — determines which properties file is consulted. */
    public enum Category {
        CONSOLE,
        GUI,
        SERVER
    }
}
