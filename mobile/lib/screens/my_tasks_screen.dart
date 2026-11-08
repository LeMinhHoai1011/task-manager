// Man hinh Viec cua toi (personal tasks) - tuong tu to-do app.
// Hien thi danh sach, toggle hoan thanh, tao nhanh, filter, sap xep.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../cache/connectivity.dart';
import '../widgets/offline_banner.dart';

class _TaskItem {
  final String id;
  final String title;
  final String state;
  final String? priority;
  final String? dueAt;
  final int version;
  _TaskItem({required this.id, required this.title, required this.state,
    this.priority, this.dueAt, required this.version});
  factory _TaskItem.fromMap(Map<String, Object?> m) => _TaskItem(
    id: m['id']?.toString() ?? '',
    title: m['title']?.toString() ?? '(không tiêu đề)',
    state: m['state']?.toString() ?? 'TODO',
    priority: m['priority']?.toString(),
    dueAt: m['due_at']?.toString() ?? m['dueAt']?.toString(),
    version: (m['version'] as num?)?.toInt() ?? 1,
  );
}

class MyTasksScreen extends StatefulWidget {
  const MyTasksScreen({super.key});

  @override
  State<MyTasksScreen> createState() => _MyTasksScreenState();
}

class _MyTasksScreenState extends State<MyTasksScreen> {
  List<_TaskItem> _items = [];
  bool _busy = true;
  String? _error;
  String _query = '';
  String _state = '';
  String _priority = '';
  bool _overdue = false;
  int _page = 0;
  bool _last = true;
  final _titleCtl = TextEditingController();
  final _searchCtl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _titleCtl.dispose();
    _searchCtl.dispose();
    super.dispose();
  }

  Future<void> _load({bool reset = true}) async {
    if (reset) {
      setState(() { _page = 0; _last = true; _items = []; _error = null; });
    }
    setState(() { _busy = true; });
    final api = context.read<SessionManager>().api;
    try {
      final data = await api.get(ApiPaths.meTasks, query: {
        'page': _page,
        'size': 20,
        'q': _query.isEmpty ? null : _query,
        'state': _state.isEmpty ? null : _state,
        'priority': _priority.isEmpty ? null : _priority,
        'overdue': _overdue ? 'true' : null,
        'sort': 'createdAt',
      });
      final page = PageParser.parsePage(data);
      if (!mounted) return;
      setState(() {
        if (reset) _items = page.items.cast<Map<String, Object?>>().map(_TaskItem.fromMap).toList();
        else _items.addAll(page.items.cast<Map<String, Object?>>().map(_TaskItem.fromMap).toList());
        _last = page.hasMore == false;
        _busy = false;
      });
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() {
        _error = err is ApiException ? err.envelope.code : e.message;
        _busy = false;
      });
    }
  }

  Future<void> _toggle(_TaskItem task) async {
    final api = context.read<SessionManager>().api;
    try {
      await api.mutate(ApiPaths.meTaskCompletion(task.id),
          method: 'PATCH', ifMatchVersion: task.version,
          idempotencyKey: api.newIdempotencyKey(),
          body: {'completed': task.state != 'COMPLETED'});
      await _load();
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err is ApiException ? err.envelope.code : 'Lỗi')),
      );
    }
  }

  Future<void> _create() async {
    final title = _titleCtl.text.trim();
    if (title.isEmpty) return;
    final api = context.read<SessionManager>().api;
    setState(() { _busy = true; });
    try {
      await api.mutate(ApiPaths.meTasks, method: 'POST',
          body: {'title': title}, idempotencyKey: api.newIdempotencyKey());
      _titleCtl.clear();
      await _load();
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() => _busy = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err is ApiException ? err.envelope.code : 'Lỗi tạo')),
      );
    }
  }

  String _statusLabel(String s) => switch (s) {
    'TODO' => 'Cần làm',
    'IN_PROGRESS' => 'Đang làm',
    'COMPLETED' => 'Hoàn thành',
    'CANCELLED' => 'Đã hủy',
    _ => s,
  };

  String _dueLabel(String? due) {
    if (due == null || due.isEmpty) return 'Chưa đặt hạn';
    try {
      final d = DateTime.parse(due);
      return DateFormat('dd/MM/yyyy', 'vi_VN').format(d);
    } catch (_) {
      return due;
    }
  }

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectionMonitor>().isOnline;
    return Scaffold(
      appBar: AppBar(title: const Text('Việc của tôi')),
      body: Column(
        children: [
          OfflineBanner(
            isOnline: online,
            onReauthorize: () => context.go('/reauth'),
          ),
          // Quick create
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _titleCtl,
                    decoration: const InputDecoration(
                      hintText: 'Bạn cần làm gì tiếp theo?',
                      isDense: true,
                    ),
                    onSubmitted: (_) => _create(),
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: _busy ? null : _create,
                  child: const Text('Thêm'),
                ),
              ],
            ),
          ),
          // Filters
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: Wrap(
              spacing: 6,
              children: [
                FilterChip(label: const Text('Tất cả'), selected: _state.isEmpty,
                    onSelected: (_) => setState(() { _state = ''; _load(); })),
                FilterChip(label: const Text('Cần làm'), selected: _state == 'TODO',
                    onSelected: (_) => setState(() { _state = 'TODO'; _load(); })),
                FilterChip(label: const Text('Đang làm'), selected: _state == 'IN_PROGRESS',
                    onSelected: (_) => setState(() { _state = 'IN_PROGRESS'; _load(); })),
                FilterChip(label: const Text('Hoàn thành'), selected: _state == 'COMPLETED',
                    onSelected: (_) => setState(() { _state = 'COMPLETED'; _load(); })),
                FilterChip(label: const Text('Quá hạn'), selected: _overdue,
                    onSelected: (_) => setState(() { _overdue = !_overdue; _load(); })),
              ],
            ),
          ),
          // Search
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: TextField(
              controller: _searchCtl,
              decoration: const InputDecoration(
                hintText: 'Tìm tiêu đề...',
                isDense: true,
                prefixIcon: Icon(Icons.search, size: 18),
              ),
              onChanged: (v) => setState(() { _query = v; _page = 0; _load(); }),
            ),
          ),
          Expanded(
            child: _busy && _items.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : _error != null && _items.isEmpty
                    ? Center(child: Text(_error!))
                    : _items.isEmpty
                        ? const Center(child: Text('Chưa có công việc phù hợp.'))
                        : RefreshIndicator(
                            onRefresh: _load,
                            child: ListView.builder(
                              itemCount: _items.length,
                              itemBuilder: (context, i) {
                                final t = _items[i];
                                final done = t.state == 'COMPLETED';
                                return ListTile(
                                  leading: Checkbox(
                                    value: done,
                                    onChanged: (_) => _toggle(t),
                                    activeColor: Theme.of(context).primaryColor,
                                  ),
                                  title: Text(
                                    t.title,
                                    style: TextStyle(
                                      decoration: done ? TextDecoration.lineThrough : null,
                                      color: done ? Colors.grey : null,
                                    ),
                                  ),
                                  subtitle: Text(
                                    '${_statusLabel(t.state)} • ${t.priority ?? 'MEDIUM'} • ${_dueLabel(t.dueAt)}',
                                  ),
                                  onTap: () => context.go('/tasks/${t.id}'),
                                );
                              },
                            ),
                          ),
          ),
          if (!_last)
            Padding(
              padding: const EdgeInsets.all(8),
              child: TextButton(
                onPressed: _busy ? null : () => _load(reset: false),
                child: const Text('Tải thêm'),
              ),
            ),
        ],
      ),
    );
  }
}
