// Offline allowlist theo DEC-12 phuong an A (khuyen nghi, an toan nhat):
// CHI duoc cache: ho so cua chinh minh, TEN Workspace/Project minh la
// thanh vien, cau hinh UI, thoi diem sync cuoi, notification da redact.
// KHONG cache: Task title/description, file, so lieu, noi dung Project/Task
// rieng tu. Neu chua reauthorize sau khi mat phien -> KHONG mo offline.
//
// Yeu cau: ma hoa (SecureStore: production la flutter_secure_storage),
// TTL mac dinh 24h, gan scope user_id (backend login KHONG tra session_id —
// xem SessionManager), xoa khi phat hien revoke/logout/refresh-expired.
library;

import 'dart:convert';

import 'package:flutter/foundation.dart';

import 'secure_store.dart';

/// Loai du lieu duoc phep cache offline (allowlist dong DEC-12/A).
enum CachedKind {
  ownProfile,
  workspaceNames,
  projectNames,
  uiPreferences,
  lastSyncAt,
  redactedNotifications,
}

/// Kiem tra loai du lieu co thuoc allowlist khong.
// ignore: avoid_classes_with_only_static_members
abstract final class OfflineAllowlist {
  static const allowed = {
    CachedKind.ownProfile,
    CachedKind.workspaceNames,
    CachedKind.projectNames,
    CachedKind.uiPreferences,
    CachedKind.lastSyncAt,
    CachedKind.redactedNotifications,
  };

  static bool isAllowed(CachedKind kind) => allowed.contains(kind);

  /// Noi dung Project/Task/file rieng tu: LUON tu choi offline.
  static bool isPrivateContent(String resourceType) {
    const private = {'task', 'project_detail', 'attachment', 'report'};
    return private.contains(resourceType);
  }
}

class _Entry {
  final String scopeKey;
  final String payloadJson;
  final DateTime storedAt;
  _Entry({
    required this.scopeKey,
    required this.payloadJson,
    required this.storedAt,
  });

  Map<String, Object?> toJson() => {
        'scope_key': scopeKey,
        // Legacy key giu de doc cache cu: truoc day gan session_id.
        'session_id': scopeKey,
        'payload': payloadJson,
        'stored_at': storedAt.toUtc().toIso8601String(),
      };

  static _Entry? fromJson(Map<String, Object?> json) {
    final sid =
        json['scope_key']?.toString() ?? json['session_id']?.toString();
    final payload = json['payload']?.toString();
    final stored = json['stored_at']?.toString();
    if (sid == null || payload == null || stored == null) return null;
    return _Entry(
      scopeKey: sid,
      payloadJson: payload,
      storedAt: DateTime.tryParse(stored) ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

class OfflineStore extends ChangeNotifier {
  final SecureStore storage;
  final Duration ttl;

  /// true khi phien hien tai da reauthorize sau lan mat ket noi/revoke.
  /// Noi dung rieng tu KHONG mo offline khi = false.
  bool reauthorized = false;

  OfflineStore({SecureStore? storage, Duration? ttl})
      : storage = storage ?? const FlutterSecureStore(),
        ttl = ttl ?? const Duration(hours: 24);

  String _key(CachedKind kind) => 'stm_cache_${kind.name}';

  Future<void> markReauthorized() async {
    reauthorized = true;
    notifyListeners();
  }

  Future<void> put(
    CachedKind kind,
    String scopeKey,
    Object payload,
  ) async {
    if (!OfflineAllowlist.isAllowed(kind)) {
      throw ArgumentError('Khong thuoc allowlist offline DEC-12: $kind');
    }
    final entry = _Entry(
      scopeKey: scopeKey,
      payloadJson: jsonEncode(payload),
      storedAt: DateTime.now().toUtc(),
    );
    await storage.write(_key(kind), jsonEncode(entry.toJson()));
  }

  /// Doc cache: tra null khi het TTL, khac scope, hoac chua reauthorize.
  /// Khong bao gio tra noi dung rieng tu (allowlist chan o [put]).
  Future<Object?> read(CachedKind kind, String currentScopeKey) async {
    if (!OfflineAllowlist.isAllowed(kind)) return null;
    final raw = await storage.read(_key(kind));
    if (raw == null) return null;
    final decoded = jsonDecode(raw);
    if (decoded is! Map) return null;
    final entry = _Entry.fromJson(decoded.cast<String, Object?>());
    if (entry == null) return null;
    if (entry.scopeKey != currentScopeKey) return null;
    if (!reauthorized) return null;
    if (DateTime.now().toUtc().difference(entry.storedAt) >= ttl) return null;
    return jsonDecode(entry.payloadJson);
  }

  /// Xoa toan bo cache khi phat hien revoke (INVALID_SESSION / 4401),
  /// logout, hoac refresh-expired.
  Future<void> clearOnRevoke() async {
    for (final kind in CachedKind.values) {
      await storage.delete(_key(kind));
    }
    reauthorized = false;
    notifyListeners();
  }

  /// Alias ngu nghia cho logout (cung chinh sach xoa nhu revoke).
  Future<void> clearOnLogout() => clearOnRevoke();

  Future<void> clearKind(CachedKind kind) async {
    await storage.delete(_key(kind));
  }
}
