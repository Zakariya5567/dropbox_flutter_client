import 'dart:async';
import 'dart:io';
import 'package:flutter/services.dart';

typedef DropboxProgressCallback = void Function(
    int currentBytes, int totalBytes);

class _CallbackInfo {
  int filesize;
  DropboxProgressCallback? callback;

  _CallbackInfo(this.filesize, this.callback);
}

class Dropbox {
  static const MethodChannel _channel = const MethodChannel('dropbox');

  static int _callbackInt = 0;
  static Map<int, _CallbackInfo> _callbackMap = <int, _CallbackInfo>{};

  /// Initialize dropbox library
  /// init() should be called only once.
  static Future<Map<String, dynamic>> init(
      String clientId, String key, String secret) async {
    _channel
        .setMethodCallHandler(_handleMethodCall); // Set up method call handler
    try {
      final result = await _channel.invokeMethod(
          'init', {'clientId': clientId, 'key': key, 'secret': secret});

      // Return success or failure with a message
      return result != null
          ? {
              'success': result['success'] ?? false,
              'message': result['message'] ?? 'Initialization failed.',
            }
          : {
              'success': false,
              'message': 'Failed to receive a response from initialization.',
            };
    } catch (e) {
      print("Initialization failed: $e");
      return {
        'success': false,
        'message': 'Error during initialization: $e',
      };
    }
  }

  static Future<void> _handleMethodCall(MethodCall call) async {
    try {
      var args = call.arguments as List;
      var key = args[0];
      var bytes = args[1];

      if (_callbackMap.containsKey(key)) {
        final info = _callbackMap[key]!;
        if (info.callback != null) {
          if (info.filesize == 0 && args.length > 2) {
            info.filesize = args[2];
          }
          info.callback!(bytes, info.filesize);
        }
      }
    } catch (e) {
      print("Error handling method call: $e");
    }
  }

  /// Authorize using Dropbox app or web browser.

  static Future<Map<String, dynamic>> authorize() async {
    try {
      // Call the platform method and expect a map response
      final result =
          await _channel.invokeMethod<Map<dynamic, dynamic>>('authorize');

      if (result != null) {
        return {
          'success': result['success'] ?? false,
          'message': result['message'] ??
              'Authorization result received, but no message provided',
        };
      } else {
        return {
          'success': false,
          'message': 'No response from authorization',
        };
      }
    } catch (e) {
      // Catch any exception that occurs during method invocation and return an error response
      return {
        'success': false,
        'message': 'Authorization failed: $e',
      };
    }
  }

