import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:uuid/uuid.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import 'chat_task_dialog.dart';

class ProjectChatScreen extends StatefulWidget {
  const ProjectChatScreen({
    super.key,
    required this.projectId,
    this.projectName,
  });

  final String projectId;
  final String? projectName;

  @override
  State<ProjectChatScreen> createState() => _ProjectChatScreenState();
}

class _ProjectChatScreenState extends State<ProjectChatScreen> {
  final _draft = TextEditingController();
  final _scroll = ScrollController();
  final _rowKeys = <String, GlobalKey>{};
  final _messages = <String, Map<String, Object?>>{};
  Timer? _timer;
  bool _loading = false, _sending = false, _reading = false;
  bool _loaded = false, _canWrite = false;
  int? _before, _revision, _firstUnread;
  int _lastRead = 0;
  int _accessEpoch = 0;
  bool _canModerate = false, _canCreateTask = false, _canAssign = false;
  String? _retryReply;
  Map<String, Object?>? _reply, _pinned;
  String? _error, _sendError, _clientId, _retryBody, _retryTask, _projectName;
  Map<String, Object?>? _linkedTask;

  String get _base => '/projects/${widget.projectId}';
  ApiClient get _api => context.read<SessionManager>().api;
  bool get _visible =>
      (ModalRoute.of(context)?.isCurrent ?? false) &&
      WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;

