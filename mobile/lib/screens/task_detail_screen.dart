// Chi tiet Task + workflow actions:
// start / submit / approve / reject / cancel / reopen.
// Moi POST kem Idempotency-Key + If-Match; 428 mo reauth; 404 trung tinh.
//
// Idempotency-Key on dinh theo (taskId, verb) trong phien man hinh (group-1):
// retry sau reauth dung dung key cu, khong sinh moi.
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

class TaskDetailScreen extends StatefulWidget {
  final String taskId;
  const TaskDetailScreen({super.key, required this.taskId});

  @override
  State<TaskDetailScreen> createState() => _TaskDetailScreenState();
}

class _TaskDetailScreenState extends State<TaskDetailScreen> {
  Map<String, Object?>? _task;
  int? _version;
  bool _busy = true;
  String? _error;
  String? _notice;
  // Idempotency-Key ổn định theo (taskId, verb) trong phiên màn hình này,
  // để retry sau reauth dùng đúng key cũ thay vì sinh mới mỗi lần.
  final Map<String, String> _idemKeys = {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final data = await api.get(ApiPaths.task(widget.taskId));
      _task = PageParser.parseObject(data);
      _version = (_task!['version'] as num?)?.toInt();
    } on DioException catch (e) {
      final err = e.error;
      _error = err is ApiException
          ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
          : e.message;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _action(String verb, {Object? body}) async {
    final session = context.read<SessionManager>();
    final reauth = context.read<ReauthFlow>();
    setState(() {
      _busy = true;
      _notice = null;
      _error = null;
    });
    try {
      final key = _idemKeys.putIfAbsent(
        '${widget.taskId}:$verb',
        () => session.api.newIdempotencyKey(),
      );
      await session.api.mutate(
        ApiPaths.taskWorkflow(widget.taskId, verb),
        method: 'POST',
        body: body ?? const {},
        ifMatchVersion: _version,
        idempotencyKey: key,
      );
      setState(() => _notice = 'Đã thực hiện $verb.');
      await _load();
    } on DioException catch (e) {
      final err = e.error;
      if (err is ApiException && err.isReauthRequired) {
        // Workflow backend hien khong doi reauth (nhanh phong thu):
        // mo reauth, retry sau giu nguyen Idempotency-Key cu.
        session.requireReauth(err.envelope.reauthActionCode ?? verb);
        reauth.beginFromError(err, targetId: widget.taskId);
        if (mounted) context.go('/reauth');
        setState(() => _error = 'Cần reauth cho $verb.');
      } else if (err is ApiException && err.isNotFound) {
        setState(() => _error = err.envelope.neutralMessage);
      } else if (err is ApiException) {
        setState(
          () => _error = '${err.envelope.code} (${err.envelope.correlationId})',
        );
      } else {
        setState(() => _error = e.message);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PrivateGate(
      resourceLabel: 'Task',
      child: Scaffold(
        appBar: AppBar(title: const Text('Task')),
        body: _busy && _task == null
            ? const Center(child: CircularProgressIndicator())
            : _error != null && _task == null
                ? Center(child: Text(_error!))
                : SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _task?['title']?.toString() ?? '',
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                        Text('state: ${_task?['state']}  version: $_version'),
                        if (_notice != null) Text(_notice!),
                        if (_error != null)
                          Text(
                            _error!,
                            style: const TextStyle(color: Colors.red),
                          ),
                        const SizedBox(height: 12),
                        Wrap(
                          spacing: 8,
                          children: [
                            for (final verb in const [
                              'start',
                              'submit',
                              'approve',
                              'reject',
                              'cancel',
                              'reopen',
                            ])
                              ElevatedButton(
                                onPressed: _busy ? null : () => _action(verb),
                                child: Text(verb),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
      ),
    );
  }
}
