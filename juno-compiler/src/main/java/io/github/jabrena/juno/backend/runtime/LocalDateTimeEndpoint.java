package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/** Validated build-time configuration for the HTTPS clock and its ISO-8601 JSON value. */
record LocalDateTimeEndpoint(String host, int port, String path, String jsonPath) {
    static final String URL_VARIABLE = "JUNO_LOCAL_DATE_TIME_URL";
    static final String JSON_PATH_VARIABLE = "JUNO_LOCAL_DATE_TIME_JSON_PATH";
    static final String DEFAULT_URL = "https://timeapi.io/api/Time/current/zone?timeZone=UTC";
    static final String DEFAULT_JSON_PATH = "dateTime";

    static LocalDateTimeEndpoint from(Map<String, String> environment) {
        URI url = parseUrl(environment.getOrDefault(URL_VARIABLE, DEFAULT_URL));
        String host = url.getHost();
        if (host == null || host.isBlank() || url.getUserInfo() != null || url.getFragment() != null) {
            throw invalidUrl("must have a host and must not contain user-info or a fragment");
        }
        int port = url.getPort() < 0 ? 443 : url.getPort();
        if (port < 1 || port > 65535) {
            throw invalidUrl("port must be between 1 and 65535");
        }
        String path = url.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (url.getRawQuery() != null) {
            path += "?" + url.getRawQuery();
        }
        if (host.length() >= 64 || path.length() >= 96) {
            throw invalidUrl("host must be at most 63 characters and path/query at most 95 characters");
        }
        String jsonPath = parseJsonPath(environment.getOrDefault(JSON_PATH_VARIABLE, DEFAULT_JSON_PATH));
        return new LocalDateTimeEndpoint(host, port, path, jsonPath);
    }

    String cppHost() {
        return cppString(host);
    }

    String cppPath() {
        return cppString(path);
    }

    String cppJsonPath() {
        return cppString(jsonPath);
    }

    private static URI parseUrl(String value) {
        try {
            URI url = new URI(value);
            if (!"https".equalsIgnoreCase(url.getScheme())) {
                throw invalidUrl("must be an HTTPS URL");
            }
            return url;
        } catch (URISyntaxException exception) {
            throw new CompileException(URL_VARIABLE + " is not a valid HTTPS URL: " + exception.getMessage(),
                    exception);
        }
    }

    private static String parseJsonPath(String value) {
        String path = value.trim();
        if (path.isEmpty() || path.length() > 96 || path.startsWith(".") || path.endsWith(".")
                || path.contains("..") || path.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new CompileException(JSON_PATH_VARIABLE
                    + " must be a non-empty dot-separated JSON object path of at most 96 characters");
        }
        return path;
    }

    private static CompileException invalidUrl(String reason) {
        return new CompileException(URL_VARIABLE + " " + reason);
    }

    private static String cppString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '"') {
                result.append('\\');
            }
            result.append(character);
        }
        return result.append('"').toString();
    }
}
