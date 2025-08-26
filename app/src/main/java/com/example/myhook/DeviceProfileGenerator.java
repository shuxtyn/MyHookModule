package com.example.myhook;

import android.content.Context;

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
        final String modelCode;    // Build.MODEL
        final String marketing;    // marketing name
        final String deviceCode;   // Build.DEVICE
        final String vendorProduct;// ro.build.product / ro.vendor.product.oem
        final String vendorDevice; // ro.vendor.product.device.oem
        final String minRelease;
        final String maxRelease;
        DeviceEntry(String code, String mk, String dev, String vProd, String vDev, String minR, String maxR) {
            this.modelCode = code; this.marketing = mk; this.deviceCode = dev;
            this.vendorProduct = vProd; this.vendorDevice = vDev;
            this.minRelease = minR; this.maxRelease = maxR;
        }
    }

    public DeviceProfileGenerator(Context ctx, String packageName, boolean shouldRandom) {
        this.profileFile = new File(ctx.getFilesDir(), "profile." + packageName + ".txt");
        ensureDevicesLoaded(); // <— KHÔNG dùng assets nữa

        if (shouldRandom) {
            // Luôn tạo mới & ghi đè
            this.profile = generate();
            saveAtomic(this.profile);
        } else {
            if (profileFile.exists()) {
                this.profile = load();
                if (this.profile == null) {
                    this.profile = generate();
                    saveAtomic(this.profile);
                }
            } else {
                this.profile = generate();
                saveAtomic(this.profile);
            }
        }
    }

    public DeviceProfile getProfile() { return profile; }

    /**
     * KHÔNG đọc assets. Dữ liệu nhúng sẵn trong code:
     * - Anchor thật-dạng vài mẫu phổ biến cho mỗi hãng
     * - Mở rộng synthetic để tăng độ đa dạng
     */
    private static synchronized void ensureDevicesLoaded() {
        if (DEVICE_MAP != null) return;
        DEVICE_MAP = new LinkedHashMap<>();

        // ======== 1) Anchors (một số mẫu phổ biến) ========
        addEntry("Samsung", "SM-G991B", "Galaxy S21",       "o1s",      "G991BXX", "G991BXX", "11", "13");
        addEntry("Samsung", "SM-S928B", "Galaxy S24 Ultra", "e3q",      "S928BXX", "S928BXX", "14", "14");
        addEntry("Samsung", "SM-A546B", "Galaxy A54 5G",    "a54x",     "A546BXX", "A546BXX", "13", "14");

        addEntry("Google",  "Pixel 6",  "Pixel 6",          "oriole",   "oriole",  "oriole",  "12", "14");
        addEntry("Google",  "Pixel 7",  "Pixel 7",          "panther",  "panther", "panther", "13", "14");
        addEntry("Google",  "Pixel 8 Pro","Pixel 8 Pro",    "husky",    "husky",   "husky",   "14", "14");

        addEntry("Xiaomi",  "2201123G","Xiaomi 12",         "cupid",    "cupid",   "cupid",   "12", "14");
        addEntry("Xiaomi",  "23013PC75G","Xiaomi 13",       "fuxi",     "fuxi",    "fuxi",    "13", "14");
        addEntry("Xiaomi",  "2304FPN6DG","Redmi Note 12 Pro","ruby",    "ruby",    "ruby",    "12", "14");

        addEntry("OnePlus", "LE2123",  "OnePlus 9 Pro",     "lemonadep","lemonadep","lemonadep","11","13");
        addEntry("OnePlus", "CPH2581", "OnePlus 12",        "aurora",   "aurora",  "aurora",  "14","14");

        addEntry("Oppo",    "PGEM10",  "Oppo Find X5 Pro",  "taro",     "taro",    "taro",    "12","14");
        addEntry("Oppo",    "PHY110",  "Oppo Find X7 Ultra","OP565FL1", "PHY110",  "OP565FL1","14","14");

        addEntry("Realme",  "RMX3301","Realme GT2 Pro",     "taro",     "taro",    "taro",    "12","14");
        addEntry("Vivo",    "V2227A", "Vivo X90 Pro",       "taro",     "taro",    "taro",    "13","14");

        addEntry("Huawei",  "ANA-NX9","Huawei P40",         "ANA",      "ANA",     "ANA",     "10","12");
        addEntry("Huawei",  "ELS-NX9","Huawei P40 Pro",     "ELS",      "ELS",     "ELS",     "10","12");

        addEntry("Motorola","XT2321-1","Moto G84",          "bangkok",  "bangkok", "bangkok", "13","14");

        addEntry("ASUS",    "ASUS_AI2301","ROG Phone 7",    "x20",      "x20",     "x20",     "13","14");

        addEntry("Sony",    "XQ-CT72","Xperia 1 IV",        "pdx223",   "pdx223",  "pdx223",  "12","14");

        addEntry("Nokia",   "TA-1462","Nokia X30 5G",       "Dragon",   "Dragon",  "Dragon",  "12","14");

        addEntry("Nothing", "A065",   "Nothing Phone (2)",  "pong",     "pong",    "pong",    "13","14");

        addEntry("Honor",   "PGT-AN10","Honor Magic6 Pro",  "PGT",      "PGT",     "PGT",     "14","14");

        // ======== 2) Synthetic expansion (nhiều hãng) ========
        // Tạo thêm entries theo quy luật để tăng phong phú mà không cần file ngoài
        expandBrand("Samsung", "SM-A", "Galaxy A", "a", 30, "10","14");
        expandBrand("Samsung", "SM-M", "Galaxy M", "m", 20, "11","14");
        expandBrand("Samsung", "SM-F", "Galaxy Z", "q", 10, "12","14");

        expandBrand("Google",  "GW",    "Pixel",    "sh", 8,  "11","14");
        expandBrand("Xiaomi",  "220",   "Xiaomi",   "xm", 20, "11","14");
        expandBrand("Xiaomi",  "230",   "Redmi",    "rm", 20, "11","14");

        expandBrand("OnePlus", "CPH",   "OnePlus",  "op", 12, "11","14");
        expandBrand("Oppo",    "CPH",   "Oppo Reno","oppo",20, "11","14");
        expandBrand("Realme",  "RMX",   "Realme",   "real",15, "12","14");
        expandBrand("Vivo",    "V",     "Vivo",     "vivo",15, "11","14");

        expandBrand("Huawei",  "ANG-",  "Huawei",   "hwa", 8, "10","12");
        expandBrand("Motorola","XT",    "Moto",     "moto",10, "11","14");
        expandBrand("ASUS",    "ASUS_AI","ROG Phone","rog",8,"11","14");
        expandBrand("Sony",    "XQ-",   "Xperia",   "pdx", 8, "10","14");
        expandBrand("Nokia",   "TA-",   "Nokia",    "nokia",8,"11","14");
        expandBrand("Nothing", "A0",    "Nothing Phone","nothing",5,"12","14");
        expandBrand("Honor",   "PGT-",  "Honor",    "honor",10,"11","14");

        // Log tóm tắt (nếu chạy trong LSPosed sẽ thấy)
        try {
            int modelCount = 0;
            for (List<DeviceEntry> v : DEVICE_MAP.values()) modelCount += v.size();
            de.robv.android.xposed.XposedBridge.log("[MyHook] built-in device table: brands=" + DEVICE_MAP.size() + ", models=" + modelCount);
        } catch (Throwable ignored) {}
    }

    /** Thêm 1 entry vào DEVICE_MAP */
    private static void addEntry(String brand, String modelCode, String marketing,
                                 String deviceCode, String vendorProduct, String vendorDevice,
                                 String minR, String maxR) {
        List<DeviceEntry> list = DEVICE_MAP.get(brand);
        if (list == null) {
            list = new ArrayList<>();
            DEVICE_MAP.put(brand, list);
        }
        list.add(new DeviceEntry(modelCode, marketing, deviceCode, vendorProduct, vendorDevice, minR, maxR));
    }

    /** Tạo thêm nhiều mẫu “giả lập hợp lý” để random đa dạng */
    private static void expandBrand(String brand, String basePrefix, String marketingPrefix,
                                    String devicePrefix, int count, String minR, String maxR) {
        Random rr = new Random(brand.hashCode() ^ basePrefix.hashCode() ^ devicePrefix.hashCode() ^ count);
        for (int i = 0; i < count; i++) {
            String suffix3 = String.format(Locale.US, "%03d", rr.nextInt(900) + 100);
            String modelCode = basePrefix + suffix3;
            String mkTier = pick(Arrays.asList("", " Pro", " Plus", " Ultra", " 5G"), rr);
            String marketing = marketingPrefix + mkTier;
            String devVar = pick(Arrays.asList("", "_pro", "_plus", "_ultra"), rr);
            String dev = devicePrefix + devVar;

            addEntry(brand, modelCode, marketing, dev, dev, dev, minR, maxR);
        }
    }

    private static <T> T pick(List<T> list, Random r) {
        return list.get(r.nextInt(list.size()));
    }

    private DeviceProfile generate() {
        Random r = new Random();

        List<String> brands = new ArrayList<>(DEVICE_MAP.keySet());
        String brand = brands.get(r.nextInt(brands.size()));
        List<DeviceEntry> entries = DEVICE_MAP.get(brand);
        DeviceEntry e = entries.get(r.nextInt(entries.size()));

        String release = pickReleaseWithin(e.minRelease, e.maxRelease, r);

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
                e.modelCode,       // Build.MODEL
                brand,
                device,            // Build.DEVICE
                manufacturer,
                fingerprint,
                androidId, imei, serial,
                advertisingId, adLimitTracking,
                fiid, appInstanceId, fcmToken,
                ua,
                e.marketing,       // marketingName
                e.vendorProduct,   // vendorProduct
                e.vendorDevice,    // vendorDevice
                buildId,           // buildId
                incremental        // buildIncremental
        );
    }

    // ===== helpers =====
    private static String uuid16() { return UUID.randomUUID().toString().replace("-", "").substring(0,16); }

    private static String toDeviceCode(String modelCode, String brand, Random r) {
        String base = (brand + "_" + modelCode).toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return base + "_" + (100 + r.nextInt(900));
    }

    private static String makeDynamicUA(String androidRelease, String marketing, String buildId, Random r) {
        int major = CHROME_MAJOR[r.nextInt(CHROME_MAJOR.length)];
        int buildA = 4000 + r.nextInt(1200);
        int buildB = 50 + r.nextInt(300);
        return String.format(Locale.US,
                "Mozilla/5.0 (Linux; Android %s; %s Build/%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%d.0.%d.%d Mobile Safari/537.36",
                androidRelease, marketing, buildId, major, buildA, buildB);
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
        int sum = 0;
        for (int i = 0; i < 14; i++) { int v = d[13 - i]; if (i % 2 == 0) { v *= 2; if (v > 9) v -= 9; } sum += v; }
        d[14] = (10 - (sum % 10)) % 10;
        StringBuilder sb = new StringBuilder(15);
        for (int x : d) sb.append(x);
        return sb.toString();
    }

    private static String pick(String[] arr, Random r) {
        return arr[r.nextInt(arr.length)];
    }

    // --- Ghi đè an toàn bằng file tạm rồi rename ---
    private void saveAtomic(DeviceProfile p) {
        File dir = profileFile.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        File tmp = new File(dir, profileFile.getName() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(tmp))) {
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
            w.write("VENDOR_PRODUCT=" + (p.vendorProduct == null ? "" : p.vendorProduct) + "\n");
            w.write("VENDOR_DEVICE=" + (p.vendorDevice  == null ? "" : p.vendorDevice ) + "\n");
            w.write("BUILD_ID=" + p.buildId + "\n");
            w.write("BUILD_INCREMENTAL=" + p.buildIncremental + "\n");
            w.write("USER_AGENT=" + p.userAgent + "\n");
        } catch (IOException e) {
            save(p);
            return;
        }
        if (!tmp.renameTo(profileFile)) {
            //noinspection ResultOfMethodCallIgnored
            profileFile.delete();
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(profileFile);
        }
    }

    // Fallback save
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
            w.write("VENDOR_PRODUCT=" + (p.vendorProduct == null ? "" : p.vendorProduct) + "\n");
            w.write("VENDOR_DEVICE=" + (p.vendorDevice  == null ? "" : p.vendorDevice ) + "\n");
            w.write("BUILD_ID=" + p.buildId + "\n");
            w.write("BUILD_INCREMENTAL=" + p.buildIncremental + "\n");
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
            String ua = m.getOrDefault(
                    "USER_AGENT",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
            );

            return new DeviceProfile(
                    m.get("MODEL"),
                    m.get("BRAND"),
                    m.get("DEVICE"),
                    m.get("MANUFACTURER"),
                    m.get("FINGERPRINT"),
                    m.get("ANDROID_ID"),
                    m.get("IMEI"),
                    m.get("SERIAL"),
                    m.getOrDefault("ADVERTISING_ID", java.util.UUID.randomUUID().toString()),
                    "true".equalsIgnoreCase(m.getOrDefault("AD_LIMITED","false")),
                    m.getOrDefault("FIREBASE_INSTALLATIONS_ID", java.util.UUID.randomUUID().toString()),
                    m.getOrDefault("FIREBASE_APP_INSTANCE_ID", java.util.UUID.randomUUID().toString().replace("-", "")),
                    m.getOrDefault("FCM_TOKEN", java.util.UUID.randomUUID().toString().replace("-", "")),
                    ua,
                    m.getOrDefault("MARKETING", m.getOrDefault("MODEL", "Generic")),
                    m.get("VENDOR_PRODUCT"),
                    m.get("VENDOR_DEVICE"),
                    m.getOrDefault("BUILD_ID", "TQ3A.230705.001"),
                    m.getOrDefault("BUILD_INCREMENTAL", "1234567")
            );
        } catch (IOException e) {
            return null;
        }
    }
}
