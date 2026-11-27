import 'package:flutter/material.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:uuid/uuid.dart';

import '../api/client.dart';

class ChatTaskDialog extends StatefulWidget {
  const ChatTaskDialog({
    super.key,
    required this.api,
    required this.projectId,
    required this.source,
    required this.assignees,
  });

  final ApiClient api;
  final String projectId, source;
  final List<Map<String, Object?>> assignees;

  @override
  State<ChatTaskDialog> createState() => _ChatTaskDialogState();
}

class _ChatTaskDialogState extends State<ChatTaskDialog> {
  late final TextEditingController _title;
  DateTime? _due;
  String? _assignee, _idempotency, _signature, _error;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _title = TextEditingController(
      text: String.fromCharCodes(
        widget.source.split('\n').first.runes.take(150),
      ),
    );
  }

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final title = _title.text.trim();
    if (title.isEmpty || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final date = _due?.toIso8601String().substring(0, 10);
      var timezone = 'UTC';
      if (date != null) {
        try {
          timezone = (await FlutterTimezone.getLocalTimezone()).identifier;
        } on Exception {
          // Date-only work can use the same UTC fallback as Calendar.
        }
      }
      final body = {
        'project_id': widget.projectId,
        'title': title,
        'description': widget.source,
        if (date != null) ...{'due_date': date, 'due_timezone': timezone},
        if (_assignee != null) 'primary_assignee_id': _assignee,
      };
      if (_signature != body.toString()) {
        _signature = body.toString();
        _idempotency = const Uuid().v4();
      }
      final result = PageParser.parseObject(
        await widget.api.mutate(
          '/tasks',
          method: 'POST',
          body: body,
          idempotencyKey: _idempotency,
        ),
      );
      if (mounted) Navigator.pop(context, result['id'] as String);
    } on Exception {
      if (mounted) {
        setState(
          () => _error =
              'Chưa tạo được công việc. Kiểm tra quyền phân công và thử lại.',
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Tạo công việc từ tin nhắn'),
    scrollable: true,
    content: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        TextField(
          controller: _title,
          enabled: !_saving,
          maxLength: 200,
          decoration: const InputDecoration(labelText: 'Tên công việc'),
        ),
        if (widget.assignees.isNotEmpty)
          DropdownButtonFormField<String>(
            initialValue: _assignee,
            isExpanded: true,
            decoration: const InputDecoration(
              labelText: 'Người phụ trách chính',
            ),
            items: [
              const DropdownMenuItem(
                value: null,
                child: Text('Chưa phân công'),
              ),
              for (final user in widget.assignees)
                DropdownMenuItem(
                  value: user['user_id'] as String,
                  child: Text(
                    (user['display_name'] ?? user['username']).toString(),
                  ),
                ),
            ],
            onChanged: _saving
                ? null
                : (value) => setState(() => _assignee = value),
          ),
        TextButton.icon(
          icon: const Icon(Icons.calendar_today_outlined),
          label: Text(
            _due == null
                ? 'Chọn hạn hoàn thành'
                : _due!.toIso8601String().substring(0, 10),
          ),
          onPressed: _saving
              ? null
              : () async {
                  final now = DateTime.now();
                  final date = await showDatePicker(
                    context: context,
                    firstDate: now,
                    lastDate: DateTime(now.year + 10, 12, 31),
                    initialDate: _due ?? now,
                  );
                  if (mounted && date != null) setState(() => _due = date);
                },
        ),
        if (_error != null)
          Text(
            _error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
      ],
    ),
    actions: [
      TextButton(
        onPressed: _saving ? null : () => Navigator.pop(context),
        child: const Text('Hủy'),
      ),
      FilledButton(
        onPressed: _saving ? null : _save,
        child: Text(_saving ? 'Đang tạo…' : 'Tạo công việc'),
      ),
    ],
  );
}
