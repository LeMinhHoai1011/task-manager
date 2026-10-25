// Client HTTP chung contract /api/v1 voi Web (ARCH-02).
//
// Doc ky truoc khi sua:
// - Base path: {baseUrl}/api/v1 cho moi endpoint REST.
// - Auth: opaque access token, header `Authorization: Bearer <token>`.
// - Header bat buoc: X-Correlation-Id (sinh UUID neu thieu), X-Client-Type
//   (ANDROID / IOS / WEB).
// - Mutation co dieu kien: If-Match (optimistic concurrency) + Idempotency-Key.
// - Envelope THANH CONG backend (ApiResult.java): `{data, message_key,
//   timestamp}`. List endpoint boc PageResponse trong `data` voi canonical
//   `items` (+ alias legacy `data`), `next_cursor`, `has_more`.
// - Envelope LOI backend (ErrorEnvelope.java): `{error: {code, message_key,
//   reason_code, details, reauth_required?, retry_after_seconds?,
//   resource_version?, ...}, correlation_id, timestamp}`. Backend KHONG gui
//   `message_params` / `reauth_action_code` (cac truong nay chi co trong docs
//   03-API-CONTRACT dac ta) — client doc `details.action_code` truoc, roi moi
//   fallback legacy. 404 hien thi trung tinh (03 §1.4).
// - Reauth ticket truyen bang header `X-Reauth-Ticket` (AiController.java);
//   docs 03 goi la `X-Reauth-Token` — backend la chuan.
// - Nested routes da co (F4) nhung tra DTO rut gon; mobile dung query day du
//   `?workspace_id=` / `?project_id=` lam primary (xem endpoints.dart).
//   Devices endpoints da co; degrade 404 chi de tuong thich backend cu.
// - KHONG goi Ollama truc tiep, KHONG co AI inference tren thiet bi.
library;

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

/// Gia tri hop le cua header X-Client-Type tren mobile.
///
/// Dung [defaultTargetPlatform] + [kIsWeb] thay vi `dart:io Platform` de khong
/// crash khi build web (dart:io khong ton tai tren web).
enum ClientType { android, ios, web }

extension ClientTypeHeader on ClientType {
  String get headerValue => switch (this) {
        ClientType.android => 'ANDROID',
        ClientType.ios => 'IOS',
        ClientType.web => 'WEB',
      };

  static ClientType current() {
    if (kIsWeb) return ClientType.web;
    return switch (defaultTargetPlatform) {
      TargetPlatform.iOS || TargetPlatform.macOS => ClientType.ios,
      _ => ClientType.android,
    };
  }
}

/// Ten header mang reauth ticket khi retry mutation nhay cam.
/// Backend (AiController: `X-Reauth-Ticket`) la chuan; docs 03 goi la
/// `X-Reauth-Token`.
const String kReauthTicketHeader = 'X-Reauth-Ticket';

/// Envelope loi chuan (ErrorEnvelope.java + fallback legacy docs-03).
class ErrorEnvelope {
  final String code;
  final String messageKey;

  /// Chi tiet loi backend (`error.details`); legacy docs-03 dung
  /// `error.message_params` — client hop nhat ca hai vao day.
  final Map<String, Object?> details;
  final String? reasonCode;
  final bool reauthRequired;

  /// Backend KHONG gui truong rieng; lay tu `details.action_code` truoc,
  /// fallback `error.reauth_action_code` (docs-03). Null nghia la caller phai
  /// tu suy action tu ngu canh (verb/target).
  final String? reauthActionCode;
  final int? retryAfterSeconds;
  final int? resourceVersion;
  final String correlationId;
  final String timestamp;
  final int httpStatus;

  const ErrorEnvelope({
    required this.code,
    required this.messageKey,
    this.details = const {},
    this.reasonCode,
    this.reauthRequired = false,
    this.reauthActionCode,
    this.retryAfterSeconds,
    this.resourceVersion,
    required this.correlationId,
    required this.timestamp,
    required this.httpStatus,
  });

  /// Tuong thich nguoc: docs-03 goi la `message_params`.
  Map<String, Object?> get messageParams => details;

  /// 404 trung tinh: khong suy luan "da bi xoa" (03 §1.4).
  bool get isNeutralNotFound => httpStatus == 404;

  /// Thong diep UI trung tinh cho 404 (khong lo doi tuong an).
  String get neutralMessage =>
      'Không tìm thấy hoặc bạn không có quyền truy cập.';

