// Test RealtimeClient (SSE): ticket qua REST moi connect, khong token trong
// query/frame, 401 -> reauth khong loop, signal -> refetch (onEvent + seq).
import 'dart:async';
import 'dart:convert';

import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smarttask_mobile/realtime/ws_client.dart';

/// Fake SSE endpoint: tra status + byte stream do test dieu khien.
/// Moi connect tao mot stream moi (that: HTTP request moi).
class FakeSse {
  int statusCode = 200;
  Uri? uri;
  final List<StreamController<List<int>>> _controllers = [];

  StreamController<List<int>> get controller => _controllers.last;

  Future<SseResponse> connect(Uri u) async {
    uri = u;
    if (statusCode != 200) {
      return SseResponse(statusCode, const Stream.empty());
    }
    final c = StreamController<List<int>>();
    _controllers.add(c);
    return SseResponse(200, c.stream);
  }

  void frame(String payload) {
    controller.add(utf8.encode('event:signal\ndata:$payload\n\n'));
  }
}

void main() {
  group('parseSseEvent', () {
    test('signal JSON hop le, comment/rac -> null', () {
      expect(
        parseSseEvent('event:signal\ndata:{"kind":"TASK_STATE_CHANGED","seq":7}'),
        {'kind': 'TASK_STATE_CHANGED', 'seq': 7},
      );
      expect(parseSseEvent(': hb'), isNull);
      expect(parseSseEvent('event:message\ndata:{"kind":"X"}'), isNull);
      expect(parseSseEvent('event:signal\ndata:not-json'), isNull);
      expect(parseSseEvent('event:signal\ndata:[1,2]'), isNull);
    });
  });

  group('RealtimeClient', () {
    test('ticket tren query SSE, khong access_token', () async {
      final sse = FakeSse();
      final client = RealtimeClient(
        wsBaseUrl: 'ws://test:8080',
        ticketProvider: () async => 'TICKET-1',
        cursorReader: () async => '42',
        cursorWriter: (_) async {},
        connector: sse.connect,
      );
      await client.connect();
      final uri = sse.uri!;
      expect(uri.scheme, 'http');
      expect(uri.path, '/api/v1/realtime/stream');
      expect(uri.queryParameters.keys.toList(), ['ticket']);
      expect(uri.queryParameters['ticket'], 'TICKET-1');
      expect(uri.toString().contains('access_token'), isFalse);
      expect(client.status, WsStatus.connected);
      client.dispose();
    });

    test('ticket null -> disconnected, khong crash', () async {
      var calls = 0;
      final client = RealtimeClient(
        wsBaseUrl: 'ws://test',
        ticketProvider: () async {
          calls++;
          return null;
        },
        cursorReader: () async => null,
        cursorWriter: (_) async {},
        connector: (_) async => SseResponse(200, const Stream.empty()),
      );
      await client.connect();
      expect(calls, 1);
      expect(client.status, WsStatus.disconnected);
      client.dispose();
    });

    test('signal tang dem + goi onEvent + luu seq (refetch qua API)',
        () async {
      final sse = FakeSse();
      String? savedCursor;
      Map<String, Object?>? got;
      final client = RealtimeClient(
        wsBaseUrl: 'ws://test',
        ticketProvider: () async => 'T',
        cursorReader: () async => null,
        cursorWriter: (c) async {
          savedCursor = c;
        },
        onEvent: (e) => got = e,
        connector: sse.connect,
      );
      await client.connect();
      // Chunk bi cat giua frame: parser phai buffer.
      sse.controller.add(utf8.encode('event:signal\ndata:{"kind":"TASK_'));
      sse.controller.add(utf8.encode('STATE_CHANGED","seq":77}\n\n'));
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      expect(client.signalsReceived, 1);
      expect(got?['kind'], 'TASK_STATE_CHANGED');
      expect(savedCursor, '77');
      client.dispose();
    });

    test('401 -> reauthRequired, khong tu reconnect (khong loop)', () {
      FakeAsync().run((fa) {
        var ticketCalls = 0;
        final client = RealtimeClient(
          wsBaseUrl: 'ws://test',
          ticketProvider: () async {
            ticketCalls++;
            return 'T';
          },
          cursorReader: () async => null,
          cursorWriter: (_) async {},
          connector: (_) async => SseResponse(401, const Stream.empty()),
        );
        client.connect();
        fa.flushMicrotasks();
        expect(client.status, WsStatus.reauthRequired);
        fa.elapse(const Duration(seconds: 30));
        fa.flushMicrotasks();
        expect(ticketCalls, 1);
        // Noi lai co kiem soat sau reauth.
        client.reconnectAfterReauth();
        fa.flushMicrotasks();
        expect(ticketCalls, 2);
        client.dispose();
      });
    });

    test('dong thuong -> reconnect voi ticket moi (backoff)', () {
      FakeAsync().run((fa) {
        final sse = FakeSse();
        var ticketCalls = 0;
        final client = RealtimeClient(
          wsBaseUrl: 'ws://test',
          ticketProvider: () async {
            ticketCalls++;
            return 'T$ticketCalls';
          },
          cursorReader: () async => null,
          cursorWriter: (_) async {},
          connector: sse.connect,
        );
        client.connect();
        fa.flushMicrotasks();
        expect(ticketCalls, 1);
        sse.controller.close();
        fa.flushMicrotasks();
        expect(client.status, WsStatus.disconnected);
        fa.elapse(const Duration(seconds: 3));
        fa.flushMicrotasks();
        // Reconnect lay ticket moi (single-use).
        expect(ticketCalls, 2);
        expect(client.status, WsStatus.connected);
        client.dispose();
      });
    });
  });
}
