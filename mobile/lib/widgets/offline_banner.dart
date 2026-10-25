// Banner offline: hien "Can ket noi de xem" + nut Reauthorize.
// Noi dung Project/Task/file rieng tu KHONG mo offline neu chua reauthorize.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../cache/offline_store.dart';

class OfflineBanner extends StatelessWidget {
  final bool isOnline;
  final VoidCallback onReauthorize;

  const OfflineBanner({
    super.key,
    required this.isOnline,
    required this.onReauthorize,
  });

  @override
  Widget build(BuildContext context) {
    if (isOnline) return const SizedBox.shrink();
    final store = context.watch<OfflineStore>();
    return MaterialBanner(
      content: Text(
        store.reauthorized
            ? 'Cần kết nối để xem nội dung mới nhất.'
            : 'Cần kết nối để xem. Nội dung riêng tư không mở offline '
                'nếu chưa reauthorize.',
      ),
      leading: const Icon(Icons.cloud_off),
      actions: [
        TextButton(
          onPressed: onReauthorize,
          child: const Text('Reauthorize'),
        ),
      ],
    );
  }
}
