package com.example.myhook;

import android.os.Build;
import android.content.Context;
import java.io.*;
import java.util.*;

public class DeviceProfileGenerator {
    private final File profileFile;
    private DeviceProfile profile;

    private static final String[] MODELS = {"Pixel 7","Pixel 8","Pixel 8 Pro","Galaxy S23","Xperia 1 V","OnePlus 11"};
    private static final String[] BRANDS = {"Google","Samsung","Sony","OnePlus","Xiaomi"};

    public DeviceProfileGenerator(Context ctx, String packageName, boolean shouldRandom) {
        this.profileFile = new File(ctx.getFilesDir(), "profile." + packageName + ".txt"); // .../files/profile.<pkg>.txt
        if (shouldRandom || !profileFile.exists()) {
            this.profile = generate();
            save(this.profile);
        } else {
            this.profile = load();
            if (this.profile == null) {
                this.profile = generate();
                save(this.profile);
            }
        }
    }

    public DeviceProfile getProfile() { return profile; }

    private DeviceProfile generate() {
        Random r = new Random();
        String brand = pick(BRANDS, r);
        String model = pick(MODELS, r);
        String device = "device_" + (1000 + r.nextInt(9000));
        String manufacturer = brand;

        String release = Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "14";
        String product = (brand + "_" + model).toLowerCase().replace(' ', '_');
        String buildId = String.format("UP1A.%06d.%03d", 100000 + r.nextInt(900000), 100 + r.nextInt(900));
        String incremental = String.valueOf(1000000 + r.nextInt(9000000));
        String fingerprint = String.format("%s/%s/%s:%s/%s/%s:user/release-keys",
                brand.toLowerCase(), product, device, release, buildId, incremental);

        String androidId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String imei = genImeiLuhn(r);
        String serial = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        String advertisingId = UUID.randomUUID().toString();
        boolean adLimitTracking = false;

        String fiid = UUID.randomUUID().toString();
        String appInstanceId = UUID.randomUUID().toString().replace("-", "");
        String fcmToken = UUID.randomUUID().toString().replace("-", "");

        String chromeMajor = String.valueOf(120 + r.nextInt(15));
        String chromeFull = chromeMajor + ".0." + (4100 + r.nextInt(500)) + "." + (50 + r.nextInt(200));
        String ua = String.format(
                "Mozilla/5.0 (Linux; Android %s; %s Build/%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Mobile Safari/537.36",
                release, model, buildId, chromeFull);

        return new DeviceProfile(
                model, brand, device, manufacturer, fingerprint,
                androidId, imei, serial,
                advertisingId, adLimitTracking,
                fiid, appInstanceId, fcmToken,
                ua
        );
    }

    private static String pick(String[] arr, Random r) { return arr[r.nextInt(arr.length)]; }

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
            w.write("FIREBASE_APP_INSTANCE_ID=" + p.firebaseAppInstanceId + "\n");
            w.write("FCM_TOKEN=" + p.fcmToken + "\n");
            w.write("USER_AGENT=" + p.userAgent + "\n");
        } catch (IOException e) {
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
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
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
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
