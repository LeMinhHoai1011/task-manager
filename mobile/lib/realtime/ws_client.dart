// Realtime: SSE la tin hieu, API la nguon du lieu (ARCH-09).
// Reconnect voi backoff + ticket moi moi lan.
//
// Contract backend F4 (da doc RealtimeController + RealtimeRegistry):
// - Transport la Server-Sent Events: GET /api/v1/realtime/stream?ticket=...
//   (spring-websocket 6.1.x khong co trong offline repo nen backend chon SSE).
// - Ticket cap qua REST da auth session (POST /realtime/ticket), single-use,
//   TTL 60s. Query CHI mang `ticket`; KHONG bao gio gui access/refresh token
//   qua query/frame.
// - Server chi gui signal {kind, scope_type, scope_id, object_id, seq, at};
//   client refetch qua API (da kiem quyen) khi nhan signal.
// - Stream tra 401 khi ticket sai/het han hoac session bi thu hoi: chuyen
//   WsStatus.reauthRequired, KHONG tu reconnect (tranh loop) — doi UI goi
//   [reconnectAfterReauth] sau khi phien on dinh.
library;

import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

enum WsStatus { disconnected, connecting, connected, reauthRequired }

/// Mot response SSE: status + byte stream (401 khong nem exception de phan biet
/// "session bi thu hoi" voi "mat mang").
class SseResponse {
  final int statusCode;
  final Stream<List<int>> bytes;
  const SseResponse(this.statusCode, this.bytes);
}

/// Ham mo stream de test co the inject fake (mac dinh dung Dio that).
typedef SseConnector = Future<SseResponse> Function(Uri uri);

Future<SseResponse> _defaultConnector(Uri uri) async {
  final dio = Dio(BaseOptions(
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: null,
  ));
  final res = await dio.get<ResponseBody>(
    uri.toString(),
    options: Options(
      responseType: ResponseType.stream,
      headers: const {'Accept': 'text/event-stream'},
      validateStatus: (_) => true,
    ),
  );
  final body = res.data;
  return SseResponse(
    res.statusCode ?? 0,
    body?.stream ?? const Stream<List<int>>.empty(),
  );
}

/// Parse mot SSE frame (cac dong da tach, chua gom dong trong cuoi) thanh Map
/// signal, hoac null neu khong phai `event: signal` / JSON khong hop le.
Map<String, Object?>? parseSseEvent(String frame) {
  var event = 'message';
  final data = <String>[];
  for (final rawLine in const LineSplitter().convert(frame)) {
    final line = rawLine.trimRight();
    if (line.isEmpty || line.startsWith(':')) continue; // comment/keep-alive
    final idx = line.indexOf(':');
    final field = idx == -1 ? line : line.substring(0, idx);
    var value = idx == -1 ? '' : line.substring(idx + 1);
    if (value.startsWith(' ')) value = value.substring(1);
    if (field == 'event') {
      event = value;
    } else if (field == 'data') {
      data.add(value);
    }
  }
  if (event != 'signal' || data.isEmpty) return null;
  try {
    final decoded = jsonDecode(data.join('\n'));
    if (decoded is Map && decoded['kind'] is String) {
      return decoded.cast<String, Object?>();
    }
  } on FormatException {
    return null;
  }
  return null;
}

class RealtimeClient extends ChangeNotifier {
  /// Origin cua backend (co the la ws:// hoac http://; SSE dung http/https).
  final String wsBaseUrl;

  /// Lay ticket handshake single-use (TTL 60s) qua REST da auth session.
  final Future<String?> Function() ticketProvider;
  final Future<String?> Function() cursorReader;
  final Future<void> Function(String cursor) cursorWriter;
  final void Function(Map<String, Object?> event)? onEvent;
  final SseConnector connector;

  StreamSubscription<String>? _sub;
  Timer? _backoffTimer;
  WsStatus status = WsStatus.disconnected;
  int _attempt = 0;
  bool _disposed = false;
  int _generation = 0;

