package com.rtms.backend.training.enums;

import java.math.BigDecimal;

/**
 * Nhóm cự ly đua. Ranh giới theo cách phân loại phổ biến (Anh, Hồng Kông): sprint đến 1400m, mile đến 1800m,
 * trung cự ly đến 2400m, trên đó là đường trường. Mỗi mốc thuộc về nhóm thấp hơn (1400m là SPRINT).
 * (A race-distance band. The limits follow the common classification (UK, Hong Kong): sprint up to 1400m, mile up
 * to 1800m, middle distance up to 2400m, and above that staying races. Each limit belongs to the lower band
 * (1400m is SPRINT).)
 *
 * Đây là NƠI DUY NHẤT định nghĩa ranh giới, muốn đổi chỉ sửa ở đây.
 * (This is the ONLY place that defines the limits; change them here.)
 */
public enum DistanceBand {
    SPRINT(1400),
    MILE(1800),
    MIDDLE(2400),
    STAYER(Integer.MAX_VALUE);

    private final int maxMeters;

    DistanceBand(int maxMeters) {
        this.maxMeters = maxMeters;
    }

    /** Nhóm chứa cự ly này. (The band that contains this distance.) */
    public static DistanceBand of(BigDecimal meters) {
        for (DistanceBand band : values()) {
            if (meters.compareTo(BigDecimal.valueOf(band.maxMeters)) <= 0) {
                return band;
            }
        }
        return STAYER;
    }
}
