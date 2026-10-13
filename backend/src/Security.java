import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing and login sessions.
 *
 * Passwords are never stored. We store a salted PBKDF2 hash in the form
 *     iterations:salt:hash        (salt and hash are Base64 text)
 *
 * After login the server gives the browser a random token. The browser sends it
 * back in the "Authorization: Bearer <token>" header. Tokens are kept in memory,
 * so restarting the server logs everybody out.
 */
public class Security {

    private static final int ITERATIONS = 120_000;
    private static final long SESSION_MILLIS = 8L * 60 * 60 * 1000; // 8 hours
    private static final SecureRandom RANDOM = new SecureRandom();

    private static class Session {
        final long customerId;
        final long expiresAt;

        Session(long customerId, long expiresAt) {
            this.customerId = customerId;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    // ---------------------------- passwords ----------------------------

    public static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verifyPassword(String password, String stored) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 3) return false;
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual); // constant-time compare
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available", e);
        }
    }

    // ----------------------------- sessions ----------------------------

    public static String createSession(long customerId) {
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(e -> e.getValue().expiresAt < now); // tidy up old ones

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        SESSIONS.put(token, new Session(customerId, now + SESSION_MILLIS));
        return token;
    }

    /** Returns the customer id for a valid token, or null if the token is missing/unknown/expired. */
    public static Long customerIdFor(String token) {
        if (token == null) return null;
        Session s = SESSIONS.get(token);
        if (s == null) return null;
        if (s.expiresAt < System.currentTimeMillis()) {
            SESSIONS.remove(token);
            return null;
        }
        return s.customerId;
    }

    public static void endSession(String token) {
        if (token != null) SESSIONS.remove(token);
    }
}
