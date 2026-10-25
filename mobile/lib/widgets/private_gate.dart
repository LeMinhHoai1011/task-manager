// Cong bao ve noi dung rieng tu (DEC-12/A): khi OFFLINE va phien chua
// reauthorize, khong mo noi dung rieng tu — hien cong yeu cau reauthorize
// (man hinh reauth) truoc. Noi dung allowlist (ten WS/Project, TB redacted)
// khong can cong nay.
library;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../cache/connectivity.dart';
import '../cache/offline_store.dart';

class PrivateGate extends StatelessWidget {
  final Widget child;
  final String resourceLabel;

  const PrivateGate({
    super.key,
    required this.child,
    this.resourceLabel = 'Nội dung riêng tư',
  });

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectionMonitor>().isOnline;
    final store = context.watch<OfflineStore>();
    if (!online && !store.reauthorized) {
      return Scaffold(
        appBar: AppBar(title: Text(resourceLabel)),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.lock, size: 48),
                const SizedBox(height: 12),
                const Text(
                  'Cần kết nối để xem. Nội dung riêng tư không mở offline '
                  'nếu chưa reauthorize.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: () => context.go('/reauth'),
                  child: const Text('Reauthorize'),
                ),
              ],
            ),
          ),
        ),
      );
    }
    return child;
  }
}
