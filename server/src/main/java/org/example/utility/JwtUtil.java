package org.example.utility;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;

public class JwtUtil {
    private static final String SECRET_KEY = System.getenv("AUTH_SECRET_KEY") != null
            ? System.getenv("AUTH_SECRET_KEY")
            : "secretDefaultKey";

    private static final Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);

    public static String createJwt(String username, String role) {
        return JWT.create()
                .withSubject(username)
                .withClaim("role", role)
                .withExpiresAt(Instant.now().plusSeconds(3600))
                .sign(algorithm);
    }

    public static DecodedJWT decodeJWT(String token) {
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }
}
