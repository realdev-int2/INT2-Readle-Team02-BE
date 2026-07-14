package com.realdev.readle.global.security;

import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecurityProperties properties;
  private final Clock clock;
  private final SecretKey signingKey;

  @Autowired
  public JwtService(SecurityProperties properties) {
    this(properties, Clock.systemUTC());
  }

  JwtService(SecurityProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.signingKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String issue(String memberUuid) {
    Date now = Date.from(clock.instant());
    Date expiry =
        Date.from(clock.instant().plus(Duration.ofMinutes(properties.accessTokenMinutes())));
    return Jwts.builder()
        .issuer(properties.jwtIssuer())
        .audience()
        .add(properties.jwtAudience())
        .and()
        .subject(memberUuid)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(signingKey, Jwts.SIG.HS256)
        .compact();
  }

  public String memberUuid(String token) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(signingKey)
              .sig()
              .clear()
              .add(Jwts.SIG.HS256)
              .and()
              .clock(() -> Date.from(clock.instant()))
              .requireIssuer(properties.jwtIssuer())
              .requireAudience(properties.jwtAudience())
              .build()
              .parseSignedClaims(token)
              .getPayload();
      if (claims.getSubject() == null || claims.getSubject().isBlank()) {
        throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
      }
      return claims.getSubject();
    } catch (CustomException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }
  }
}
