// Smoke test: app khoi dong duoc (khong doi hoi backend).
import 'package:flutter_test/flutter_test.dart';
import 'package:smarttask_mobile/main.dart';

void main() {
  testWidgets('SmartTaskApp builds', (WidgetTester tester) async {
    await tester.pumpWidget(const SmartTaskApp());
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Đăng nhập'), findsWidgets);
  });
}
