private static synchronized void ensureDevicesLoaded(Context ctx) {
    if (DEVICE_MAP != null) return;
    DEVICE_MAP = new LinkedHashMap<>();

    // thay bằng applicationId của module trong build.gradle
    final String MODULE_PKG = "com.example.myhookmodule";

    try {
        // lấy context của chính module (không phải app target)
        Context mctx = ctx.createPackageContext(
                MODULE_PKG,
                Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE
        );

        try (InputStream is = mctx.getAssets().open("devices.json");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            JSONObject root = new JSONObject(sb.toString());

            int brandCount = 0;
            int modelCount = 0;

            Iterator<String> brands = root.keys();
            while (brands.hasNext()) {
                String brand = brands.next();
                JSONArray arr = root.getJSONArray(brand);
                brandCount++;
                List<DeviceEntry> list = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONObject) {
                        JSONObject o = (JSONObject) item;
                        String code  = o.optString("modelCode",  o.optString("model", "Generic"));
                        String mk    = o.optString("marketing", code);
                        String dev   = o.optString("deviceCode", null);
                        String vProd = o.optString("vendorProduct", null);
                        String vDev  = o.optString("vendorDevice", null);
                        String minR  = o.optString("minRelease", null);
                        String maxR  = o.optString("maxRelease", null);
                        list.add(new DeviceEntry(code, mk, dev, vProd, vDev, minR, maxR));
                        modelCount++;
                    } else {
                        String code = String.valueOf(item);
                        list.add(new DeviceEntry(code, code, null, null, null, null, null));
                        modelCount++;
                    }
                }
                if (!list.isEmpty()) DEVICE_MAP.put(brand, list);
            }

            de.robv.android.xposed.XposedBridge.log(
                    "[MyHook] devices.json loaded from module assets: brands=" + brandCount +
                    ", models=" + modelCount
            );
        }
    } catch (Throwable t) {
        de.robv.android.xposed.XposedBridge.log("[MyHook] FAILED to load devices.json from module assets: " + t);
        // fallback: chỉ Oppo Find X7 Ultra
        DEVICE_MAP.put("Oppo", Arrays.asList(
                new DeviceEntry("PHY110", "Oppo Find X7 Ultra", "OP565FL1", "PHY110", "OP565FL1", "14", "14")
        ));
    }
}
