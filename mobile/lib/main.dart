// SmartTask Mobile — entry point.
// Chung contract /api/v1 voi Web (ARCH-02). Khong co AI tren thiet bi.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'api/client.dart';
import 'api/endpoints.dart';
import 'app.dart';
import 'auth/device_info.dart';
import 'auth/reauth.dart';
import 'auth/session.dart';
import 'cache/connectivity.dart';
import 'cache/offline_store.dart';
import 'push/fcm_stub.dart';
import 'realtime/ws_client.dart';

const String _defaultBaseUrl = String.fromEnvironment(
  'STM_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080',
);

const String _defaultWsUrl = String.fromEnvironment(
  'STM_WS_URL',
  defaultValue: 'ws://10.0.2.2:8080',
);

String get baseUrl => _defaultBaseUrl;
String get wsUrl => _defaultWsUrl;

void main() {
  runApp(const SmartTaskApp());
}

class SmartTaskApp extends StatefulWidget {
  const SmartTaskApp({super.key});

  @override
  State<SmartTaskApp> createState() => _SmartTaskAppState();
}

class _SmartTaskAppState extends State<SmartTaskApp> {
  late final SessionManager session;
  late final ReauthFlow reauth;
  late final OfflineStore offline;
  late final PushRegistrar push;
  late final ConnectionMonitor connection;
  late final RealtimeClient realtime;

  @override
  void initState() {
    super.initState();
    offline = OfflineStore();
    connection = ConnectionMonitor();
    session = SessionManager(
      clientFactory: (reader) => ApiClient(
        baseUrl: baseUrl,
        tokenReader: reader,
        clientType: DeviceInfo.clientType(),
        onSessionRevoked: (_) async {
          // INVALID_SESSION tu API: danh dau revoke, don qua _clear de
          // onSessionEnded xoa offline + push mot moi duy nhat.
          await session.handleRevokedElsewhere();
        },
      ),
    );
    // Moi duong ket thuc phien (logout / revoke / expired / refresh-expired)
    // deu xoa cache offline + huy push dang ky (khong luu rieng tu offline).
    session.onSessionEnded = () async {
      await offline.clearOnRevoke();
      await push.unregister();
      await realtime.disconnect();
    };
    push = PushRegistrar(api: session.api);
    reauth = ReauthFlow(api: session.api);
    realtime = RealtimeClient(
      wsBaseUrl: wsUrl,
      // Ticket cap qua REST da auth moi connect/reconnect (single-use 60s).
      ticketProvider: () async {
        try {
          final data = await session.api.dio.post<Map<String, dynamic>>(
            ApiPaths.realtimeTicket,
            data: const {},
          );
          return PageParser.parseObject(data.data)['ticket']?.toString();
        } on Exception {
          return null;
        }
      },
      // Resync cursor luu o allowlist lastSyncAt (gan user_id).
      cursorReader: () async {
        final scope = session.userId;
        if (scope == null) return null;
        final v = await offline.read(CachedKind.lastSyncAt, scope);
        return v?.toString();
      },
      cursorWriter: (cursor) async {
        final scope = session.userId;
        if (scope == null) return;
        await offline.put(CachedKind.lastSyncAt, scope, cursor);
      },
      // WS chi la tin hieu: UI tu refetch qua API khi co event.
      // (Cac screen dung RefreshIndicator + _load; khong tin data tu WS.)
      onEvent: (_) {},
    );
    session.restore();
    session.addListener(_onSessionChanged);
  }

  void _onSessionChanged() {
    // 401 tu SSE (ticket/session bi thu hoi): khong tu reconnect loop.
    // RealtimeClient tu chuyen reauthRequired; o day chi dam bao khong giu
    // ket noi cu khi phien da ket thuc.
    if (session.status == AuthStatus.authenticated) {
      if (realtime.status == WsStatus.disconnected) {
        unawaited(realtime.connect());
      } else if (realtime.status == WsStatus.reauthRequired) {
        unawaited(realtime.reconnectAfterReauth());
      }
    } else if (session.status == AuthStatus.unauthenticated) {
      unawaited(realtime.disconnect());
    }
    // Push: chua co FCM/APNs token that trong V1 -> KHONG goi register
    // (quy dinh: khong dang ky fake). Cho backend/dev co token that moi goi:
    //   push.register(token: realToken, platform: 'ANDROID_FCM',
    //     deviceLabel: DeviceInfo.deviceLabel())
  }

  @override
  void dispose() {
    session.removeListener(_onSessionChanged);
    session.dispose();
    reauth.dispose();
    offline.dispose();
    connection.dispose();
    push.dispose();
    realtime.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: session),
        ChangeNotifierProvider.value(value: reauth),
        ChangeNotifierProvider.value(value: offline),
        ChangeNotifierProvider.value(value: connection),
        ChangeNotifierProvider.value(value: push),
        ChangeNotifierProvider.value(value: realtime),
      ],
      child: Builder(
        builder: (context) {
          final router = buildRouter(context.read<SessionManager>());
          return MaterialApp.router(
            title: 'SmartTask',
            theme: ThemeData(
              colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
              useMaterial3: true,
            ),
            routerConfig: router,
          );
        },
      ),
    );
  }
}
