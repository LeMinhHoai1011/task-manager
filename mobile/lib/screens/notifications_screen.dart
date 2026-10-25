// Trung tam thong bao + deep link co server revalidate:
// mo deep link -> goi API doc resource -> mat quyen thi 404 trung tinh.
//
// Backend (NotificationController.list): ApiResult.data = PageResponse voi
// items {id, kind, read} — KHONG co target_type/target_id, KHONG co title/body
// (TB da redact server-side; client chi render "co cap nhat moi", khong bao
// gio render noi dung nhay cam tu push). Deep-link chi kich hoat khi payload
// tuong lai co target hop le; hien tai degrade tuong minh (khong tu doan id).
// Co read-single (POST /notifications/{id}/read) + read-all.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';

/// Map target_type -> route app.dart (dung khi payload co target).
/// Hien tai backend khong gui target -> ham nay chua kich hoat (degrade).
String? deepLinkRoute(String targetType, String targetId) {
  switch (targetType) {
    case 'TASK':
      return '/tasks/$targetId';
    case 'PROJECT':
      return '/projects/$targetId/tasks';
    case 'WORKSPACE':
      return '/workspaces/$targetId/projects';
    default:
      return null;
  }
}

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  List<Map<String, Object?>> _items = [];
  String? _nextCursor;
  bool _busy = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  /// Cursor pagination theo contract: `cursor` + `limit`, `next_cursor` o
  /// PageResponse. `more=true` noi tiep trang hien tai.
  Future<void> _load({bool more = false}) async {
    final api = context.read<SessionManager>().api;
    if (more && _nextCursor == null) return;
    setState(() {
      if (!more) _busy = true;
      _error = null;
    });
    try {
      final data = await api.get(ApiPaths.notifications, query: {
        'limit': 20,
        if (more && _nextCursor != null) 'cursor': _nextCursor,
      });
      final page = PageParser.parsePage(data);
      if (!mounted) return;
      setState(() {
        _items = more ? [..._items, ...page.items] : page.items;
        _nextCursor = page.nextCursor;
      });
    } on DioException catch (e) {
      final err = e.error;
      _error = err is ApiException ? err.envelope.code : e.message;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Mark-all-read cap tai khoan (đồng bộ read-state).
  Future<void> _readAll() async {
    final api = context.read<SessionManager>().api;
    try {
      await api.mutate(
        ApiPaths.notificationReadAll,
        method: 'POST',
        idempotencyKey: api.newIdempotencyKey(),
      );
      await _load();
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(err is ApiException ? err.envelope.code : 'Lỗi.'),
        ),
      );
    }
  }

  /// Read-single: danh dau mot TB da doc (POST /notifications/{id}/read).
  Future<void> _readOne(String id) async {
    final api = context.read<SessionManager>().api;
    try {
      await api.mutate(
        ApiPaths.notificationRead(id),
        method: 'POST',
        idempotencyKey: api.newIdempotencyKey(),
      );
      await _load();
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            err is ApiException && err.isNotFound
                ? err.envelope.neutralMessage
                : 'Không đánh dấu được.',
          ),
        ),
      );
    }
  }

  /// Deep link: revalidate tren server truoc khi dieu huong.
  /// Degrade tuong minh: payload hien tai khong co target -> bao ro, khong
  /// tu doan dieu huong (tranh lo thong tin ton tai).
  Future<void> _openDeepLink(Map<String, Object?> n) async {
    final targetType = n['target_type']?.toString();
    final targetId = n['target_id']?.toString();
    if (targetType == null || targetId == null) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Thông báo này không kèm liên kết chi tiết.'),
        ),
      );
      return;
    }
    final api = context.read<SessionManager>().api;
    try {
      if (targetType == 'TASK') {
        await api.get(ApiPaths.task(targetId));
      } else if (targetType == 'PROJECT') {
        await api.get(ApiPaths.project(targetId));
      } else if (targetType == 'WORKSPACE') {
        await api.get(ApiPaths.workspace(targetId));
      } else {
        return;
      }
      if (!mounted) return;
      final route = deepLinkRoute(targetType, targetId);
      if (route != null) context.go(route);
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            err is ApiException && err.isNotFound
                ? err.envelope.neutralMessage
                : 'Không mở được liên kết.',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Thông báo'),
        actions: [
          TextButton(
            onPressed: _readAll,
            child: const Text('Đọc hết', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
      body: _busy
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.builder(
                    itemCount: _items.length + (_nextCursor != null ? 1 : 0),
                    itemBuilder: (context, i) {
                      if (i >= _items.length) {
                        return Center(
                          child: TextButton(
                            onPressed: () => _load(more: true),
                            child: const Text('Tải thêm'),
                          ),
                        );
                      }
                      final n = _items[i];
                      // Redacted check: chi render kind (khong co title/body
                      // trong payload). Neu backend doi gui them truong nhay
                      // cam thi van khong render o day (DEC-13/A).
                      final read = n['read'] == true;
                      return ListTile(
                        // Tieu de co dinh, khong lay tu payload.
                        title: const Text('Có cập nhật mới'),
                        subtitle: Text(n['kind']?.toString() ?? ''),
                        trailing: read
                            ? null
                            : IconButton(
                                icon: const Icon(Icons.mark_email_read),
                                tooltip: 'Đánh dấu đã đọc',
                                onPressed: () =>
                                    _readOne(n['id']?.toString() ?? ''),
                              ),
                        onTap: () => _openDeepLink(n),
                      );
                    },
                  ),
                ),
    );
  }
}
