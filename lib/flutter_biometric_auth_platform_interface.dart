import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'flutter_biometric_auth_method_channel.dart';

abstract class FlutterBiometricAuthPlatform extends PlatformInterface {
  FlutterBiometricAuthPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterBiometricAuthPlatform _instance =
      MethodChannelFlutterBiometricAuth();

  static FlutterBiometricAuthPlatform get instance => _instance;

  static set instance(FlutterBiometricAuthPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<bool?> authenticate() {
    throw UnimplementedError('authenticate() has not been implemented.');
  }
}
