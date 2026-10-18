package com.logistics.proyect.group5.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utilidades para generar {@code slug} a partir de nombres.
 */
public final class SlugUtils {

    private SlugUtils() {
    }

    /**
     * Convierte "Audífonos &amp; Audio" en "audifonos-audio".
     *
     * @return el slug normalizado, o {@code ""} si no queda ningún carácter válido
     */
    public static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")     // quita acentos
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return normalized;
    }
}
