// Test ApiClient: headers bat buoc, parse envelope loi backend-shape +
// fallback legacy, PageParser canonical + fallback legacy.
import 'package:flutter_test/flutter_test.dart';
import 'package:smarttask_mobile/api/client.dart';

import 'fake_backend.dart';

void main() {
  group('ApiClient headers', () {
    test('gui Authorization + X-Correlation-Id + X-Client-Type', () async {
      final backend = FakeBackend()
        ..when('GET', '/workspaces', const FakeRoute(200, {'data': []}));
      final client = backend.newClient();
      await client.get('/workspaces');
      final req = backend.lastRequestFor('GET', '/workspaces')!;
      expect(req.headers['Authorization'], 'Bearer test-token');
      expect(req.headers['X-Correlation-Id'], isNotNull);
      expect(req.headers['X-Client-Type'], 'ANDROID');
    });

    test('khong gui Authorization khi chua co token', () async {
      final backend = FakeBackend()
        ..when('GET', '/workspaces', const FakeRoute(200, {'data': []}));
      final client = backend.newClient(tokenReader: () async => null);
      await client.get('/workspaces');
      final req = backend.lastRequestFor('GET', '/workspaces')!;
      expect(req.headers.containsKey('Authorization'), isFalse);
    });

    test('mutate gui If-Match (quoted) + Idempotency-Key + X-Reauth-Ticket',
        () async {
      final backend = FakeBackend()
        ..when('POST', '/tasks/1/workflow/start',
            const FakeRoute(200, {'data': {}}));
      final client = backend.newClient();
      await client.mutate(
        '/tasks/1/workflow/start',
        method: 'POST',
        body: const {},
        ifMatchVersion: 7,
        idempotencyKey: 'idem-1',
        reauthToken: 'ticket-1',
      );
      final req = backend.lastRequestFor('POST', '/tasks/1/workflow/start')!;
      expect(req.headers['If-Match'], '"7"');
      expect(req.headers['Idempotency-Key'], 'idem-1');
      // Backend contract: X-Reauth-Ticket (khong phai X-Reauth-Token).
      expect(req.headers['X-Reauth-Ticket'], 'ticket-1');
      expect(req.headers.containsKey('X-Reauth-Token'), isFalse);
    });
  });

  group('ErrorEnvelope backend-shape', () {
    test('parse code/details/reauth/retry/version + corr tu header', () async {
      final backend = FakeBackend()
        ..when(
          'GET',
          '/tasks/1',
          FakeRoute(
            428,
            errJson(
              'REAUTH_REQUIRED',
              reauthRequired: true,
              actionCode: 'workflow.approve',
              retryAfter: 5,
              resourceVersion: 9,
            ),
          ),
        );
      final client = backend.newClient();
      try {
        await client.get('/tasks/1');
        fail('phai nem');
      } on ApiException catch (e) {
        expect(e.envelope.code, 'REAUTH_REQUIRED');
        expect(e.isReauthRequired, isTrue);
        expect(e.envelope.reauthActionCode, 'workflow.approve');
        expect(e.envelope.retryAfterSeconds, 5);
        expect(e.envelope.resourceVersion, 9);
        // Docs 03: body correlation_id = echo X-Correlation-Id (nguon chuan).
        expect(e.envelope.correlationId, 'corr-body');
      }
    });

    test('404 trung tinh khong lo doi tuong', () async {
      final backend = FakeBackend()
        ..when('GET', '/tasks/x', FakeRoute(404, errJson('RESOURCE_NOT_FOUND')));
      final client = backend.newClient();
      try {
        await client.get('/tasks/x');
        fail('phai nem');
      } on ApiException catch (e) {
        expect(e.isNotFound, isTrue);
        expect(e.envelope.isNeutralNotFound, isTrue);
        expect(e.envelope.neutralMessage, isNotEmpty);
      }
    });

    test('fallback legacy tuong minh: message_params + reauth_action_code',
        () async {
      final backend = FakeBackend()
        ..when(
          'GET',
          '/legacy',
          FakeRoute(428, <String, dynamic>{
            'error': <String, dynamic>{
              'code': 'REAUTH_REQUIRED',
              'message_key': 'auth.reauth.required',
              'message_params': <String, dynamic>{'device_label': 'old'},
              'reauth_action_code': 'legacy.action',
            },
            'correlation_id': 'corr-legacy',
            'timestamp': '',
          }),
        );
      final client = backend.newClient();
      try {
        await client.get('/legacy');
        fail('phai nem');
      } on ApiException catch (e) {
        expect(e.envelope.messageParams['device_label'], 'old');
        expect(e.envelope.reauthActionCode, 'legacy.action');
        expect(e.envelope.correlationId, 'corr-legacy');
      }
    });
  });

  group('PageParser', () {
    test('canonical: data.items + next_cursor + has_more', () {
      final page = PageParser.parsePage(<String, dynamic>{
        'data': <String, dynamic>{
          'data': [],
          'items': [
            {'id': '1'}
          ],
          'next_cursor': 'c9',
          'has_more': true,
        },
        'message_key': 'ok',
      });
      expect(page.items.single['id'], '1');
      expect(page.nextCursor, 'c9');
      expect(page.hasMore, isTrue);
    });

    test('fallback legacy: alias data ben trong page', () {
      final page = PageParser.parsePage(<String, dynamic>{
        'data': <String, dynamic>{
          'data': [
            {'id': '2'}
          ],
          'next_cursor': null,
          'has_more': false,
        },
      });
      expect(page.items.single['id'], '2');
      expect(page.hasMore, isFalse);
    });

    test('fallback legacy: top-level items + bare list', () {
      final top = PageParser.parsePage(<String, dynamic>{
        'items': [
          {'id': '3'}
        ],
      });
      expect(top.items.single['id'], '3');
      final bare = PageParser.parsePage([
        {'id': '4'}
      ]);
      expect(bare.items.single['id'], '4');
    });

    test('parseObject boc ApiResult.data; parseList cho calendar', () {
      final obj = PageParser.parseObject(<String, dynamic>{
        'data': {'ticket_id': 't1'}
      });
      expect(obj['ticket_id'], 't1');
      final list = PageParser.parseList(<String, dynamic>{
        'data': [
          {'task_id': 'a'}
        ],
      });
      expect(list.single['task_id'], 'a');
    });
  });
}