  factory ErrorEnvelope.fromResponse(
    int status,
    Map<String, Object?> body,
    String fallbackCorrelationId,
  ) {
    final err = body['error'];
    final Map<String, Object?> e =
        err is Map ? err.cast<String, Object?>() : const {};
    final Object? rawDetails = e['details'];
    final Map<String, Object?> details = rawDetails is Map
        ? rawDetails.cast<String, Object?>()
        : (e['message_params'] is Map
            // Fallback legacy tuong minh: docs-03 envelope cu.
            ? (e['message_params'] as Map).cast<String, Object?>()
            : const {});
    return ErrorEnvelope(
      code: e['code']?.toString() ?? 'INTERNAL_ERROR',
      messageKey: e['message_key']?.toString() ?? 'error.internal',
      details: details,
      reasonCode: e['reason_code']?.toString(),
      reauthRequired: e['reauth_required'] == true,
      reauthActionCode: details['action_code']?.toString() ??
          e['reauth_action_code']?.toString(),
      retryAfterSeconds: (e['retry_after_seconds'] as num?)?.toInt(),
      resourceVersion: (e['resource_version'] as num?)?.toInt(),
      correlationId:
          body['correlation_id']?.toString() ?? fallbackCorrelationId,
      timestamp: body['timestamp']?.toString() ?? '',
      httpStatus: status,
    );
  }
}

/// Ngoai le HTTP da parse ErrorEnvelope.
class ApiException implements Exception {
  final ErrorEnvelope envelope;
  const ApiException(this.envelope);

  bool get isUnauthorized => envelope.httpStatus == 401;
  bool get isNotFound => envelope.httpStatus == 404;
  bool get isReauthRequired =>
      envelope.httpStatus == 428 || envelope.reauthRequired;
  bool get isIfMatchRequired =>
      envelope.httpStatus == 428 && envelope.code == 'IF_MATCH_REQUIRED';
  bool get isVersionConflict =>
      envelope.httpStatus == 409 && envelope.code == 'VERSION_CONFLICT';

  @override
  String toString() =>
      'ApiException(${envelope.code}, http=${envelope.httpStatus}, '
      'corr=${envelope.correlationId})';
}

typedef TokenReader = Future<String?> Function();
typedef SessionRevokedHandler = Future<void> Function(String reason);

/// Ket qua trang chuan: boc ApiResult.data -> PageResponse.
class PageResult {
  final List<Map<String, Object?>> items;
  final String? nextCursor;
  final bool hasMore;

  const PageResult({
    this.items = const [],
    this.nextCursor,
    this.hasMore = false,
  });
}

/// Parse envelope phan trang dung contract backend, fallback legacy tuong minh.
abstract final class PageParser {
  /// Boc ApiResult: `{data: ...}`. Neu body khong co `data` (legacy/mock cu),
  /// tra chinh body.
  static Object? unwrapData(Object? body) {
    if (body is Map) {
      final m = body.cast<String, Object?>();
      if (m.containsKey('data')) return m['data'];
    }
    return body;
  }

  /// Parse PageResponse canonical `items` (+ alias legacy `data` ben trong
  /// page — backend gui ca hai), `next_cursor`, `has_more`. Fallback tuong
  /// minh: top-level `items` (client cu), bare list.
  static PageResult parsePage(Object? body) {
    final payload = unwrapData(body);
    if (payload is Map) {
      final m = payload.cast<String, Object?>();
      final Object? rawItems = m['items'] ?? m['data'];
      final items = rawItems is List
          ? rawItems.whereType<Map>().map((e) => e.cast<String, Object?>()).toList()
          : const <Map<String, Object?>>[];
      return PageResult(
        items: items,
        nextCursor:
            (m['next_cursor'] ?? m['nextCursor'])?.toString(),
        hasMore: (m['has_more'] ?? m['hasMore']) == true,
      );
    }
    if (payload is List) {
      // Fallback legacy tuong minh: bare list khong envelope.
      return PageResult(
        items: payload.whereType<Map>().map((e) => e.cast<String, Object?>()).toList(),
      );
    }
    return const PageResult();
  }

  /// Boc object don: ApiResult.data la Map thi tra Map do.
  static Map<String, Object?> parseObject(Object? body) {
    final payload = unwrapData(body);
    if (payload is Map) return payload.cast<String, Object?>();
    return const {};
  }

  /// Boc list tran: ApiResult.data la List (vd calendar) thi tra List do.
  static List<Map<String, Object?>> parseList(Object? body) {
    final payload = unwrapData(body);
    if (payload is List) {
      return payload.whereType<Map>().map((e) => e.cast<String, Object?>()).toList();
    }
    if (payload is Map) {
      final m = payload.cast<String, Object?>();
      final inner = m['items'] ?? m['data'];
      if (inner is List) {
        return inner.whereType<Map>().map((e) => e.cast<String, Object?>()).toList();
      }
    }
    return const [];
  }
}

/// Dio client dung chung cho mobile.
class ApiClient {
  final Dio dio;
  final TokenReader tokenReader;
  final ClientType clientType;
  final SessionRevokedHandler? onSessionRevoked;
  final _uuid = const Uuid();

