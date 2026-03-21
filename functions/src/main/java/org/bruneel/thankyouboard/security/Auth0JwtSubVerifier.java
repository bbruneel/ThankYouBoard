package org.bruneel.thankyouboard.security;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal Auth0 JWT verification:
 * - verifies RS256 signature using Auth0 JWKS
 * - validates issuer, audience, exp
 * - returns the JWT "sub" claim when valid
 *
 * We keep this dependency-free (no jose/java-jwt libraries) using standard JCA APIs.
 */
public class Auth0JwtSubVerifier {

    private static final Logger log = LoggerFactory.getLogger(Auth0JwtSubVerifier.class);
    private static final Gson GSON = new Gson();

    private final String issuer;
    private final String audience;
    private final String jwksUrl;

    private final HttpClient httpClient;

    // Simple in-memory JWKS cache per Lambda instance.
    private volatile JwksCache cache = new JwksCache(Map.of(), Instant.EPOCH);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    public Auth0JwtSubVerifier(String auth0Domain, String auth0Audience) {
        if (auth0Domain == null || auth0Domain.isBlank()) {
            throw new IllegalArgumentException("auth0Domain must be set");
        }
        if (auth0Audience == null || auth0Audience.isBlank()) {
            throw new IllegalArgumentException("auth0Audience must be set");
        }
        this.issuer = "https://" + auth0Domain + "/";
        this.audience = auth0Audience;
        this.jwksUrl = "https://" + auth0Domain + "/.well-known/jwks.json";
        this.httpClient = HttpClient.newHttpClient();
    }

    public String verifyAndExtractSub(String jwt) {
        try {
            if (jwt == null || jwt.isBlank()) return null;

            String[] parts = jwt.split("\\.");
            if (parts.length != 3) return null;

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            JsonObject header = GSON.fromJson(headerJson, JsonObject.class);
            JsonObject payload = GSON.fromJson(payloadJson, JsonObject.class);
            if (header == null || payload == null) return null;

            String alg = getAsString(header, "alg");
            if (!"RS256".equals(alg)) {
                return null;
            }

            String tokenIssuer = getAsString(payload, "iss");
            if (tokenIssuer == null || !issuer.equals(tokenIssuer)) {
                return null;
            }

            if (!audienceMatches(payload)) {
                return null;
            }

            long exp = getAsLong(payload, "exp");
            if (exp <= 0) return null;
            long now = Instant.now().getEpochSecond();
            if (now >= exp) return null;

            String kid = getAsString(header, "kid");
            if (kid == null || kid.isBlank()) return null;

            PublicKey key = getKeyForKid(kid);
            if (key == null) return null;

            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            String signingInput = parts[0] + "." + parts[1];

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            boolean verified = signature.verify(signatureBytes);
            if (!verified) return null;

            return getAsString(payload, "sub");
        } catch (Exception e) {
            // Keep response stable: return null means unauthorized.
            log.debug("JWT verification failed: {}", e.getMessage());
            return null;
        }
    }

    private PublicKey getKeyForKid(String kid) throws Exception {
        JwksCache local = cache;
        if (local.keys.isEmpty() || Instant.now().isAfter(local.fetchedAt.plus(CACHE_TTL))) {
            synchronized (this) {
                // Re-check after acquiring lock.
                local = cache;
                if (local.keys.isEmpty() || Instant.now().isAfter(local.fetchedAt.plus(CACHE_TTL))) {
                    cache = fetchJwksCache();
                    local = cache;
                }
            }
        }

        return local.keys.get(kid);
    }

    private JwksCache fetchJwksCache() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(jwksUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            log.warn("JWKS fetch failed. status={} url={}", resp.statusCode(), jwksUrl);
            return new JwksCache(Map.of(), Instant.now());
        }

        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        if (json == null) return new JwksCache(Map.of(), Instant.now());

        JsonArray keys = json.getAsJsonArray("keys");
        if (keys == null) return new JwksCache(Map.of(), Instant.now());

        Map<String, PublicKey> keysByKid = new HashMap<>();
        for (JsonElement el : keys) {
            if (!el.isJsonObject()) continue;
            JsonObject keyObj = el.getAsJsonObject();
            String keyKid = getAsString(keyObj, "kid");
            String kty = getAsString(keyObj, "kty");
            if (keyKid == null || keyKid.isBlank()) continue;
            if (!"RSA".equals(kty)) continue;

            String n = getAsString(keyObj, "n");
            String e = getAsString(keyObj, "e");
            if (n == null || e == null) continue;

            PublicKey pk = rsaPublicKeyFromModulusAndExponent(n, e);
            if (pk != null) {
                keysByKid.put(keyKid, pk);
            }
        }

        return new JwksCache(keysByKid, Instant.now());
    }

    private static PublicKey rsaPublicKeyFromModulusAndExponent(String nB64Url, String eB64Url) throws Exception {
        byte[] nBytes = Base64.getUrlDecoder().decode(nB64Url);
        byte[] eBytes = Base64.getUrlDecoder().decode(eB64Url);
        BigInteger n = new BigInteger(1, nBytes);
        BigInteger e = new BigInteger(1, eBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    private boolean audienceMatches(JsonObject payload) {
        JsonElement aud = payload.get("aud");
        if (aud == null) return false;

        if (aud.isJsonArray()) {
            JsonArray arr = aud.getAsJsonArray();
            for (JsonElement el : arr) {
                if (el != null && el.getAsString().equals(audience)) {
                    return true;
                }
            }
            return false;
        }
        if (aud.isJsonPrimitive()) {
            return audience.equals(aud.getAsString());
        }
        return false;
    }

    private static String getAsString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        if (!el.isJsonPrimitive()) return null;
        try {
            return el.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static long getAsLong(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return -1;
        if (!el.isJsonPrimitive()) return -1;
        try {
            return el.getAsLong();
        } catch (Exception e) {
            return -1;
        }
    }

    private record JwksCache(Map<String, PublicKey> keys, Instant fetchedAt) {}
}

