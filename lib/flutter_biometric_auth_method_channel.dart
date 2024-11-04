import 'package:flutter/services.dart';
import 'flutter_biometric_auth_platform_interface.dart';

class MethodChannelFlutterBiometricAuth extends FlutterBiometricAuthPlatform {
  final methodChannel = const MethodChannel('flutter_biometric_auth');

  @override
  Future<bool?> authenticate() async {
    final result = await methodChannel.invokeMethod<bool>('authenticate');
    return result;
  }
}
