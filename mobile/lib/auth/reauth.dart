// Luong reauth cho action nhay cam (04-RBAC §14), DUNG CONTRACT BACKEND
// (da doc AuthController.java + IdentityService.java truoc khi chot):
//
// - Challenge: POST /auth/reauth/challenge {action, target_id?}
//   -> ApiResult.data {ticket_id, expires_at}. TTL 5 phut, single-use, gan
//   (user, action, target). Challenge KHONG kiem tra mat khau.
// - Verify: POST /auth/reauth/verify {ticket_id, password, action, target_id?}
//   -> ApiResult.data {verified: true}. Sai mat khau -> 401, ticket van song.
//   Verify DUNG thi ticket BI CONSUME ngay (single-use).
//
// He qua quan trong: ticket da verify KHONG dung lai duoc cho mutation retry
// (mutation tieu thu ticket se gap 428). Nen sau verify thanh cong, client xin
// challenge MOI de gan vao retry, giu NGUYEN Idempotency-Key cua request goc
// (03 §4.1: retry dung key cu). Thuc hien o [reauthorize].
// Dau cham audit: verify thanh cong da duoc server ghi (auth.reauth.verify).
//
// Khac docs-03: verify backend tra {verified} chu khong tra token; khong co
// truong `challenge_id` / `reauth_token` tren backend (code cu dung sai).
library;

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../api/client.dart';
import '../api/endpoints.dart';

class ReauthFlow extends ChangeNotifier {
  final ApiClient api;
  String? actionCode;
  String? targetId;
  bool inProgress = false;
  String? error;

  ReauthFlow({required this.api});

  /// Bat dau reauth tu ApiException 428. Backend khong gui action code trong
  /// envelope (khong co error.reauth_action_code) -> lay tu caller/verb, mac
  /// dinh 'sensitive' (giong AuthController.getOrDefault).
  void beginFromError(ApiException e, {String? targetId}) {
    actionCode = e.envelope.reauthActionCode ?? actionCode ?? 'sensitive';
    this.targetId = targetId;
    error = null;
    notifyListeners();
  }

  /// Xin challenge tu server, tra ve ticket_id (TTL 5 phut, single-use).
  Future<String?> requestChallenge({String? action, String? targetId}) async {
    final act = action ?? actionCode ?? 'sensitive';
    final tgt = targetId ?? this.targetId;
    inProgress = true;
    error = null;
    notifyListeners();
    try {
      final payload = <String, Object?>{'action': act};
      final String? tid = tgt;
      if (tid != null) payload['target_id'] = tid;
      final res = await api.dio.post<Map<String, dynamic>>(
        ApiPaths.reauthChallenge,
        data: payload,
      );
      final data = PageParser.parseObject(res.data);
      return data['ticket_id']?.toString();
    } on DioException catch (e) {
      error = e.error?.toString() ?? e.message;
      return null;
    } finally {
      inProgress = false;
      notifyListeners();
    }
  }

  /// Xac minh mat khau hien tai voi ticket. Tra true khi
  /// ApiResult.data.verified == true. Ticket bi consume phia server ngay ca
  /// khi verify dung — khong dung lai ticket nay cho retry (xem [reauthorize]).
  Future<bool> verify(
    String password,
    String ticketId, {
    String? action,
    String? targetId,
  }) async {
    final act = action ?? actionCode ?? 'sensitive';
    final tgt = targetId ?? this.targetId;
    inProgress = true;
    error = null;
    notifyListeners();
    try {
      final payload = <String, Object?>{
        'ticket_id': ticketId,
        'password': password,
        'action': act,
      };
      final String? tid = tgt;
      if (tid != null) payload['target_id'] = tid;
      final res = await api.dio.post<Map<String, dynamic>>(
        ApiPaths.reauthVerify,
        data: payload,
      );
      final data = PageParser.parseObject(res.data);
      return data['verified'] == true;
    } on DioException catch (e) {
      error = e.error?.toString() ?? e.message;
      return false;
    } finally {
      inProgress = false;
      notifyListeners();
    }
  }

  /// Tron flow: challenge -> verify (password) -> challenge moi cho retry.
  /// Tra ve ticket_id TUOI de gan vao mutation retry (header X-Reauth-Ticket
  /// hoac body reauth_ticket_id), giu nguyen Idempotency-Key goc o caller.
  /// Tra null khi bat ky buoc nao that bai (xem [error]).
  Future<String?> reauthorize(
    String password, {
    String? action,
    String? targetId,
  }) async {
    final act = action ?? actionCode ?? 'sensitive';
    final tgt = targetId ?? this.targetId;
    final ticket = await requestChallenge(action: act, targetId: tgt);
    if (ticket == null) return null;
    final ok = await verify(password, ticket, action: act, targetId: tgt);
    if (!ok) return null;
    // Ticket vua verify da bi consume -> xin ticket tuoi cho retry.
    return requestChallenge(action: act, targetId: tgt);
  }

  void reset() {
    actionCode = null;
    targetId = null;
    error = null;
    notifyListeners();
  }
}
