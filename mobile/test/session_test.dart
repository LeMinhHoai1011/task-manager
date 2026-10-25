// Test SessionManager: login (device_label + client_type that),
// single-session phong thu, refresh rotation + refresh-expired don sach,
// logout best-effort.
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smarttask_mobile/api/client.dart';
import 'package:smarttask_mobile/auth/session.dart';
import 'package:smarttask_mobile/cache/secure_store.dart';

import 'fake_backend.dart';

Map<String, dynamic> loginOk() => <String, dynamic>{
      'data': <String, dynamic>{
        'access_token': 'a1',
        'refresh_token': 'r1',
        'expires_at': '2026-10-13T00:00:00Z',
        'user_id': 'u1',
      },
      'message_key': 'auth.login.success',
    };

SessionManager newSession(FakeBackend backend, MemorySecureStore mem) {
  return SessionManager(
    clientFactory: (reader) => ApiClient(
      baseUrl: 'http://test',
      tokenReader: reader,
      clientType: ClientType.android,
    )..dio.httpClientAdapter = backend,
    storage: mem,
  );
}

void main() {
  group('SessionManager', () {
    test('login thanh cong luu token + user_id (khong doi session_id)',
        () async {
      final backend = FakeBackend()
        ..when('POST', '/auth/login', FakeRoute(200, loginOk()));
      final mem = MemorySecureStore();
      final session = newSession(backend, mem);
      await session.login(username: 'u', password: 'p');
      expect(session.status, AuthStatus.authenticated);
      expect(session.userId, 'u1');
      expect(await mem.read('stm_access_token'), 'a1');
      expect(await mem.read('stm_refresh_token'), 'r1');
      expect(await mem.read('stm_user_id'), 'u1');
      final body = backend.lastBodyFor('POST', '/auth/login')!;
      // Nhan that theo nen tang (khong hardcode 'Android app').
      expect(body['device_label'], isNotNull);
      expect(body['device_label'], isNot('Android app'));
      expect(body['client_type'], isNotNull);
    });

    test('single-session phong thu: 409 thi bat notice', () async {
      final backend = FakeBackend()
        ..when(
          'POST',
          '/auth/login',
          FakeRoute(
            409,
            errJson('SESSION_ALREADY_ACTIVE', actionCode: null),
          ),
        );
      final session = newSession(backend, MemorySecureStore());
      try {
        await session.login(username: 'u', password: 'p');
        fail('phai nem');
      } on DioException {
        // expected
      }
      expect(session.singleSessionNotice, isTrue);
    });

    test('refresh that bai (expired) don sach + goi onSessionEnded', () async {
      final backend = FakeBackend()
        ..when('POST', '/auth/login', FakeRoute(200, loginOk()))
        ..when(
          'POST',
          '/auth/refresh',
          FakeRoute(401, errJson('INVALID_SESSION')),
        );
      final mem = MemorySecureStore();
      final session = newSession(backend, mem);
      var ended = 0;
      session.onSessionEnded = () async {
        ended++;
      };
      await session.login(username: 'u', password: 'p');
      final ok = await session.refresh();
      expect(ok, isFalse);
      expect(session.status, AuthStatus.unauthenticated);
      // Duong refresh-expired da sua: xoa ca token + goi hook don offline.
      expect(await mem.read('stm_access_token'), isNull);
      expect(await mem.read('stm_refresh_token'), isNull);
      expect(ended, 1);
      expect(session.endReason, SessionEndReason.expired);
    });

    test('logout best-effort: API loi van xoa local + hook', () async {
      final backend = FakeBackend()
        ..when('POST', '/auth/login', FakeRoute(200, loginOk()))
        ..when('POST', '/auth/logout',
            FakeRoute(500, errJson('INTERNAL_ERROR')));
      final mem = MemorySecureStore();
      final session = newSession(backend, mem);
      var ended = 0;
      session.onSessionEnded = () async {
        ended++;
      };
      await session.login(username: 'u', password: 'p');
      await session.logout();
      expect(session.status, AuthStatus.unauthenticated);
      expect(await mem.read('stm_access_token'), isNull);
      expect(ended, 1);
    });

    test('requireReauth/clearReauth chuyen trang thai', () {
      final session = SessionManager(
        clientFactory: (reader) => ApiClient(
          baseUrl: 'http://test',
          tokenReader: reader,
          clientType: ClientType.android,
        ),
        storage: MemorySecureStore(),
      );
      session.status = AuthStatus.authenticated;
      session.requireReauth('workflow.approve');
      expect(session.status, AuthStatus.reauthRequired);
      expect(session.pendingReauthActionCode, 'workflow.approve');
      session.clearReauth();
      expect(session.status, AuthStatus.authenticated);
    });
  });
}
