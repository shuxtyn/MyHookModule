package com.example.myhook;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Tạo hồ sơ thiết bị fake:
 * - Dùng danh sách hardcode thiết bị phổ biến (anchors)
 * - Cộng thêm mở rộng synthetic đa hãng (có trần) để random phong phú nhưng không lag
 * - SHOULD_RANDOM=true => luôn sinh profile mới + ghi đè (atomic)
 * - SHOULD_RANDOM=false => giữ profile cũ
 */
public class DeviceProfileGenerator {
    private final File profileFile;
    private DeviceProfile profile;

    // ====== cấu hình sinh dữ liệu ======
    private static final int MAX_TOTAL_MODELS = 800; // trần an toàn để tránh lag
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
        ensureDevicesLoaded(); // KHÔNG dùng assets

        if (shouldRandom) {
            this.profile = generate();
            saveAtomic(this.profile);
        } else {
            if (profileFile.exists()) {
                this.profile = load();
                if (this.profile == null) { this.profile = generate(); saveAtomic(this.profile); }
            } else {
                this.profile = generate();
                saveAtomic(this.profile);
            }
        }
    }

    public DeviceProfile getProfile() { return profile; }

    /** KHỞI TẠO BẢNG THIẾT BỊ:
     * bơm nhiều mẫu nhưng có trần để tránh lag; anchors thật-dạng + synthetic mở rộng.
     */
    private static synchronized void ensureDevicesLoaded() {
        if (DEVICE_MAP != null) return;
        DEVICE_MAP = new LinkedHashMap<>();
        int total = 0;

        // ------------------ SAMSUNG (anchors) ------------------
        total += addEntry("Samsung", "SM-G991B", "Galaxy S21", "o1s", "o1s", "G991BXX", "11", "13");
        total += addEntry("Samsung", "SM-G996B", "Galaxy S21+", "p3s", "p3s", "G996BXX", "11", "13");
        total += addEntry("Samsung", "SM-G998B", "Galaxy S21 Ultra", "p3s", "p3s", "G998BXX", "11", "13");
        total += addEntry("Samsung", "SM-S901B", "Galaxy S22", "r0s", "r0s", "S901BXX", "12", "14");
        total += addEntry("Samsung", "SM-S906B", "Galaxy S22+", "r0s", "r0s", "S906BXX", "12", "14");
        total += addEntry("Samsung", "SM-S908B", "Galaxy S22 Ultra", "r0s", "r0s", "S908BXX", "12", "14");
        total += addEntry("Samsung", "SM-S911B", "Galaxy S23", "dm1q", "dm1q", "S911BXX", "13", "14");
        total += addEntry("Samsung", "SM-S916B", "Galaxy S23+", "dm2q", "dm2q", "S916BXX", "13", "14");
        total += addEntry("Samsung", "SM-S918B", "Galaxy S23 Ultra", "dm3q", "dm3q", "S918BXX", "13", "14");
        total += addEntry("Samsung", "SM-S921B", "Galaxy S24", "e1q", "e1q", "S921BXX", "14", "14");
        total += addEntry("Samsung", "SM-S926B", "Galaxy S24+", "e2q", "e2q", "S926BXX", "14", "14");
        total += addEntry("Samsung", "SM-S928B", "Galaxy S24 Ultra", "e3q", "e3q", "S928BXX", "14", "14");
        total += addEntry("Samsung", "SM-A146B", "Galaxy A14 5G", "a14", "a14", "A146BXX", "12", "14");
        total += addEntry("Samsung", "SM-A346B", "Galaxy A34 5G", "a34x", "a34x", "A346BXX", "13", "14");
        total += addEntry("Samsung", "SM-A546B", "Galaxy A54 5G", "a54x", "a54x", "A546BXX", "13", "14");
        total += addEntry("Samsung", "SM-M336B", "Galaxy M33 5G", "m33x", "m33x", "M336BXX", "12", "14");
        total += addEntry("Samsung", "SM-F731B", "Galaxy Z Flip5", "q5", "q5", "F731BXX", "13", "14");
        total += addEntry("Samsung", "SM-F946B", "Galaxy Z Fold5", "q5", "q5", "F946BXX", "13", "14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ GOOGLE PIXEL (anchors) ------------------
        total += addEntry("Google", "Pixel 4", "Pixel 4", "flame", "flame", "flame", "10", "13");
        total += addEntry("Google", "Pixel 4 XL", "Pixel 4 XL", "coral", "coral", "coral", "10", "13");
        total += addEntry("Google", "Pixel 5", "Pixel 5", "redfin", "redfin", "redfin", "11", "14");
        total += addEntry("Google", "Pixel 6", "Pixel 6", "oriole", "oriole", "oriole", "12", "14");
        total += addEntry("Google", "Pixel 6 Pro", "Pixel 6 Pro", "raven", "raven", "raven", "12", "14");
        total += addEntry("Google", "Pixel 6a", "Pixel 6a", "bluejay", "bluejay", "bluejay", "12", "14");
        total += addEntry("Google", "Pixel 7", "Pixel 7", "panther", "panther", "panther", "13", "14");
        total += addEntry("Google", "Pixel 7 Pro", "Pixel 7 Pro", "cheetah", "cheetah", "cheetah", "13", "14");
        total += addEntry("Google", "Pixel 7a", "Pixel 7a", "lynx", "lynx", "lynx", "13", "14");
        total += addEntry("Google", "Pixel 8", "Pixel 8", "shiba", "shiba", "shiba", "14", "14");
        total += addEntry("Google", "Pixel 8 Pro", "Pixel 8 Pro", "husky", "husky", "husky", "14", "14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ XIAOMI / REDMI / POCO (anchors) ------------------
        total += addEntry("Xiaomi", "2201123G", "Xiaomi 12", "cupid", "cupid", "cupid", "12", "14");
        total += addEntry("Xiaomi", "2201122C", "Xiaomi 12X", "psyche", "psyche", "psyche", "12", "14");
        total += addEntry("Xiaomi", "2211133C", "Xiaomi 13", "fuxi", "fuxi", "fuxi", "13", "14");
        total += addEntry("Xiaomi", "2211133G", "Xiaomi 13 Pro", "nuwa", "nuwa", "nuwa", "13", "14");
        total += addEntry("Xiaomi", "23013PC75G", "Xiaomi 13 Lite", "ziyi", "ziyi", "ziyi", "13", "14");
        total += addEntry("Xiaomi", "2304FPN6DG", "Redmi Note 12 Pro", "ruby", "ruby", "ruby", "12", "14");
        total += addEntry("Xiaomi", "23049PCD8G", "Redmi Note 12", "tapas", "tapas", "tapas", "12", "14");
        total += addEntry("Xiaomi", "2312DRA50G", "Redmi Note 13 Pro", "garnet", "garnet", "garnet", "13", "14");
        total += addEntry("Xiaomi", "M2012K11G", "POCO F3", "alioth", "alioth", "alioth", "11", "14");
        total += addEntry("Xiaomi", "22041216G", "POCO F4", "munch", "munch", "munch", "12", "14");
        total += addEntry("Xiaomi", "23013PC75I", "POCO X5 Pro", "redwood", "redwood", "redwood", "13", "14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ ONEPLUS (anchors) ------------------
        total += addEntry("OnePlus","HD1913","OnePlus 7T Pro","hotdogg","hotdogg","hotdogg","10","12");
        total += addEntry("OnePlus","IN2023","OnePlus 8 Pro","instantnoodlep","instantnoodlep","instantnoodlep","10","13");
        total += addEntry("OnePlus","KB2003","OnePlus 8T","kebab","kebab","kebab","11","13");
        total += addEntry("OnePlus","LE2113","OnePlus 9","lemonade","lemonade","lemonade","11","13");
        total += addEntry("OnePlus","LE2123","OnePlus 9 Pro","lemonadep","lemonadep","lemonadep","11","13");
        total += addEntry("OnePlus","NE2213","OnePlus 10 Pro","negroni","negroni","negroni","12","14");
        total += addEntry("OnePlus","CPH2411","OnePlus 11","salami","salami","salami","13","14");
        total += addEntry("OnePlus","CPH2581","OnePlus 12","aurora","aurora","aurora","14","14");
        total += addEntry("OnePlus","CPH2573","OnePlus 12R","iron","iron","iron","14","14");
        total += addEntry("OnePlus","PHB110","OnePlus Open","aries","aries","aries","14","14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ OPPO (anchors) ------------------
        total += addEntry("Oppo","CPH2217","OPPO Find X3 Pro","kona","kona","kona","11","13");
        total += addEntry("Oppo","PGEM10","OPPO Find X5 Pro","taro","taro","taro","12","14");
        total += addEntry("Oppo","PHU110","OPPO Find X7","OP565F","PHU110","OP565F","14","14");
        total += addEntry("Oppo","PHY110","OPPO Find X7 Ultra","OP565FL1","PHY110","OP565FL1","14","14");
        total += addEntry("Oppo","CPH2371","OPPO Reno8","holi","holi","holi","12","14");
        total += addEntry("Oppo","CPH2437","OPPO Reno10 Pro+","waffle","waffle","waffle","13","14");
        total += addEntry("Oppo","CPH2449","OPPO Reno11 Pro","ferrari","ferrari","ferrari","14","14");
        total += addEntry("Oppo","CPH2553","OPPO Reno12","cobalt","cobalt","cobalt","14","14");
        total += addEntry("Oppo","CPH2609","OPPO A3 Pro","lito","lito","lito","13","14");
        total += addEntry("Oppo","PFTM10","OPPO K10 Pro","sm8250","sm8250","sm8250","12","13");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ REALME (anchors) ------------------
        total += addEntry("Realme","RMX3301","realme GT2 Pro","taro","taro","taro","12","14");
        total += addEntry("Realme","RMX3561","realme GT Neo 3","tienna","tienna","tienna","12","14");
        total += addEntry("Realme","RMX3741","realme GT5","manet","manet","manet","13","14");
        total += addEntry("Realme","RMX3820","realme GT6","silver","silver","silver","14","14");
        total += addEntry("Realme","RMX3771","realme 12 Pro+","jackson","jackson","jackson","14","14");
        total += addEntry("Realme","RMX3311","realme GT Neo 5","phx","phx","phx","13","14");
        total += addEntry("Realme","RMX3461","realme Q3s","ossi","ossi","ossi","11","12");
        total += addEntry("Realme","RMX3393","realme 9 Pro+","miel","miel","miel","12","13");
        total += addEntry("Realme","RMX3350","realme GT Neo 2","lemon","lemon","lemon","11","12");
        total += addEntry("Realme","RMX3612","realme 10","stone","stone","stone","12","13");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ VIVO (anchors) ------------------
        total += addEntry("Vivo","V2056A","vivo X60 Pro+","kona","kona","kona","11","12");
        total += addEntry("Vivo","V2145A","vivo X70 Pro+","lahaina","lahaina","lahaina","11","13");
        total += addEntry("Vivo","V2227A","vivo X90 Pro","taro","taro","taro","13","14");
        total += addEntry("Vivo","V2241A","vivo X90","taro","taro","taro","13","14");
        total += addEntry("Vivo","V2303A","vivo X100","blue","blue","blue","14","14");
        total += addEntry("Vivo","V2307A","vivo X100 Pro","blue","blue","blue","14","14");
        total += addEntry("Vivo","V2230A","iQOO 11","kalama","kalama","kalama","13","14");
        total += addEntry("Vivo","V2360A","iQOO 12","zircon","zircon","zircon","14","14");
        total += addEntry("Vivo","V2141A","vivo Y76 5G","rain","rain","rain","11","12");
        total += addEntry("Vivo","V2312A","vivo S18","jade","jade","jade","14","14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ HUAWEI (anchors) ------------------
        total += addEntry("Huawei","ANA-NX9","Huawei P40","ANA","ANA","ANA","10","12");
        total += addEntry("Huawei","ELS-NX9","Huawei P40 Pro","ELS","ELS","ELS","10","12");
        total += addEntry("Huawei","NOH-NX9","Huawei Mate 40 Pro","NOH","NOH","NOH","10","12");
        total += addEntry("Huawei","LIO-N29","Huawei Mate 30 Pro","LIO","LIO","LIO","10","11");
        total += addEntry("Huawei","TAS-L29","Huawei Mate 30","TAS","TAS","TAS","10","11");
        total += addEntry("Huawei","VOG-L29","Huawei P30 Pro","VOG","VOG","VOG","9","11");
        total += addEntry("Huawei","ELE-L29","Huawei P30","ELE","ELE","ELE","9","11");
        total += addEntry("Huawei","MAR-LX1M","Huawei P30 Lite","MAR","MAR","MAR","9","10");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ MOTOROLA (anchors) ------------------
        total += addEntry("Motorola","XT2125-4","Motorola Edge 20 Pro","berlna","berlna","berlna","11","13");
        total += addEntry("Motorola","XT2201-2","Motorola Edge 30 Pro","racer","racer","racer","12","13");
        total += addEntry("Motorola","XT2321-1","Motorola Edge 40","rhode","rhode","rhode","13","14");
        total += addEntry("Motorola","XT2335-3","Motorola Edge 40 Neo","manaus","manaus","manaus","13","14");
        total += addEntry("Motorola","XT2311-3","Motorola Razr 40","juno","juno","juno","13","14");
        total += addEntry("Motorola","XT2313-3","Motorola Razr 40 Ultra","venus","venus","venus","13","14");
        total += addEntry("Motorola","XT2343-2","Motorola G84","bangkok","bangkok","bangkok","13","14");
        total += addEntry("Motorola","XT2225-2","Motorola G73","cebu","cebu","cebu","12","13");
        total += addEntry("Motorola","XT2171-2","Motorola G60","hanoi","hanoi","hanoi","11","12");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ ASUS (anchors) ------------------
        total += addEntry("ASUS","ASUS_I005DA","ROG Phone 5","lithium","lithium","lithium","11","13");
        total += addEntry("ASUS","ASUS_AI2201","ROG Phone 6","diablo","diablo","diablo","12","13");
        total += addEntry("ASUS","ASUS_AI2301","ROG Phone 7","x20","x20","x20","13","14");
        total += addEntry("ASUS","ASUS_AI2401","ROG Phone 8","hydra","hydra","hydra","14","14");
        total += addEntry("ASUS","ASUS_I003DD","Zenfone 7 Pro","redwood","redwood","redwood","10","12");
        total += addEntry("ASUS","ASUS_I004D","Zenfone 8","sake","sake","sake","11","13");
        total += addEntry("ASUS","ASUS_AI2202","Zenfone 9","mermaid","mermaid","mermaid","12","14");
        total += addEntry("ASUS","ASUS_AI2302","Zenfone 10","catfish","catfish","catfish","13","14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ SONY (anchors) ------------------
        total += addEntry("Sony","XQ-AT51","Xperia 1 II","pdx203","pdx203","pdx203","10","12");
        total += addEntry("Sony","XQ-BC72","Xperia 1 III","pdx215","pdx215","pdx215","11","13");
        total += addEntry("Sony","XQ-CT72","Xperia 1 IV","pdx223","pdx223","pdx223","12","14");
        total += addEntry("Sony","XQ-DQ54","Xperia 1 V","pdx234","pdx234","pdx234","14","14");
        total += addEntry("Sony","XQ-AS52","Xperia 5 II","pdx206","pdx206","pdx206","10","12");
        total += addEntry("Sony","XQ-BQ62","Xperia 5 III","pdx214","pdx214","pdx214","11","13");
        total += addEntry("Sony","XQ-CQ44","Xperia 5 IV","pdx224","pdx224","pdx224","12","14");
        total += addEntry("Sony","XQ-DQ72","Xperia 5 V","pdx236","pdx236","pdx236","14","14");
        total += addEntry("Sony","XQ-AD51","Xperia 10 II","pdx201","pdx201","pdx201","10","12");
        total += addEntry("Sony","XQ-BT52","Xperia 10 III","pdx213","pdx213","pdx213","11","12");
        total += addEntry("Sony","XQ-CT54","Xperia 10 IV","pdx225","pdx225","pdx225","12","14");
        total += addEntry("Sony","XQ-DQ54","Xperia 10 V","pdx233","pdx233","pdx233","13","14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ NOKIA (anchors) ------------------
        total += addEntry("Nokia","TA-1462","Nokia X30 5G","dragon","dragon","dragon","12","14");
        total += addEntry("Nokia","TA-1399","Nokia G60","hima","hima","hima","12","14");
        total += addEntry("Nokia","TA-1510","Nokia XR21","atlas","atlas","atlas","13","14");
        total += addEntry("Nokia","TA-1289","Nokia 8.3 5G","draco","draco","draco","10","12");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ NOTHING (anchors) ------------------
        total += addEntry("Nothing","A063","Nothing Phone (1)","spacewar","spacewar","spacewar","12","14");
        total += addEntry("Nothing","A065","Nothing Phone (2)","pong","pong","pong","13","14");
        total += addEntry("Nothing","A142","Nothing Phone (2a)","pacman","pacman","pacman","14","14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ------------------ HONOR (anchors) ------------------
        total += addEntry("Honor","NTH-NX9","Honor 50","NTH","NTH","NTH","11","12");
        total += addEntry("Honor","ANY-NX9","Honor 70","ANY","ANY","ANY","12","13");
        total += addEntry("Honor","FNE-NX9","Honor 90","FNE","FNE","FNE","13","14");
        total += addEntry("Honor","PGT-AN10","Honor Magic6 Pro","PGT","PGT","PGT","14","14");
        total += addEntry("Honor","LGE-AN00","Honor Magic5 Pro","LGE","LGE","LGE","13","14");
        total += addEntry("Honor","VNE-AN00","Honor X50","VNE","VNE","VNE","13","14");
        if (total >= MAX_TOTAL_MODELS) { logBuilt(total); return; }

        // ======== SYNTHETIC EXPANSION (có trần) ========
        total += expandBrandCapped("Samsung", "SM-A", "Galaxy A", "a",   80, "10","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Samsung", "SM-M", "Galaxy M", "m",   40, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Samsung", "SM-F", "Galaxy Z", "q",   25, "12","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}

        total += expandBrandCapped("Google",  "GW",    "Pixel",    "sh",  20, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}

        total += expandBrandCapped("Xiaomi",  "220",   "Xiaomi",   "xm",  80, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Xiaomi",  "230",   "Redmi",    "rm",  80, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}

        total += expandBrandCapped("OnePlus", "CPH",   "OnePlus",  "op",  40, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Oppo",    "CPH",   "Oppo Reno","oppo",60, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Realme",  "RMX",   "Realme",   "real",50, "12","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Vivo",    "V",     "Vivo",     "vivo",50, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}

        total += expandBrandCapped("Huawei",  "ANG-",  "Huawei",   "hwa", 30, "10","12", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Motorola","XT",    "Moto",     "moto",30, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("ASUS",    "ASUS_AI","ROG Phone","rog",20, "11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Sony",    "XQ-",   "Xperia",   "pdx", 20, "10","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Nokia",   "TA-",   "Nokia",    "nokia",20,"11","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Nothing", "A0",    "Nothing Phone","nothing",12,"12","14", total); if (total>=MAX_TOTAL_MODELS){logBuilt(total);return;}
        total += expandBrandCapped("Honor",   "PGT-",  "Honor",    "honor",30,"11","14", total);

        logBuilt(total);
    }

    private static void logBuilt(int total) {
        try {
            de.robv.android.xposed.XposedBridge.log("[MyHook] built-in device table ready: brands=" + DEVICE_MAP.size() + ", models=" + total);
        } catch (Throwable ignored) {}
    }

    /** add 1 entry, trả về 1 nếu thêm thành công (để cộng dồn total) */
    private static int addEntry(String brand, String modelCode, String marketing,
                                String deviceCode, String vendorProduct, String vendorDevice,
                                String minR, String maxR) {
        List<DeviceEntry> list = DEVICE_MAP.get(brand);
        if (list == null) {
            list = new ArrayList<>();
            DEVICE_MAP.put(brand, list);
        }
        list.add(new DeviceEntry(modelCode, marketing, deviceCode, vendorProduct, vendorDevice, minR, maxR));
        return 1;
    }

    /** Mở rộng có trần: nếu đạt MAX_TOTAL_MODELS thì dừng ngay */
    private static int expandBrandCapped(String brand, String basePrefix, String marketingPrefix,
                                         String devicePrefix, int count, String minR, String maxR, int currentTotal) {
        int added = 0;
        Random rr = new Random(brand.hashCode() ^ basePrefix.hashCode() ^ devicePrefix.hashCode() ^ count);
        final String[] suffixTiers = {"", " Pro", " Plus", " Ultra", " 5G"};
        final String[] devTiers = {"", "_pro", "_plus", "_ultra"};

        List<DeviceEntry> list = DEVICE_MAP.get(brand);
        if (list == null) { list = new ArrayList<>(); DEVICE_MAP.put(brand, list); }

        for (int i = 0; i < count; i++) {
            if (currentTotal + added >= MAX_TOTAL_MODELS) break;

            String suffix3 = String.format(Locale.US, "%03d", rr.nextInt(900) + 100);
            String modelCode = basePrefix + suffix3;

            String marketing = marketingPrefix + suffixTiers[rr.nextInt(suffixTiers.length)];
            String dev = devicePrefix + devTiers[rr.nextInt(devTiers.length)];

            list.add(new DeviceEntry(modelCode, marketing, dev, dev, dev, minR, maxR));
            added++;
        }
        return added;
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
        String product = (brand + "_" + e.modelCode).toLowerCase(Locale.US)
                .replace(' ', '_').replace('/', '_');

        String fingerprint = String.format(
                Locale.US,
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
                .replace(' ', '_').replace('/', '_');
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
        for (int i = 0; i < 14; i++) {
            int v = d[13 - i];
            if (i % 2 == 0) { v *= 2; if (v > 9) v -= 9; }
            sum += v;
        }
        d[14] = (10 - (sum % 10)) % 10;
        StringBuilder sb = new StringBuilder(15);
        for (int x : d) sb.append(x);
        return sb.toString();
    }

    private static String pick(String[] arr, Random r) { return arr[r.nextInt(arr.length)]; }

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
