package com.example.myhook;

public class DeviceProfile {
    // Build / product identity
    public final String model;           // Build.MODEL
    public final String brand;
    public final String device;          // Build.DEVICE
    public final String manufacturer;
    public final String fingerprint;

    // IDs
    public final String androidId;
    public final String imei;
    public final String serial;

    // Ads / Firebase
    public final String advertisingId;
    public final boolean adLimitTracking;
    public final String firebaseInstallationsId;
    public final String appInstanceId;
    public final String fcmToken;

    // UA + marketing
    public final String userAgent;
    public final String marketingName;

    // Vendor props
    public final String vendorProduct;   // ro.build.product / ro.vendor.product.oem
    public final String vendorDevice;    // ro.vendor.product.device.oem

    // Build number
    public final String buildId;         // e.g. TQ3A.230705.001
    public final String buildIncremental;// e.g. 1234567 or C1

    public DeviceProfile(String model, String brand, String device, String manufacturer, String fingerprint,
                         String androidId, String imei, String serial,
                         String advertisingId, boolean adLimitTracking,
                         String firebaseInstallationsId, String appInstanceId, String fcmToken,
                         String userAgent, String marketingName,
                         String vendorProduct, String vendorDevice,
                         String buildId, String buildIncremental) {
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

        this.buildId = buildId;
        this.buildIncremental = buildIncremental;
    }
}
