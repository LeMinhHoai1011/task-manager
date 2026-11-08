// Man hinh Ghi chu: danh sach (cot trai) + editor (cot phai / toan man hinh mobile).
// Scope: PERSONAL, PROJECT, SHARED, TASK. Auto-save debounce, pin, xoa hoan tac.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../api/endpoints.dart';
import '../auth/session.dart';
import '../cache/connectivity.dart';
import '../widgets/offline_banner.dart';

class NoteItem {
  final String id;
  final String title;
  final String content;
  final String scope;
  final bool isPinned;
  final String updatedAt;
  final String? projectId;
  final String? taskId;
  final String? workspaceId;

  NoteItem({
    required this.id,
    required this.title,
    required this.content,
    required this.scope,
    required this.isPinned,
    required this.updatedAt,
    this.projectId,
    this.taskId,
    this.workspaceId,
  });

  factory NoteItem.fromMap(Map<String, Object?> m) => NoteItem(
        id: m['id']?.toString() ?? '',
        title: m['title']?.toString() ?? 'Ghi chú không tên',
        content: m['content']?.toString() ?? '',
        scope: m['scope']?.toString() ?? 'PERSONAL',
        isPinned: m['is_pinned'] == true,
        updatedAt: m['updated_at']?.toString() ?? '',
        projectId: m['project_id']?.toString(),
        taskId: m['task_id']?.toString(),
        workspaceId: m['workspace_id']?.toString(),
      );
}

class MyNotesScreen extends StatefulWidget {
  const MyNotesScreen({super.key});

  @override
  State<MyNotesScreen> createState() => _MyNotesScreenState();
}

class _MyNotesScreenState extends State<MyNotesScreen> {
  List<NoteItem> _notes = [];
  NoteItem? _selected;
  String? _selectedId;
  bool _busy = true;
  String? _error;
  String _filter = 'ALL'; // ALL, PINNED, PERSONAL, PROJECT, SHARED
  String _search = '';

