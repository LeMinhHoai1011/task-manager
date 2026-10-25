// Bao cao rut gon (chi so lieu actor co quyen; khong xep hang ca nhan).
//
// Export (ReportController): POST /reports/export-jobs {report_code,
// scope_type, format, ...} -> {export_job_id, status}; download
// GET /export-jobs/{id}/download tra BYTES TRAN (khong phai JSON) — tai that
// qua downloadBytes roi luu file vao thu muc tam cua thiet bi (khong co
// path_provider/share trong V1 nen chi luu + hien duong dan, khong mo share
// sheet). TTL 24h, requester-only (server kiem tra lai khi download).
library;

import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../widgets/private_gate.dart';

String _extFor(String format) {
  switch (format.toUpperCase()) {
    case 'PDF':
      return 'pdf';
    case 'XLSX':
      return 'xlsx';
    default:
      return 'csv';
  }
}

class ReportsScreen extends StatefulWidget {
  const ReportsScreen({super.key});

  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen> {
  Map<String, Object?>? _summary;
  String? _exportJobId;
  String? _exportFormat;
  String? _notice;
  bool _busy = true;
  String? _error;

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
      final data = await api.get(ApiPaths.reportsSummary);
      _summary = PageParser.parseObject(data);
    } on DioException catch (e) {
      final err = e.error;
      _error = err is ApiException
          ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
          : e.message;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _export(String format) async {
    final api = context.read<SessionManager>().api;
    setState(() => _notice = null);
    try {
      final res = await api.mutate(
        ApiPaths.reportExportJobs,
        method: 'POST',
        body: {
          'report_code': 'PERSONAL_SUMMARY',
          'scope_type': 'PERSONAL',
          'format': format,
        },
        idempotencyKey: api.newIdempotencyKey(),
      );
      final m = PageParser.parseObject(res);
      setState(() {
        _exportJobId = m['export_job_id']?.toString();
        _exportFormat = format;
        _notice = _exportJobId == null
            ? 'Không tạo được export job.'
            : 'Đã tạo export job $_exportJobId (TTL 24h, chỉ bạn tải được).';
      });
    } on DioException catch (e) {
      final err = e.error;
      setState(() {
        _notice = err is ApiException ? err.envelope.code : e.message;
      });
    }
  }

  /// Tai BYTES THAT qua gateway + luu file vao thu muc tam thiet bi.
  Future<void> _downloadExport() async {
    final api = context.read<SessionManager>().api;
    final id = _exportJobId;
    if (id == null) return;
    try {
      final bytes = await api.downloadBytes(ApiPaths.exportDownload(id));
      final ext = _extFor(_exportFormat ?? 'CSV');
      final dir = Directory.systemTemp;
      final file = File('${dir.path}/report-$id.$ext');
      await file.writeAsBytes(bytes, flush: true);
      if (!mounted) return;
      setState(
        () => _notice = 'Đã lưu export (${bytes.length} byte) tại ${file.path}.',
      );
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() {
        // 409 EXPORT_NOT_READY: job chua xong — bao ro, khong crash.
        _notice = err is ApiException ? err.envelope.code : e.message;
      });
    } on FileSystemException catch (e) {
      if (!mounted) return;
      setState(() => _notice = 'Không lưu được file: ${e.message}');
    }
  }

  @override
  Widget build(BuildContext context) {
    return PrivateGate(
      resourceLabel: 'Báo cáo',
      child: Scaffold(
        appBar: AppBar(title: const Text('Báo cáo')),
        body: _busy
            ? const Center(child: CircularProgressIndicator())
            : _error != null
                ? Center(child: Text(_error!))
                : ListView(
                    children: [
                      for (final e in (_summary ?? const {}).entries)
                        ListTile(
                          title: Text(e.key),
                          subtitle: Text('${e.value}'),
                        ),
                      if (_notice != null)
                        ListTile(title: Text(_notice!)),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          for (final f in const ['PDF', 'XLSX', 'CSV'])
                            ElevatedButton(
                              onPressed: () => _export(f),
                              child: Text(f),
                            ),
                        ],
                      ),
                      if (_exportJobId != null)
                        ElevatedButton(
                          onPressed: _downloadExport,
                          child: const Text('Tải export'),
                        ),
                    ],
                  ),
      ),
    );
  }
}
