// Man hinh chi tiet du an co 7 tab: Tong quan, Cong viec, Ghi chu, Tien do,
// Thanh vien, Bao cao, Cai dat.
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

class ProjectDetailScreen extends StatefulWidget {
  final String projectId;
  const ProjectDetailScreen({super.key, required this.projectId});

  @override
  State<ProjectDetailScreen> createState() => _ProjectDetailScreenState();
}

class _ProjectDetailScreenState extends State<ProjectDetailScreen>
    with SingleTickerProviderStateMixin {
  Map<String, Object?> _project = {};
  List<Map<String, Object?>> _tasks = [];
  List<Map<String, Object?>> _logs = [];
  List<Map<String, Object?>> _members = [];
  String _tab = 'overview';
  bool _busy = true;
  String? _error;
  late TabController _tabCtrl;

  final List<Map<String, String>> _tabs = const [
    {'id': 'overview', 'label': 'Tổng quan'},
    {'id': 'tasks', 'label': 'Công việc'},
    {'id': 'notes', 'label': 'Ghi chú'},
    {'id': 'progress', 'label': 'Tiến độ'},
    {'id': 'members', 'label': 'Thành viên'},
    {'id': 'reports', 'label': 'Báo cáo'},
    {'id': 'settings', 'label': 'Cài đặt'},
  ];

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: _tabs.length, vsync: this);
    _tabCtrl.addListener(() {
      if (!_tabCtrl.indexIsChanging) {
        setState(() => _tab = _tabs[_tabCtrl.index]['id']!);
      }
    });
    _load();
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final api = context.read<SessionManager>().api;
    setState(() { _busy = true; _error = null; });
    try {
      // 1. Project detail
      final pData = await api.get(ApiPaths.project(widget.projectId));
      final pObj = PageParser.parseObject(pData);
      _project = pObj.cast<String, Object?>();

      // 2. Tasks
      final tData = await api.get(ApiPaths.tasks(widget.projectId), query: {'limit': 100});
      final tPage = PageParser.parsePage(tData);
      _tasks = tPage.items;

      // 3. Progress logs
      try {
        final lData = await api.get(ApiPaths.projectProgressLogs(widget.projectId));
        final lPage = PageParser.parsePage(lData);
        _logs = lPage.items;
      } catch (_) { _logs = []; }

      // 4. Members
      try {
        final mData = await api.get('/projects/${widget.projectId}/members');
        final mPage = PageParser.parsePage(mData);
        _members = mPage.items;
      } catch (_) { _members = []; }

      if (!mounted) return;
      setState(() { _busy = false; });
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() {
        _error = err is ApiException ? err.envelope.code : e.message;
        _busy = false;
      });
    }
  }

  int _completedCount() => _tasks.where((t) => t['state'] == 'COMPLETED').length;
  int _activeCount() => _tasks.where((t) => t['state'] != 'CANCELLED').length;
  int _overdueCount() {
    final now = DateTime.now();
    return _tasks.where((t) {
      final due = t['due_at']?.toString();
      if (due == null) return false;
      try {
        return DateTime.parse(due).isBefore(now) && t['state'] != 'COMPLETED';
      } catch (_) { return false; }
    }).length;
  }

  double _computedProgress() {
    final active = _activeCount();
    if (active == 0) return 0;
    return (_completedCount() / active) * 100;
  }

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectionMonitor>().isOnline;
    final name = _project['name']?.toString() ?? 'Chi tiết dự án';
    final desc = _project['description']?.toString() ?? '';
    final status = _project['status']?.toString() ?? 'PLANNING';

    return Scaffold(
      appBar: AppBar(
        title: Text(name),
        bottom: TabBar(
          controller: _tabCtrl,
          isScrollable: true,
          tabs: _tabs.map((t) => Tab(text: t['label'])).toList(),
        ),
      ),
      body: Column(
        children: [
          OfflineBanner(
            isOnline: online,
            onReauthorize: () => context.go('/reauth'),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.all(8),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          // Project banner
          if (!_busy)
            Container(
              padding: const EdgeInsets.all(12),
              color: Theme.of(context).colorScheme.surfaceContainerHighest.withOpacity(0.3),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(name, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                  if (desc.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(desc, style: const TextStyle(fontSize: 14, color: Colors.grey)),
                  ],
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: Theme.of(context).primaryColor.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(status, style: TextStyle(
                          color: Theme.of(context).primaryColor, fontSize: 12)),
                      ),
                      const SizedBox(width: 12),
                      Text('${_completedCount()}/${_activeCount()} công việc',
                          style: const TextStyle(fontSize: 13, color: Colors.grey)),
                      const SizedBox(width: 12),
                      Text('${_overdueCount()} quá hạn',
                          style: const TextStyle(fontSize: 13, color: Colors.red)),
                    ],
                  ),
                  const SizedBox(height: 8),
                  // Dual progress bars
                  Row(
                    children: [
                      Expanded(child: _progressBar('Tự động (task)', _computedProgress(),
                          Theme.of(context).primaryColor)),
                      const SizedBox(width: 12),
                      if (_logs.isNotEmpty)
                        Expanded(child: _progressBar('Báo cáo',
                            (_logs.first['progress_percent'] as num?)?.toDouble() ?? 0,
                            Colors.amber)),
                    ],
                  ),
                ],
              ),
            ),
          Expanded(
            child: _busy
                ? const Center(child: CircularProgressIndicator())
                : TabBarView(
                    controller: _tabCtrl,
                    children: [
                      _buildOverview(),
                      _buildTasks(),
                      _buildNotes(),
                      _buildProgress(),
                      _buildMembers(),
                      _buildReports(),
                      _buildSettings(),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  Widget _progressBar(String label, double pct, Color color) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
            Text('${pct.toInt()}%', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
          ],
        ),
        const SizedBox(height: 4),
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: LinearProgressIndicator(
            value: pct.clamp(0, 100) / 100,
            minHeight: 6,
            backgroundColor: Colors.grey.shade300,
            color: color,
          ),
        ),
      ],
    );
  }

  Widget _buildOverview() {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        const Text('Công việc cần chú ý', style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        ..._tasks.take(5).map((t) => ListTile(
          title: Text(t['title']?.toString() ?? '(không tiêu đề)'),
          subtitle: Text('${_statusLabel(t['state']?.toString())} • Hạn: ${t['due_at']?.toString() ?? '—'}'),
          trailing: Chip(label: Text(_statusLabel(t['state']?.toString()))),
          onTap: () => context.go('/tasks/${t['id']?.toString()}'),
        )).toList(),
      ],
    );
  }

  Widget _buildTasks() {
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: _tasks.length,
      itemBuilder: (context, i) {
        final t = _tasks[i];
        return ListTile(
          title: Text(t['title']?.toString() ?? '(không tiêu đề)'),
          subtitle: Text('${_statusLabel(t['state']?.toString())} • Ưu tiên: ${t['priority']?.toString() ?? '—'}'),
          trailing: Chip(label: Text(_statusLabel(t['state']?.toString()))),
          onTap: () => context.go('/tasks/${t['id']?.toString()}'),
        );
      },
    );
  }

  Widget _buildNotes() {
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: _logs.length, // reuse logs as placeholder for notes
      itemBuilder: (context, i) => ListTile(
        title: Text(_logs[i]['summary']?.toString() ?? 'Ghi chú'),
        subtitle: Text(_logs[i]['updateDate']?.toString() ?? ''),
      ),
    );
  }

  Widget _buildProgress() {
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: _logs.length,
      itemBuilder: (context, i) {
        final l = _logs[i];
        return Card(
          child: ListTile(
            title: Text(l['summary']?.toString() ?? 'Tiến độ'),
            subtitle: Text('${l['progressPercent']}% • ${l['updateDate'] ?? ''}'),
          ),
        );
      },
    );
  }

  Widget _buildMembers() {
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: _members.length,
      itemBuilder: (context, i) {
        final m = _members[i];
        return ListTile(
          title: Text(m['user_email']?.toString() ?? m['email']?.toString() ?? 'Thành viên'),
          subtitle: Text(m['role']?.toString() ?? 'MEMBER'),
          trailing: Chip(label: Text(m['role']?.toString() ?? 'MEMBER')),
        );
      },
    );
  }

  Widget _buildReports() {
    return const Center(
      child: Text('Báo cáo dự án - xem chi tiết trên Web'),
    );
  }

  Widget _buildSettings() {
    return ListView(
      children: [
        ListTile(title: Text('Cài đặt dự án - xem chi tiết trên Web')),
      ],
    );
  }

  String _statusLabel(String? s) => switch (s ?? '') {
    'TODO' => 'Cần làm',
    'IN_PROGRESS' => 'Đang làm',
    'PENDING_APPROVAL' => 'Chờ duyệt',
    'COMPLETED' => 'Hoàn thành',
    'CANCELLED' => 'Đã hủy',
    _ => s ?? '—',
  };
}
