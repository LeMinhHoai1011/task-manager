// Router + shell chinh (go_router). Dieu huong theo AuthStatus.
library;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'auth/session.dart';
import 'screens/ai_screen.dart';
import 'screens/calendar_screen.dart';
import 'screens/comments_screen.dart';
import 'screens/files_screen.dart';
import 'screens/login_screen.dart';
import 'screens/notifications_screen.dart';
import 'screens/projects_screen.dart';
import 'screens/reauth_screen.dart';
import 'screens/reports_screen.dart';
import 'screens/task_detail_screen.dart';
import 'screens/tasks_screen.dart';
import 'screens/workspaces_screen.dart';

GoRouter buildRouter(SessionManager session) {
  return GoRouter(
    refreshListenable: session,
    initialLocation: '/workspaces',
    redirect: (context, state) {
      final authed = session.status == AuthStatus.authenticated ||
          session.status == AuthStatus.reauthRequired;
      final atLogin = state.matchedLocation == '/login';
      if (!authed && !atLogin) return '/login';
      if (authed && atLogin) return '/workspaces';
      if (session.status == AuthStatus.reauthRequired &&
          state.matchedLocation != '/reauth') {
        return '/reauth';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (c, s) => const LoginScreen()),
      GoRoute(path: '/reauth', builder: (c, s) => const ReauthScreen()),
      GoRoute(
        path: '/workspaces',
        builder: (c, s) => const WorkspacesScreen(),
      ),
      GoRoute(
        path: '/workspaces/:wid/projects',
        builder: (c, s) =>
            ProjectsScreen(workspaceId: s.pathParameters['wid'] ?? ''),
      ),
      GoRoute(
        path: '/projects/:pid/tasks',
        builder: (c, s) =>
            TasksScreen(projectId: s.pathParameters['pid'] ?? ''),
      ),
      GoRoute(
        path: '/tasks/:tid',
        builder: (c, s) =>
            TaskDetailScreen(taskId: s.pathParameters['tid'] ?? ''),
      ),
      GoRoute(
        path: '/tasks/:tid/comments',
        builder: (c, s) =>
            CommentsScreen(taskId: s.pathParameters['tid'] ?? ''),
      ),
      GoRoute(
        path: '/tasks/:tid/files',
        builder: (c, s) =>
            FilesScreen(taskId: s.pathParameters['tid'] ?? ''),
      ),
      GoRoute(
        path: '/ai',
        builder: (c, s) =>
            AiScreen(projectId: s.uri.queryParameters['projectId'] ?? ''),
      ),
      GoRoute(path: '/calendar', builder: (c, s) => const CalendarScreen()),
      GoRoute(
        path: '/notifications',
        builder: (c, s) => const NotificationsScreen(),
      ),
      GoRoute(path: '/reports', builder: (c, s) => const ReportsScreen()),
    ],
  );
}

class HomeShell extends StatelessWidget {
  final Widget child;
  const HomeShell({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: child,
      bottomNavigationBar: BottomNavigationBar(
        type: BottomNavigationBarType.fixed,
        currentIndex: _indexOf(context),
        onTap: (i) => _go(context, i),
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.workspaces),
            label: 'WS',
          ),
          BottomNavigationBarItem(icon: Icon(Icons.calendar_month), label: 'Lịch'),
          BottomNavigationBarItem(
            icon: Icon(Icons.notifications),
            label: 'TB',
          ),
          BottomNavigationBarItem(icon: Icon(Icons.bar_chart), label: 'BC'),
        ],
      ),
    );
  }

  int _indexOf(BuildContext context) {
    final loc = GoRouterState.of(context).matchedLocation;
    if (loc.startsWith('/calendar')) return 1;
    if (loc.startsWith('/notifications')) return 2;
    if (loc.startsWith('/reports')) return 3;
    return 0;
  }

  void _go(BuildContext context, int i) {
    final session = context.read<SessionManager>();
    session.clearReauth();
    switch (i) {
      case 1:
        context.go('/calendar');
      case 2:
        context.go('/notifications');
      case 3:
        context.go('/reports');
      default:
        context.go('/workspaces');
    }
  }
}
