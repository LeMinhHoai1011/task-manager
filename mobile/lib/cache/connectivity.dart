// Theo doi trang thai mang de hien OfflineBanner. Khong cache noi dung
// rieng tu o day — chi la co online hay khong.
library;

import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';

class ConnectionMonitor extends ChangeNotifier {
  final Connectivity _connectivity;
  StreamSubscription<List<ConnectivityResult>>? _sub;

  bool isOnline = true;

  ConnectionMonitor({Connectivity? connectivity})
      : _connectivity = connectivity ?? Connectivity() {
    try {
      _sub = _connectivity.onConnectivityChanged.listen(
        _onChanged,
        onError: (_) {},
      );
      _connectivity.checkConnectivity().then(_onChanged, onError: (_) {});
    } on Exception {
      // Plugin khong kha dung (test/exotic platform): mac dinh online,
      // khong crash app.
    }
  }

  /// Ctor cho test: nhan stream trang thai mang gia lap.
  ConnectionMonitor.tested({required Stream<List<ConnectivityResult>> stream})
      : _connectivity = Connectivity() {
    _sub = stream.listen(_onChanged);
  }

  void _onChanged(List<ConnectivityResult> results) {
    final online = results.any((r) => r != ConnectivityResult.none);
    if (online != isOnline) {
      isOnline = online;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}
