// Test OfflineStore: allowlist, TTL 24h, gan scope, gate reauthorize,
// xoa khi revoke, tuong thich cache cu (session_id).
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:smarttask_mobile/cache/offline_store.dart';
import 'package:smarttask_mobile/cache/secure_store.dart';

void main() {
  group('OfflineAllowlist', () {
    test('chi chap nhan loai allowlist, tu choi noi dung rieng tu', () {
      for (final k in CachedKind.values) {
        expect(OfflineAllowlist.isAllowed(k), isTrue);
      }
      expect(OfflineAllowlist.isPrivateContent('task'), isTrue);
      expect(OfflineAllowlist.isPrivateContent('project_detail'), isTrue);
      expect(OfflineAllowlist.isPrivateContent('attachment'), isTrue);
      expect(OfflineAllowlist.isPrivateContent('report'), isTrue);
      expect(OfflineAllowlist.isPrivateContent('workspace_name'), isFalse);
    });
  });

  group('OfflineStore', () {
    test('gate reauthorize: chua mark thi read null', () async {
      final store = OfflineStore(storage: MemorySecureStore());
      await store.put(CachedKind.workspaceNames, 'u1', [
        {'id': 'w1', 'name': 'WS'}
      ]);
      expect(await store.read(CachedKind.workspaceNames, 'u1'), isNull);
      await store.markReauthorized();
      final back = await store.read(CachedKind.workspaceNames, 'u1');
      expect(back, isNotNull);
    });

    test('khac scope thi khong doc duoc', () async {
      final store = OfflineStore(storage: MemorySecureStore());
      await store.markReauthorized();
      await store.put(CachedKind.projectNames, 'u1', [
        {'id': 'p1'}
      ]);
      expect(await store.read(CachedKind.projectNames, 'u2'), isNull);
      expect(await store.read(CachedKind.projectNames, 'u1'), isNotNull);
    });

    test('het TTL thi null (TTL 24h mac dinh)', () async {
      final store = OfflineStore(
        storage: MemorySecureStore(),
        ttl: Duration.zero,
      );
      await store.markReauthorized();
      await store.put(CachedKind.uiPreferences, 'u1', {'theme': 'dark'});
      expect(await store.read(CachedKind.uiPreferences, 'u1'), isNull);
    });

    test('clearOnRevoke xoa het + reset reauthorized', () async {
      final store = OfflineStore(storage: MemorySecureStore());
      await store.markReauthorized();
      await store.put(CachedKind.workspaceNames, 'u1', [
        {'id': 'w1'}
      ]);
      expect(await store.read(CachedKind.workspaceNames, 'u1'), isNotNull);
      await store.clearOnRevoke();
      // Kiem tra truc tiep store thap: key da bi xoa.
      expect(await store.storage.read('stm_cache_workspaceNames'), isNull);
      expect(store.reauthorized, isFalse);
    });

    test('doc duoc cache cu dung key session_id (legacy)', () async {
      final mem = MemorySecureStore();
      final legacy = jsonEncode({
        'session_id': 'u9',
        'payload': jsonEncode([
          {'id': 'w9'}
        ]),
        'stored_at': DateTime.now().toUtc().toIso8601String(),
      });
      await mem.write('stm_cache_workspaceNames', legacy);
      final store = OfflineStore(storage: mem);
      await store.markReauthorized();
      final back = await store.read(CachedKind.workspaceNames, 'u9');
      expect(back, isNotNull);
    });
  });
}
