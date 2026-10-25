// AI toi thieu qua API (AiController): request draft TASK_DRAFT + xem status
// + doc suggestion + confirm (accept) / reject. KHONG co inference local,
// KHONG goi Ollama truc tiep — client chi goi backend, backend tu chay
// pipeline (Ollama ngoai tx) + reauthorize hai lan phia server.
//
// Body POST /ai/request dung camelCase theo record AiRequest (khong co
// @JsonProperty): {feature, projectId, inputHint, requestedFields,
// idempotencyKey}. idempotencyKey nam TRONG body (khong phai header).
// inputHint gioi han 2000 ky tu (PromptSanitizer.MAX_INPUT_HINT).
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../widgets/private_gate.dart';

class AiScreen extends StatefulWidget {
  final String projectId;
  const AiScreen({super.key, required this.projectId});

  @override
  State<AiScreen> createState() => _AiScreenState();
}

class _AiScreenState extends State<AiScreen> {
  final _hintCtl = TextEditingController();
  String? _jobId;
  Map<String, Object?>? _job;
  Map<String, Object?>? _suggestion;
  bool _busy = false;
  String? _error;
  String? _notice;

  @override
  void dispose() {
    _hintCtl.dispose();
    super.dispose();
  }

  Future<void> _request() async {
    final hint = _hintCtl.text.trim();
    if (hint.isEmpty) {
      setState(() => _error = 'Gợi ý đầu vào không được để trống.');
      return;
    }
    // Client pre-check khop backend INPUT_HINT_TOO_LONG (<= 2000).
    if (hint.length > 2000) {
      setState(() => _error = 'Gợi ý tối đa 2000 ký tự.');
      return;
    }
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
      _notice = null;
    });
    try {
      final res = await api.mutate(
        ApiPaths.aiRequest,
        method: 'POST',
        body: {
          'feature': 'TASK_DRAFT',
          'projectId': widget.projectId,
          'inputHint': hint,
          'requestedFields': ['title', 'description', 'priority'],
          'idempotencyKey': api.newIdempotencyKey(),
        },
      );
      final data = PageParser.parseObject(res);
      setState(() {
        _jobId = data['id']?.toString();
        _job = data;
        _notice = 'Đã gửi yêu cầu AI ($_jobId). Nhấn trạng thái để xem.';
      });
    } on DioException catch (e) {
      final err = e.error;
      setState(() {
        _error = err is ApiException
            ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
            : e.message;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _status() async {
    final id = _jobId;
    if (id == null) return;
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final res = await api.get(ApiPaths.aiJob(id));
      final data = PageParser.parseObject(res);
      setState(() => _job = data);
      // Tu dong tai suggestion khi job COMPLETED.
      if (data['status']?.toString() == 'COMPLETED') {
        await _loadSuggestion();
      }
    } on DioException catch (e) {
      final err = e.error;
      setState(() {
        // AI-02: job cua nguoi khac (ke ca SA) doc nhu 404 — hien trung tinh.
        _error = err is ApiException
            ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
            : e.message;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _loadSuggestion() async {
    final id = _jobId;
    if (id == null) return;
    final api = context.read<SessionManager>().api;
    try {
      final res = await api.get(ApiPaths.aiSuggestion(id));
      if (!mounted) return;
      setState(() => _suggestion = PageParser.parseObject(res));
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() {
        _error = err is ApiException ? err.envelope.code : e.message;
      });
    }
  }

  /// Confirm (accept): backend materialize draft qua TaskService.create voi
  /// actor=user (AI-03) — AI khong bao gio tu ghi task. Chi COMPLETED + DRAFT
  /// moi accept duoc (server kiem tra, 409 neu sai trang thai).
  Future<void> _accept() async {
    final id = _jobId;
    if (id == null) return;
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final res = await api.mutate(ApiPaths.aiAccept(id), method: 'POST');
      final data = PageParser.parseObject(res);
      if (!mounted) return;
      setState(() {
        _notice = 'Đã tạo task ${data['id']} từ draft AI.';
        _suggestion = null;
      });
      await _status();
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() {
        _error = err is ApiException ? err.envelope.code : e.message;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reject() async {
    final id = _jobId;
    if (id == null) return;
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await api.mutate(ApiPaths.aiReject(id), method: 'POST');
      if (!mounted) return;
      setState(() {
        _notice = 'Đã từ chối draft AI.';
        _suggestion = null;
      });
      await _status();
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() {
        _error = err is ApiException ? err.envelope.code : e.message;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PrivateGate(
      resourceLabel: 'AI Draft',
      child: Scaffold(
        appBar: AppBar(title: const Text('AI Draft (qua API)')),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Project: ${widget.projectId}'),
              TextField(
                controller: _hintCtl,
                maxLines: 3,
                maxLength: 2000,
                decoration: const InputDecoration(
                  labelText: 'Mô tả task muốn AI soạn (tối đa 2000 ký tự)',
                ),
              ),
              const SizedBox(height: 8),
              ElevatedButton(
                onPressed: _busy ? null : _request,
                child: const Text('Gửi yêu cầu draft'),
              ),
              if (_jobId != null) ...[
                const SizedBox(height: 8),
                Text('Job: $_jobId — status: ${_job?['status'] ?? '?'}'),
                Row(
                  children: [
                    ElevatedButton(
                      onPressed: _busy ? null : _status,
                      child: const Text('Trạng thái'),
                    ),
                    const SizedBox(width: 8),
                    ElevatedButton(
                      onPressed: _busy ? null : _loadSuggestion,
                      child: const Text('Xem draft'),
                    ),
                  ],
                ),
              ],
              if (_suggestion != null) ...[
                const SizedBox(height: 8),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(
                      _suggestion!['payload']?.toString() ?? '(trống/redacted)',
                    ),
                  ),
                ),
                Row(
                  children: [
                    ElevatedButton(
                      onPressed: _busy ? null : _accept,
                      child: const Text('Confirm (tạo task)'),
                    ),
                    const SizedBox(width: 8),
                    OutlinedButton(
                      onPressed: _busy ? null : _reject,
                      child: const Text('Reject'),
                    ),
                  ],
                ),
              ],
              if (_busy)
                const Padding(
                  padding: EdgeInsets.all(12),
                  child: Center(child: CircularProgressIndicator()),
                ),
              if (_error != null)
                Text(_error!, style: const TextStyle(color: Colors.red)),
              if (_notice != null) Text(_notice!),
              const SizedBox(height: 12),
              const Text(
                'Lưu ý: AI chạy trên backend (Ollama), không inference trên '
                'thiết bị. Draft mất quyền giữa chừng sẽ bị redact (AUD-13).',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
