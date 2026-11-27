import 'package:integration_test/integration_test.dart';

import '../test/project_chat_test.dart' as chat;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  chat.main();
}
