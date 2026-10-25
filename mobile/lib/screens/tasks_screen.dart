// Danh sach Task: filter (state mo rong COMPLETED/CANCELLED, assignee,
// overdue), sort (allowlist khop backend TaskSort), cursor pagination.
// Query ?project_id= theo contract backend (nested chua co).
// Noi dung Task KHONG cache offline (DEC-12/A) -> canh boi PrivateGate.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../cache/connectivity.dart';
import '../widgets/offline_banner.dart';
import '../widgets/private_gate.dart';

/// Sort allowlist khop backend TaskSort (due_at, start_at, priority, state,
/// title, created_at, updated_at) voi cu phap canonical `-field` = desc.
/// Backend tra 400 UNSUPPORTED_SORT_FIELD neu sai — khong fallback am tham.
const _allowedSorts = {
  '-updated_at',
  '-created_at',
  'due_at',
  '-due_at',
  'start_at',
  '-start_at',
  'priority',
  '-priority',
  'state',
  '-state',
  'title',
  '-title',
};

/// State filter mo rong khop backend (TODO/IN_PROGRESS/PENDING_APPROVAL +
/// COMPLETED/CANCELLED cho overdue semantics).
const _stateFilters = {
  'TODO',
  'IN_PROGRESS',
  'PENDING_APPROVAL',
  'COMPLETED',
  'CANCELLED',
};

class TasksScreen extends StatefulWidget {
  final String projectId;
  const TasksScreen({super.key, required this.projectId});

  @override
  State<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends State<TasksScreen> {
  final List<Map<String, Object?>> _items = [];
  final _assigneeCtl = TextEditingController();
  String? _cursor;
  bool _hasMore = true;
  bool _busy = false;
  String? _error;
  String _sort = '-updated_at';
  String? _stateFilter;
  bool _overdueOnly = false;

  @override
  void initState() {
    super.initState();
    _loadMore(reset: true);
  }

  @override
  void dispose() {
    _assigneeCtl.dispose();
    super.dispose();
  }

  Future<void> _loadMore({bool reset = false}) async {
    if (_busy || (!reset && !_hasMore)) return;
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
      if (reset) {
        _items.clear();
        _cursor = null;
        _hasMore = true;
      }
    });
    try {
      final query = <String, Object?>{'limit': 20, 'sort': _sort};
      final String? cur = _cursor;
      if (cur != null) query['cursor'] = cur;
      final String? sf = _stateFilter;
      if (sf != null) query['state'] = sf;
      if (_overdueOnly) query['overdue'] = true;
      final assignee = _assigneeCtl.text.trim();
      if (assignee.isNotEmpty) query['assignee_id'] = assignee;
      final data = await api.get(ApiPaths.tasks(widget.projectId), query: query);
      final page = PageParser.parsePage(data);
      _items.addAll(page.items);
      _cursor = page.nextCursor;
      _hasMore = page.hasMore;
    } on DioException catch (e) {
      final err = e.error;
      _error = err is ApiException
          ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
          : e.message;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectionMonitor>().isOnline;
    return PrivateGate(
      resourceLabel: 'Tasks',
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Tasks'),
          actions: [
            IconButton(
              icon: const Icon(Icons.auto_awesome),
              tooltip: 'AI draft cho project này',
              onPressed: () =>
                  context.go('/ai?projectId=${widget.projectId}'),
            ),
            DropdownButton<String>(
              value: _sort,
              items: _allowedSorts
                  .map((s) => DropdownMenuItem(value: s, child: Text(s)))
                  .toList(),
              onChanged: (v) {
                if (v == null) return;
                setState(() => _sort = v);
                _loadMore(reset: true);
              },
            ),
          ],
        ),
        body: Column(
          children: [
            OfflineBanner(
              isOnline: online,
              onReauthorize: () => context.go('/reauth'),
            ),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final s in _stateFilters)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: FilterChip(
                        label: Text(s),
                        selected: _stateFilter == s,
                        onSelected: (_) {
                          setState(() {
                            _stateFilter = _stateFilter == s ? null : s;
                          });
                          _loadMore(reset: true);
                        },
                      ),
                    ),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: FilterChip(
                      label: const Text('OVERDUE'),
                      selected: _overdueOnly,
                      onSelected: (_) {
                        setState(() => _overdueOnly = !_overdueOnly);
                        _loadMore(reset: true);
                      },
                    ),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _assigneeCtl,
                      decoration: const InputDecoration(
                        labelText: 'assignee_id (UUID, tùy chọn)',
                        isDense: true,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.search),
                    onPressed: () => _loadMore(reset: true),
                  ),
                ],
              ),
            ),
            Expanded(
              child: _error != null && _items.isEmpty
                  ? Center(child: Text(_error!))
                  : RefreshIndicator(
                      onRefresh: () => _loadMore(reset: true),
                      child: ListView.builder(
                        itemCount: _items.length + 1,
                        itemBuilder: (context, i) {
                          if (i == _items.length) {
                            if (!_hasMore) return const SizedBox.shrink();
                            return TextButton(
                              onPressed: _busy ? null : () => _loadMore(),
                              child: const Text('Tải thêm'),
                            );
                          }
                          final t = _items[i];
                          return ListTile(
                            title: Text(t['title']?.toString() ?? '(không tiêu đề)'),
                            subtitle: Text(t['state']?.toString() ?? ''),
                            onTap: () =>
                                context.go('/tasks/${t['id']}'),
                          );
                        },
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
