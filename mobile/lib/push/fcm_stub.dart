// Dang ky push gan voi session hien tai.
//
// TRANG THAI XAC NHAN (backend F4): devices endpoints (`GET/POST/DELETE
// /notifications/devices`, EP-176..178) DA co tren backend (DeviceController);
// token chi luu dang hash + token_hint. Firebase project / APNs key CHUA co o
// V1 nen push THAT (FCM/APNs/SMTP) van NOT_TESTED - xem bao cao.
//
// Quy tac (DEC-13 phuong an A + quy dinh khong dang ky fake):
// - Chi goi [register] khi co push token THAT tu FCM/APNs. Chua co
//   firebase_messaging trong V1 (tranh hong build CI thieu
//   google-services.json) nen khong bao gio co token that -> khong goi.
// - KHONG dang ky token fake/gia lap: xoa han registerFake.
// - Endpoint devices da co: register POST that. Neu gap 404 (backend cu) thi
//   degrade tuong minh (outcome.endpointAbsent) chu khong crash.
//
// - Payload push KHONG chua tieu de Task / ten Project (DEC-13 phuong an A):
//   chi bao "co cap nhat moi", noi dung render khi goi API co authz.
// - Server (Nhom 01) revoke device_push_registration khi login noi khac;
//   client huy trang thai local khi logout/revoke qua [unregister].
library;

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../api/client.dart';
import '../api/endpoints.dart';

/// Ket qua dang ky push (de UI/log phan biet degrade).
enum PushRegisterOutcome {
  /// Da dang ky that voi backend.
  registered,

  /// Khong co token that -> khong goi gi ca (dung quy dinh, khong phai loi).
  noToken,

  /// Backend cu chua co devices endpoint (404) -> degrade tuong minh.
  endpointAbsent,

  /// Loi mang/server khac.
  failed,
}

class PushRegistrar extends ChangeNotifier {
  final ApiClient api;

  String? registrationId;
  bool registered = false;
  PushRegisterOutcome? lastOutcome;

  PushRegistrar({required this.api});

  /// Dang ky thiet bi voi push token THAT. [token] null/rong -> no-op
  /// ([PushRegisterOutcome.noToken]), KHONG gui token fake.
  /// [platform] theo docs EP-177: ANDROID_FCM / IOS_APNS.
  Future<PushRegisterOutcome> register({
    required String? token,
    required String platform,
    required String deviceLabel,
    String? appVersion,
    String? locale,
  }) async {
    if (token == null || token.isEmpty) {
      lastOutcome = PushRegisterOutcome.noToken;
      notifyListeners();
      return lastOutcome!;
    }
    try {
      final payload = <String, Object?>{
        'push_token': token,
        'platform': platform,
        'device_label': deviceLabel,
      };
      final String? av = appVersion;
      if (av != null) payload['app_version'] = av;
      final String? loc = locale;
      if (loc != null) payload['locale'] = loc;
      final res = await api.mutate(
        ApiPaths.devices,
        method: 'POST',
        body: payload,
        idempotencyKey: api.newIdempotencyKey(),
      );
      final data = PageParser.parseObject(res);
      registrationId = (data['registration_id'] ?? data['id'])?.toString();
      registered = true;
      lastOutcome = PushRegisterOutcome.registered;
      notifyListeners();
      return lastOutcome!;
    } on DioException catch (e) {
      final err = e.error;
      if (err is ApiException && err.isNotFound) {
        // Backend cu chua co devices endpoint -> degrade tuong minh.
        registered = false;
        lastOutcome = PushRegisterOutcome.endpointAbsent;
        notifyListeners();
        return lastOutcome!;
      }
      registered = false;
      lastOutcome = PushRegisterOutcome.failed;
      notifyListeners();
      return lastOutcome!;
    }
  }

  /// Huy dang ky khi logout / revoke: best-effort DELETE neu biet id, luon
  /// xoa trang thai local.
  Future<void> unregister() async {
    final id = registrationId;
    registrationId = null;
    registered = false;
    notifyListeners();
    if (id == null) return;
    try {
      await api.mutate(ApiPaths.device(id), method: 'DELETE');
    } on DioException {
      // Best-effort.
    }
  }
}
