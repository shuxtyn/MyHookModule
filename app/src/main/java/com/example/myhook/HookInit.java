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

                        ConfigManager cfg = new ConfigManager(ctx);
                        DeviceProfileGenerator gen =
                                new DeviceProfileGenerator(ctx, lpparam.packageName, cfg.shouldRandom());
                        profile = gen.getProfile();
                        if (cfg.shouldRandom()) cfg.setShouldRandom(false);

                        // ==== Fake Build.* cơ bản + Build Number ====
                        safe(() -> {
                            String brandLower = nzLower(profile.brand);
                            String mfrLower   = nzLower(profile.manufacturer);

                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MODEL", profile.model);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "BRAND", brandLower);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "DEVICE", profile.device);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MANUFACTURER", mfrLower);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "FINGERPRINT", profile.fingerprint);

                            XposedHelpers.setStaticObjectField(android.os.Build.class, "ID", profile.buildId);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "INCREMENTAL", profile.buildIncremental);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "DISPLAY",
                                    profile.buildId + "." + profile.buildIncremental);
                        });

                        // ==== Fake SystemProperties ====
                        final Map<String, String> propMap = new HashMap<>();

                        String nameFallback = makeProductName(profile);

                        fillProductNamespace(propMap, "",           profile, nameFallback);
                        fillProductNamespace(propMap, "system",     profile, nameFallback);
                        fillProductNamespace(propMap, "system_ext", profile, nameFallback);
                        fillProductNamespace(propMap, "product",    profile, nameFallback);
                        fillProductNamespace(propMap, "odm",        profile, nameFallback);
                        fillProductNamespace(propMap, "vendor",     profile, nameFallback);

                        // Chung
                        put(propMap, "ro.build.product", profile.vendorProduct);
                        put(propMap, "ro.product.board", profile.device);
                        put(propMap, "ro.build.fingerprint", profile.fingerprint);

                        put(propMap, "ro.build.id", profile.buildId);
                        put(propMap, "ro.build.display.id", profile.buildId + "." + profile.buildIncremental);
                        put(propMap, "ro.build.version.incremental", profile.buildIncremental);

                        // ==== Vendor cụ thể ====

                        // Samsung
                        put(propMap, "ro.product.vendor.model", profile.model);
                        put(propMap, "ro.boot.hardware.sku", profile.model);

                        // Oppo / Realme / OPlus
                        put(propMap, "ro.vendor.product.oem", nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.vendor.product.device.oem", nz(profile.vendorDevice, profile.device));
                        put(propMap, "ro.oplus.market.name", profile.marketingName);
                        put(propMap, "ro.oplus.device", profile.device);
                        put(propMap, "ro.oplus.product.name", nz(profile.vendorProduct, profile.model));
                        // Realme bổ sung
                        put(propMap, "ro.realme.device", profile.device);
                        put(propMap, "ro.realme.product.name", profile.marketingName);
                        put(propMap, "ro.realme.market.name", profile.marketingName);

                        // OnePlus
                        put(propMap, "ro.oneplus.device", profile.device);
                        put(propMap, "ro.oneplus.product.name", nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.product.oem", nz(profile.vendorProduct, profile.model));

                        // Vivo
                        put(propMap, "ro.vivo.model", profile.model);
                        put(propMap, "ro.vivo.product.model", profile.model);
                        put(propMap, "ro.vivo.market.name", profile.marketingName);
                        put(propMap, "ro.vivo.product.device", profile.device);

                        // Một số khóa market chung
                        put(propMap, "ro.product.market.name", profile.marketingName);
                        put(propMap, "ro.product.vendor.market.name", profile.marketingName);

                        // Hook SystemProperties.get
                        XposedHelpers.findAndHookMethod(
                                "android.os.SystemProperties",
                                lpparam.classLoader,
                                "get",
                                String.class,
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam p) {
                                        String key = (String) p.args[0];
                                        if (propMap.containsKey(key)) {
                                            p.setResult(propMap.get(key));
                                        }
                                    }
                                }
                        );
                        XposedHelpers.findAndHookMethod(
                                "android.os.SystemProperties",
                                lpparam.classLoader,
                                "get",
                                String.class, String.class,
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam p) {
                                        String key = (String) p.args[0];
                                        if (propMap.containsKey(key)) {
                                            p.setResult(propMap.get(key));
                                        }
                                    }
                                }
                        );

                        XposedBridge.log("[MyHook] Applied for " + lpparam.packageName);
                    }
                }
        );
    }

    // ==== Helpers ====
    private static void fillProductNamespace(Map<String, String> map, String partition, DeviceProfile p, String nameFallback) {
        String prefix = (partition == null || partition.isEmpty())
                ? "ro.product"
                : "ro.product." + partition;
        put(map, prefix + ".brand", nzLower(p.brand));
        put(map, prefix + ".manufacturer", nzLower(p.manufacturer));
        put(map, prefix + ".model", p.model);
        put(map, prefix + ".device", p.device);
        put(map, prefix + ".name", nameFallback);
    }

    private static String makeProductName(DeviceProfile p) {
        String b = (p.brand == null ? "brand" : p.brand).toLowerCase(Locale.US);
        String m = (p.model == null ? "model" : p.model);
        return (b + "_" + m).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (key != null && value != null && !value.isEmpty()) map.put(key, value);
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
