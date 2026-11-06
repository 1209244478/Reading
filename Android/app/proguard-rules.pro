# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===== AirPlay（UxPlay JNI）=====
# 原生库通过固定类名/方法名查找 NativeBridge 与 RaopCallbackHandler 回调，禁止混淆
-keep class io.github.jqssun.airplay.bridge.** { *; }
-keep class com.wrz.reading.ui.read.airplay.AirPlayReceiverManager { *; }
-keepclassmembers class com.wrz.reading.ui.read.airplay.AirPlayReceiverManager {
    void onVideoData(byte[], long, boolean);
    void onAudioFormat(int, int, boolean);
    void onVideoSize(float, float, float, float);
    void onClientVolume(float);
    float onClientVolumeQuery();
    void onAudioTeardown();
    void onConnectionInit();
    void onConnectionDestroy();
    void onConnectionReset(int);
    void onDisplayPin(java.lang.String);
    void onMetadata(byte[]);
    void onCoverArt(byte[]);
    void onProgress(long, long, long);
    void onDacpId(java.lang.String, java.lang.String);
    void onMirrorRunning(boolean);
    void onVideoPlay(java.lang.String, float);
    void onVideoScrub(float);
    void onVideoRate(float);
    void onVideoStop();
    void onVideoSessionPoll();
    void onLog(java.lang.String);
}