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

    private static String genIm
