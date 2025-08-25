package com.example.myhook;

public class DeviceProfile {
    public final String model, brand, device, manufacturer, fingerprint;
    public final String androidId, imei, serial;
    public final String advertisingId;        // GAID
    public final boolean adLimitTracking;     // LAT

    // Firebase
    public final String firebaseInstallationsId;
    public final String firebaseAppInstanceId;
    public final String fcmToken;

    // User-Agent
    public final String userAgent;

    public DeviceProfile(String model, String brand, String device, String manufacturer,
                         String fingerprint, String androidId, String imei, String serial,
                         String advertisingId, boolean adLimitTracking,
                         String firebaseInstallationsId, String firebaseAppInstanceId, String fcmToken,
                         String userAgent) {
        this.model = model; this.brand = brand; this.device = device; this.manufacturer = manufacturer;
        this.fingerprint = fingerprint; this.androidId = androidId; this.imei = imei; this.serial = serial;
        this.advertisingId = advertisingId; this.adLimitTracking = adLimitTracking;
        this.firebaseInstallationsId = firebaseInstallationsId;
        this.firebaseAppInstanceId = firebaseAppInstanceId;
        this.fcmToken = fcmToken;
        this.userAgent = userAgent;
    }
}
