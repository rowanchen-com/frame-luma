package com.xiaomi.mitv.light.core;

/** Identity returned by a verified controller handshake, separate from a USB bridge match. */
public final class LightIdentity {
    public final String model, firmware;
    public LightIdentity(String model, String firmware) {
        if(model==null||model.trim().isEmpty()||model.length()>128||firmware==null||firmware.length()>128)
            throw new IllegalArgumentException("Invalid controller identity");
        this.model=model;this.firmware=firmware;
    }
}
