package com.xiaomi.mitv.light.core;

public final class DeviceIdentity {
    public final String brand, product, device, model, hardware, fingerprint;
    public final Integer projectId;
    public DeviceIdentity(String brand, String product, String device, String model,
                          String hardware, String fingerprint, Integer projectId) {
        this.brand=brand;this.product=product;this.device=device;this.model=model;
        this.hardware=hardware;this.fingerprint=fingerprint;this.projectId=projectId;
    }
}
