// Helper backend gia cho test: match (method, path) -> {status, json}.
//
// Dung HttpClientAdapter (khong phai Interceptor) de toan bo interceptor cua
// ApiClient (headers + map ErrorEnvelope) chay dung thu tu: request di qua
// auth header, response loi quay ve onError cua ApiClient.
library;

import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:smarttask_mobile/api/client.dart';

class FakeRoute {
  final int status;
  final Map<String, dynamic> json;
  const FakeRoute(this.status, this.json);
}

class FakeBackend implements HttpClientAdapter {
  final Map<String, FakeRoute> routes = {};
  final List<RequestOptions> requests = [];
  final List<Map<String, dynamic>?> bodies = [];

  String _key(String method, String path) => '$method $path';

  void when(String method, String path, FakeRoute route) {
    routes[_key(method.toUpperCase(), path)] = route;
  }

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final rawBody = options.data;
    bodies.add(rawBody is Map ? rawBody.cast<String, dynamic>() : null);

    final route = routes[_key(options.method, options.path)];
    final status = route?.status ?? 404;
    final json = route?.json ??
        <String, dynamic>{
          'error': {
            'code': 'RESOURCE_NOT_FOUND',
            'message_key': 'resource.not_found',
          },
          'correlation_id': 'fake-corr',
          'timestamp': '2026-01-01T00:00:00Z',
        };
    return ResponseBody.fromBytes(
      utf8.encode(jsonEncode(json)),
      status,
      headers: <String, List<String>>{
        Headers.contentTypeHeader: <String>[Headers.jsonContentType],
        'x-correlation-id': <String>['fake-corr-header'],
      },
    );
  }

  @override
  void close({bool force = false}) {}

  /// Tao ApiClient test di qua FakeBackend (headers/envelope that).
  ApiClient newClient({
    Future<String?> Function()? tokenReader,
    SessionRevokedHandler? onSessionRevoked,
  }) {
    final client = ApiClient(
      baseUrl: 'http://test',
      tokenReader: tokenReader ?? () async => 'test-token',
      clientType: ClientType.android,
      onSessionRevoked: onSessionRevoked,
    );
    client.dio.httpClientAdapter = this;
    return client;
  }

  Map<String, dynamic>? lastBodyFor(String method, String path) {
    for (var i = requests.length - 1; i >= 0; i--) {
      if (requests[i].method == method.toUpperCase() &&
          requests[i].path == path) {
        return bodies[i];
      }
    }
    return null;
  }

  RequestOptions? lastRequestFor(String method, String path) {
    for (var i = requests.length - 1; i >= 0; i--) {
      if (requests[i].method == method.toUpperCase() &&
          requests[i].path == path) {
        return requests[i];
      }
    }
    return null;
  }
}

/// Envelope loi backend-shape dung chung cho test.
Map<String, dynamic> errJson(
  String code, {
  String messageKey = 'error.test',
  bool reauthRequired = false,
  String? actionCode,
  int? retryAfter,
  int? resourceVersion,
}) {
  return <String, dynamic>{
    'error': <String, dynamic>{
      'code': code,
      'message_key': messageKey,
      'reason_code': 'TEST',
      'details': <String, dynamic>{
        'action_code': ?actionCode,
      },
      if (reauthRequired) 'reauth_required': true,
      'retry_after_seconds': ?retryAfter,
      'resource_version': ?resourceVersion,
    },
    'correlation_id': 'corr-body',
    'timestamp': '2026-01-01T00:00:00Z',
  };
}
