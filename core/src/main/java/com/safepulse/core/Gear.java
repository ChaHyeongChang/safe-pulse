package com.safepulse.core;

/** 변속 레버 위치. AAOS VehicleGear 값과 시나리오 문자(P/R/N/D)를 모두 해석한다. */
public enum Gear {
    PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("-");

    public final String symbol;

    Gear(String symbol) {
        this.symbol = symbol;
    }

    /** AAOS VehicleGear: NEUTRAL=0x1, REVERSE=0x2, PARK=0x4, DRIVE=0x8, GEAR_1..9=0x10~. */
    public static Gear fromVhal(int value) {
        switch (value) {
            case 0x1: return NEUTRAL;
            case 0x2: return REVERSE;
            case 0x4: return PARK;
            case 0x8: return DRIVE;
            default: return value >= 0x10 ? DRIVE : UNKNOWN;
        }
    }

    public static Gear parse(String s) {
        if (s == null) return UNKNOWN;
        switch (s.trim().toUpperCase()) {
            case "P": return PARK;
            case "R": return REVERSE;
            case "N": return NEUTRAL;
            case "D": return DRIVE;
            default: return UNKNOWN;
        }
    }

    public boolean isMovingGear() {
        return this == DRIVE || this == REVERSE;
    }
}
