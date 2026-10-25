// File dinh kem: upload tu camera/thu vien, download QUA GATEWAY
// (khong cache file rieng tu offline theo DEC-12/A).
//
// Validate client truoc khi goi (FilePolicy + seed V4__ph3.sql):
// - Allowlist MIME (server la chuan, client pre-check de bao loi som):
//   pdf, png, jpeg, docx, xlsx, zip, csv.
// - Max 50MB (file.max_upload_mb). Qua muc -> bao loi, khong goi API.
// - Scan engine backend tat (file.scan_enabled=false) -> GHI RO file tai len
//   KHONG duoc bao ve malware (FilePolicy.scanStub = UNSUPPORTED).
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../widgets/private_gate.dart';

/// Allowlist MIME client mirror seed backend (V4__ph3.sql
/// file.allowed_content_types). Server van kiem tra lai — day chi la
/// pre-check de bao loi som, khong phai bao mat.
const _allowedMime = {
  'application/pdf',
  'image/png',
  'image/jpeg',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/zip',
  'text/csv',
};

/// Max upload client mirror seed backend (file.max_upload_mb = 50).
const _maxUploadBytes = 50 * 1024 * 1024;

String _mimeForName(String name) {
  final lower = name.toLowerCase();
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.gif')) return 'image/gif';
  if (lower.endsWith('.webp')) return 'image/webp';
  if (lower.endsWith('.pdf')) return 'application/pdf';
  if (lower.endsWith('.csv') || lower.endsWith('.txt')) return 'text/csv';
  if (lower.endsWith('.zip')) return 'application/zip';
  return 'application/octet-stream';
}

class FilesScreen extends StatefulWidget {
  final String taskId;
  const FilesScreen({super.key, required this.taskId});

  @override
  State<FilesScreen> createState() => _FilesScreenState();
}

class _FilesScreenState extends State<FilesScreen> {
  final _picker = ImagePicker();
  List<Map<String, Object?>> _items = [];
  bool _busy = true;
  String? _error;
  String? _notice;

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
      final data = await api.get(ApiPaths.attachments(widget.taskId));
      // Backend tra {data: {items: [...]}} (FileController.listByTask).
      _items = PageParser.parseList(data);
    } on DioException catch (e) {
      final err = e.error;
      _error = err is ApiException
          ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
          : e.message;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _pick(ImageSource source) async {
    final api = context.read<SessionManager>().api;
    final file = await _picker.pickImage(source: source);
    if (file == null) return;
    setState(() {
      _busy = true;
      _notice = null;
      _error = null;
    });
    try {
      final bytes = await file.readAsBytes();
      final mime = _mimeForName(file.name);
      // Validate client truoc khi goi API.
      if (!_allowedMime.contains(mime)) {
        setState(() => _error =
            'Định dạng chưa hỗ trợ ($mime). Hỗ trợ: pdf, png, jpeg, docx, xlsx, zip, csv.');
        return;
      }
      if (bytes.isEmpty || bytes.length > _maxUploadBytes) {
        setState(() => _error =
            'Kích thước file không hợp lệ (tối đa 50MB).');
        return;
      }
      // PH-3: init upload session -> complete staging -> commit (version monotonic).
      final init = await api.mutate(
        ApiPaths.fileUploadInit,
        method: 'POST',
        body: {
          'task_id': widget.taskId,
          'file_name': file.name,
          'mime_type': mime,
          'declared_size': bytes.length,
        },
        idempotencyKey: api.newIdempotencyKey(),
      );
      final initMap = PageParser.parseObject(init);
      final sid = (initMap['upload_session_id'])?.toString();
      if (sid == null) throw StateError('missing session');
      await api.mutate(
        ApiPaths.fileUploadComplete(sid),
        method: 'POST',
        body: {'actual_bytes': bytes.length},
        idempotencyKey: api.newIdempotencyKey(),
      );
      await api.mutate(
        ApiPaths.fileUploadCommit(sid),
        method: 'POST',
        body: {'change_note': 'mobile upload'},
        idempotencyKey: api.newIdempotencyKey(),
      );
      setState(() => _notice =
          'Đã tải lên ${file.name} (lưu ý: file chưa được quét malware).');
      await _load();
    } on DioException catch (e) {
      final err = e.error;
      setState(() {
        _error = err is ApiException ? err.envelope.code : e.message;
      });
    } on StateError catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _download(String attachmentId) async {
    final api = context.read<SessionManager>().api;
    try {
      // PH-3: mint single-use ticket (TTL 30s) roi stream qua gateway.
      final t = await api.get(ApiPaths.fileDownloadTicket(attachmentId));
      final tMap = PageParser.parseObject(t);
      final token = (tMap['ticket'])?.toString();
      if (token == null) throw StateError('missing ticket');
      final bytes = await api.downloadBytes(ApiPaths.fileStream(token));
      if (!mounted) return;
      setState(() => _notice = 'Đã tải ${bytes.length} byte qua gateway.');
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
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
      resourceLabel: 'Files',
      child: Scaffold(
        appBar: AppBar(title: const Text('Files')),
        body: Column(
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton(
                  onPressed: () => _pick(ImageSource.camera),
                  child: const Text('Camera'),
                ),
                ElevatedButton(
                  onPressed: () => _pick(ImageSource.gallery),
                  child: const Text('Thư viện'),
                ),
              ],
            ),
            if (_notice != null) Text(_notice!),
            if (_error != null)
              Text(_error!, style: const TextStyle(color: Colors.red)),
            Expanded(
              child: _busy && _items.isEmpty
                  ? const Center(child: CircularProgressIndicator())
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.builder(
                        itemCount: _items.length,
                        itemBuilder: (context, i) {
                          final a = _items[i];
                          return ListTile(
                            title: Text(a['file_name']?.toString() ?? ''),
                            subtitle: Text(a['status']?.toString() ?? ''),
                            trailing: IconButton(
                              icon: const Icon(Icons.download),
                              onPressed: () =>
                                  _download(a['id']?.toString() ?? ''),
                            ),
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
