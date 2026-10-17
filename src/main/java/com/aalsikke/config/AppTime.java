package com.aalsikke.config;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * KAN-364: the app's time zone. Aal Sikke is built for Lebanon, so days, weeks and weekly repeats
 * follow Beirut time (including the summer/winter clock change), whatever zone the server runs in.
 * Never use ZoneId.systemDefault() or LocalDate.now() without a zone: Railway's server runs in UTC.
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Beirut");

    private AppTime() {}

    /** Today's date in Beirut. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}