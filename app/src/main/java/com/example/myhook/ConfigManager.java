package com.example.myhook;

import java.io.*;

public class ConfigManager {
    private final File configFile;
    private boolean shouldRandom = false;

    public ConfigManager(Context ctx) {
        // chọn internal
        this.configFile = new File(ctx.getFilesDir(), "config.txt");
        load();
    }

    // ... phần load(), save() giữ nguyên ...
}

    public ConfigManager() { load(); }

    private void load() {
        if (!configFile.exists()) { save(); return; }
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim().toUpperCase();
                if (s.startsWith("SHOULD_RANDOM=")) shouldRandom = s.endsWith("TRUE");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void save() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile))) {
            bw.write("SHOULD_RANDOM=" + shouldRandom + "\n");
        } catch (IOException e) { e.printStackTrace(); }
    }

    public boolean shouldRandom() { return shouldRandom; }
    public void setShouldRandom(boolean v) { shouldRandom = v; save(); }
}
