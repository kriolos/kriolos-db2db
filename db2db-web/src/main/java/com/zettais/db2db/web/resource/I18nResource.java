package com.zettais.db2db.web.resource;

import com.zettais.db2db.core.i18n.Language;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.util.*;

/**
 * Exposes server-side i18n ResourceBundle properties as JSON and manages
 * the 'zettais.db2db.locale' HTTP cookie for cross-tier synchronization.
 */
@Path("/api/v1/i18n")
@Produces(MediaType.APPLICATION_JSON)
public class I18nResource {

    public static final String COOKIE_NAME = "zettais.db2db.locale";
    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    @GET
    public Response getMessages(
            @QueryParam("lang") String langParam,
            @CookieParam(COOKIE_NAME) Cookie cookie
    ) {
        String effectiveLangStr = (langParam != null && !langParam.isBlank())
                ? langParam
                : (cookie != null ? cookie.getValue() : "en");

        Language language = Language.fromString(effectiveLangStr);
        String langCode = language.name().toLowerCase();
        Locale locale = language.getLocale();

        Map<String, String> messages = new LinkedHashMap<>();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale, new ResourceBundle.Control() {
                @Override
                public List<String> getFormats(String baseName) {
                    return FORMAT_PROPERTIES;
                }
            });

            Enumeration<String> keys = bundle.getKeys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                messages.put(key, bundle.getString(key));
            }
        } catch (MissingResourceException ignored) {
        }

        NewCookie localeCookie = new NewCookie.Builder(COOKIE_NAME)
                .value(langCode)
                .path("/")
                .maxAge(365 * 24 * 60 * 60)
                .httpOnly(false)
                .sameSite(NewCookie.SameSite.LAX)
                .build();

        return Response.ok(messages)
                .cookie(localeCookie)
                .header("X-DB2DB-Language", langCode)
                .build();
    }

    @POST
    @Path("/locale")
    public Response setLocale(@QueryParam("lang") @DefaultValue("en") String langParam) {
        Language language = Language.fromString(langParam);
        String langCode = language.name().toLowerCase();

        NewCookie localeCookie = new NewCookie.Builder(COOKIE_NAME)
                .value(langCode)
                .path("/")
                .maxAge(365 * 24 * 60 * 60)
                .httpOnly(false)
                .sameSite(NewCookie.SameSite.LAX)
                .build();

        return Response.ok(Map.of(
                "status", "UPDATED",
                "language", langCode,
                "cookie", COOKIE_NAME
        )).cookie(localeCookie).build();
    }
}
