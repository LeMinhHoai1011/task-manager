// Danh sach Workspace (chi TEN thuoc allowlist offline DEC-12/A).
// Online: tai qua API (PageResponse canonical) + luu cache ten.
// Offline: doc cache ten (can reauthorized) + OfflineBanner.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../cache/connectivity.dart';
import '../cache/offline_store.dart';
import '../realtime/ws_client.dart';
import '../widgets/offline_banner.dart';
import '../widgets/realtime_indicator.dart';

class WorkspacesScreen extends StatefulWidget {
  const WorkspacesScreen({super.key});

  @override
  State<WorkspacesScreen> createState() => _WorkspacesScreenState();
}

class _WorkspacesScreenState extends State<WorkspacesScreen> {
  List<Map<String, Object?>> _items = [];
  bool _busy = true;
  String? _error;
  bool _fromCache = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final session = context.read<SessionManager>();
    final offline = context.read<OfflineStore>();
    setState(() {
      _busy = true;
      _error = null;
      _fromCache = false;
    });
    try {
      final data = await session.api.get(ApiPaths.workspaces());
      final page = PageParser.parsePage(data);
      _items = page.items;
      _fromCache = false;
      // Chi cache TEN (allowlist), gan scope user_id.
      final scope = session.userId;
      if (scope != null) {
        await offline.put(
          CachedKind.workspaceNames,
          scope,
          _items
              .map((w) => {
                    'id': w['id']?.toString(),
                    'name': w['name']?.toString(),
                  })
              .toList(),
        );
      }
    } on DioException catch (e) {
      final err = e.error;
      // Offline: roi ve cache ten (doi hoi reauthorized theo OfflineStore).
      final scope = session.userId;
      if (scope != null) {
        final cached = await offline.read(CachedKind.workspaceNames, scope);
        if (cached is List) {
          _items = cached
              .whereType<Map>()
              .map((w) => w.cast<String, Object?>())
              .toList();
          _fromCache = true;
          _error = null;
        } else {
          _error = err is ApiException
              ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
              : e.message;
        }
      } else {
        _error = err is ApiException
            ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
            : e.message;
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectionMonitor>().isOnline;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Workspaces'),
        actions: [
          RealtimeIndicator(
            onReconnect: () =>
                context.read<RealtimeClient>().reconnectAfterReauth(),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => context.read<SessionManager>().logout(),
          ),
        ],
      ),
      body: Column(
        children: [
          OfflineBanner(
            isOnline: online,
            onReauthorize: () => context.go('/reauth'),
          ),
          if (_fromCache)
            const Padding(
              padding: EdgeInsets.all(8),
              child: Text('Đang xem tên đã lưu offline (cần reauthorize).'),
            ),
          Expanded(
            child: _busy
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(_error!),
                            TextButton(
                              onPressed: _load,
                              child: const Text('Thử lại'),
                            ),
                          ],
                        ),
                      )
                    : RefreshIndicator(
                        onRefresh: _load,
                        child: ListView.builder(
                          itemCount: _items.length,
                          itemBuilder: (context, i) {
                            final ws = _items[i];
                            return ListTile(
                              title: Text(ws['name']?.toString() ?? '(không tên)'),
                              subtitle: Text(ws['id']?.toString() ?? ''),
                              onTap: () => context.go(
                                '/workspaces/${ws['id']}/projects',
                              ),
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
    );
  }
}
