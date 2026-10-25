// Quan ly phien dang nhap: login / logout / refresh, single-session,
// reauth flow 428. Token luu trong SecureStore (production:
// flutter_secure_storage, ma hoa platform keystore/keychain), khong luu
// plaintext.
//
// Doc ky truoc khi sua (IdentityService.java, AuthController.java):
// - Login: POST /auth/login {username, password, device_label, client_type}
//   -> ApiResult.data {access_token, refresh_token, expires_at, user_id}.
//   Backend KHONG tra `session_id`, KHONG co 409 SESSION_ALREADY_ACTIVE
//   (login luon revoke phien cu roi tao moi — single-session phia server).
//   Xu ly 409 giu lai o dang phong thu cho backend tuong lai.
// - Refresh: POST /auth/refresh {refresh_token} -> cap token moi (rotation).
//   Refresh hong (401/revoked/het han) nghia la phien ket thuc: xoa token +
//   xoa cache offline (clearOnRevoke) qua [onSessionEnded] — day la duong
//   refresh-expired da duoc sua (truoc day chi xoa token).
// - Moi duong ket thuc phien (logout / revoke / expired / refresh-expired)
//   deu di qua [_clear] va goi [onSessionEnded] de main.dart xoa offline +
//   huy push dang ky. Khong tu dong luu noi dung rieng tu offline.
library;

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../cache/secure_store.dart';
import 'device_info.dart';

const _kAccessToken = 'stm_access_token';
const _kRefreshToken = 'stm_refresh_token';
const _kUserId = 'stm_user_id';
// Legacy key: backend khong tra session_id, giu de don du lieu cu neu co.
const _kSessionIdLegacy = 'stm_session_id';

/// Ly do phien ket thuc (de UI hien thi dung).
enum SessionEndReason {
  logout,
  revokedElsewhere,
  expired,
  reauthFailed,
}

/// Trang thai xac thuc don gian cho UI.
enum AuthStatus { unknown, authenticated, unauthenticated, reauthRequired }

class SessionManager extends ChangeNotifier {
  final ApiClient Function(TokenReader reader) clientFactory;
  final SecureStore storage;

  /// Goi sau MOI lan phien ket thuc (logout/revoke/expired/refresh-expired)
  /// de lop Join (main.dart) xoa cache offline + huy push.
  Future<void> Function()? onSessionEnded;

  late ApiClient api;

  AuthStatus status = AuthStatus.unknown;
  SessionEndReason? endReason;
  String? userId;
  String? pendingReauthActionCode;
  bool singleSessionNotice = false;
  String? singleSessionDeviceLabel;

  SessionManager({required this.clientFactory, SecureStore? storage})
      : storage = storage ?? const FlutterSecureStore() {
    api = clientFactory(_readAccessToken);
  }

  Future<String?> _readAccessToken() => storage.read(_kAccessToken);

  Future<void> restore() async {
    try {
      final token = await storage.read(_kAccessToken);
      userId = await storage.read(_kUserId);
      status = token == null
          ? AuthStatus.unauthenticated
          : AuthStatus.authenticated;
    } on Exception {
      // Store khong kha dung (vd test khong co platform channel):
      // coi nhu chua dang nhap, khong crash app.
      status = AuthStatus.unauthenticated;
    }
    notifyListeners();
  }

  Future<Map<String, Object?>> _post(
    String path,
    Map<String, Object?> body,
  ) async {
    final res = await api.dio.post<Map<String, dynamic>>(path, data: body);
    return PageParser.parseObject(res.data);
  }

  /// Dang nhap. Gui device_label that theo nen tang + client_type
  /// (IdentityService.login nhan ca hai; client_type chi de ghi nhan).
  /// Backend hien tai khong tra 409 SESSION_ALREADY_ACTIVE (luon revoke phien
  /// cu): nhanh confirm_revoke chi la phong thu, khong bao gio kich hoat.
  Future<void> login({
    required String username,
    required String password,
    String? deviceLabel,
    bool confirmRevokeExistingSession = false,
  }) async {
    try {
      final body = <String, Object?>{
        'username': username,
        'password': password,
        'device_label': deviceLabel ?? DeviceInfo.deviceLabel(),
        'client_type': DeviceInfo.clientType().headerValue,
        // Phong thu: backend hien tai khong doc field nay.
        'confirm_revoke_existing_session': confirmRevokeExistingSession,
      };
      final data = await _post(ApiPaths.login, body);
      await _persistSession(data);
      status = AuthStatus.authenticated;
      singleSessionNotice = false;
      notifyListeners();
    } on DioException catch (e) {
      final err = e.error;
      if (err is ApiException && err.envelope.code == 'SESSION_ALREADY_ACTIVE') {
        singleSessionNotice = true;
        singleSessionDeviceLabel =
            (err.envelope.details['device_label'])?.toString();
        notifyListeners();
      }
      rethrow;
    }
  }

  Future<void> _persistSession(Map<String, Object?> data) async {
    final access = data['access_token']?.toString();
    final refresh = data['refresh_token']?.toString();
    userId = data['user_id']?.toString();
    if (access != null) await storage.write(_kAccessToken, access);
    if (refresh != null) {
      await storage.write(_kRefreshToken, refresh);
    }
    if (userId != null) {
      await storage.write(_kUserId, userId!);
    }
    // Don key session_id cu (backend khong tra): tranh scope treo.
    await storage.delete(_kSessionIdLegacy);
  }

  /// Refresh access token bang refresh_token (rotation server-side).
  /// That bai -> phien ket thuc + goi [onSessionEnded] (duong
  /// refresh-expired: xoa ca offline, khong chi token).
  Future<bool> refresh() async {
    final refreshToken = await storage.read(_kRefreshToken);
    if (refreshToken == null) return false;
    try {
      final data = await _post(ApiPaths.refresh, {
        'refresh_token': refreshToken,
      });
      await _persistSession(data);
      status = AuthStatus.authenticated;
      notifyListeners();
      return true;
    } on DioException {
      await _clear(reason: SessionEndReason.expired);
      return false;
    }
  }

  Future<void> logout() async {
    try {
      await api.dio.post<Object?>(ApiPaths.logout, data: const {});
    } on DioException {
      // Logout best-effort: van xoa token local ke ca khi mang loi.
    }
    await _clear(reason: SessionEndReason.logout);
  }

  /// Server dong WS phien cu bang close code rieng 4401 hoac API tra
  /// INVALID_SESSION -> danh dau revoke de UI + cache xu ly.
  Future<void> handleRevokedElsewhere() async {
    await _clear(reason: SessionEndReason.revokedElsewhere);
  }

  Future<void> _clear({required SessionEndReason reason}) async {
    await storage.delete(_kAccessToken);
    await storage.delete(_kRefreshToken);
    await storage.delete(_kUserId);
    await storage.delete(_kSessionIdLegacy);
    userId = null;
    endReason = reason;
    status = AuthStatus.unauthenticated;
    pendingReauthActionCode = null;
    notifyListeners();
    final cb = onSessionEnded;
    if (cb != null) await cb();
  }

  /// Danh dau can reauth (428 REAUTH_REQUIRED) cho action nhay cam.
  void requireReauth(String actionCode) {
    pendingReauthActionCode = actionCode;
    status = AuthStatus.reauthRequired;
    notifyListeners();
  }

  void clearReauth() {
    pendingReauthActionCode = null;
    if (status == AuthStatus.reauthRequired) {
      status = AuthStatus.authenticated;
    }
    notifyListeners();
  }
}
