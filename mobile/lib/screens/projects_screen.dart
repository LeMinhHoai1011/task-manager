// Danh sach Project trong Workspace (query ?workspace_id= — nested
// /workspaces/{id}/projects chua co tren backend, xem endpoints.dart).
// Chi TEN thuoc allowlist offline; OfflineBanner khi mat mang.
library;

import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../cache/connectivity.dart';
import '../cache/offline_store.dart';
import '../widgets/offline_banner.dart';

class ProjectsScreen extends StatefulWidget {
  final String workspaceId;
  const ProjectsScreen({super.key, required this.workspaceId});

  @override
  State<ProjectsScreen> createState() => _ProjectsScreenState();
}

class _ProjectsScreenState extends State<ProjectsScreen> {
  List<Map<String, Object?>> _items = [];
  bool _busy = true;
  String? _error;
  bool _fromCache = false;
  Map<String, Object?> _chatCounts = {};
  Timer? _chatTimer;

  Future<void> _loadChatCounts() async {
    final session = context.read<SessionManager>();
    final actor = session.userId;
    try {
      final result = PageParser.parseObject(
        await session.api.get('/chat/unread'),
      );
      if (mounted && session.userId == actor) {
        setState(() => _chatCounts = result);
      }
    } on Exception {
      if (mounted) setState(() => _chatCounts = {});
    }
  }

  @override
  void dispose() {
    _chatTimer?.cancel();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _load();
    unawaited(_loadChatCounts());
    _chatTimer = Timer.periodic(const Duration(seconds: 15), (_) {
      if (ModalRoute.of(context)?.isCurrent == true &&
          WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
        unawaited(_loadChatCounts());
      }
    });
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
      final data = await session.api.get(ApiPaths.projects(widget.workspaceId));
      final page = PageParser.parsePage(data);
      _items = page.items;
      final scope = session.userId;
      if (scope != null) {
        await offline.put(
          CachedKind.projectNames,
          scope,
          _items
              .map(
                (p) => {
                  'id': p['id']?.toString(),
                  'workspace_id':
                      p['workspace_id']?.toString() ?? widget.workspaceId,
                  'name': p['name']?.toString(),
                  'status': p['status']?.toString(),
                },
              )
              .toList(),
        );
      }
    } on DioException catch (e) {
      final err = e.error;
      final scope = session.userId;
      if (scope != null) {
        final cached = await offline.read(CachedKind.projectNames, scope);
        if (cached is List) {
          _items = cached
              .whereType<Map>()
              .map((p) => p.cast<String, Object?>())
              .where(
                (p) =>
                    widget.workspaceId.isEmpty ||
                    p['workspace_id']?.toString() == widget.workspaceId ||
                    // Cache cu khong luu workspace_id: hien tat ca, ghi ro.
                    p['workspace_id'] == null,
              )
              .toList();
          _fromCache = true;
          _error = null;
        } else {
          _error = err is ApiException
              ? (err.isNotFound
                    ? err.envelope.neutralMessage
                    : err.envelope.code)
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
      appBar: AppBar(title: const Text('Projects')),
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
                ? Center(child: Text(_error!))
                : RefreshIndicator(
                    onRefresh: _load,
                    child: ListView.builder(
                      itemCount: _items.length,
                      itemBuilder: (context, i) {
                        final p = _items[i];
                        return ListTile(
                          title: Text(p['name']?.toString() ?? '(không tên)'),
                          subtitle: Text(p['status']?.toString() ?? ''),
                          trailing:
                              !_fromCache &&
                                  (_chatCounts[p['id']] as num? ?? 0) > 0
                              ? TextButton.icon(
                                  onPressed: () async {
                                    await context.push(
                                      '/projects/${p['id']}/chat',
                                      extra: p['name'],
                                    );
                                    if (mounted) await _loadChatCounts();
                                  },
                                  icon: const Icon(Icons.chat_bubble_outline),
                                  label: Text(
                                    '${_chatCounts[p['id']]} chưa đọc',
                                  ),
                                )
                              : null,
                          onTap: () async {
                            await context.push('/projects/${p['id']}');
                            if (mounted) await _loadChatCounts();
                          },
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
