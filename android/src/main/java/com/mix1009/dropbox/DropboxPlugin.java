package com.mix1009.dropbox;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxHost;
import com.dropbox.core.json.JsonReadException;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuth;
import com.dropbox.core.android.Auth;
import com.dropbox.core.android.AuthActivity;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetTemporaryLinkResult;
import com.dropbox.core.v2.files.ListFolderResult;

import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.UploadBuilder;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.users.FullAccount;
import com.dropbox.core.http.OkHttp3Requestor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** DropboxPlugin */
public class DropboxPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  // The channel name remains the same
  private static final String CHANNEL_NAME = "dropbox";
  private static Activity activity;
  private MethodChannel channel;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    // Updated to use the FlutterPluginBinding for proper setup
    setupChannel(binding.getBinaryMessenger(), binding.getApplicationContext());
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    // Clean up the channel when detached from the engine
    teardownChannel();
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    // Set activity on attachment to activity
    DropboxPlugin.activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    // Handle activity detachment for configuration changes
    DropboxPlugin.activity = null;
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    // Reattach the activity in case of config changes
    DropboxPlugin.activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivity() {
    // Handle detachment from activity
    DropboxPlugin.activity = null;
  }

  private void setupChannel(BinaryMessenger messenger, Context context) {
    // Set up the MethodChannel for communication with Dart
    channel = new MethodChannel(messenger, CHANNEL_NAME);
    channel.setMethodCallHandler(this);
  }

  private void teardownChannel() {
    // Tear down the MethodChannel
    if (channel != null) {
      channel.setMethodCallHandler(null);
      channel = null;
    }
  }

  // Static Dropbox-related variables (no change required for these)
  protected static DbxRequestConfig sDbxRequestConfig;
  protected static DbxClientV2 client;
  protected static DbxWebAuth webAuth;
  protected static String accessToken;
  protected static DbxCredential credentials;
  protected static String clientId;
  protected static DbxAppInfo appInfo;


  /**
   * Checks if the Dropbox client is initialized and authenticates the user if necessary.
   *
   * @param result The result callback to send success or error responses back to the Flutter side.
   * @return true if the client is authenticated and initialized, false otherwise.
   */
  boolean checkClient(@NonNull Result result) {
    // Check if the Dropbox client is already initialized
    if (client != null) {
      // If the client is already initialized, no need to authenticate again
      return true;
    }

    // Retrieve the OAuth2 authentication token
    String authToken = Auth.getOAuth2Token();


    // If an authorization token is found, initialize the Dropbox client
    if (authToken != null) {
      // Create a new request configuration with the app's clientId
      sDbxRequestConfig = DbxRequestConfig.newBuilder(DropboxPlugin.clientId)  // Access clientId as a static field
              .withHttpRequestor(new OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))  // Use OkHttp for network requests
              .build();

      // Initialize the Dropbox client with the config and the auth token
      client = new DbxClientV2(sDbxRequestConfig, authToken);

      // Set the access token in the static field for future use
      DropboxPlugin.accessToken = authToken;  // Set the access token using the static field

      // Return true to indicate successful client initialization
      return true;
    }

    // If no authentication token is found, send an error response to the Flutter side
    result.error("UNAUTHORIZED", "Client not logged in. Authorization token is missing.", null);
    return false;
  }


  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {

      case "init": {
        try {
          // Retrieve the method arguments
          String clientId = call.argument("clientId");
          String key = call.argument("key");
          String secret = call.argument("secret");

          // Initialize the Dropbox client
          DropboxPlugin.clientId = clientId;
          appInfo = new DbxAppInfo(key, secret);

          // Ensure the clientId is not null
          if (DropboxPlugin.clientId == null) {
            throw new Exception("Client ID is null");
          }

          // Set up the Dropbox request configuration
          sDbxRequestConfig = DbxRequestConfig.newBuilder(DropboxPlugin.clientId)
                  .withHttpRequestor(new OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
                  .build();

          // Return success response
          Map<String, Object> successResponse = new HashMap<>();
          successResponse.put("success", true);
          successResponse.put("message", "Initialization successful.");
          result.success(successResponse);  // Return success message
        } catch (Exception e) {
          // Handle any exception and return failure response
          Map<String, Object> errorResponse = new HashMap<>();
          errorResponse.put("success", false);
          errorResponse.put("message", "Initialization failed: " + e.getMessage());
          result.success(errorResponse);  // Return failure message
        }
        break;
      }


      case "authorizePKCE":
        try {
          // Start OAuth2 authentication
          String clientId = call.argument("clientId");

          sDbxRequestConfig = DbxRequestConfig.newBuilder(clientId)
                  .withHttpRequestor(new OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
                  .build();

          Auth.startOAuth2PKCE(
                  DropboxPlugin.activity ,
                  appInfo.getKey(),
                  sDbxRequestConfig,
                  DbxHost.DEFAULT
          );


          // Return success response
          Map<String, Object> successResponse = new HashMap<>();
          successResponse.put("success", true);
          successResponse.put("message", "Authorization started successfully.");
          result.success(successResponse);  // Return success message
        } catch (Exception e) {
          // Handle any exceptions that occur during the authorization process
          Map<String, Object> errorResponse = new HashMap<>();
          errorResponse.put("success", false);
          errorResponse.put("message", "Authorization failed: " + e.getMessage());
          result.success(errorResponse);  // Return failure message
        }
        break;

     case "authorize":
        try {
          // Start OAuth2 authentication

          Auth.startOAuth2Authentication(DropboxPlugin.activity, appInfo.getKey());

          // Return success response
          Map<String, Object> successResponse = new HashMap<>();
          successResponse.put("success", true);
          successResponse.put("message", "Authorization started successfully.");
          result.success(successResponse);  // Return success message
        } catch (Exception e) {
          // Handle any exceptions that occur during the authorization process
          Map<String, Object> errorResponse = new HashMap<>();
          errorResponse.put("success", false);
          errorResponse.put("message", "Authorization failed: " + e.getMessage());
          result.success(errorResponse);  // Return failure message
        }
        break;

     case "authorizeWithAccessToken":
        try {
          // Retrieve the access token from the method call arguments
          String argAccessToken = call.argument("accessToken");

          // Build the Dropbox client configuration
          sDbxRequestConfig = DbxRequestConfig.newBuilder(DropboxPlugin.clientId)
                  .withHttpRequestor(new OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
                  .build();

          // Create the Dropbox client with the provided access token
          client = new DbxClientV2(sDbxRequestConfig, argAccessToken);
          accessToken = argAccessToken;

          // Prepare the success response
          Map<String, Object> successResponse = new HashMap<>();
          successResponse.put("success", true);
          successResponse.put("message", "Authorization with access token succeeded.");
          result.success(successResponse);  // Return success response
        } catch (Exception e) {
          // Handle any exception that occurs during the authorization process
          Map<String, Object> errorResponse = new HashMap<>();
          errorResponse.put("success", false);  // Indicate failure
          errorResponse.put("message", "Authorization failed: " + e.getMessage());
          result.success(errorResponse);  // Return failure response with error message
        }
        break;
      case "getAccessToken":
        try {
          String token = Auth.getOAuth2Token();
          // Prepare success result with token
          Map<String, Object> successResult = new HashMap<>();
          successResult.put("success", true);
          successResult.put("message", "Access token retrieved successfully.");
          successResult.put("accessToken", token);

          result.success(successResult);  // Return the success response with token
        } catch (Exception e) {
          // Handle error if something goes wrong
          Map<String, Object> errorResult = new HashMap<>();
          errorResult.put("success", false);
          errorResult.put("message", "Failed to retrieve access token: " + e.getMessage());
          errorResult.put("accessToken", null);

          result.success(errorResult);  // Return the error response
        }
        break;


      case "listFolder": {
        String path = call.argument("path");
        if (!checkClient(result)) return;  // Ensure client is authenticated
        (new ListFolderTask(result)).execute(path);
        break;
      }

      case "upload": {
        String filepath = call.argument("filepath");
        String dropboxpath = call.argument("dropboxpath");
        Integer key = call.argument("key");  // Use Integer to allow null checks
        if (key == null || filepath == null || dropboxpath == null) {
          result.error("INVALID_ARGUMENT", "Filepath, dropboxpath, or key is missing", null);
          return;
        }
        if (!checkClient(result)) return;  // Ensure client is authenticated
        (new UploadTask(channel, key, result)).execute(filepath, dropboxpath);
        break;
      }

      case "download": {
        String filepath = call.argument("filepath");
        String dropboxpath = call.argument("dropboxpath");
        Integer key = call.argument("key");  // Use Integer to allow null checks
        if (key == null || filepath == null || dropboxpath == null) {
          result.error("INVALID_ARGUMENT", "Filepath, dropboxpath, or key is missing", null);
          return;
        }
        if (!checkClient(result)) return;  // Ensure client is authenticated
        (new DownloadTask(channel, key, result)).execute(dropboxpath, filepath);
        break;
      }


      default:
        result.notImplemented();  // Return not implemented if the method is not supported
        break;
    }
  }

  static class ListFolderTask {
    Result result;
    List<Object> paths = new ArrayList<>();
    private final ExecutorService executor;

    // Constructor to initialize the task
    public ListFolderTask(Result _result) {
      result = _result;
      executor = Executors.newSingleThreadExecutor();  // Background executor
    }

    // Execute method to start the task
    public void execute(String folderPath) {
      executor.submit(() -> {
        try {
          ListFolderResult listFolderResult = DropboxPlugin.client.files().listFolder(folderPath);
          String pattern = "yyyyMMdd HHmmss";
          @SuppressLint("SimpleDateFormat") DateFormat df = new SimpleDateFormat(pattern);

          // Loop through folder results
          while (true) {
            for (Metadata metadata : listFolderResult.getEntries()) {
              Map<String, Object> map = new HashMap<>();
              map.put("name", metadata.getName());
              map.put("pathLower", metadata.getPathLower());
              map.put("pathDisplay", metadata.getPathDisplay());

              if (metadata instanceof FileMetadata) {
                FileMetadata fileMetadata = (FileMetadata) metadata;
                map.put("filesize", fileMetadata.getSize());
                map.put("clientModified", df.format(fileMetadata.getClientModified()));
                map.put("serverModified", df.format(fileMetadata.getServerModified()));
              }

              paths.add(map);
            }

            if (!listFolderResult.getHasMore()) break;

            listFolderResult = DropboxPlugin.client.files().listFolderContinue(listFolderResult.getCursor());
          }

          // Post success result on the main thread (returns true and paths)
          Map<String, Object> successResult = new HashMap<>();
          successResult.put("success", true);
          successResult.put("message", "Folder listing successful.");
          successResult.put("paths", paths);

          new Handler(Looper.getMainLooper()).post(() -> result.success(successResult));

        } catch (DbxException e) {
          e.printStackTrace();

          // Post error result on the main thread (returns false and error message)
          Map<String, Object> errorResult = new HashMap<>();
          errorResult.put("success", false);
          errorResult.put("message", "Failed to list folder: " + e.getMessage());

          new Handler(Looper.getMainLooper()).post(() -> result.success(errorResult));
        }
      });
    }
  }


  static class UploadTask {
    Result result;
    int key;
    MethodChannel channel;
    private final ExecutorService executor;

    // Constructor to initialize the task
    public UploadTask(MethodChannel _channel, int _key, Result _result) {
      channel = _channel;
      key = _key;
      result = _result;
      executor = Executors.newSingleThreadExecutor();  // Background executor
    }

    // Execute method to start the task
    public void execute(String localPath, String remotePath) {
      executor.submit(() -> {
        try {
          InputStream in = null;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            in = Files.newInputStream(Paths.get(localPath));
          }
          UploadBuilder uploadBuilder = DropboxPlugin.client.files()
                  .uploadBuilder(remotePath)
                  .withMode(WriteMode.OVERWRITE)
                  .withAutorename(true)
                  .withMute(false);

          uploadBuilder.uploadAndFinish(in, bytesWritten -> {
            new Handler(Looper.getMainLooper()).post(() -> {
              List<Long> ret = new ArrayList<>();
              ret.add((long) key);
              ret.add(bytesWritten);
              channel.invokeMethod("progress", ret, null);
            });
          });

          // Success response
          Map<String, Object> successResult = new HashMap<>();
          successResult.put("success", true);
          successResult.put("message", "Upload completed successfully.");
          new Handler(Looper.getMainLooper()).post(() -> result.success(successResult));

        } catch (DbxException | IOException e) {
          e.printStackTrace();

          // Error response
          Map<String, Object> errorResult = new HashMap<>();
          errorResult.put("success", false);
          errorResult.put("message", "Upload failed: " + e.getMessage());
          new Handler(Looper.getMainLooper()).post(() -> result.success(errorResult));
        }
      });
    }
  }

  class DownloadTask {
    Result result;
    int key;
    long fileSize;
    MethodChannel channel;
    List<Object> paths = new ArrayList<>();
    private final ExecutorService executor;

    // Constructor to initialize the task
    public DownloadTask(MethodChannel _channel, int _key, Result _result) {
      channel = _channel;
      key = _key;
      result = _result;
      executor = Executors.newSingleThreadExecutor();  // Background executor
    }

    // Execute method to start the task
    public void execute(String dropboxPath, String localPath) {
      executor.submit(() -> {
        try {
          fileSize = 0;
          Metadata metadata = DropboxPlugin.client.files().getMetadata(dropboxPath);

          if (metadata instanceof FileMetadata) {
            FileMetadata fileMetadata = (FileMetadata) metadata;
            fileSize = fileMetadata.getSize();
          }

          DbxDownloader<FileMetadata> downloader = DropboxPlugin.client.files().download(dropboxPath);
          OutputStream out = null;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            out = Files.newOutputStream(Paths.get(localPath));
          }

          downloader.download(out, bytesRead -> {
            new Handler(Looper.getMainLooper()).post(() -> {
              List<Long> ret = new ArrayList<>();
              ret.add((long) key);
              ret.add(bytesRead);
              ret.add(fileSize);
              channel.invokeMethod("progress", ret, null);
            });
          });

          // Success response
          Map<String, Object> successResult = new HashMap<>();
          successResult.put("success", true);
          successResult.put("message", "Download completed successfully.");
          new Handler(Looper.getMainLooper()).post(() -> result.success(successResult));

        } catch (DbxException | IOException e) {
          e.printStackTrace();

          // Error response
          Map<String, Object> errorResult = new HashMap<>();
          errorResult.put("success", false);
          errorResult.put("message", "Download failed: " + e.getMessage());
          new Handler(Looper.getMainLooper()).post(() -> result.success(errorResult));
        }
      });
    }
  }

}