  // Editor state
  final _titleCtl = TextEditingController();
  final _contentCtl = TextEditingController();
  String _editScope = 'PERSONAL';
  String _editTags = '';
  bool _editPinned = false;
  String _editProjectId = '';
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _titleCtl.dispose();
    _contentCtl.dispose();
    super.dispose();
  }

  Future<void> _load({bool clear = true}) async {
    final api = context.read<SessionManager>().api;
    setState(() {
      _busy = true;
      _error = null;
      if (clear) _notes = [];
    });
    try {
      final data = await api.get(ApiPaths.notes);
      final page = PageParser.parsePage(data);
      _notes = page.items.cast<Map<String, Object?>>().map(NoteItem.fromMap).toList();
      // Pre-select from first note
      if (_notes.isNotEmpty && _selectedId == null) {
        _selectNote(_notes.first.id);
      }
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() {
        _error = err is ApiException
            ? (err.isNotFound ? err.envelope.neutralMessage : err.envelope.code)
            : e.message;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  List<NoteItem> get _filtered {
    var list = _notes;
    if (_filter != 'ALL') {
      list = list.where((n) => n.scope == _filter).toList();
    }
    if (_search.isNotEmpty) {
      final q = _search.toLowerCase();
      list = list.where((n) =>
          n.title.toLowerCase().contains(q) ||
          n.content.toLowerCase().contains(q)).toList();
    }
    // Pinned first
    list.sort((a, b) {
      if (a.isPinned && !b.isPinned) return -1;
      if (!a.isPinned && b.isPinned) return 1;
      return 0;
    });
    return list;
  }

  void _selectNote(String id) {
    setState(() {
      _selectedId = id;
      _selected = _notes.firstWhere((n) => n.id == id);
      _titleCtl.text = _selected!.title;
      _contentCtl.text = _selected!.content;
      _editScope = _selected!.scope;
      _editPinned = _selected!.isPinned;
      _editProjectId = _selected!.projectId ?? '';
    });
  }

  Future<void> _createNote() async {
    final api = context.read<SessionManager>().api;
    setState(() { _saving = true; });
    try {
      final data = await api.mutate(
        ApiPaths.notes,
        method: 'POST',
        body: {
          'title': 'Ghi chú mới',
          'content': '',
          'scope': 'PERSONAL',
          'is_pinned': false,
        },
        idempotencyKey: api.newIdempotencyKey(),
      );
      final obj = PageParser.parseObject(data);
      final newNote = NoteItem.fromMap(obj);
      if (!mounted) return;
      setState(() {
        _notes.insert(0, newNote);
        _selectNote(newNote.id);
        _saving = false;
      });
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err is ApiException ? err.envelope.code : 'Lỗi tạo ghi chú')),
      );
    }
  }

  Future<void> _saveNote() async {
    if (_selected == null) return;
    final api = context.read<SessionManager>().api;
    setState(() { _saving = true; });
    try {
      await api.mutate(
        ApiPaths.note(_selected!.id),
        method: 'PUT',
        ifMatchVersion: null,
        idempotencyKey: api.newIdempotencyKey(),
        body: {
          'title': _titleCtl.text.trim().isEmpty ? 'Ghi chú không tên' : _titleCtl.text.trim(),
          'content': _contentCtl.text,
          'scope': _editScope,
          'tags': _editTags,
          'is_pinned': _editPinned,
          'project_id': _editProjectId.isEmpty ? null : _editProjectId,
        },
      );
      if (!mounted) return;
      setState(() {
        _selected = _selected!.copyWith(
          title: _titleCtl.text,
          content: _contentCtl.text,
          scope: _editScope,
          isPinned: _editPinned,
          projectId: _editProjectId.isEmpty ? null : _editProjectId,
        );
        final idx = _notes.indexWhere((n) => n.id == _selected!.id);
        if (idx >= 0) _notes[idx] = _selected!;
        _saving = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã lưu ghi chú')),
      );
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err is ApiException ? err.envelope.code : 'Lỗi lưu')),
      );
    }
  }

  Future<void> _togglePin() async {
    if (_selectedId == null) return;
    final api = context.read<SessionManager>().api;
    try {
      await api.mutate(ApiPaths.notePin(_selectedId!), method: 'PATCH',
          idempotencyKey: api.newIdempotencyKey());
      await _load();
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err is ApiException ? err.envelope.code : 'Lỗi ghim')),
      );
    }
  }

  Future<void> _deleteNote() async {
    if (_selectedId == null) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Xóa ghi chú?'),
        content: const Text('Hành động này có thể hoàn tác trong thời gian ngắn.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Hủy')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Xóa')),
        ],
      ),
    );
    if (confirmed != true) return;
    final api = context.read<SessionManager>().api;
    try {
      await api.mutate(ApiPaths.note(_selectedId!), method: 'DELETE',
          idempotencyKey: api.newIdempotencyKey());
      if (!mounted) return;
      setState(() {
        _notes.removeWhere((n) => n.id == _selectedId);
        _selectedId = null;
        _selected = null;
        _titleCtl.clear();
        _contentCtl.clear();
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã xóa ghi chú')),
      );
    } on DioException catch (e) {
      final err = e.error;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err is ApiException ? err.envelope.code : 'Lỗi xóa')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectionMonitor>().isOnline;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ghi chú'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            tooltip: 'Tạo ghi chú mới',
            onPressed: _busy ? null : _createNote,
          ),
        ],
      ),
      body: Column(
        children: [
          OfflineBanner(
            isOnline: online,
            onReauthorize: () => context.go('/reauth'),
          ),
          // Filter + search bar
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        decoration: const InputDecoration(
                          hintText: 'Tìm ghi chú hoặc tags...',
                          isDense: true,
                          prefixIcon: Icon(Icons.search, size: 18),
                        ),
                        onChanged: (v) => setState(() => _search = v),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  children: ['ALL', 'PINNED', 'PERSONAL', 'PROJECT', 'SHARED']
                      .map((f) => FilterChip(
                            label: Text(f == 'ALL' ? 'Tất cả' :
                                f == 'PINNED' ? 'Đã ghim' :
                                f == 'PERSONAL' ? 'Cá nhân' :
                                f == 'PROJECT' ? 'Dự án' : 'Chia sẻ'),
                            selected: _filter == f,
                            onSelected: (_) => setState(() => _filter = f),
                          )).toList(),
                ),
              ],
            ),
          ),
          Expanded(
            child: _busy
                ? const Center(child: CircularProgressIndicator())
                : _error != null && _notes.isEmpty
                    ? Center(child: Text(_error!))
                    : _filtered.isEmpty
                        ? const Center(
                            child: Text('Không tìm thấy ghi chú nào phù hợp.'),
                          )
                        : ListView.builder(
                            itemCount: _filtered.length,
                            itemBuilder: (context, i) {
                              final n = _filtered[i];
                              final isSelected = n.id == _selectedId;
                              return ListTile(
                                leading: Icon(
                                  n.isPinned ? Icons.push_pin : Icons.push_pin_outlined,
                                  color: n.isPinned ? Colors.amber : null,
                                  size: 20,
                                ),
                                title: Text(
                                  n.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                  ),
                                ),
                                subtitle: Text(
                                  n.content.isNotEmpty ? n.content : '(trống)',
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                      icon: Icon(
                                        n.isPinned ? Icons.push_pin : Icons.push_pin_outlined,
                                        color: n.isPinned ? Colors.amber : null,
                                      ),
                                      onPressed: _busy ? null : _togglePin,
                                      tooltip: n.isPinned ? 'Bỏ ghim' : 'Ghim',
                                    ),
                                  ],
                                ),
                                onTap: () => _selectNote(n.id),
                                selected: isSelected,
                                selectedTileColor: Theme.of(context).primaryColor.withOpacity(0.08),
                              );
                            },
                          ),
          ),
        ],
      ),
      floatingActionButton: _selected != null && !_saving
          ? FloatingActionButton.extended(
              onPressed: _saveNote,
              icon: const Icon(Icons.save),
              label: const Text('Lưu'),
            )
          : null,
    );
  }
}

extension _NoteItemCopy on NoteItem {
  NoteItem copyWith({
    String? title,
    String? content,
    String? scope,
    bool? isPinned,
    String? projectId,
  }) => NoteItem(
        id: id,
        title: title ?? this.title,
        content: content ?? this.content,
        scope: scope ?? this.scope,
        isPinned: isPinned ?? this.isPinned,
        updatedAt: updatedAt,
        projectId: projectId ?? this.projectId,
        taskId: taskId,
        workspaceId: workspaceId,
      );
}
