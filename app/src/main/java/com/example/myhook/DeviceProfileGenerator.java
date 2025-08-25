package com.example.myhook;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * DeviceProfileGenerator (assets-backed)
 * - Đọc {brand: [models...]} từ assets/devices.json
 * - Random fingerprint/ID/UA đa dạng
 * - Lưu/đọc profile tại internal storage: <filesDir>/profile.<package>.txt
 */
public class DeviceProfileGenerator {
    private final File profileFile;
    private DeviceProfile profile;

    private static final String[] ANDROID_RELEASES = { "11", "12", "12L", "13", "14" };

    private static final String[] BUILD_IDS = {
            "RP1A.200720.012", "RQ3A.210905.001", "SP1A.210812.016",
            "TP1A.220624.021", "TQ2A.230505.002", "TQ3A.230705.001",
            "UQ1A.231105.003", "UP1A.231005.007", "UP1A.240105.003",
            "AP1A.240505.004"
    };

    private static final int[] CHROME_MAJOR = {
            118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136
    };

    private static final String[] UA_SAMPLES = {
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.82 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.74 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 12; M2102K1G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.85 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 11; OnePlus11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.5993.90 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14; Xperia 1 V) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.6789.45 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; ELS-NX9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.6677.12 Mobile Safari/537.36"
    };

    // Nạp từ assets/devices.json: brand -> models
    private static Map<String, List<String>> DEVICE_MAP;

    public DeviceProfileGenerator(Context ctx, String packageName, boolean shouldRandom) {
        this.profileFile = new File(ctx.getFilesDir(), "profile." + packageName + ".txt");
        ensureDevicesLoaded(ctx);

        if (shouldRandom || !profileFile.exists()) {
            this.profile = generate(ctx);
            save(this.profile);
        } else {
            this.profile = load();
            if (this.profile == null) { this.profile = generate(ctx); save(this.profile); }
        }
    }

    public DeviceProfile getProfile() { return profile; }

    // ===== Dataset loader =====
    private static synchronized void ensureDevicesLoaded(Context ctx) {
        if (DEVICE_MAP != null) return;
        DEVICE_MAP = new LinkedHashMap<>();
        try (InputStream is = ctx.getAssets().open("devices.json");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            JSONObject root = new JSONObject(sb.toString());

            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String brand = keys.next();
                JSONArray arr = root.getJSONArray(brand);
                List<String> list = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
                if (!list.isEmpty()) DEVICE_MAP.put(brand, list);
            }
        } catch (Throwable t) {
            // Fallback nhỏ nếu thiếu assets
            DEVICE_MAP.put("Google", Arrays.asList("Pixel 7", "Pixel 8", "Pixel 8 Pro"));
            DEVICE_MAP.put("Samsung", Arrays.asList("SM-S911B Galaxy S23", "SM-S918B Galaxy S23 Ultra"));
            DEVICE_MAP.put("Xiaomi", Arrays.asList("Xiaomi 12", "Xiaomi 13"));
        }
    }

    // ===== Generator =====
    private DeviceProfile generate(Context ctx) {
        Random r = new Random();

        List<String> brands = new ArrayList<>(DEVICE_MAP.keySet());
        String brand = pick(brands, r);
        List<String> models = DEVICE_MAP.getOrDefault(brand, Collections.singletonList("GenericPhone"));
        String model = pick(models, r);

        String device = toDeviceCode(model, brand, r);
        String manufacturer = brand;

        String release = pick(ANDROID_RELEASES, r);
        String buildId = pick(BUILD_IDS, r);
        String incremental = String.valueOf(1000000 + r.nextInt(9000000));
        String product = (brand + "_" + model)
                .toLowerCase(Locale.US)
                .replace(" ", "_")
                .replace("-", "_");

        String fingerprint = String.format(
                "%s/%s/%s:%s/%s/%s:user/release-keys",
                brand.toLowerCase(Locale.US), product, device, release, buildId, incremental
        );

        String androidId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String imei = genImeiLuhn(r);
        String serial = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.US);

        String advertisingId = UUID.randomUUID().toString();
        boolean adLimitTracking = r.nextBoolean();

        String fiid = UUID.randomUUID().toString();
        String appInstanceId = UUID.randomUUID().toString().replace("-", "");
        String fcmToken = UUID.randomUUID().toString().replace("-", "");

        String ua = r.nextBoolean() ? makeDynamicUA(release, model, buildId, r) : pick(UA_SAMPLES, r);

        return new DeviceProfile(
                model, brand, device, manufacturer, fingerprint,
                androidId, imei, serial,
                advertisingId, adLimitTracking,
                fiid, appInstanceId, fcmToken,
                ua
        );
    }

    // ===== Helpers =====
    private static String toDeviceCode(String model, String brand, Random r) {
        String base = (brand + "_" + model)
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return base + "_" + (100 + r.nextInt(900));
    }

    private static String makeDynamicUA(String androidRelease, String model, String buildId, Random r) {
        int major = pick(CHROME_MAJOR, r);
        int buildA = 4000 + r.nextInt(1200);
        int buildB = 50 + r.nextInt(300);
        return String.format(
                Locale.US,
                "Mozilla/5.0 (Linux; Android %s; %s Build/%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%d.0.%d.%d Mobile Safari/537.36",
                androidRelease, model, buildId, major, buildA, buildB
        );
    }

    private static <T> T pick(List<T> list, Random r) { return list.get(r.nextInt(list.size())); }
    private static String pick(String[] arr, Random r) { return arr[r.nextInt(arr.length)]; }
    private static int pick(int[] arr, Random r) { return arr[r.nextInt(arr.length)]; }

    private static String genImeiLuhn(Random r) {
        int[] d = new int[15];
        for (int i = 0; i < 14; i++) d[i] = r.nextInt(10);
        d[14] = luhnDigit(d, 14);
        StringBuilder sb = new StringBuilder(15);
        for (int x : d) sb.append(x);
        return sb.toString();
    }

    private static int luhnDigit(int[] digits, int len) {
        int sum = 0;
        for (int i = 0; i < len; i++) {
            int v = digits[len - 1 - i];
            if (i % 2 == 0) { v *= 2; if (v > 9) v -= 9; }
            sum += v;
        }
        return (10 - (sum % 10)) % 10;
    }

    private void save(DeviceProfile p) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(profileFile))) {
            w.write("MODEL=" + p.model + "\n");
            w.write("BRAND=" + p.brand + "\n");
            w.write("DEVICE=" + p.device + "\n");
            w.write("MANUFACTURER=" + p.manufacturer + "\n");
            w.write("FINGERPRINT=" + p.fingerprint + "\n");
            w.write("ANDROID_ID=" + p.androidId + "\n");
            w.write("IMEI=" + p.imei + "\n");
            w.write("SERIAL=" + p.serial + "\n");
            w.write("ADVERTISING_ID=" + p.advertisingId + "\n");
            w.write("AD_LIMITED=" + p.adLimitTracking + "\n");
            w.write("FIREBASE_INSTALLATIONS_ID=" + p.firebaseInstallationsId + "\n");
            w.write("FIREBASE_APP_INSTANCE_ID=" + p.appInstanceId + "\n");
            w.write("FCM_TOKEN=" + p.fcmToken + "\n");
            w.write("USER_AGENT=" + p.userAgent + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private DeviceProfile load() {
        try (BufferedReader r = new BufferedReader(new FileReader(profileFile))) {
            Map<String,String> m = new HashMap<>();
            String line;
            while ((line = r.readLine()) != null) {
                String[] kv = line.split("=", 2);
                if (kv.length == 2) m.put(kv[0], kv[1]);
            }
            String ua = m.getOrDefault("USER_AGENT",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36");
            return new DeviceProfile(
                    m.get("MODEL"), m.get("BRAND"), m.get("DEVICE"),
                    m.get("MANUFACTURER"), m.get("FINGERPRINT"),
                    m.get("ANDROID_ID"), m.get("IMEI"), m.get("SERIAL"),
                    m.getOrDefault("ADVERTISING_ID", UUID.randomUUID().toString()),
                    "true".equalsIgnoreCase(m.getOrDefault("AD_LIMITED","false")),
                    m.getOrDefault("FIREBASE_INSTALLATIONS_ID", UUID.randomUUID().toString()),
                    m.getOrDefault("FIREBASE_APP_INSTANCE_ID", UUID.randomUUID().toString().replace("-", "")),
                    m.getOrDefault("FCM_TOKEN", UUID.randomUUID().toString().replace("-", "")),
                    ua
            );
        } catch (Exception e) {
            return null;
        }
    }
}
