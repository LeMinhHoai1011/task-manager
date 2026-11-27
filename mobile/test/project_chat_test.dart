import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:smarttask_mobile/auth/session.dart';
import 'package:smarttask_mobile/cache/secure_store.dart';
import 'package:smarttask_mobile/screens/project_chat_screen.dart';

import 'fake_backend.dart';

void main() {
  const path = '/projects/p1/messages';
  const peer = {
    'id': 'm1',
    'seq': 1,
    'changed_seq': 1,
    'sender_id': 'peer',
    'sender_name': 'Thành viên',
    'body': 'Tin mới của dự án',
    'created_at': '2026-11-27T09:00:00Z',
    'deleted': false,
    'linked_task': {'id': 't1', 'title': 'Việc cần xem', 'state': 'TODO'},
  };

  FakeRoute page({required bool write}) => FakeRoute(200, {
    'data': {
      'items': [peer],
      'revision': 1,
      'next_after': 1,
      'has_more': false,
      'access': {'read': true, 'write': write},
      'unread': {'count': 1, 'last_read_seq': 0},
    },
  });

  Future<SessionManager> open(WidgetTester tester, FakeBackend backend) async {
    final session = SessionManager(
      storage: MemorySecureStore(),
      clientFactory: (reader) => backend.newClient(tokenReader: reader),
    )..userId = 'me';
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: session,
        child: const MaterialApp(
          home: ProjectChatScreen(projectId: 'p1', projectName: 'Dự án A'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return session;
  }

  testWidgets('shows project history and provides a read-only room', (
    tester,
  ) async {
    final backend = FakeBackend()
      ..when('GET', path, page(write: false))
      ..when(
        'PUT',
        '/projects/p1/chat/read',
        const FakeRoute(200, {
          'data': {'count': 0, 'last_read_seq': 1},
        }),
      );
    final session = await open(tester, backend);
    expect(find.text('Dự án A'), findsOneWidget);
    expect(find.text('Tin mới của dự án'), findsOneWidget);
    expect(find.text('Tin chưa đọc'), findsOneWidget);
    expect(find.text('Việc cần xem'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);
    await tester.pumpWidget(const SizedBox());
    session.dispose();
  });

  testWidgets('reply, pinned preview and search stay in the current project', (
    tester,
  ) async {
    final backend = FakeBackend()
      ..when('GET', path, page(write: true))
      ..when(
        'PUT',
        '/projects/p1/chat/read',
        const FakeRoute(200, {
          'data': {'count': 0, 'last_read_seq': 1},
        }),
      )
      ..when(
        'GET',
        '/projects/p1/chat/pinned',
        const FakeRoute(200, {
          'data': [peer],
        }),
      )
      ..when(
        'GET',
        '/projects/p1/chat/search',
        const FakeRoute(200, {
          'data': [peer],
        }),
      )
      ..when(
        'POST',
        path,
        FakeRoute(200, {
          'data': {
            ...peer,
            'id': 'm2',
            'seq': 2,
            'changed_seq': 2,
            'sender_id': 'me',
            'body': 'Đồng ý',
            'reply_to': {'id': 'm1', 'body': peer['body']},
          },
        }),
      );
    final session = await open(tester, backend);
    expect(find.byIcon(Icons.push_pin_outlined), findsOneWidget);
    await tester.tap(find.byTooltip('Tìm tin nhắn'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.descendant(
        of: find.byType(AlertDialog),
        matching: find.byType(TextField),
      ),
      'Tin mới',
    );
    await tester.tap(find.text('Tìm'));
    await tester.pumpAndSettle();
    expect(
      backend
          .lastRequestFor('GET', '/projects/p1/chat/search')
          ?.queryParameters['q'],
      'Tin mới',
    );
    await tester.tap(
      find.descendant(
        of: find.byType(BottomSheet),
        matching: find.text('Tin mới của dự án'),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Thao tác tin nhắn'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Trả lời'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Đồng ý');
    await tester.tap(find.byTooltip('Gửi tin nhắn'));
    await tester.pumpAndSettle();
    expect(backend.lastBodyFor('POST', path)?['reply_to_id'], 'm1');
    expect(find.text('Đồng ý'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    session.dispose();
  });

  testWidgets('retried message keeps the same client ID', (tester) async {
    final backend = FakeBackend()
      ..when('GET', path, page(write: true))
      ..when(
        'PUT',
        '/projects/p1/chat/read',
        const FakeRoute(200, {
          'data': {'count': 0, 'last_read_seq': 1},
        }),
      )
      ..when('POST', path, FakeRoute(503, errJson('TEMPORARILY_UNAVAILABLE')));
    final session = await open(tester, backend);
    await tester.enterText(find.byType(TextField), 'Tiến độ đã được cập nhật');
    await tester.tap(find.byTooltip('Gửi tin nhắn'));
    await tester.pumpAndSettle();
    final first = backend.lastBodyFor('POST', path)!;
    expect(find.text('Chưa gửi được. Bấm gửi để thử lại.'), findsOneWidget);

    backend.when(
      'POST',
      path,
      FakeRoute(200, {
        'data': {
          ...peer,
          'id': 'm2',
          'seq': 2,
          'changed_seq': 2,
          'sender_id': 'me',
          'linked_task': null,
          'body': 'Tiến độ đã được cập nhật',
        },
      }),
    );
    await tester.tap(find.byTooltip('Gửi tin nhắn'));
    await tester.pumpAndSettle();
    expect(backend.lastBodyFor('POST', path), first);
    expect(find.text('Tiến độ đã được cập nhật'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    session.dispose();
  });
}
