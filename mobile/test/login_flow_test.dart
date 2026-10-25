// Test login flow tren widget that: nhap user/pass -> session
// authenticated + token luu store (khong can backend).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:smarttask_mobile/api/client.dart';
import 'package:smarttask_mobile/auth/session.dart';
import 'package:smarttask_mobile/cache/secure_store.dart';
import 'package:smarttask_mobile/screens/login_screen.dart';

import 'fake_backend.dart';

void main() {
  testWidgets('login flow: submit -> authenticated + luu token', (tester) async {
    final backend = FakeBackend()
      ..when(
        'POST',
        '/auth/login',
        FakeRoute(200, <String, dynamic>{
          'data': <String, dynamic>{
            'access_token': 'a9',
            'refresh_token': 'r9',
            'expires_at': '2026-10-13T00:00:00Z',
            'user_id': 'u9',
          },
          'message_key': 'auth.login.success',
        }),
      );
    final mem = MemorySecureStore();
    final session = SessionManager(
      clientFactory: (reader) => ApiClient(
        baseUrl: 'http://test',
        tokenReader: reader,
        clientType: ClientType.android,
      )..dio.httpClientAdapter = backend,
      storage: mem,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: ChangeNotifierProvider.value(
          value: session,
          child: const LoginScreen(),
        ),
      ),
    );
    await tester.enterText(
      find.widgetWithText(TextField, 'Tên đăng nhập'),
      'alice',
    );
    await tester.enterText(
      find.widgetWithText(TextField, 'Mật khẩu'),
      'secret',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await tester.pumpAndSettle();

    expect(session.status, AuthStatus.authenticated);
    expect(await mem.read('stm_access_token'), 'a9');
    expect(session.userId, 'u9');
  });
}