  /// So signal da nhan (de UI refetch qua API — realtime khong mang data).
  int signalsReceived = 0;

  RealtimeClient({
    required this.wsBaseUrl,
    required this.ticketProvider,
    required this.cursorReader,
    required this.cursorWriter,
    this.onEvent,
    SseConnector? connector,
  }) : connector = connector ?? _defaultConnector;

  String get _httpBase {
    final u = Uri.parse(wsBaseUrl);
    final scheme = switch (u.scheme) {
      'wss' => 'https',
      'ws' => 'http',
      _ => u.scheme,
    };
    return u.replace(scheme: scheme).toString();
  }

  Future<void> connect() async {
    if (_disposed) return;
    _backoffTimer?.cancel();
    final gen = ++_generation;
    status = WsStatus.connecting;
    notifyListeners();

    // Moi connect (ke ca reconnect) lay ticket moi: ticket single-use.
    final ticket = await ticketProvider();
    if (_disposed || gen != _generation) return;
    if (ticket == null || ticket.isEmpty) {
      status = WsStatus.disconnected;
      notifyListeners();
      return;
    }

    // Query chi mang ticket; khong bao gio kem access_token.
    final uri = Uri.parse('$_httpBase/api/v1/realtime/stream')
        .replace(queryParameters: {'ticket': ticket});
    final SseResponse res;
    try {
      res = await connector(uri);
    } on Exception {
      if (_disposed || gen != _generation) return;
      _scheduleReconnect();
      return;
    }
    if (_disposed || gen != _generation) return;
    if (res.statusCode == 401) {
      // Session bi thu hoi / ticket bi dung lai: reauth, khong loop.
      _enterReauthRequired();
      return;
    }
    if (res.statusCode != 200) {
      _scheduleReconnect();
      return;
    }

    status = WsStatus.connected;
    _attempt = 0;
    notifyListeners();

    final buffer = StringBuffer();
    _sub = res.bytes
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .listen(
      (line) {
        if (line.isEmpty) {
          final frame = buffer.toString();
          buffer.clear();
          if (frame.isNotEmpty) _onFrame(frame);
        } else {
          buffer.writeln(line);
        }
      },
      onError: (_) => _scheduleReconnect(),
      onDone: _onDone,
      cancelOnError: false,
    );
  }

  void _onFrame(String frame) {
    final msg = parseSseEvent(frame);
    if (msg == null) return;
    final seq = msg['seq']?.toString();
    if (seq != null && seq.isNotEmpty) {
      unawaited(cursorWriter(seq));
    }
    // Realtime chi la tin hieu: dem + chuyen cho UI refetch qua API.
    signalsReceived++;
    notifyListeners();
    onEvent?.call(msg);
  }

  void _onDone() {
    if (_disposed) return;
    if (status == WsStatus.reauthRequired) return;
    _scheduleReconnect();
  }

  void _enterReauthRequired() {
    _backoffTimer?.cancel();
    status = WsStatus.reauthRequired;
    notifyListeners();
  }

  /// Goi sau khi phien on dinh lai (login/refresh) de noi lai co kiem soat.
  Future<void> reconnectAfterReauth() => connect();

  void _scheduleReconnect() {
    if (_disposed) return;
    status = WsStatus.disconnected;
    notifyListeners();
    _attempt++;
    final delay = Duration(
      seconds: _attempt * 2 > 30 ? 30 : _attempt * 2,
    );
    _backoffTimer?.cancel();
    _backoffTimer = Timer(delay, connect);
  }

  Future<void> disconnect() async {
    _generation++;
    _backoffTimer?.cancel();
    await _sub?.cancel();
    _sub = null;
    status = WsStatus.disconnected;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _backoffTimer?.cancel();
    unawaited(_sub?.cancel());
    super.dispose();
  }
}
