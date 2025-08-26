package com.example.myhook;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Lưu cấu hình module trong thư mục files của app.
 * Khóa đang dùng:
 *   - SHOULD_RANDOM=true/false
 */
public class ConfigManager {
    private final File configFile;
    private boolean shouldRandom = false; // mặc định

    public ConfigManager(Context ctx) {
        this.configFile = new File(ctx.getFilesDir(), "config.txt");
        load();
    }

    public synchronized void load() {
        if (!configFile.exists()) {
            save();
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int p = line.indexOf('=');
                if (p <= 0) continue;
                String k = line.substring(0, p).trim();
                String v = line.substring(p + 1).trim();
                if ("SHOULD_RANDOM".equalsIgnoreCase(k)) {
                    shouldRandom = "true".equalsIgnoreCase(v);
                }
            }
        } catch (IOException ignored) { }
    }

    public synchronized void save() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile))) {
            bw.write("# MyHookModule config\n");
            bw.write("SHOULD_RANDOM=" + shouldRandom + "\n");
        } catch (IOException ignored) { }
    }

    /** Trả về cờ random hiện tại. Nếu muốn luôn lấy mới từ file, có thể gọi load() trước khi return. */
    public synchronized boolean shouldRandom() {
        // load(); // bật nếu muốn luôn đọc lại file mỗi lần hỏi
        return shouldRandom;
    }

    /** Đặt lại cờ và lưu xuống ngay. */
    public synchronized void setShouldRandom(boolean v) {
        shouldRandom = v;
        save();
    }

    public File getConfigFile() {
        return configFile;
    }
}
