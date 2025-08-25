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

                        // === Load config + sinh profile ===
                        ConfigManager cfg = new ConfigManager(ctx);
                        DeviceProfileGenerator gen =
                                new DeviceProfileGenerator(ctx, lpparam.packageName, cfg.shouldRandom());
                        profile = gen.getProfile();
                        if (cfg.shouldRandom()) cfg.setShouldRandom(false);

                        // === Hook Build.* cơ bản + Build Number ===
                        safe(() -> {
                            String brandLower = nzLower(profile.brand);
                            String mfrLower   = nzLower(profile.manufacturer);

                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MODEL",       profile.model);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "BRAND",       brandLower);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "DEVICE",      profile.device);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MANUFACTURER", mfrLower);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "FINGERPRINT", profile.fingerprint);

                            // Build Number
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "ID",         profile.buildId);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "INCREMENTAL", profile.buildIncremental);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "DISPLAY",
                                    profile.buildId + "." + profile.buildIncremental);
                        });

                        // === SystemProperties.get(...) → trả về giá trị giả lập ===
                        final Map<String, String> propMap = new HashMap<>();

                        // Helper điền nhóm ro.product.<partition>.*
                        String nameFallback = makeProductName(profile); // ví dụ: samsung_SM-G991B
                        fillProductNamespace(propMap, "",           profile, nameFallback);
                        fillProductNamespace(propMap, "system",     profile, nameFallback);
                        fillProductNamespace(propMap, "system_ext", profile, nameFallback);
                        fillProductNamespace(propMap, "product",    profile, nameFallback);
                        fillProductNamespace(propMap, "odm",        profile, nameFallback);
                        fillProductNamespace(propMap, "vendor",     profile, nameFallback);

                        // Tổng quát
                        put(propMap, "ro.build.product",         nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.product.board",         profile.device);
                        put(propMap, "ro.build.fingerprint",     profile.fingerprint);

                        // Build Number (một số app đọc từ prop)
                        put(propMap, "ro.build.id",              profile.buildId);
                        put(propMap, "ro.build.display.id",      profile.buildId + "." + profile.buildIncremental);
                        put(propMap, "ro.build.version.incremental", profile.buildIncremental);

                        // Samsung (thường không cần, nhưng thêm cho đủ)
                        put(propMap, "ro.product.vendor.model",  profile.model);        // [Unverified]
                        put(propMap, "ro.boot.hardware.sku",     profile.model);        // [Unverified]

                        // OPPO / Realme (OPlus)
                        put(propMap, "ro.vendor.product.oem",        nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.vendor.product.device.oem", nz(profile.vendorDevice,  profile.device));
                        put(propMap, "ro.oplus.market.name",         profile.marketingName);   // [Unverified]
                        put(propMap, "ro.oplus.device",              profile.device);         // [Unverified]
                        put(propMap, "ro.oplus.product.name",        nz(profile.vendorProduct, profile.model)); // [Unverified]

                        // OnePlus
                        put(propMap, "ro.oneplus.device",            profile.device);         // [Unverified]
                        put(propMap, "ro.oneplus.product.name",      nz(profile.vendorProduct, profile.model)); // [Unverified]
                        put(propMap, "ro.product.oem",               nz(profile.vendorProduct, profile.model)); // [Unverified]

                        // Vivo
                        put(propMap, "ro.vivo.model",                profile.model);          // [Unverified]
                        put(propMap, "ro.vivo.product.model",        profile.model);          // [Unverified]
                        put(propMap, "ro.vivo.market.name",          profile.marketingName);  // [Unverified]

                        // Một số khóa hiển thị marketing
                        put(propMap, "ro.product.market.name",        profile.marketingName); // [Unverified]
                        put(propMap, "ro.product.vendor.market.name", profile.marketingName); // [Unverified]

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
        String prefix = (partition == null || partition.isEmpty())
                ? "ro.product"
                : "ro.product." + partition;

        String brandLower = nzLower(p.brand);
        String manufLower = nzLower(p.manufacturer);

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

    private static String nzLower(String s) {
        return (s == null) ? null : s.toLowerCase(Locale.US);
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) { XposedBridge.log(t); }
    }
}
