package com.example.myhook;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookInit implements IXposedHookLoadPackage {
    private static DeviceProfile profile;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Bỏ qua tiến trình hệ thống
        if ("android".equals(lpparam.packageName) || "system".equals(lpparam.packageName)) return;

        XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = (Context) param.args[0];

                        // === Load cấu hình + sinh profile ===
                        ConfigManager cfg = new ConfigManager(ctx);
                        DeviceProfileGenerator gen = new DeviceProfileGenerator(ctx, lpparam.packageName, cfg.shouldRandom());
                        profile = gen.getProfile();
                        if (cfg.shouldRandom()) cfg.setShouldRandom(false);

                        // === Hook Build.* cơ bản ===
                        safe(() -> {
                            // Lưu ý: nhiều ROM kỳ vọng chữ thường cho BRAND/MANUFACTURER
                            String brandLower = profile.brand == null ? null : profile.brand.toLowerCase(Locale.US);
                            String mfrLower   = profile.manufacturer == null ? null : profile.manufacturer.toLowerCase(Locale.US);

                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MODEL", profile.model);              // SM-G991B / PHY110 ...
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "BRAND", brandLower);                // samsung / oppo / vivo ...
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "DEVICE", profile.device);            // o1s / husky / OP565FL1 ...
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MANUFACTURER", mfrLower);           // samsung / oppo ...
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "FINGERPRINT", profile.fingerprint); // build fingerprint
                        });

                        // === SystemProperties.get(...) → trả về giá trị giả lập ===
                        final Map<String, String> propMap = new HashMap<>();

                        // Helper điền nhóm ro.product.<partition>.*
                        String nameFallback = makeProductName(profile); // ví dụ: samsung_SM-G991B
                        fillProductNamespace(propMap, "",        profile, nameFallback);
                        fillProductNamespace(propMap, "system",  profile, nameFallback);
                        fillProductNamespace(propMap, "system_ext", profile, nameFallback);
                        fillProductNamespace(propMap, "product", profile, nameFallback);
                        fillProductNamespace(propMap, "odm",     profile, nameFallback);
                        fillProductNamespace(propMap, "vendor",  profile, nameFallback);

                        // Các khóa tổng quát
                        put(propMap, "ro.build.product",         nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.product.board",         profile.device);           // thường gần DEVICE
                        put(propMap, "ro.build.fingerprint",     profile.fingerprint);

                        // Một số khóa "OEM/vendor" hay gặp (tùy hãng/ROM) — [Unverified] tên khóa theo cộng đồng
                        // Samsung
                        put(propMap, "ro.product.vendor.model",  profile.model);           // [Unverified]
                        put(propMap, "ro.boot.hardware.sku",     profile.model);           // [Unverified]

                        // OPPO / Realme (OPlus)
                        put(propMap, "ro.vendor.product.oem",          nz(profile.vendorProduct, profile.model)); // ví dụ PHY110
                        put(propMap, "ro.vendor.product.device.oem",   nz(profile.vendorDevice,  profile.device)); // ví dụ OP565FL1
                        put(propMap, "ro.oplus.market.name",           profile.marketingName);  // [Unverified] hiển thị marketing
                        put(propMap, "ro.oplus.device",                profile.device);         // [Unverified]
                        put(propMap, "ro.oplus.product.name",          nz(profile.vendorProduct, profile.model)); // [Unverified]

                        // OnePlus
                        put(propMap, "ro.oneplus.device",              profile.device);         // [Unverified]
                        put(propMap, "ro.oneplus.product.name",        nz(profile.vendorProduct, profile.model)); // [Unverified]
                        put(propMap, "ro.product.oem",                 nz(profile.vendorProduct, profile.model)); // [Unverified]

                        // Vivo
                        put(propMap, "ro.vivo.model",                  profile.model);          // [Unverified]
                        put(propMap, "ro.vivo.product.model",          profile.model);          // [Unverified]
                        put(propMap, "ro.vivo.market.name",            profile.marketingName);  // [Unverified]

                        // Một số khóa hay đọc để hiển thị tên máy dạng marketing
                        put(propMap, "ro.product.market.name",         profile.marketingName);  // [Unverified]
                        put(propMap, "ro.product.vendor.market.name",  profile.marketingName);  // [Unverified]

                        // Hook get(String)
                        XposedHelpers.findAndHookMethod(
                                "android.os.SystemProperties",
                                lpparam.classLoader,
                                "get",
                                String.class,
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                                        String key = (String) p.args[0];
                                        String v = propMap.get(key);
                                        if (v != null) p.setResult(v);
                                    }
                                }
                        );

                        // Hook get(String, String defaultValue)
                        XposedHelpers.findAndHookMethod(
                                "android.os.SystemProperties",
                                lpparam.classLoader,
                                "get",
                                String.class, String.class,
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                                        String key = (String) p.args[0];
                                        String v = propMap.get(key);
                                        if (v != null) p.setResult(v);
                                    }
                                }
                        );

                        XposedBridge.log("[MyHook] Applied for " + lpparam.packageName);
                    }
                }
        );
    }

    // ===== Helpers =====

    private static void fillProductNamespace(Map<String, String> map, String partition, DeviceProfile p, String nameFallback) {
        String prefix = partition == null || partition.isEmpty()
                ? "ro.product"
                : "ro.product." + partition;

        String brandLower = p.brand == null ? null : p.brand.toLowerCase(Locale.US);
        String manufLower = p.manufacturer == null ? null : p.manufacturer.toLowerCase(Locale.US);

        put(map, prefix + ".brand",         brandLower);
        put(map, prefix + ".manufacturer",  manufLower);
        put(map, prefix + ".model",         p.model);
        put(map, prefix + ".device",        p.device);
        put(map, prefix + ".name",          nameFallback);
    }

    private static String makeProductName(DeviceProfile p) {
        String b = (p.brand == null ? "brand" : p.brand).toLowerCase(Locale.US);
        String m = (p.model == null ? "model" : p.model);
        return (b + "_" + m).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (key == null || value == null || value.isEmpty()) return;
        map.put(key, value);
    }

    private static String nz(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) { XposedBridge.log(t); }
    }
}