  @override
  void initState() {
    super.initState();
    _projectName = widget.projectName;
    _scroll.addListener(() => unawaited(_markRead()));
    unawaited(_load());
    _timer = Timer.periodic(const Duration(seconds: 5), (_) {
      if (_visible) unawaited(_load());
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _scroll.dispose();
    _draft.dispose();
    super.dispose();
  }

  static int _seq(Map<String, Object?> row) => (row['seq'] as num).toInt();

  bool _accessDenied(Exception error) {
    final status = switch (error) {
      ApiException() => error.envelope.httpStatus,
      DioException() => error.response?.statusCode,
      _ => null,
    };
    if (![401, 403, 404].contains(status)) return false;
    _accessEpoch++;
    setState(() {
      _messages.clear();
      _rowKeys.clear();
      _before = null;
      _revision = null;
      _firstUnread = null;
      _lastRead = 0;
      _canWrite = false;
      _error = 'Bạn không có quyền xem cuộc trò chuyện này.';
      _sendError = null;
      _linkedTask = null;
      _reply = null;
      _pinned = null;
      _canModerate = _canCreateTask = _canAssign = false;
      _clientId = null;
      _retryBody = null;
      _retryTask = null;
      _draft.clear();
    });
    return true;
  }

  Future<void> _load({bool older = false}) async {
    if (_loading || (older && _before == null)) return;
    setState(() => _loading = true);
    final api = _api;
    final epoch = _accessEpoch;
    final revisionBefore = _revision;
    final initial = revisionBefore == null;
    try {
      int? until;
      do {
        final delta = !older && _revision != null;
        final page = PageParser.parseObject(
          await api.get(
            '$_base/messages',
            query: {
              if (older) 'before': _before,
              if (delta) 'after': _revision,
              'until': ?until,
              'limit': 50,
            },
          ),
        );
        if (!mounted || epoch != _accessEpoch) return;
        final rows = (page['items'] as List).cast<Map<String, Object?>>();
        final access = page['access'] as Map;
        final unread = page['unread'] as Map;
        final next = (page['next_after'] as num?)?.toInt();
        final more = delta && page['has_more'] == true;
        if (delta &&
            (next == null ||
                next < _revision! ||
                (more && next == _revision))) {
          throw const FormatException('Invalid chat cursor');
        }
        setState(() {
          _error = null;
          _loaded = true;
          _canWrite = access['write'] == true;
          _canModerate = access['moderate'] == true && _canWrite;
          for (final row in rows) {
            _messages[row['id'] as String] = row;
            if (row['deleted'] == true) {
              if (_reply?['id'] == row['id']) _reply = null;
              if (_pinned?['id'] == row['id']) _pinned = null;
            }
          }
          if (older || initial) {
            _before = (page['next_before'] as num?)?.toInt();
          }
          if (!older) {
            _revision = delta ? next : (page['revision'] as num).toInt();
            final lastRead = (unread['last_read_seq'] as num).toInt();
            if (lastRead > _lastRead) _lastRead = lastRead;
            if (initial && (unread['count'] as num) > 0) {
              final me = context.read<SessionManager>().userId;
              for (final row in rows) {
                if (_seq(row) <= _lastRead ||
                    row['sender_id'] == me ||
                    row['deleted'] == true) {
                  continue;
                }
                if (_firstUnread == null || _seq(row) < _firstUnread!) {
                  _firstUnread = _seq(row);
                }
              }
            }
          }
        });
        if (!more) break;
        until = (page['revision'] as num).toInt();
      } while (true);
      if (!older && revisionBefore != _revision) await _loadPin(epoch);
      if (initial && mounted && epoch == _accessEpoch) {
        await _loadTaskPermissions(epoch);
      }
      if (_projectName == null && initial) {
        try {
          final project = PageParser.parseObject(
            await api.get(ApiPaths.project(widget.projectId)),
          );
          if (mounted && epoch == _accessEpoch) {
            setState(() => _projectName = project['name'] as String?);
          }
        } on Exception {
          // Chat access and project metadata have separate permissions.
        }
      }
      if (!older) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) unawaited(_markRead());
        });
      }
    } on Exception catch (error) {
      if (mounted && epoch == _accessEpoch && !_accessDenied(error)) {
        setState(() => _error = 'Chưa tải được tin nhắn. Thử lại để đồng bộ.');
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _markRead() async {
    if (!_visible || _reading || !_scroll.hasClients || _scroll.offset > 48) {
      return;
    }
    var latest = 0;
    for (final row in _messages.values) {
      final seq = _seq(row);
      if (seq > latest && seq <= (_revision ?? 0)) latest = seq;
    }
    if (latest <= _lastRead) return;
    _reading = true;
    final epoch = _accessEpoch;
    try {
      final result = PageParser.parseObject(
        await _api.mutate(
          '$_base/chat/read',
          method: 'PUT',
          body: {'last_read_seq': latest},
        ),
      );
      if (!mounted || epoch != _accessEpoch) return;
      final read = (result['last_read_seq'] as num?)?.toInt() ?? latest;
      if (read > _lastRead) _lastRead = read;
    } on Exception catch (error) {
      if (mounted && epoch == _accessEpoch) _accessDenied(error);
    } finally {
      _reading = false;
    }
  }

  Future<void> _send() async {
    final body = _draft.text.trim();
    final task = _linkedTask?['id'] as String?;
    final reply = _reply?['id'] as String?;
    if (!_canWrite || _sending || body.isEmpty || body.runes.length > 4000) {
      return;
    }
    if (body != _retryBody ||
        task != _retryTask ||
        reply != _retryReply ||
        _clientId == null) {
      _clientId = const Uuid().v4();
      _retryBody = body;
      _retryTask = task;
      _retryReply = reply;
    }
    setState(() {
      _sending = true;
      _sendError = null;
    });
    final epoch = _accessEpoch;
    try {
      final row = PageParser.parseObject(
        await _api.mutate(
          '$_base/messages',
          method: 'POST',
          body: {
            'client_message_id': _clientId,
            'body': body,
            'linked_task_id': task,
            'reply_to_id': ?reply,
          },
        ),
      );
      if (!mounted || epoch != _accessEpoch) return;
      setState(() {
        _messages.putIfAbsent(row['id'] as String, () => row);
        _draft.clear();
        _linkedTask = null;
        _reply = null;
        _clientId = null;
      });
      if (_scroll.hasClients) _scroll.jumpTo(0);
      unawaited(_load());
    } on Exception catch (error) {
      if (mounted && epoch == _accessEpoch && !_accessDenied(error)) {
        setState(() => _sendError = 'Chưa gửi được. Bấm gửi để thử lại.');
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _loadPin(int epoch) async {
    try {
      final items = PageParser.parseList(await _api.get('$_base/chat/pinned'));
      if (mounted && epoch == _accessEpoch) {
        setState(() => _pinned = items.firstOrNull);
      }
    } on Exception {
      // Chat history remains usable when refreshing the pin fails.
    }
  }

  Future<void> _loadTaskPermissions(int epoch) async {
    try {
      final result = PageParser.parseObject(await _api.get('/me/access'));
      final projects = result['projects'] as List? ?? [];
      final project = projects
          .whereType<Map>()
          .where((row) => row['id'] == widget.projectId)
          .firstOrNull;
      if (mounted && epoch == _accessEpoch) {
        setState(() {
          _canCreateTask = project?['canCreateTask'] == true;
          _canAssign = _canModerate || project?['assignment'] == true;
        });
      }
    } on Exception {
      // Permissions are checked by the task API before creation.
    }
  }

  Future<void> _openMessage(String id) async {
    final node = _rowKeys[id]?.currentContext;
    if (node != null) {
      await Scrollable.ensureVisible(node, alignment: .5);
      return;
    }
    final epoch = _accessEpoch;
    try {
      final message = PageParser.parseObject(
        await _api.get('$_base/messages/$id'),
      );
      if (!mounted || epoch != _accessEpoch) return;
      await showDialog<void>(
        context: context,
        builder: (dialog) => AlertDialog(
          title: const Text('Tin nhắn gốc'),
          content: Text(
            message['deleted'] == true
                ? 'Tin nhắn đã được xóa'
                : (message['body'] ?? '').toString(),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialog),
              child: const Text('Đóng'),
            ),
          ],
        ),
      );
    } on Exception catch (error) {
      if (mounted && epoch == _accessEpoch && !_accessDenied(error)) {
        setState(() => _error = 'Chưa mở được tin nhắn.');
      }
    }
  }

  Future<void> _search() async {
    var query = '';
    final q = await showDialog<String>(
      context: context,
      builder: (dialog) => AlertDialog(
        title: const Text('Tìm tin nhắn'),
        content: TextField(
          autofocus: true,
          maxLength: 200,
          onChanged: (value) => query = value,
          onSubmitted: (value) => Navigator.pop(dialog, value.trim()),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialog, query.trim()),
            child: const Text('Tìm'),
          ),
        ],
      ),
    );
    if (!mounted || q == null || q.isEmpty) return;
    final epoch = _accessEpoch;
    try {
      final items = PageParser.parseList(
        await _api.get('$_base/chat/search', query: {'q': q}),
      );
      if (!mounted || epoch != _accessEpoch) return;
      final result = await showModalBottomSheet<String>(
        context: context,
        showDragHandle: true,
        builder: (sheet) => SafeArea(
          child: ListView(
            children: [
              ListTile(
                title: Text(
                  items.isEmpty ? 'Không tìm thấy tin nào' : 'Kết quả tìm kiếm',
                ),
              ),
              for (final row in items)
                ListTile(
                  title: Text(
                    row['body'].toString(),
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                  ),
                  onTap: () => Navigator.pop(sheet, row['id'] as String),
                ),
            ],
          ),
        ),
      );
      if (mounted && epoch == _accessEpoch && result != null) {
        await _openMessage(result);
      }
    } on Exception catch (error) {
      if (mounted && epoch == _accessEpoch && !_accessDenied(error)) {
        setState(() => _error = 'Chưa tìm được tin nhắn.');
      }
    }
  }

  Future<void> _pin(Map<String, Object?> row) async {
    final epoch = _accessEpoch;
    try {
      await _api.mutate(
        '$_base/messages/${row['id']}/pin',
        method: 'PUT',
        body: {'pinned': row['pinned'] != true},
      );
      if (mounted && epoch == _accessEpoch) {
        await _loadPin(epoch);
        if (mounted) await _load();
      }
    } on Exception catch (error) {
      if (mounted && epoch == _accessEpoch && !_accessDenied(error)) {
        setState(() => _error = 'Chưa cập nhật được tin ghim.');
      }
    }
  }

  Future<void> _createTask(Map<String, Object?> row) async {
    final epoch = _accessEpoch;
    final api = _api;
    var people = <Map<String, Object?>>[];
    if (_canAssign) {
      try {
        people = PageParser.parseList(
          await api.get('$_base/members'),
        ).where((user) => user['role'] != 'GUEST').toList();
      } on Exception catch (error) {
        if (mounted && epoch == _accessEpoch && !_accessDenied(error)) {
          setState(() => _error = 'Chưa tải được danh sách người phụ trách.');
        }
        return;
      }
    }
    if (!mounted || epoch != _accessEpoch) return;
    final task = await showDialog<String>(
      context: context,
      builder: (_) => ChatTaskDialog(
        api: api,
        projectId: widget.projectId,
        source: row['body'].toString(),
        assignees: people,
      ),
    );
    if (mounted && epoch == _accessEpoch && task != null) {
      context.push('/tasks/$task');
    }
  }

  Future<void> _pickTask() async {
    final epoch = _accessEpoch;
    try {
      final page = PageParser.parsePage(
        await _api.get(ApiPaths.tasks(widget.projectId), query: {'limit': 100}),
      );
      if (!mounted || epoch != _accessEpoch) return;
      final task = await showModalBottomSheet<Map<String, Object?>>(
        context: context,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: page.items.isEmpty
              ? const ListTile(title: Text('Dự án chưa có công việc'))
              : ListView(
                  children: [
                    const ListTile(title: Text('Chọn công việc để gửi')),
                    for (final item in page.items)
                      ListTile(
                        title: Text(item['title'].toString()),
                        onTap: () => Navigator.pop(context, item),
                      ),
                  ],
                ),
        ),
      );
      if (mounted && epoch == _accessEpoch && task != null && !_sending) {
        setState(() => _linkedTask = task);
      }
    } on Exception {
      if (mounted && epoch == _accessEpoch) {
        setState(() => _sendError = 'Chưa tải được công việc của dự án.');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final me = context.watch<SessionManager>().userId;
    final rows = _messages.values.toList()
      ..sort((a, b) => _seq(b).compareTo(_seq(a)));
    return Scaffold(
      appBar: AppBar(
        title: Text(_projectName ?? 'Trò chuyện dự án'),
        leading: BackButton(
          onPressed: () {
            if (context.canPop()) {
              context.pop();
            } else {
              context.go('/projects/${widget.projectId}');
            }
          },
        ),
        actions: [
          if (_loaded && _error == null)
            IconButton(
              tooltip: 'Tìm tin nhắn',
              onPressed: _search,
              icon: const Icon(Icons.search),
            ),
          IconButton(
            tooltip: 'Làm mới',
            onPressed: _loading ? null : () => _load(),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            if (_pinned != null)
              ListTile(
                dense: true,
                leading: const Icon(Icons.push_pin_outlined),
                title: Text(
                  _pinned!['body'].toString(),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                onTap: () => _openMessage(_pinned!['id'].toString()),
                trailing: _canModerate
                    ? IconButton(
                        tooltip: 'Bỏ ghim',
                        onPressed: () => _pin(_pinned!),
                        icon: const Icon(Icons.close),
                      )
                    : null,
              ),
            if (_error != null)
              ListTile(
                title: Text(_error!, style: TextStyle(color: colors.error)),
                trailing: TextButton(
                  onPressed: _loading ? null : () => _load(),
                  child: const Text('Thử lại'),
                ),
              ),
            Expanded(
              child: ColoredBox(
                color: colors.surfaceContainerLowest,
                child: rows.isEmpty
                    ? Center(
                        child: !_loaded && _loading
                            ? const CircularProgressIndicator()
                            : Text(
                                _error != null
                                    ? ''
                                    : _canWrite
                                    ? 'Gửi tin nhắn đầu tiên cho dự án'
                                    : 'Chưa có tin nhắn',
                              ),
                      )
                    : ListView.builder(
                        reverse: true,
                        controller: _scroll,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        itemCount: rows.length + (_before == null ? 0 : 1),
                        itemBuilder: (context, index) {
                          if (index == rows.length) {
                            return Center(
                              child: TextButton(
                                onPressed: _loading
                                    ? null
                                    : () => _load(older: true),
                                child: const Text('Xem tin trước'),
                              ),
                            );
                          }
                          final row = rows[index];
                          final time = DateTime.parse(
                            row['created_at'] as String,
                          ).toLocal();
                          final previous = index + 1 < rows.length
                              ? rows[index + 1]
                              : null;
                          final previousTime = previous == null
                              ? null
                              : DateTime.parse(
                                  previous['created_at'] as String,
                                ).toLocal();
                          final day =
                              previousTime == null ||
                              !DateUtils.isSameDay(time, previousTime);
                          final firstUnread = _seq(row) == _firstUnread;
                          final grouped =
                              !day &&
                              !firstUnread &&
                              previous?['sender_id'] == row['sender_id'] &&
                              previous?['deleted'] != true &&
                              row['deleted'] != true &&
                              time.difference(previousTime).inMinutes < 5;
                          return Column(
                            children: [
                              if (day)
                                Padding(
                                  padding: const EdgeInsets.all(8),
                                  child: Text(
                                    DateFormat('dd/MM/yyyy').format(time),
                                  ),
                                ),
                              if (firstUnread)
                                Padding(
                                  padding: const EdgeInsets.all(8),
                                  child: Text(
                                    'Tin chưa đọc',
                                    style: TextStyle(
                                      color: colors.primary,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                              _bubble(
                                row,
                                time,
                                row['sender_id'] == me,
                                grouped,
                              ),
                            ],
                          );
                        },
                      ),
              ),
            ),
            if (_canWrite) _composer(),
            if (_loaded && !_canWrite && _error == null)
              const Padding(
                padding: EdgeInsets.all(12),
                child: Text('Bạn chỉ có quyền xem cuộc trò chuyện.'),
              ),
          ],
        ),
      ),
    );
  }

  Widget _bubble(
    Map<String, Object?> row,
    DateTime time,
    bool mine,
    bool grouped,
  ) {
    final colors = Theme.of(context).colorScheme;
    final ink = mine ? colors.onPrimaryContainer : colors.onSurface;
    final deleted = row['deleted'] == true;
    final task = deleted ? null : row['linked_task'] as Map?;
    final reply = deleted ? null : row['reply_to'] as Map?;
    return Align(
      key: _rowKeys.putIfAbsent(row['id'].toString(), GlobalKey.new),
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * .82,
        ),
        margin: EdgeInsets.only(top: grouped ? 2 : 8),
        padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 9),
        decoration: BoxDecoration(
          color: mine
              ? colors.primaryContainer
              : colors.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!mine && !grouped)
              Text(
                row['sender_name'].toString(),
                style: TextStyle(
                  color: ink,
                  fontWeight: FontWeight.w700,
                  fontSize: 12,
                ),
              ),
            if (reply != null)
              TextButton(
                onPressed: () => _openMessage(reply['id'] as String),
                child: Text(
                  (reply['body'] ?? 'Tin gốc đã được xóa').toString(),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            Text(
              deleted ? 'Tin nhắn đã được xóa' : (row['body'] ?? '').toString(),
              style: TextStyle(color: ink),
            ),
            if (task != null)
              TextButton.icon(
                icon: const Icon(Icons.link),
                onPressed: () => context.push('/tasks/${task['id']}'),
                label: Text(task['title'].toString(), softWrap: true),
              ),
            if (!deleted && row['task_unavailable'] == true)
              Text(
                'Công việc không còn khả dụng',
                style: TextStyle(color: ink, fontSize: 12),
              ),
            if (!deleted && (_canWrite || _canCreateTask))
              PopupMenuButton<String>(
                tooltip: 'Thao tác tin nhắn',
                icon: const Icon(Icons.more_horiz, size: 18),
                onSelected: (action) {
                  switch (action) {
                    case 'reply':
                      setState(() => _reply = row);
                    case 'pin':
                      unawaited(_pin(row));
                    case 'create':
                      unawaited(_createTask(row));
                  }
                },
                itemBuilder: (_) => [
                  if (_canWrite)
                    const PopupMenuItem(value: 'reply', child: Text('Trả lời')),
                  if (_canModerate)
                    PopupMenuItem(
                      value: 'pin',
                      child: Text(row['pinned'] == true ? 'Bỏ ghim' : 'Ghim'),
                    ),
                  if (_canCreateTask)
                    const PopupMenuItem(
                      value: 'create',
                      child: Text('Tạo công việc'),
                    ),
                ],
              ),
            Text(
              DateFormat('HH:mm').format(time),
              style: TextStyle(color: ink, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }

  Widget _composer() => Padding(
    padding: const EdgeInsets.fromLTRB(8, 4, 8, 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_sendError != null)
          Text(
            _sendError!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        if (_reply != null)
          InputChip(
            label: Text(
              'Trả lời ${_reply!['body']}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            onDeleted: _sending ? null : () => setState(() => _reply = null),
          ),
        if (_linkedTask != null)
          InputChip(
            label: Text(
              _linkedTask!['title'].toString(),
              overflow: TextOverflow.ellipsis,
            ),
            onDeleted: _sending
                ? null
                : () => setState(() => _linkedTask = null),
          ),
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            IconButton(
              tooltip: 'Liên kết công việc',
              onPressed: _sending ? null : _pickTask,
              icon: const Icon(Icons.attach_file),
            ),
            Expanded(
              child: TextField(
                controller: _draft,
                enabled: !_sending,
                minLines: 1,
                maxLines: 4,
                maxLength: 4000,
                keyboardType: TextInputType.multiline,
                decoration: const InputDecoration(
                  hintText: 'Nhắn tin hoặc @tên_đăng_nhập…',
                  counterText: '',
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.all(Radius.circular(22)),
                  ),
                ),
              ),
            ),
            IconButton.filled(
              tooltip: 'Gửi tin nhắn',
              onPressed: _sending ? null : _send,
              icon: _sending
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.send_rounded),
            ),
          ],
        ),
      ],
    ),
  );
}
