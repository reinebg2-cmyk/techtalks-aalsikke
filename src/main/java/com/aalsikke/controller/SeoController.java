package com.aalsikke.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves /robots.txt and /sitemap.xml so search engines can find the public pages.
 * The site address comes from app.base-url (the same value used in email links);
 * if it is empty we fall back to the address of the incoming request.
 */
@RestController
public class SeoController {

    /** Public pages only. App pages (dashboard, tasks, ...) need a login and are not listed. */
    private static final List<String> PUBLIC_PAGES = List.of(
            "/", "/help.html", "/login.html", "/register.html", "/forgot-password.html");

    private final String configuredBaseUrl;

    public SeoController(@Value("${app.base-url:}") String configuredBaseUrl) {
        this.configuredBaseUrl = configuredBaseUrl == null ? "" : configuredBaseUrl.trim();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots(HttpServletRequest request) {
        return "User-agent: *\n"
                + "Allow: /\n"
                + "Disallow: /api/\n"
                + "Disallow: /dashboard.html\n"
                + "Disallow: /calendar.html\n"
                + "Disallow: /tasks.html\n"
                + "Disallow: /settings.html\n"
                + "Disallow: /reset-password.html\n"
                + "Disallow: /verify-email.html\n\n"
                + "Sitemap: " + baseUrl(request) + "/sitemap.xml\n";
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        String base = baseUrl(request);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : PUBLIC_PAGES) {
            xml.append("  <url><loc>").append(base).append(path).append("</loc></url>\n");
        }
        xml.append("</urlset>\n");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml.toString());
    }

    private String baseUrl(HttpServletRequest request) {
        if (!configuredBaseUrl.isEmpty()) {
            return configuredBaseUrl.endsWith("/")
                    ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
                    : configuredBaseUrl;
        }
        String scheme = request.getScheme();
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) scheme = forwarded.split(",")[0].trim();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort || request.getHeader("X-Forwarded-Proto") != null ? "" : ":" + port);
    }
}
