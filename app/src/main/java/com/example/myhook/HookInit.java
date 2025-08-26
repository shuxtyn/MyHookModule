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

                        // ---- Hook Build.* + Build Number ----
                        safe(() -> {
                            String brandLower = nzLower(profile.brand);
                            String mfrLower   = nzLower(profile.manufacturer);

                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MODEL",       profile.model);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "BRAND",       brandLower);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "DEVICE",      profile.device);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MANUFACTURER", mfrLower);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "FINGERPRINT", profile.fingerprint);

                            XposedHelpers.setStaticObjectField(android.os.Build.class, "ID",           profile.buildId);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "INCREMENTAL",  profile.buildIncremental);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "DISPLAY",
                                    profile.buildId + "." + profile.buildIncremental);
                        });

                        // ---- Fake SystemProperties ----
                        final Map<String, String> propMap = new HashMap<>();

                        String nameFallback = makeProductName(profile);

                        // ro.product.*
                        fillProductTree(propMap, "ro.product",
                                profile.brand, profile.manufacturer, profile.model, profile.device, nameFallback);

                        // ro.product.vendor.*
                        fillProductTree(propMap, "ro.product.vendor",
                                profile.brand, profile.manufacturer,
                                nz(profile.vendorProduct, profile.model),
                                nz(profile.vendorDevice,  profile.device),
                                nz(profile.vendorProduct, profile.model));

                        // ro.product.odm.*
                        fillProductTree(propMap, "ro.product.odm",
                                profile.brand, profile.manufacturer,
                                nz(profile.vendorProduct, profile.model),
                                nz(profile.vendorDevice,  profile.device),
                                nz(profile.vendorProduct, profile.model));

                        // ro.product.bootimage.*
                        fillProductTree(propMap, "ro.product.bootimage",
                                profile.brand, profile.manufacturer,
                                nz(profile.vendorProduct, profile.model),
                                nz(profile.vendorDevice,  profile.device),
                                nz(profile.vendorProduct, profile.model));

                        // fingerprints
                        put(propMap, "ro.build.fingerprint",              profile.fingerprint);
                        put(propMap, "ro.vendor.build.fingerprint",       profile.fingerprint);
                        put(propMap, "ro.odm.build.fingerprint",          profile.fingerprint);
                        put(propMap, "ro.bootimage.build.fingerprint",    profile.fingerprint);

                        // build number props
                        put(propMap, "ro.build.id",                        profile.buildId);
                        put(propMap, "ro.build.display.id",                profile.buildId + "." + profile.buildIncremental);
                        put(propMap, "ro.build.version.incremental",       profile.buildIncremental);

                        // general extras
                        put(propMap, "ro.build.product",                   nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.product.board",                   profile.device);

                        // OPlus / OPPO / Realme specifics
                        put(propMap, "ro.vendor.product.oem",              nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.vendor.product.device.oem",       nz(profile.vendorDevice,  profile.device));
                        put(propMap, "ro.vendor.dolby.manufacturer",       "OPLUS"); // [Unverified]
                        put(propMap, "ro.vendor.dolby.brand",              "OPLUS"); // [Unverified]
                        put(propMap, "ro.vendor.oplus.market.name",        profile.marketingName);
                        put(propMap, "ro.oplus.market.name",               profile.marketingName);
                        put(propMap, "ro.realme.market.name",              profile.marketingName);

                        // OnePlus
                        put(propMap, "ro.oneplus.device",                  nz(profile.vendorDevice, profile.device));
                        put(propMap, "ro.oneplus.product.name",            nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.product.oem",                     nz(profile.vendorProduct, profile.model));

                        // Vivo
                        put(propMap, "ro.vivo.model",                      profile.model);
                        put(propMap, "ro.vivo.product.device",             nz(profile.vendorDevice, profile.device));
                        put(propMap, "ro.vivo.product.model",              nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.vivo.market.name",                profile.marketingName);

                        // Samsung (nhẹ)
                        put(propMap, "ro.product.vendor.model",            nz(profile.vendorProduct, profile.model));
                        put(propMap, "ro.boot.hardware.sku",               profile.model);

                        // Hook SystemProperties.get(key)
                        XposedHelpers.findAndHookMethod(
                                "android.os.SystemProperties",
                                lpparam.classLoader,
                                "get",
                                String.class,
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam p) {
                                        String key = (String) p.args[0];
                                        if (key != null) {
                                            String v = propMap.get(key);
                                            if (v != null) p.setResult(v);
                                        }
                                    }
                                }
                        );
                        // Hook SystemProperties.get(key, def)
                        XposedHelpers.findAndHookMethod(
                                "android.os.SystemProperties",
                                lpparam.classLoader,
                                "get",
                                String.class, String.class,
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam p) {
                                        String key = (String) p.args[0];
                                        if (key != null) {
                                            String v = propMap.get(key);
                                            if (v != null) p.setResult(v);
                                        }
                                    }
                                }
                        );

                        XposedBridge.log("[MyHook] Applied for " + lpparam.packageName);
                    }
                }
        );
    }

    // ===== Helpers =====
    private static void fillProductTree(Map<String,String> map, String prefix,
                                        String brand, String manufacturer,
                                        String model, String device, String name) {
        put(map, prefix + ".brand",         nzLower(brand));
        put(map, prefix + ".manufacturer",  nzLower(manufacturer));
        put(map, prefix + ".model",         model);
        put(map, prefix + ".device",        device);
        put(map, prefix + ".name",          name);
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
try {
    NativeBridge.initHooks(lpparam.packageName);

    java.util.ArrayList<String> k = new java.util.ArrayList<>();
    java.util.ArrayList<String> v = new java.util.ArrayList<>();

    k.add("ro.product.brand");            v.add(profile.brand);
    k.add("ro.product.model");            v.add(profile.model);
    k.add("ro.product.device");           v.add(profile.device);
    k.add("ro.product.manufacturer");     v.add(profile.manufacturer);
    k.add("ro.build.fingerprint");        v.add(profile.fingerprint);
    k.add("ro.build.id");                 v.add(profile.buildId);
    k.add("ro.build.version.incremental");v.add(profile.buildIncremental);

    if (profile.vendorProduct != null) {
        k.add("ro.vendor.product.oem");   v.add(profile.vendorProduct);
        k.add("ro.build.product");        v.add(profile.vendorProduct);
        k.add("ro.product.name");         v.add(profile.vendorProduct);
    }
    if (profile.vendorDevice != null) {
        k.add("ro.vendor.product.device.oem"); v.add(profile.vendorDevice);
    }

    k.add("ro.vendor.build.fingerprint");    v.add(profile.fingerprint);
    k.add("ro.odm.build.fingerprint");       v.add(profile.fingerprint);
    k.add("ro.bootimage.build.fingerprint"); v.add(profile.fingerprint);

    NativeBridge.setOverrides(
        k.toArray(new String[0]),
        v.toArray(new String[0])
    );
} catch (Throwable t) {
    de.robv.android.xposed.XposedBridge.log(t);
}
