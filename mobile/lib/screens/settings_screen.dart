// Man hinh Cai dat: Ho so, Giao dien, Ngon ngue, Thong bao, Thiet bi, Ba mat.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../push/fcm_stub.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  String _tab = 'profile';
  Map<String, bool> _prefs = {
    'TASK_ASSIGNED': true,
    'DEADLINE_WARNING': true,
    'WORKFLOW_STATUS': true,
    'COMMENT_NEW': true,
    'PROJECT_UPDATE': true,
  };
  bool _busy = true;
  String? _error;
  List<Map<String, Object?>> _devices = [];
  PushRegistrar? _push;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final api = context.read<SessionManager>().api;
    try {
      // Load preferences
      try {
        final data = await api.get(ApiPaths.notifications + '/preferences');
        final page = PageParser.parsePage(data);
        final map = <String, bool>{};
        for (var p in page.items) {
          final type = p['type']?.toString() ?? '';
          final muted = p['muted'] == true;
          map[type] = !muted;
        }
        if (!mounted) return;
        setState(() { _prefs = map..addAll(_prefs); });
      } catch (_) {}

      // Load devices
      try {
        final data = await api.get(ApiPaths.devices);
        final page = PageParser.parsePage(data);
        if (!mounted) return;
        setState(() { _devices = page.items; });
      } catch (_) {}

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

  Future<void> _togglePref(String type) async {
    final next = !(_prefs[type] ?? true);
    setState(() { _prefs[type] = next; });
    final api = context.read<SessionManager>().api;
    try {
      await api.mutate(ApiPaths.notifications + '/preferences', method: 'PUT',
          idempotencyKey: api.newIdempotencyKey(),
          body: {'type': type, 'muted': !next});
    } on DioException catch (e) {
      setState(() { _prefs[type] = !next; });
      final err = e.error;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err is ApiException ? err.envelope.code : 'Lỗi')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = context.read<SessionManager>();
    return Scaffold(
      appBar: AppBar(title: const Text('Cài đặt')),
      body: _busy
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                // Tab selector
                Container(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest.withOpacity(0.3),
                  child: Wrap(
                    spacing: 4,
                    children: [
                      _tabBtn('profile', 'Hồ sơ'),
                      _tabBtn('appearance', 'Giao diện'),
                      _tabBtn('language', 'Ngôn ngữ'),
                      _tabBtn('notifications', 'Thông báo'),
                      _tabBtn('devices', 'Thiết bị'),
                      _tabBtn('security', 'Bảo mật'),
                      _tabBtn('backup', 'Sao lưu'),
                    ],
                  ),
                ),
                Expanded(
                  child: _buildTabContent(session),
                ),
              ],
            ),
    );
  }

  Widget _tabBtn(String id, String label) => FilterChip(
    label: Text(label),
    selected: _tab == id,
    onSelected: (_) => setState(() => _tab = id),
  );

  Widget _buildTabContent(SessionManager session) {
    switch (_tab) {
      case 'profile':
        return _profileTab(session);
      case 'appearance':
        return _appearanceTab();
      case 'language':
        return _languageTab();
      case 'notifications':
        return _notificationsTab();
      case 'devices':
        return _devicesTab();
      case 'security':
        return _securityTab();
      case 'backup':
        return _backupTab();
      default:
        return _profileTab(session);
    }
  }

  Widget _profileTab(SessionManager session) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Thông tin tài khoản', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          const SizedBox(height: 16),
          _infoRow('Email', session.userId ?? '—'),
          _infoRow('Vai trò', 'Thành viên'),
          const SizedBox(height: 24),
          const Text('Lưu ý: Backend hiện chưa tra session_id; vai trò hiển thị trung tính.', style: TextStyle(fontSize: 12, color: Colors.grey)),
        ],
      ),
    );
  }

  Widget _infoRow(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Row(
      children: [
        SizedBox(width: 120, child: Text(label, style: const TextStyle(color: Colors.grey))),
        Expanded(child: Text(value)),
      ],
    ),
  );

  Widget _appearanceTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Chế độ giao diện', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          const SizedBox(height: 16),
          ListTile(
            leading: const Icon(Icons.light_mode),
            title: const Text('Sáng'),
            trailing: _tab == 'appearance' ? const Icon(Icons.check) : null,
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.dark_mode),
            title: const Text('Tối'),
            trailing: null,
            onTap: () {},
          ),
        ],
      ),
    );
  }

  Widget _languageTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Ngôn ngữ hiển thị', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          const SizedBox(height: 16),
          ListTile(
            leading: const Text('🇻🇳'),
            title: const Text('Tiếng Việt'),
            subtitle: const Text('Mặc định hệ thống'),
            trailing: _tab == 'language' ? const Icon(Icons.check) : null,
            onTap: () {},
          ),
          ListTile(
            leading: const Text('🇬🇧'),
            title: const Text('English'),
            subtitle: const Text('Standard English interface'),
            trailing: null,
            onTap: () {},
          ),
        ],
      ),
    );
  }

  Widget _notificationsTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Tùy chọn nhận thông báo', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          const SizedBox(height: 16),
          ..._prefs.entries.map((e) => SwitchListTile(
            title: Text(_prefLabel(e.key)),
            value: e.value,
            onChanged: (v) => _togglePref(e.key),
          )),
        ],
      ),
    );
  }

  Widget _devicesTab() {
    final push = context.watch<PushRegistrar>();
    _push = push;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Thiết bị & Push', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          const SizedBox(height: 16),
          if (push.lastOutcome != null)
            Text('Trạng thái đăng ký: ${_outcomeLabel(push.lastOutcome!)}',
                style: TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 16),
          const Text('Danh sách thiết bị', style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          if (_devices.isEmpty)
            const Text('Chưa có thiết bị nào được liên kết.')
          else
            ..._devices.map((d) => ListTile(
              title: Text(d['device_label']?.toString() ?? d['platform']?.toString() ?? ''),
              subtitle: Text('Token: ${d['token_hint']?.toString() ?? '—'}'),
              trailing: IconButton(
                icon: const Icon(Icons.delete_outline, color: Colors.red),
                onPressed: () async {
                  final id = d['id']?.toString();
                  if (id == null) return;
                  final api = context.read<SessionManager>().api;
                  try {
                    await api.mutate(ApiPaths.device(id), method: 'DELETE',
                        idempotencyKey: api.newIdempotencyKey());
                    await _load();
                  } on DioException catch (e) {
                    final err = e.error;
                    if (!mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(err is ApiException ? err.envelope.code : 'Lỗi')),
                    );
                  }
                },
              ),
            )),
        ],
      ),
    );
  }

  Widget _securityTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Bảo mật & Mật khẩu', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          const SizedBox(height: 16),
          const Text('Đổi mật khẩu được thực hiện trên Web.', style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 16),
          ListTile(
            title: const Text('Đăng xuất'),
            trailing: const Icon(Icons.logout, color: Colors.red),
            onTap: () async {
              final api = context.read<SessionManager>().api;
              try {
                await api.mutate(ApiPaths.logout, method: 'POST');
                if (!mounted) return;
                GoRouter.of(context).go('/login');
              } on DioException catch (_) {
                if (!mounted) return;
                GoRouter.of(context).go('/login');
              }
            },
          ),
        ],
      ),
    );
  }

  Widget _backupTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Dữ liệu & Sao lưu', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          const SizedBox(height: 16),
          const Text('Sao lưu được quản trị trên Web (Admin).', style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 16),
          const Text('Chính sách: PostgreSQL 16 (Flyway V1..V17), MinIO.', style: TextStyle(fontSize: 12, color: Colors.grey)),
        ],
      ),
    );
  }

  String _prefLabel(String type) => switch (type) {
    'TASK_ASSIGNED' => 'Khi được giao công việc mới',
    'DEADLINE_WARNING' => 'Cảnh báo sắp đến hạn / quá hạn',
    'WORKFLOW_STATUS' => 'Công việc được duyệt / yêu cầu sửa',
    'COMMENT_NEW' => 'Bình luận mới trong công việc',
    'PROJECT_UPDATE' => 'Cập nhật tiến độ dự án',
    _ => type,
  };

  String _outcomeLabel(PushRegisterOutcome o) => switch (o) {
    PushRegisterOutcome.registered => 'Đã đăng ký push',
    PushRegisterOutcome.noToken => 'Chưa có token thực',
    PushRegisterOutcome.endpointAbsent => 'Backend chưa có endpoint',
    PushRegisterOutcome.failed => 'Lỗi đăng ký',
  };
}