  /// Authorize using Dropbox app or web browser With offline access token
  static Future<Map<String, dynamic>> authorizePKCE(
      {required String clientId}) async {
    try {
      // Call the platform method and expect a map response
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
          'authorize', {'clientId': clientId});

      if (result != null) {
        return {
          'success': result['success'] ?? false,
          'message': result['message'] ??
              'Authorization result received, but no message provided',
        };
      } else {
        return {
          'success': false,
          'message': 'No response from authorization',
        };
      }
    } catch (e) {
      // Catch any exception that occurs during method invocation and return an error response
      return {
        'success': false,
        'message': 'Authorization failed: $e',
      };
    }
  }

  /// Authorize with AccessToken

  static Future<Map<String, dynamic>> authorizeWithAccessToken(
      String accessToken) async {
    try {
      // Call the platform method and expect a map response
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
          'authorizeWithAccessToken', {'accessToken': accessToken});

      if (result != null) {
        return {
          'success': result['success'] ?? false,
          'message': result['message'] ??
              'Authorization with access token succeeded, but no message provided',
        };
      } else {
        return {
          'success': false,
          'message': 'No response from authorization with access token',
        };
      }
    } catch (e) {
      // Catch any exception that occurs during method invocation and return an error response
      return {
        'success': false,
        'message': 'Authorization with access token failed: $e',
      };
    }
  }

  /// get Access Token after authorization.
  static Future<Map<String, dynamic>?> getAccessToken() async {
    try {
      // Attempt to invoke the platform method to retrieve the access token
      final result = await _channel.invokeMethod('getAccessToken');

      // If result is null, treat it as token not available
      if (result != null) {
        print("Access token not available.");
        return {
          'success': result['success'] ?? false,
          'message': result['message'] ??
              'Access token succeeded, but no message provided',
          'accessToken': result['accessToken'] ?? null,
        };
      } else {
        return {
          'success': false,
          'message': 'Failed to retrieved Access token.',
          'accessToken': null,
        };
      }
    } catch (e) {
      // Catch and log any exception that occurs during the method call
      print("Error retrieving access token: $e");

      // Return an error response
      return {
        'success': false,
        'message': 'Error retrieving access token.',
        'accessToken': null,
      };
    }
  }

  /// get folder/file list for path.

  /// Get folder/file list for the given path.
  static Future<Map<String, dynamic>> listFolder(String path) async {
    try {
      // Attempt to invoke the platform method to retrieve the folder list
      final result = await _channel.invokeMethod('listFolder', {'path': path});

      // Return the result if successful
      if (result != null) {
        return {
          'success': result['success'] ?? false,
          'message': result['message'] ?? 'Folder list retrieved failed.',
          'paths': result['paths'],
        };
      } else {
        // If no result, return a failure response
        return {
          'success': false,
          'message': 'Failed to retrieve folder list.',
          'paths': null,
        };
      }
    } catch (e) {
      // Catch and log any exception that occurs during the method call
      print("Error retrieving folder list: $e");

      // Return an error response
      return {
        'success': false,
        'message': 'Error retrieving folder list: $e',
        'data': null,
      };
    }
  }

  /// upload local file in filepath to dropboxpath.

  static Future<Map<String, dynamic>> upload(
      String filepath, String dropboxpath,
      [DropboxProgressCallback? callback]) async {
    try {
      // Get the file size and generate a unique callback key
      final fileSize = File(filepath).lengthSync();
      final key = ++_callbackInt;

      // Store callback info for tracking progress
      _callbackMap[key] = _CallbackInfo(fileSize, callback);

      // Attempt to invoke the platform method for uploading the file
      final result = await _channel.invokeMethod('upload', {
        'filepath': filepath,
        'dropboxpath': dropboxpath,
        'key': key,
      });

      // Remove the callback info after upload
      _callbackMap.remove(key);

      // Check the result and return appropriate success/failure message
      if (result != null) {
        return {
          'success': result['success'] ?? false,
          'message': result['message'] ?? 'Upload failed.',
        };
      } else {
        return {
          'success': false,
          'message': 'Failed to upload file.',
        };
      }
    } catch (e) {
      // Catch and log any exception that occurs during the upload process
      print("Error during file upload: $e");

      // Return an error response
      return {
        'success': false,
        'message': 'Error during file upload: $e',
      };
    }
  }

  /// download file from dropboxpath to local file(filepath).

  static Future<Map<String, dynamic>> download(
      String dropboxpath, String filepath,
      [DropboxProgressCallback? callback]) async {
    try {
      // Generate a unique callback key
      final key = ++_callbackInt;

      // Store callback info for progress tracking
      _callbackMap[key] = _CallbackInfo(0, callback);

      // Attempt to invoke the platform method for downloading the file
      final result = await _channel.invokeMethod('download', {
        'filepath': filepath,
        'dropboxpath': dropboxpath,
        'key': key,
      });

      // Remove the callback info after the download
      _callbackMap.remove(key);

      // Check the result and return appropriate success/failure message
      if (result != null) {
        return {
          'success': result['success'] ?? false,
          'message': result['message'] ?? 'Download failed.',
        };
      } else {
        return {
          'success': false,
          'message': 'Failed to download file.',
        };
      }
    } catch (e) {
      // Catch and log any exception during the download process
      print("Error during file download: $e");

      // Return an error response
      return {
        'success': false,
        'message': 'Error during file download: $e',
      };
    }
  }
}
