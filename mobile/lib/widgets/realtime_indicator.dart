// Den trang thai realtime tren AppBar: SSE chi la tin hieu, API la nguon
// du lieu (ARCH-09). Cham de xem chi tiet / noi lai sau reauth.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../realtime/ws_client.dart';

class RealtimeIndicator extends StatelessWidget {
  final Future<void> Function()? onReconnect;
  const RealtimeIndicator({super.key, this.onReconnect});

  @override
  Widget build(BuildContext context) {
    final client = context.watch<RealtimeClient>();
    final (icon, color, label) = switch (client.status) {
      WsStatus.connected => (Icons.cloud_done, Colors.green, 'Realtime: da noi'),
      WsStatus.connecting => (
          Icons.cloud_sync,
          Colors.orange,
          'Realtime: dang noi'
        ),
      WsStatus.reauthRequired => (
          Icons.cloud_off,
          Colors.red,
          'Realtime: phien cuc, can dang nhap lai'
        ),
      WsStatus.disconnected => (
          Icons.cloud_queue,
          Colors.grey,
          'Realtime: chua noi (degrade: dung API)'
        ),
    };
    return IconButton(
      icon: Icon(icon, color: color),
      tooltip: '$label - SSE chi la tin hieu, du lieu lay qua API.',
      onPressed: () async {
        final messenger = ScaffoldMessenger.of(context);
        if (client.status == WsStatus.reauthRequired && onReconnect != null) {
          await onReconnect!();
        }
        messenger.showSnackBar(SnackBar(content: Text(label)));
      },
    );
  }
}
