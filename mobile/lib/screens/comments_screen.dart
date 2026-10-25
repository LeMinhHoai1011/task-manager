// Binh luan cua Task (CollabController: GET/POST /tasks/{id}/comments,
// PageResponse trong ApiResult.data). 404 trung tinh; 428 mo reauth
// (phong thu — comment backend hien khong doi reauth).
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/reauth.dart';
import '../auth/session.dart';
import '../widgets/private_gate.dart';

class CommentsScreen extends StatefulWidget {
  final String taskId;
  const CommentsScreen({super.key, required this.taskId});

  @override
  State<CommentsScreen> createState() => _CommentsScreenState();
}

class _CommentsScreenState extends State<CommentsScreen> {
  final _ctl = TextEditingController();
  List<Map<String, Object?>> _items = [];
  bool _busy = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _ctl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final data = await api.get(ApiPaths.taskComments(widget.taskId));
      _items = PageParser.parsePage(data).items;
    } on DioException catch (e) {
      final err = e.error;
      _error = err is ApiException
          ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
          : e.message;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _send() async {
    final text = _ctl.text.trim();
    // Client pre-check khop backend BODY_REQUIRED (CommentService).
    if (text.isEmpty) {
      setState(() => _error = 'Nội dung bình luận không được để trống.');
      return;
    }
    final session = context.read<SessionManager>();
    final reauth = context.read<ReauthFlow>();
    try {
      await session.api.mutate(
        ApiPaths.taskComments(widget.taskId),
        method: 'POST',
        body: {'body': text},
        idempotencyKey: session.api.newIdempotencyKey(),
      );
      _ctl.clear();
      await _load();
    } on DioException catch (e) {
      final err = e.error;
      if (err is ApiException && err.isReauthRequired) {
        session.requireReauth(err.envelope.reauthActionCode ?? 'comment.create');
        reauth.beginFromError(err, targetId: widget.taskId);
        if (mounted) context.go('/reauth');
        return;
      }
      setState(() {
        _error = err is ApiException
            ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
            : e.message;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return PrivateGate(
      resourceLabel: 'Bình luận',
      child: Scaffold(
        appBar: AppBar(title: const Text('Bình luận')),
        body: Column(
          children: [
            Expanded(
              child: _busy
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null && _items.isEmpty
                      ? Center(child: Text(_error!))
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: ListView.builder(
                            itemCount: _items.length,
                            itemBuilder: (context, i) => ListTile(
                              title: Text(
                                _items[i]['body']?.toString() ?? '',
                              ),
                            ),
                          ),
                        ),
            ),
            if (_error != null && _items.isNotEmpty)
              Text(_error!, style: const TextStyle(color: Colors.red)),
            Row(
              children: [
                Expanded(child: TextField(controller: _ctl)),
                IconButton(
                  icon: const Icon(Icons.send),
                  onPressed: _send,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
