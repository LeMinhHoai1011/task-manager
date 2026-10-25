// Luu tru bao mat truu tuong hoa: production dung flutter_secure_storage
// (ma hoa keystore/keychain), test dung MemorySecureStore trong bo nho.
// Khong luu token/plaintext ngoai hai cai nay.
library;

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Giao dien luu tru key-value toi thieu cho token + cache offline.
abstract class SecureStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

/// Adapter production: flutter_secure_storage.
class FlutterSecureStore implements SecureStore {
  final FlutterSecureStorage inner;
  const FlutterSecureStore({FlutterSecureStorage? inner})
      : inner = inner ?? const FlutterSecureStorage();

  @override
  Future<String?> read(String key) => inner.read(key: key);

  @override
  Future<void> write(String key, String value) =>
      inner.write(key: key, value: value);

  @override
  Future<void> delete(String key) => inner.delete(key: key);
}

/// Store trong bo nho cho test (khong cham platform channel).
class MemorySecureStore implements SecureStore {
  final Map<String, String> _map = {};

  @override
  Future<String?> read(String key) async => _map[key];

  @override
  Future<void> write(String key, String value) async {
    _map[key] = value;
  }

  @override
  Future<void> delete(String key) async {
    _map.remove(key);
  }
}
