package com.example.myhook;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookInit implements IXposedHookLoadPackage {

    private static DeviceProfile profile;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Bỏ qua system
        if ("android".equals(lpparam.packageName) || "system".equals(lpparam.packageName)) return;

        // Hook Application.attach(Context)
        XposedHelpers.findAndHookMethod(
                Application.class.getName(),
                lpparam.classLoader,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = (Context) param.args[0];
                        ClassLoader cl = ctx.getClassLoader();

                        // --- Load & giữ profile ---
                        ConfigManager cfg = new ConfigManager(ctx);
                        DeviceProfileGenerator gen = new DeviceProfileGenerator(
                                ctx,
                                lpparam.packageName,
                                cfg.shouldRandom()
                        );
                        profile = gen.getProfile();
                        if (cfg.shouldRandom()) cfg.setShouldRandom(false);

                        // --- Fake Build.* ở Java layer ---
                        safe(() -> {
                            XposedHelpers.setStaticObjectField(Build.class, "MODEL", profile.model);
                            XposedHelpers.setStaticObjectField(Build.class, "BRAND", profile.brand);
                            XposedHelpers.setStaticObjectField(Build.class, "DEVICE", profile.device);
                            XposedHelpers.setStaticObjectField(Build.class, "MANUFACTURER", profile.manufacturer);
                            // (tuỳ nhu cầu có thể set thêm BOARD/PRODUCT/ID…)
                        });

                        // --- NATIVE: nạp .so và set overrides cho __system_property_get ---
                        try {
                            NativeBridge.initHooks(lpparam.packageName);

                            java.util.ArrayList<String> k = new java.util.ArrayList<>();
                            java.util.ArrayList<String> v = new java.util.ArrayList<>();

                            // cốt lõi
                            k.add("ro.product.brand");                v.add(profile.brand);
                            k.add("ro.product.model");                v.add(profile.model);
                            k.add("ro.product.device");               v.add(profile.device);
                            k.add("ro.product.manufacturer");         v.add(profile.manufacturer);
                            k.add("ro.build.fingerprint");            v.add(profile.fingerprint);
                            k.add("ro.build.id");                     v.add(profile.buildId);
                            k.add("ro.build.version.incremental");    v.add(profile.buildIncremental);

                            // vendor keys (nếu có)
                            if (profile.vendorProduct != null && !profile.vendorProduct.isEmpty()) {
                                k.add("ro.vendor.product.oem");       v.add(profile.vendorProduct);
                                k.add("ro.build.product");            v.add(profile.vendorProduct);
                                k.add("ro.product.name");             v.add(profile.vendorProduct);
                                // tuỳ app: nhiều app dùng ro.product.model để so OEM code
                                // k.add("ro.product.model");         v.add(profile.vendorProduct);
                            }
                            if (profile.vendorDevice != null && !profile.vendorDevice.isEmpty()) {
                                k.add("ro.vendor.product.device.oem"); v.add(profile.vendorDevice);
                                k.add("ro.product.vendor.device");     v.add(profile.vendorDevice);
                            }

                            // các fingerprint phụ thường bị soi
                            k.add("ro.vendor.build.fingerprint");     v.add(profile.fingerprint);
                            k.add("ro.odm.build.fingerprint");        v.add(profile.fingerprint);
                            k.add("ro.bootimage.build.fingerprint");  v.add(profile.fingerprint);

                            NativeBridge.setOverrides(
                                    k.toArray(new String[0]),
                                    v.toArray(new String[0])
                            );
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }

                        XposedBridge.log("[MyHook] Applied for " + lpparam.packageName);
                    }
                }
        );
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) { XposedBridge.log(t); }
    }
}
