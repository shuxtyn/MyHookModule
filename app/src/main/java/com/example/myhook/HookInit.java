package com.example.myhook;

import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookInit implements IXposedHookLoadPackage {
    private static DeviceProfile profile;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Bỏ qua system process
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
                        ClassLoader cl = ctx.getClassLoader();

                        // ✅ Dùng constructor mới có Context
                        ConfigManager cfg = new ConfigManager(ctx);
                        DeviceProfileGenerator gen =
                                new DeviceProfileGenerator(ctx, lpparam.packageName, cfg.shouldRandom());
                        profile = gen.getProfile();
                        if (cfg.shouldRandom()) cfg.setShouldRandom(false);

                        // Ví dụ hook Build.* (giữ nguyên các hook khác của bạn nếu có)
                        safe(() -> {
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MODEL", profile.model);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "BRAND", profile.brand.toLowerCase());
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "DEVICE", profile.device);
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "MANUFACTURER", profile.manufacturer.toLowerCase());
                            XposedHelpers.setStaticObjectField(android.os.Build.class, "FINGERPRINT", profile.fingerprint);
                        });

                        XposedBridge.log("[MyHook] Applied for " + lpparam.packageName);
                    }
                }
        );
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) { XposedBridge.log(t); }
    }
}
