import 'flutter_biometric_auth_platform_interface.dart';

class FlutterBiometricAuth {
  Future<bool?> authenticate() {
    return FlutterBiometricAuthPlatform.instance.authenticate();
  }
}
