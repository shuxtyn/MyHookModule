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
        // bỏ qua system process
        if (lpparam.packageName.equals("android") || lpparam.packageName.equals("system")) return;

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "attach", Context.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context ctx = (Context) param.args[0];
                    ClassLoader cl = ctx.getClassLoader();

                    ConfigManager cfg = new ConfigManager();
                    DeviceProfileGenerator gen = new DeviceProfileGenerator(lpparam.packageName, cfg.shouldRandom());
                    profile = gen.getProfile();
                    if (cfg.shouldRandom()) cfg.setShouldRandom(false);

                    safe(() -> {
                        XposedHelpers.setStaticObjectField(android.os.Build.class, "MODEL", profile.model);
                        XposedHelpers.setStaticObjectField(android.os.Build.class, "BRAND", profile.brand);
                        XposedHelpers.setStaticObjectField(android.os.Build.class, "DEVICE", profile.device);
                    });

                    XposedBridge.log("[MyHook] Applied for " + lpparam.packageName);
                }
            });
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) { XposedBridge.log(t); }
    }
}
