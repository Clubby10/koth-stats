package com.lucasdunn.kothstats.capture;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class KoreConfigImporter {
    private static final int MAX_DIRECTORY_DEPTH = 8;
    private static final int MAX_YAML_DEPTH = 16;
    private static final int MAX_FILES = 32;
    private static final long MAX_FILE_BYTES = 1024L * 1024L;
    private final JavaPlugin plugin;
    private final Set<String> visitedDirectories = new HashSet<String>();
    private File pluginsRoot;
    private int inspectedFiles;

    public KoreConfigImporter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<KoreKothDefinition> load() {
        Map<String, KoreKothDefinition> definitions =
            new LinkedHashMap<String, KoreKothDefinition>();
        visitedDirectories.clear();
        inspectedFiles = 0;
        File source = resolveSource();
        if (source == null || !source.exists()) {
            plugin.getLogger().warning("Could not find the configured FactionsKore data path.");
            return new ArrayList<KoreKothDefinition>();
        }

        if (source.isFile()) {
            inspect(source, definitions);
        } else {
            scan(source, definitions, 0);
        }
        return new ArrayList<KoreKothDefinition>(definitions.values());
    }

    private File resolveSource() {
        String explicit = plugin.getConfig().getString("kore-config-import.config-path", "");
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        try {
            pluginsRoot = pluginsFolder.getCanonicalFile();
            File configured;
            if (explicit != null && !explicit.trim().isEmpty()) {
                File path = new File(explicit.trim());
                if (path.isAbsolute()) {
                    plugin.getLogger().warning(
                        "Absolute kore-config-import.config-path values are not allowed.");
                    return null;
                }
                configured = new File(pluginsRoot, explicit.trim());
            } else {
                String folder = plugin.getConfig().getString(
                    "kore-config-import.plugin-folder", "FactionsKore");
                configured = new File(pluginsRoot, folder);
            }
            File canonical = configured.getCanonicalFile();
            if (!isInsidePlugins(canonical)) {
                plugin.getLogger().warning(
                    "Rejected FactionsKore config path outside the plugins directory.");
                return null;
            }
            return canonical;
        } catch (IOException exception) {
            plugin.getLogger().warning(
                "Could not resolve the FactionsKore config path: " + exception.getMessage());
            return null;
        }
    }

    private void scan(File directory, Map<String, KoreKothDefinition> definitions, int depth) {
        if (depth > MAX_DIRECTORY_DEPTH || inspectedFiles >= MAX_FILES) {
            return;
        }
        try {
            File canonical = directory.getCanonicalFile();
            if (!isInsidePlugins(canonical)
                || !visitedDirectories.add(canonical.getPath())) {
                return;
            }
        } catch (IOException exception) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scan(file, definitions, depth + 1);
            } else if (inspectedFiles < MAX_FILES && isKothYaml(file)) {
                inspect(file, definitions);
            }
        }
    }

    private boolean isKothYaml(File file) {
        String name = file.getName().toLowerCase(Locale.ENGLISH);
        return (name.endsWith(".yml") || name.endsWith(".yaml")) && name.contains("koth");
    }

    private void inspect(File file, Map<String, KoreKothDefinition> definitions) {
        try {
            File canonical = file.getCanonicalFile();
            if (!isInsidePlugins(canonical) || !canonical.isFile()
                || canonical.length() > MAX_FILE_BYTES) {
                plugin.getLogger().warning("Skipped unsafe or oversized KOTH config: "
                    + file.getName());
                return;
            }
            inspectedFiles++;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(canonical);
            int before = definitions.size();
            findDefinitions(yaml, definitions, 0);
            int found = definitions.size() - before;
            if (found > 0) {
                plugin.getLogger().info("Imported " + found + " KOTH region(s) from "
                    + canonical.getName());
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not inspect KOTH config " + file.getName());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Skipped malformed KOTH config " + file.getName());
        }
    }

    private void findDefinitions(ConfigurationSection section,
                                 Map<String, KoreKothDefinition> definitions, int depth) {
        if (depth > MAX_YAML_DEPTH || definitions.size() >= 256) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            String first = child.getString("corner-one");
            String second = child.getString("corner-two");
            if (first != null && second != null) {
                String normalized = key.toLowerCase(Locale.ENGLISH);
                if (!definitions.containsKey(normalized)) {
                    definitions.put(normalized,
                        new KoreKothDefinition(key, first, second));
                }
            }
            findDefinitions(child, definitions, depth + 1);
        }
    }

    private boolean isInsidePlugins(File candidate) throws IOException {
        if (pluginsRoot == null) {
            return false;
        }
        String root = pluginsRoot.getCanonicalPath();
        String path = candidate.getCanonicalPath();
        return path.equals(root) || path.startsWith(root + File.separator);
    }
}
