/*
 * This is the source code of Telegram for Android v. 7.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.tgnet;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.KeepAliveJob;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsManager implements Client.ClientDelegate {

    // --- НАЧАЛО МОДИФИКАЦИИ ---
    /**
     * Временное хранилище для ключа авторизации, который будет импортирован.
     * Это поле будет установлено методом setCustomAuthDataForNextInit.
     */
    private byte[] customAuthKeyToImport = null;

    /**
     * Временное хранилище для DC ID, который будет импортирован.
     * Это поле будет установлено методом setCustomAuthDataForNextInit.
     */
    private int customDcIdToImport = 0;
    // --- КОНЕЦ МОДИФИКАЦИИ ---

    public static final int ConnectionStateConnecting = 1;
    public static final int ConnectionStateConnected = 2;
    public static final int ConnectionStateUpdating = 3;

    public static final int ConnectionStateConnectingToProxy = 4;

    public static final int RequestFlagEnableUnauthorized = 1;
    public static final int RequestFlagFailOnServerErrors = 2;
    public static final int RequestFlagForceDownload = 4;
    public static final int RequestFlagInvokeAfter = 8;
    public static final int RequestFlagNeedQuickAck = 16;
    public static final int RequestFlagCanCompress = 32;
    public static final int RequestFlagTryDifferentDc = 64;
    public static final int RequestFlagWithoutLogin = 128;
    public static final int RequestFlagUseUnboundKey = 256;

    private static volatile ConnectionsManager[] instances = new ConnectionsManager[UserConfig.MAX_ACCOUNT_COUNT];
    public static ConnectionsManager getInstance(int num) {
        ConnectionsManager localInstance = instances[num];
        if (localInstance == null) {
            synchronized (ConnectionsManager.class) {
                localInstance = instances[num];
                if (localInstance == null) {
                    instances[num] = localInstance = new ConnectionsManager(num);
                }
            }
        }
        return localInstance;
    }

    private boolean appPaused = true;
    private boolean isUpdating;
    private int connectionState = ConnectionStateConnecting;
    private PowerManager.WakeLock wakeLock;
    private int currentAccount;
    private boolean wasConnected;
    private long lastPushPingTime;
    private boolean pushConnectionEnabled = true;
    private int clientBuildVersion;
    private static AtomicInteger requestToken = new AtomicInteger(1);

    private Client client;
    private int instanceNum;
    private long lastPauseTime = System.currentTimeMillis();

    public ConnectionsManager(final int account) {
        instanceNum = account;
        currentAccount = account;
        SharedPreferences preferences = ApplicationLoader.getApplicationContext().getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        clientBuildVersion = preferences.getInt("client_version", 0);

        try {
            PowerManager powerManager = (PowerManager) ApplicationLoader.getApplicationContext().getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "telegram-pushes");
            wakeLock.setReferenceCounted(false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        client = new Client(new File(ApplicationLoader.getFilesDirFixed(), "account" + currentAccount + "/tgnet.dat"), BuildVars.DEBUG_VERSION, BuildVars.LOGS_ENABLED, BuildVars.isMainApp, this);

        init(UserConfig.getInstance(currentAccount).getClientUserId(), ApplicationLoader.getAppVersion(), Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.RELEASE, LocaleController.getInstance().getSystemDefaultLocale().toString(), LocaleController.getInstance().getCurrentLocaleInfo() != null ? LocaleController.getInstance().getCurrentLocaleInfo().shortName : "", LocaleController.getInstance().getLangpackVersion(), new File(ApplicationLoader.getFilesDirFixed(), "account" + currentAccount + "/").getAbsolutePath(), new File(ApplicationLoader.getFilesDirFixed(), "logs/").getAbsolutePath(), true);

        BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkConnection();
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        ApplicationLoader.getApplicationContext().registerReceiver(networkStateReceiver, filter);

        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
            resumeNetworkMaybe();
        }
    }

    public long getPauseTime() {
        return lastPauseTime;
    }

    public void setAppPaused(boolean value, boolean byScreenState) {
        if (client == null) {
            return;
        }
        if (!byScreenState) {
            appPaused = value;
            FileLog.d("app paused = " + value);
        }
        if (value) {
            lastPauseTime = System.currentTimeMillis();
        }
        client.onAppPause();
        if (!isUpdating) {
            if (value) {
                if (lastPauseTime - lastPushPingTime > 25000) {
                    client.onPause();
                }
            } else {
                client.onResume();
            }
        }
    }

    public void resumeNetworkMaybe() {
        if (client == null) {
            return;
        }
        client.onResume();
    }

    public void updateDcSettings() {
        if (client == null) {
            return;
        }
        client.updateDcSettings(0);
    }

    public int getConnectionState() {
        return connectionState;
    }

    public int sendRequest(final TLObject object, final RequestDelegate onComplete) {
        return sendRequest(object, onComplete, null, 0);
    }

    public int sendRequest(final TLObject object, final RequestDelegate onComplete, final int flags) {
        return sendRequest(object, onComplete, null, flags, 0, 0, true);
    }

    public int sendRequest(final TLObject object, final RequestDelegate onComplete, final QuickAckDelegate onQuickAck, final int flags) {
        return sendRequest(object, onComplete, onQuickAck, flags, 0, 0, true);
    }

    public int sendRequest(final TLObject object, final RequestDelegate onComplete, int connectionType) {
        return sendRequest(object, onComplete, null, 0, connectionType, 0, true);
    }

    public int sendRequest(final TLObject object, final RequestDelegate onComplete, final QuickAckDelegate onQuickAck, final int flags, int connectionType) {
        return sendRequest(object, onComplete, onQuickAck, flags, connectionType, 0, true);
    }

    public int sendRequest(TLObject object, RequestDelegate onComplete, QuickAckDelegate onQuickAck, int flags, int dcId, int connectionType, boolean immediate) {
        if (client == null) {
            return 0;
        }
        final int token = requestToken.getAndIncrement();
        client.sendRequest(object, (response, error, responseCode) -> {
            if (onComplete != null) {
                onComplete.run(response, error);
            }
        }, onQuickAck, flags, dcId, connectionType, immediate, token);
        return token;
    }

    public void cancelRequest(int token, boolean notifyServer) {
        if (client == null) {
            return;
        }
        client.cancelRequest(token, notifyServer);
    }

    public void cleanUp(boolean reset) {
        if (client == null) {
            return;
        }
        client.cleanup(reset);
    }

    public void cancelRequestsForGuid(final int guid) {
        if (client == null) {
            return;
        }
        client.cancelRequestsForGuid(guid);
    }

    public void bindRequestToGuid(final int requestToken, final int guid) {
        if (client == null) {
            return;
        }
        client.bindRequestToGuid(requestToken, guid);
    }

    public void applyCountryPortNumber(String country) {
        if (client == null) {
            return;
        }
        try {
            if ("ir".equals(country) || "cn".equals(country)) {
                client.setGeyNoPenalty(true);
            } else {
                client.setGeyNoPenalty(false);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setProxySettings(boolean enabled, String address, int port, String username, String password, String secret) {
        if (client == null) {
            return;
        }
        client.setProxySettings(enabled, address, port, username, password, secret);
        checkConnection();
    }

    public void checkConnection() {
        if (client == null) {
            return;
        }
        client.checkConnection();
    }

    public long getPushRequestTime() {
        return client.getPushRequestTime();
    }

    public void setPushConnectionEnabled(boolean value) {
        pushConnectionEnabled = value;
    }

    public void init(long userId, int appVersion, String systemLangCode, String deviceModel, String systemVersion, String langPack, String langPackVersion, String configPath, String logPath, boolean log) {
        if (client == null) {
            return;
        }
        TdApi.TdlibParameters parameters = new TdApi.TdlibParameters();
        parameters.useTestDc = BuildVars.useTestBackend;
        parameters.apiId = BuildVars.API_ID;
        parameters.apiHash = BuildVars.API_HASH;
        parameters.systemLanguageCode = systemLangCode;
        parameters.deviceModel = deviceModel;
        parameters.systemVersion = systemVersion;
        parameters.applicationVersion = appVersion + "";
        parameters.useSecretChats = true;
        parameters.useMessageDatabase = true;

        parameters.databaseDirectory = configPath;
        parameters.filesDirectory = configPath;
        parameters.useFileDatabase = true;
        parameters.enableStorageOptimizer = true;

        if (BuildVars.LOGS_ENABLED) {
            parameters.useChatInfoDatabase = true;
        }

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("proxy_enabled", false)) {
            String address = sharedPreferences.getString("proxy_ip", "");
            String secret = sharedPreferences.getString("proxy_secret", "");
            int port = sharedPreferences.getInt("proxy_port", 1080);
            String user = sharedPreferences.getString("proxy_user", "");
            String pass = sharedPreferences.getString("proxy_pass", "");
            try {
                if (secret.length() > 0) {
                    parameters.proxy = new TdApi.Proxy(address, port, true, new TdApi.ProxyTypeMtproto(secret));
                } else {
                    parameters.proxy = new TdApi.Proxy(address, port, false, new TdApi.ProxyTypeSocks5(user, pass));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        
        // --- НАЧАЛО МОДИФИКАЦИИ ---
        // Это место, где должны использоваться импортируемые данные.
        // Без модификации C++ кода TDLib для добавления специальной функции
        // (условно `native_importAuthKey`), этот блок не будет иметь эффекта.
        // Он оставлен здесь, чтобы показать, где должна происходить интеграция.
        if (customAuthKeyToImport != null && customDcIdToImport != 0) {
            FileLog.d("ConnectionsManager[" + currentAccount + "]: Attempting to use custom auth data for DC " + customDcIdToImport + ". THIS REQUIRES C++ MODIFICATIONS TO WORK.");
            
            // В идеальном мире здесь был бы вызов нативной функции:
            // native_importAuthKey(currentAccount, customDcIdToImport, customAuthKeyToImport);
            
            // Сбрасываем данные после попытки, чтобы они не использовались повторно.
            customAuthKeyToImport = null;
            customDcIdToImport = 0;
        }
        // --- КОНЕЦ МОДИФИКАЦИИ ---

        client.sendRequest(parameters, (response, error) -> {
            if (error != null) {
                if (error.code == 400 && error.message != null && error.message.contains("APPLICATION_VERSION_TOO_OLD")) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.appUpdateAvailable);
                }
            }
        });
    }

    @Override
    public void onUpdate() {
        Utilities.stageQueue.postRunnable(() -> {
            if (UserConfig.getInstance(currentAccount).isClientActivated()) {
                AccountInstance.getInstance(currentAccount).getMessagesController().update();
            }
        });
    }

    @Override
    public void onSessionCreated() {
        Utilities.stageQueue.postRunnable(() -> StatsController.getInstance(currentAccount).checkStats());
    }

    @Override
    public void onConnectionStateChanged(int state) {
        int oldState = connectionState;
        connectionState = state;
        if (oldState != state) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdateConnectionState, currentAccount);
        }
    }

    @Override
    public void onInternalPushReceived() {
        resumeNetworkMaybe();
    }
    @Override
    public void onLogout() {
        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
            UserConfig.getInstance(currentAccount).setIsWaitingForPasscode(false);
            AccountInstance.getInstance(currentAccount).getMessagesController().performLogout(0);
        }
    }

    @Override
    public void onTlsError() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", "tls");
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            int count = preferences.getInt("bad_tls_count", 0) + 1;
            preferences.edit().putInt("bad_tls_count", count).commit();
            if (count >= 3) {
                jsonObject.put("force", true);
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("send tls error to stats " + jsonObject.toString());
            }
            StatsController.getInstance(currentAccount).sendStats(jsonObject.toString());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String getProxyIp() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).getString("proxy_ip", "");
    }

    @SuppressLint("ApplySharedPref")
    public static void setLangpackVersion(int version) {
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).edit().putInt("lang_version", version).commit();
    }

    public static int getLangpackVersion() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).getInt("lang_version", 0);
    }

    public static void setRegId(String id, int type, String token) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        preferences.edit().putString("push_reg_id_v2", id).putInt("push_type_v2", type).putString("push_reg_token_v2", token).commit();
    }

    public static String getRegId() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).getString("push_reg_id_v2", "");
    }

    public static int getPushType() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).getInt("push_type_v2", 10);
    }

s
    public static String getPushToken() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).getString("push_reg_token_v2", "");
    }

    public static boolean isNetworkOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo != null && (netInfo.isConnectedOrConnecting() || netInfo.isAvailable())) {
                return true;
            }

            netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            } else {
                netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
        return false;
    }

    public static boolean isRoaming() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo != null) {
                return netInfo.isRoaming();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectedToWiFi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (netInfo != null && netInfo.isConnected()) {
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }
    
    // --- НАЧАЛО МОДИФИКАЦИИ ---
    /**
     * Устанавливает кастомные данные авторизации, которые будут использованы
     * при следующей инициализации клиента TDLib. Этот метод должен быть вызван
     * из UserConfig перед вызовом `init()`.
     *
     * @param dcId ID дата-центра.
     * @param authKey 256-байтный ключ авторизации.
     */
    public void setCustomAuthDataForNextInit(int dcId, byte[] authKey) {
        this.customDcIdToImport = dcId;
        this.customAuthKeyToImport = authKey;
        FileLog.d("ConnectionsManager[" + currentAccount + "]: Custom auth data has been set. It will be used on next init. DC_ID: " + dcId);
    }
    // --- КОНЕЦ МОДИФИКАЦИИ ---
}