// Nhan dang thiet bi gui kem login (device_label + client_type body).
// Web-safe: khong dung dart:io Platform (crash tren web).
library;

import 'package:flutter/foundation.dart';

import '../api/client.dart';

/// Tien ich nhan dang thiet bi, an toan tren moi nen tang.
abstract final class DeviceInfo {
  static ClientType clientType() => ClientTypeHeader.current();

  /// Nhan thiet bi that theo nen tang (khong hardcode 'Android app').
  /// Khong co device_info_plus trong V1 nen nhan o muc nen tang/OS.
  static String deviceLabel() {
    if (kIsWeb) return 'Web app (Flutter)';
    return switch (defaultTargetPlatform) {
      TargetPlatform.android => 'Android app (Flutter)',
      TargetPlatform.iOS => 'iOS app (Flutter)',
      TargetPlatform.macOS => 'macOS app (Flutter)',
      TargetPlatform.windows => 'Windows app (Flutter)',
      TargetPlatform.linux => 'Linux app (Flutter)',
      TargetPlatform.fuchsia => 'Fuchsia app (Flutter)',
    };
  }
}
