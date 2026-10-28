package com.xiaomi.mitv.light.core;

/** Platform aliases never substitute for a required exact project identifier. */
public final class MatchRule {
    private final String brand,product,device,model,hardware,firmware;
    private final int project;
    public MatchRule(String brand,String product,String device,String model,String hardware,String firmware,int project) {
        this.brand=brand;this.product=product;this.device=device;this.model=model;
        this.hardware=hardware;this.firmware=firmware;this.project=project;
    }
    public boolean matchesPlatform(DeviceIdentity d) {
        return brand.equalsIgnoreCase(d.brand)&&product.equalsIgnoreCase(d.product)
            &&device.equalsIgnoreCase(d.device)&&model.equalsIgnoreCase(d.model)
            &&hardware.equalsIgnoreCase(d.hardware)&&d.fingerprint.contains(firmware);
    }
    public boolean matches(DeviceIdentity d) {
        return matchesPlatform(d)&&d.projectId!=null&&d.projectId==project;
    }
}
