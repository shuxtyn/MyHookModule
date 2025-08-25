package com.example.myhook;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DeviceProfileGenerator {
    private final File profileFile;
    private DeviceProfile profile;

    private static final String[] BUILD_IDS = {
            "RP1A.200720.012", "RQ3A.210905.001", "SP1A.210812.016",
            "TP1A.220624.021", "TQ2A.230505.002", "TQ3A.230705.001",
            "UQ1A.231105.003", "UP1A.231005.007", "UP1A.240105.003",
            "AP1A.240505.004"
    };
    private static final int[] CHROME_MAJOR = {118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136};
    private static final String[] ALL_RELEASES = {"11","12","12L","13","14"};

    // brand -> entries
    private static Map<String, List<DeviceEntry>> DEVICE_MAP;

    private static class DeviceEntry {
        final String modelCode;   // ex: SM-G991B
        final String marketing;   // ex: Galaxy S21
        final String deviceCode;  // ex: o1s (optional)
        final String minRelease;  // optional
        final String maxRelease;  // optional
        DeviceEntry(String code, String marketing, String deviceCode, String minR, String maxR) {
            this.modelCode = code; this.marketing = marketing; this.deviceCode = deviceCode;
            this.minRelease = minR; this.maxRelease = maxR;
        }
    }

    public DeviceProfileGenerator(Context ctx, String packageName, boolean shouldRandom) {
        this.profileFile = new File(ctx.getFilesDir(), "profile." + packageName + ".txt");
        ensureDevicesLoaded(ctx);
        if (shouldRandom || !profileFile.exists()) {
            this.profile = generate();
            save(this.profile);
        } else {
            this.profile = load();
            if (this.profile == null) { this.profile = generate(); save(this.profile); }
        }
    }

    public DeviceProfile getProfile() { return profile; }

    // ===== load JSON =====
    private static synchronized void ensureDevicesLoaded(Context ctx) {
        if (DEVICE_MAP != null) return;
        DEVICE_MAP = new LinkedHashMap<>();
        try (InputStream is = ctx.getAssets().open("devices.json");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            JSONObject root = new JSONObject(sb.toString());

            Iterator<String> brands = root.keys();
            while (brands.hasNext()) {
                String brand = brands.next();
                JSONArray arr = root.getJSONArray(brand);
                List<DeviceEntry> list = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONObject) {
                        JSONObject o = (JSONObject) item;
                        String code  = o.optString("modelCode",  o.optString("model", "Generic"));
                        String mk    = o.optString("marketing", code);
                        String dev   = o.optString("deviceCode", null);
                        String minR  = o.optString("minRelease", null);
                        String maxR  = o.optString("maxRelease", null);
                        list.add(new DeviceEntry(code, mk, dev, minR, maxR));
                    } else {
                        String code = String.valueOf(item);
                        list.add(new DeviceEntry(code, code, null, null, null));
                    }
                }
                if (!list.isEmpty()) DEVICE_MAP.put(brand, list);
            }
        } catch (Throwable t) {
            DEVICE_MAP.put("Samsung", Arrays.asList(
                    new DeviceEntry("SM-G991B", "Galaxy S21", "o1s", "11","13")
            ));
        }
    }

    // ===== generate =====
    private DeviceProfile generate() {
        Random r = new Random();

        List<String> brands = new ArrayList<>(DEVICE_MAP.keySet());
        String brand = brands.get(r.nextInt(brands.size()));
        List<DeviceEntry> entries = DEVICE_MAP.get(brand);
        DeviceEntry e = entries.get(r.nextInt(entries.size()));

        String release = pickReleaseWithin(e.minRelease, e.maxRelease, r);

        // Nếu có deviceCode trong JSON, dùng trực tiếp. Nếu không có -> sinh hợp lệ.
        String device = (e.deviceCode != null && !e.deviceCode.isEmpty())
                ? e.deviceCode
                : toDeviceCode(e.modelCode, brand, r);

        String manufacturer = brand;
        String buildId = pick(BUILD_IDS, r);
        String incremental = String.valueOf(1000000 + r.nextInt(9000000));
        String product = (brand + "_" + e.modelCode).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_");

        String fingerprint = String.format(
                "%s/%s/%s:%s/%s/%s:user/release-keys",
                brand.toLowerCase(Locale.US), product, device, release, buildId, incremental
        );

        // IDs
        String androidId = uuid16();
        String imei = genImeiLuhn(r);
        String serial = UUID.randomUUID().toString().replace("-", "").substring(0,12).toUpperCase(Locale.US);

        String advertisingId = UUID.randomUUID().toString();
        boolean adLimitTracking = r.nextBoolean();

        String fiid = UUID.randomUUID().toString();
        String appInstanceId = UUID.randomUUID().toString().replace("-", "");
        String fcmToken = UUID.randomUUID().toString().replace("-", "");

        String ua = makeDynamicUA(release, e.marketing, buildId, r);

        return new DeviceProfile(
                e.modelCode,       // model (Build.MODEL)
                brand,             // brand
                device,            // device (Build.DEVICE)
                manufacturer,      // manufacturer
                fingerprint,
                androidId, imei, serial,
                advertisingId, adLimitTracking,
                fiid, appInstanceId, fcmToken,
                ua,
                e.marketing        // marketingName
        );
    }

    // ===== helpers =====
    private static String uuid16() { return UUID.randomUUID().toString().replace("-", "").substring(0,16); }

    private static String toDeviceCode(String modelCode, String brand, Random r) {
        String base = (brand + "_" + modelCode).toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return base + "_" + (100 + r.nextInt(900));
    }

    private static String makeDynamicUA(String androidRelease, String marketing, String buildId, Random r) {
        int major = CHROME_MAJOR[r.nextInt(CHROME_MAJOR.length)];
        int buildA = 4000 + r.nextInt(1200);
        int buildB = 50 + r.nextInt(300);
        return String.format(
                Locale.US,
                "Mozilla/5.0 (Linux; Android %s; %s Build/%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%d.0.%d.%d Mobile Safari/537.36",
                androidRelease, marketing, buildId, major, buildA, buildB
        );
    }

    private static String pickReleaseWithin(String minR, String maxR, Random r) {
        List<String> ordered = Arrays.asList(ALL_RELEASES);
        int lo = (minR == null) ? 0 : Math.max(0, ordered.indexOf(minR));
        int hi = (maxR == null) ? ordered.size()-1 : Math.max(lo, ordered.indexOf(maxR));
        if (lo < 0) lo = 0; if (hi < 0) hi = ordered.size()-1;
        return ordered.get(lo + r.nextInt(hi - lo + 1));
    }

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
            w.write("MARKETING=" + p.marketingName + "\n");
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
        } catch (IOException ignored) {}
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
                    ua,
                    m.getOrDefault("MARKETING", m.getOrDefault("MODEL", "Generic"))
            );
        } catch (IOException e) {
            return null;
        }
    }
}
