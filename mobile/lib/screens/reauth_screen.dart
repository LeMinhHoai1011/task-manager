// Man hinh reauth: vua cho action nhay cam (428), vua la cong reauthorize
// de mo noi dung offline (PrivateGate / OfflineBanner dan ve day).
//
// Flow dung contract backend (xem ReauthFlow): nhap mat khau MOT lan ->
// challenge -> verify -> challenge tuoi cho retry. Khong tach 2 nut
// challenge/verify rieng (code cu sai contract: challenge_id/reauth_token
// khong ton tai tren backend).
// Thanh cong: session.clearReauth() + offline.markReauthorized() + quay lai.
library;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../auth/reauth.dart';
import '../auth/session.dart';
import '../cache/offline_store.dart';

class ReauthScreen extends StatefulWidget {
  const ReauthScreen({super.key});

  @override
  State<ReauthScreen> createState() => _ReauthScreenState();
}

class _ReauthScreenState extends State<ReauthScreen> {
  final _pass = TextEditingController();
  String? _result;

  @override
  void dispose() {
    _pass.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final flow = context.read<ReauthFlow>();
    final session = context.read<SessionManager>();
    final offline = context.read<OfflineStore>();
    final router = GoRouter.of(context);
    setState(() => _result = null);
    final ticket = await flow.reauthorize(_pass.text);
    if (!mounted) return;
    if (ticket != null) {
      session.clearReauth();
      await offline.markReauthorized();
      if (!mounted) return;
      setState(() {
        _result = 'OK. Hãy thử lại thao tác.';
      });
      router.pop();
    } else {
      if (!mounted) return;
      setState(() {
        _result = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final flow = context.watch<ReauthFlow>();
    final session = context.watch<SessionManager>();
    return Scaffold(
      appBar: AppBar(title: const Text('Xác thực lại')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Text(
              'Hành động cần xác thực lại: '
              '${flow.actionCode ?? session.pendingReauthActionCode ?? 'sensitive'}',
            ),
            const SizedBox(height: 8),
            const Text(
              'Nhập mật khẩu hiện tại. Xác minh đúng sẽ mở lại thao tác '
              'và cho phép xem nội dung đã lưu offline.',
            ),
            TextField(
              controller: _pass,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Mật khẩu hiện tại'),
            ),
            const SizedBox(height: 12),
            if (flow.error != null)
              Text(flow.error!, style: const TextStyle(color: Colors.red)),
            if (_result != null) Text(_result!),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: flow.inProgress ? null : _submit,
              child: flow.inProgress
                  ? const CircularProgressIndicator()
                  : const Text('Xác minh'),
            ),
          ],
        ),
      ),
    );
  }
}
