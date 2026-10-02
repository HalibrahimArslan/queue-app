package com.rabbitlab.event;

import java.math.BigDecimal;

/** Event'lerin kendi kendini doğrulaması için küçük yardımcılar. Geçersiz event oluşturulamaz. */
final class Require {

    private Require() {
    }

    static String notBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " boş olamaz");
        }
        return value;
    }

    static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " sıfırdan büyük olmalı: " + value);
        }
        return value;
    }

    static int notNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " negatif olamaz: " + value);
        }
        return value;
    }

    static long positive(long value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " 1 veya daha büyük olmalı: " + value);
        }
        return value;
    }
}
