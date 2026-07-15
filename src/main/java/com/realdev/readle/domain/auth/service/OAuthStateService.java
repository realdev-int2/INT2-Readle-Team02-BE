package com.realdev.readle.domain.auth.service;

import com.realdev.readle.domain.auth.entity.OAuthAuthorizationState;
import com.realdev.readle.domain.auth.repository.OAuthAuthorizationStateRepository;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Service
public class OAuthStateService {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OAuthAuthorizationStateRepository stateRepository;
  private final SecurityProperties properties;
  private final Clock clock;
  private final List<PathPattern> allowedReturnPaths;

  @Autowired
  public OAuthStateService(
      OAuthAuthorizationStateRepository stateRepository, SecurityProperties properties) {
    this(stateRepository, properties, Clock.systemUTC());
  }

  OAuthStateService(
      OAuthAuthorizationStateRepository stateRepository,
      SecurityProperties properties,
      Clock clock) {
    this.stateRepository = stateRepository;
    this.properties = properties;
    this.clock = clock;
    PathPatternParser parser = new PathPatternParser();
    this.allowedReturnPaths =
        properties.allowedReturnPaths().stream()
            .map(
                pattern -> {
                  if ("/**".equals(pattern)) {
                    throw new IllegalArgumentException("OAuth return path pattern must not be /**");
                  }
                  return parser.parse(pattern);
                })
            .toList();
  }

  @Transactional
  public OAuthStart create(OAuthProvider provider, String requestedReturnTo) {
    String state = randomUrlValue(32);
    String verifier = randomUrlValue(32);
    LocalDateTime expiry = now().plusMinutes(properties.stateMinutes());
    stateRepository.save(
        OAuthAuthorizationState.create(
            sha256(state),
            provider,
            safeReturnTo(requestedReturnTo),
            encrypt(verifier, provider),
            expiry));
    return new OAuthStart(state, pkceChallenge(verifier));
  }

  @Transactional(noRollbackFor = CustomException.class)
  public ConsumedOAuthState consume(OAuthProvider provider, String rawState) {
    if (rawState == null || rawState.isBlank()) {
      throw oauthFailure();
    }
    OAuthAuthorizationState state =
        stateRepository
            .findByStateHashAndOauthProvider(sha256(rawState), provider)
            .orElseThrow(this::oauthFailure);
    if (!state.isUsableAt(now())) {
      stateRepository.delete(state);
      throw oauthFailure();
    }
    state.consume(now());
    stateRepository.saveAndFlush(state);
    String verifier = decrypt(state.getCodeVerifierCiphertext(), provider);
    stateRepository.delete(state);
    return new ConsumedOAuthState(state.getReturnTo(), verifier);
  }

  public String safeReturnTo(String returnTo) {
    if (returnTo == null
        || returnTo.isBlank()
        || returnTo.startsWith("//")
        || returnTo.indexOf('\\') >= 0
        || returnTo.indexOf('\r') >= 0
        || returnTo.indexOf('\n') >= 0) {
      return "/";
    }
    try {
      URI uri = new URI(returnTo);
      String path = uri.getPath();
      if (uri.getScheme() != null
          || uri.getAuthority() != null
          || uri.getFragment() != null
          || path == null
          || !path.startsWith("/")
          || path.startsWith("//")
          || path.contains("\\")
          || path.contains("\r")
          || path.contains("\n")) {
        return "/";
      }
      PathContainer pathContainer = PathContainer.parsePath(path);
      return allowedReturnPaths.stream().anyMatch(pattern -> pattern.matches(pathContainer))
          ? returnTo
          : "/";
    } catch (URISyntaxException exception) {
      return "/";
    }
  }

  private String pkceChallenge(String verifier) {
    return URL_ENCODER.encodeToString(digest(verifier));
  }

  private String randomUrlValue(int bytes) {
    byte[] random = new byte[bytes];
    RANDOM.nextBytes(random);
    return URL_ENCODER.encodeToString(random);
  }

  private String sha256(String value) {
    return HexFormat.of().formatHex(digest(value));
  }

  private byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String encrypt(String plaintext, OAuthProvider provider) {
    try {
      byte[] iv = new byte[12];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
      cipher.updateAAD(provider.name().getBytes(StandardCharsets.UTF_8));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return URL_ENCODER.encodeToString(
          ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("OAuth state encryption unavailable", exception);
    }
  }

  private String decrypt(String ciphertext, OAuthProvider provider) {
    try {
      byte[] all = URL_DECODER.decode(ciphertext);
      if (all.length <= 12) {
        throw oauthFailure();
      }
      ByteBuffer buffer = ByteBuffer.wrap(all);
      byte[] iv = new byte[12];
      buffer.get(iv);
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
      cipher.updateAAD(provider.name().getBytes(StandardCharsets.UTF_8));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (CustomException exception) {
      throw exception;
    } catch (GeneralSecurityException | IllegalArgumentException | ProviderException exception) {
      throw oauthFailure();
    }
  }

  private SecretKeySpec encryptionKey() {
    byte[] key = properties.stateEncryptionKey().getBytes(StandardCharsets.UTF_8);
    if (key.length != 16 && key.length != 24 && key.length != 32) {
      throw new IllegalStateException("OAuth state encryption key has invalid length");
    }
    return new SecretKeySpec(key, "AES");
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private CustomException oauthFailure() {
    return new CustomException(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
  }

  public record OAuthStart(String state, String codeChallenge) {}

  public record ConsumedOAuthState(String returnTo, String codeVerifier) {}
}
