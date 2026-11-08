package vn.smarttask.notify.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;
import vn.smarttask.admin.repo.SystemSettingRepository;

/**
 * VAPID (Voluntary Application Server Identification) key pair for Web Push.
 *
 * <p>Key pair is persisted in {@code system_setting} table so subscriptions
 * remain valid across server restarts. Public key is exposed to clients for
 * PushManager.subscribe; private key is retained for signing push payloads.
 */
@Service
public class VapidKeyService {

  private static final String PUBLIC_KEY_SETTING = "vapid.public_key";
  private static final String PRIVATE_KEY_SETTING = "vapid.private_key";

  private final SystemSettingRepository settings;
  private final KeyPair keyPair;

  public VapidKeyService(SystemSettingRepository settings) {
    this.settings = settings;
    this.keyPair = loadOrGenerate();
  }

  private KeyPair loadOrGenerate() {
    try {
      var pub = settings.findById(PUBLIC_KEY_SETTING);
      var priv = settings.findById(PRIVATE_KEY_SETTING);
      if (pub.isPresent() && priv.isPresent()) {
        // Reconstruct KeyPair from stored PKCS#8 / X.509 encoded bytes.
        byte[] pubBytes = Base64.getDecoder().decode(jsonStringValue(pub.get().getValue()));
        byte[] privBytes = Base64.getDecoder().decode(jsonStringValue(priv.get().getValue()));
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("EC");
        java.security.PublicKey pKey = kf.generatePublic(
            new java.security.spec.X509EncodedKeySpec(pubBytes));
        java.security.PrivateKey pPriv = kf.generatePrivate(
            new java.security.spec.PKCS8EncodedKeySpec(privBytes));
        return new KeyPair(pKey, pPriv);
      }
    } catch (Exception ignored) {
      // Fall through to generate new.
    }
    return generateNewAndSave();
  }

  private KeyPair generateNewAndSave() {
    try {
      KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
      g.initialize(256, new SecureRandom());
      KeyPair kp = g.generateKeyPair();
      settings.save(new vn.smarttask.admin.entity.SystemSetting(
          PUBLIC_KEY_SETTING, jsonString(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()))));
      settings.save(new vn.smarttask.admin.entity.SystemSetting(
          PRIVATE_KEY_SETTING, jsonString(Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()))));
      return kp;
    } catch (Exception e) {
      throw new IllegalStateException("Cannot generate VAPID key pair", e);
    }
  }

  /** Public key as base64url (no padding) for PushManager.subscribe applicationServerKey. */
  public String getPublicKey() {
    return base64UrlEncode(keyPair.getPublic().getEncoded());
  }

  public KeyPair getKeyPair() { return keyPair; }

  private static String base64UrlEncode(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String jsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String jsonStringValue(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return value;
  }
}