  ApiClient({
    required String baseUrl,
    required this.tokenReader,
    required this.clientType,
    this.onSessionRevoked,
    Dio? dioOverride,
  }) : dio = dioOverride ??
            Dio(
              BaseOptions(
                // ignore: avoid_redundant_argument_values
                baseUrl: '$baseUrl/api/v1',
                connectTimeout: const Duration(seconds: 15),
                receiveTimeout: const Duration(seconds: 30),
                contentType: 'application/json; charset=utf-8',
                responseType: ResponseType.json,
              ),
            ) {
    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await tokenReader();
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          options.headers.putIfAbsent(
            'X-Correlation-Id',
            () => _uuid.v4(),
          );
          options.headers['X-Client-Type'] = clientType.headerValue;
          handler.next(options);
        },
        onError: (e, handler) async {
          final res = e.response;
          if (res != null && res.data is Map) {
            final corr = res.headers.value('X-Correlation-Id') ?? _uuid.v4();
            final envelope = ErrorEnvelope.fromResponse(
              res.statusCode ?? 0,
              (res.data as Map).cast<String, Object?>(),
              corr,
            );
            // Single-session: phien cu bi revoke -> thong bao de UI xu ly.
            if (envelope.code == 'INVALID_SESSION' &&
                onSessionRevoked != null) {
              await onSessionRevoked!('INVALID_SESSION');
            }
            handler.reject(
              DioException(
                requestOptions: e.requestOptions,
                response: e.response,
                type: e.type,
                error: ApiException(envelope),
              ),
            );
            return;
          }
          handler.next(e);
        },
      ),
    );
  }

  /// Tao Idempotency-Key moi cho mutation bat buoc (03 §4.1).
  String newIdempotencyKey() => _uuid.v4();

  /// Tao correlation id moi.
  String newCorrelationId() => _uuid.v4();

  /// GET don gian, tra body JSON.
  Future<Object?> get(
    String path, {
    Map<String, Object?>? query,
    String? correlationId,
  }) async {
    final Map<String, Object?> headers = {};
    final String? cid = correlationId;
    if (cid != null) headers['X-Correlation-Id'] = cid;
    try {
      final res = await dio.get<Object?>(
        path,
        queryParameters: query,
        options: Options(headers: headers),
      );
      return res.data;
    } on DioException catch (e) {
      throw _asApiException(e);
    }
  }

  /// Mutation co dieu kien: If-Match + Idempotency-Key (03 §1.3, §4.1).
  /// [reauthToken] la reauth **ticket_id** da verify, gui qua header
  /// `X-Reauth-Ticket` dung contract backend (AiController).
  Future<Object?> mutate(
    String path, {
    required String method,
    Object? body,
    Map<String, Object?>? query,
    int? ifMatchVersion,
    String? idempotencyKey,
    String? reauthToken,
    String? correlationId,
  }) async {
    final Map<String, Object?> headers = {};
    final int? v = ifMatchVersion;
    if (v != null) headers['If-Match'] = '"$v"';
    final String? k = idempotencyKey;
    if (k != null) headers['Idempotency-Key'] = k;
    final String? t = reauthToken;
    if (t != null) headers[kReauthTicketHeader] = t;
    final String? cid = correlationId;
    if (cid != null) headers['X-Correlation-Id'] = cid;
    try {
      final res = await dio.request<Object?>(
        path,
        queryParameters: query,
        data: body,
        options: Options(method: method, headers: headers),
      );
      return res.data;
    } on DioException catch (e) {
      throw _asApiException(e);
    }
  }

  /// Upload chunk/octet-stream (EP-145) — van qua gateway co authz.
  Future<Object?> uploadBytes(
    String path,
    List<int> bytes, {
    required String contentType,
    String? idempotencyKey,
    void Function(int sent, int total)? onProgress,
  }) async {
    final Map<String, Object?> headers = {};
    final String? k2 = idempotencyKey;
    if (k2 != null) headers['Idempotency-Key'] = k2;
    try {
      final res = await dio.put<Object?>(
        path,
        data: Stream.fromIterable([bytes]),
        options: Options(contentType: contentType, headers: headers),
        onSendProgress: onProgress,
      );
      return res.data;
    } on DioException catch (e) {
      throw _asApiException(e);
    }
  }

  /// Download file QUA GATEWAY (ARCH-07): moi request kiem tra quyen,
  /// khong cache file rieng tu offline (DEC-12 phuong an A).
  Future<List<int>> downloadBytes(String path) async {
    try {
      final res = await dio.get<List<int>>(
        path,
        options: Options(responseType: ResponseType.bytes),
      );
      return res.data ?? const [];
    } on DioException catch (e) {
      throw _asApiException(e);
    }
  }

  Object _asApiException(DioException e) {
    if (e.error is ApiException) return e.error!;
    return e;
  }
}
