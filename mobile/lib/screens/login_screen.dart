// Man hinh dang nhap: hien canh bao single-session (phong thu — backend hien
// tai revoke phien cu ngay khi login, khong tra 409) va cho xac nhan revoke.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../auth/device_info.dart';
import '../auth/session.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _user = TextEditingController();
  final _pass = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _user.dispose();
    _pass.dispose();
    super.dispose();
  }

  Future<void> _submit({bool confirmRevoke = false}) async {
    final session = context.read<SessionManager>();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await session.login(
        username: _user.text.trim(),
        password: _pass.text,
        // Nhan that theo nen tang (khong hardcode 'Android app').
        deviceLabel: DeviceInfo.deviceLabel(),
        confirmRevokeExistingSession: confirmRevoke,
      );
    } on DioException catch (e) {
      final err = e.error;
      setState(() {
        _error = err is ApiException
            ? '${err.envelope.code} (${err.envelope.correlationId})'
            : e.message;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<SessionManager>();
    return Scaffold(
      appBar: AppBar(title: const Text('Đăng nhập')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _user,
              decoration: const InputDecoration(labelText: 'Tên đăng nhập'),
            ),
            TextField(
              controller: _pass,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Mật khẩu'),
            ),
            const SizedBox(height: 12),
            if (session.singleSessionNotice)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    children: [
                      const Text(
                        'Tài khoản đang có phiên hoạt động trên thiết bị khác. '
                        'Đăng nhập sẽ thu hồi phiên cũ.',
                      ),
                      TextButton(
                        onPressed: _busy ? null : () => _submit(confirmRevoke: true),
                        child: const Text('Xác nhận đăng nhập, thu hồi phiên cũ'),
                      ),
                    ],
                  ),
                ),
              ),
            if (_error != null)
              Text(_error!, style: const TextStyle(color: Colors.red)),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _busy ? null : () => _submit(),
              child: _busy
                  ? const CircularProgressIndicator()
                  : const Text('Đăng nhập'),
            ),
          ],
        ),
      ),
    );
  }
}
