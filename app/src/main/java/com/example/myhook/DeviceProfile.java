package com.example.myhook;

public class DeviceProfile {
    public final String model;           // modelCode (vd SM-G991B / PHY110)
    public final String brand;
    public final String device;          // deviceCode (vd o1s / husky / OP565FL1)
    public final String manufacturer;
    public final String fingerprint;

    public final String androidId;
    public final String imei;
    public final String serial;

    public final String advertisingId;
    public final boolean adLimitTracking;

    public final String firebaseInstallationsId;
    public final String appInstanceId;
    public final String fcmToken;

    public final String userAgent;
    public final String marketingName;   // NEW (đã có ở bản trước)

    // NEW: vendor props
    public final String vendorProduct;   // map -> ro.build.product / ro.vendor.product.oem
    public final String vendorDevice;    // map -> ro.vendor.product.device.oem

    public DeviceProfile(String model, String brand, String device, String manufacturer, String fingerprint,
                         String androidId, String imei, String serial,
                         String advertisingId, boolean adLimitTracking,
                         String firebaseInstallationsId, String appInstanceId, String fcmToken,
                         String userAgent, String marketingName,
                         String vendorProduct, String vendorDevice) {
        this.model = model;
        this.brand = brand;
        this.device = device;
        this.manufacturer = manufacturer;
        this.fingerprint = fingerprint;
        this.androidId = androidId;
        this.imei = imei;
        this.serial = serial;
        this.advertisingId = advertisingId;
        this.adLimitTracking = adLimitTracking;
        this.firebaseInstallationsId = firebaseInstallationsId;
        this.appInstanceId = appInstanceId;
        this.fcmToken = fcmToken;
        this.userAgent = userAgent;
        this.marketingName = marketingName;
        this.vendorProduct = vendorProduct;
        this.vendorDevice = vendorDevice;
    }
}
