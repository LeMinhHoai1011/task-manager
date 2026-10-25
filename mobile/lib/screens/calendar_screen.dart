// Lich: su kien theo khoang ngay + timezone IANA cua thiet bi.
//
// Backend (CalendarController + CalendarService):
// - GET /calendar?view=&date=&timezone= tra ApiResult.data la LIST tran (khong
//   phai PageResponse), moi phan tu {task_id, project_id, due_at} — KHONG co
//   title (khong render tieu de khong ton tai).
// - `timezone` bat buoc la IANA hop le (ZoneId.of): sai -> 400
//   TIMEZONE_UNKNOWN. `DateTime.now().timeZoneName` tra viet tat ("+07",
//   "ICT") nen KHONG dung duoc — lay IANA qua flutter_timezone, fallback
//   "UTC" + ghi ro khi khong lay duoc.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../widgets/private_gate.dart';

class CalendarScreen extends StatefulWidget {
  const CalendarScreen({super.key});

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen> {
  List<Map<String, Object?>> _items = [];
  bool _busy = true;
  String? _error;
  String _view = 'week';
  String _timezone = 'UTC';
  bool _tzFallback = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    await _resolveTimezone();
    await _load();
  }

  /// Lay IANA timezone that cua thiet bi; khong duoc thi fallback UTC + co.
  Future<void> _resolveTimezone() async {
    try {
      // flutter_timezone 5.x tra TimezoneInfo (khong phai String).
      final info = await FlutterTimezone.getLocalTimezone();
      final name = info.identifier;
      if (name.isNotEmpty) {
        if (mounted) {
          setState(() {
            _timezone = name;
            _tzFallback = false;
          });
        } else {
          _timezone = name;
        }
        return;
      }
    } on Exception {
      // Roi xuong fallback.
    }
    if (mounted) {
      setState(() {
        _timezone = 'UTC';
        _tzFallback = true;
      });
    } else {
      _timezone = 'UTC';
      _tzFallback = true;
    }
  }

  Future<void> _load() async {
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final now = DateTime.now().toUtc();
      final data = await api.get(ApiPaths.calendar, query: {
        'view': _view,
        'date':
            '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}',
        'timezone': _timezone,
      });
      _items = PageParser.parseList(data);
    } on DioException catch (e) {
      final err = e.error;
      // Timezone khong hop le (400 TIMEZONE_UNKNOWN): fallback UTC mot lan.
      if (err is ApiException &&
          err.envelope.code == 'TIMEZONE_UNKNOWN' &&
          !_tzFallback) {
        _timezone = 'UTC';
        _tzFallback = true;
        await _load();
        return;
      }
      _error = err is ApiException ? err.envelope.code : e.message;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _setView(String v) async {
    setState(() => _view = v);
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return PrivateGate(
      resourceLabel: 'Lịch',
      child: Scaffold(
        appBar: AppBar(title: const Text('Lịch')),
        body: Column(
          children: [
            if (_tzFallback)
              const Padding(
                padding: EdgeInsets.all(8),
                child: Text(
                  'Không lấy được múi giờ thiết bị — đang dùng UTC.',
                ),
              ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                for (final v in const ['day', 'week', 'month', 'unscheduled'])
                  TextButton(
                    onPressed: () => _setView(v),
                    child: Text(v, style: TextStyle(
                      fontWeight: _view == v ? FontWeight.bold : FontWeight.normal,
                    )),
                  ),
              ],
            ),
            Expanded(
              child: _busy
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null
                      ? Center(child: Text(_error!))
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: ListView.builder(
                            itemCount: _items.length,
                            itemBuilder: (context, i) {
                              final e = _items[i];
                              return ListTile(
                                title: Text(
                                  e['due_at']?.toString() ?? '(không hạn)',
                                ),
                                subtitle: Text(
                                  'task: ${e['task_id']} • project: ${e['project_id']}',
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
