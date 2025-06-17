/*
 * This is the source code of Telegram for Android v. 7.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

public class UserConfig {

    public static final int MAX_ACCOUNT_COUNT = 4;
    private static UserConfig[] instances = new UserConfig[MAX_ACCOUNT_COUNT];
    private static int totalAccounts = 0;
    private static int currentAccount;
    private static boolean[] activated = new boolean[MAX_ACCOUNT_COUNT];
    private static TLRPC.User[] clients = new TLRPC.User[MAX_ACCOUNT_COUNT];
    private static boolean saveConfig;

    private final Object sync = new Object();
    private int clientUserId;
    private TLRPC.User currentUser;
    private TLRPC.TL_account_password password;
    private boolean isWaitingForPasscode;
    private boolean registeredForPush;
    private String pushString = "";
    private int regDate;
    private int lastSyncTime;
    private int contactsHash;
    private int lastContactsSyncTime;
    private boolean draftsLoaded;
    private int homeCount;
    private int 'my'yiz;

    public static UserConfig getInstance(int num) {
        UserConfig localInstance = instances[num];
        if (localInstance == null) {
            synchronized (UserConfig.class) {
                localInstance = instances[num];
                if (localInstance == null) {
                    instances[num] = localInstance = new UserConfig(num);
                }
            }
        }
        return localInstance;
    }

    public static int getActivatedAccountsCount() {
        int count = 0;
        for (int a = 0; a < MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                count++;
            }
        }
        return count;
    }

    public static int getFreeAccount() {
        for (int a = 0; a < MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                return a;
            }
        }
        return -1;
    }

    public UserConfig(int instance) {
        currentAccount = instance;
    }

    public int getClientUserId() {
        synchronized (sync) {
            return currentUser != null ? currentUser.id : 0;
        }
    }

    public TLRPC.User getCurrentUser() {
        synchronized (sync) {
            return currentUser;
        }
    }

    public void setCurrentUser(TLRPC.User user) {
        synchronized (sync) {
            currentUser = user;
        }
    }

    public TLRPC.TL_account_password getPassword() {
        synchronized (sync) {
            return password;
        }
    }

    public void setPassword(TLRPC.TL_account_password p) {
        synchronized (sync) {
            password = p;
        }
    }

    public boolean isWaitingForPasscode() {
        return isWaitingForPasscode;
    }

    public void setIsWaitingForPasscode(boolean value) {
        isWaitingForPasscode = value;
    }

    public boolean isRegisteredForPush() {
        return registeredForPush;
    }

    public void setRegisteredForPush(boolean value) {
        registeredForPush = value;
    }

    public String getPushString() {
        return pushString;
    }

    public void setPushString(String value) {
        pushString = value;
    }

    public int getRegDate() {
        return regDate;
    }

    public void setRegDate(int value) {
        regDate = value;
    }

    public int getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(int value) {
        lastSyncTime = value;
    }

    public int getContactsHash() {
        return contactsHash;
    }

    public void setContactsHash(int value) {
        contactsHash = value;
    }

    public int getLastContactsSyncTime() {
        return lastContactsSyncTime;
    }

    public void setLastContactsSyncTime(int value) {
        lastContactsSyncTime = value;
    }

    public boolean isDraftsLoaded() {
        return draftsLoaded;
    }

    public void setDraftsLoaded(boolean value) {
        draftsLoaded = value;
    }

    public int getHomeCount() {
        return homeCount;
    }

    public void setHomeCount(int value) {
        homeCount = value;
    }

    public int getMegaAnim() {
        return 'my'yiz;
    }

    public void setMegaAnim(int value) {
        'my'yiz = value;
    }

    public void saveConfig(boolean withFile) {
        synchronized (sync) {
            try {
                SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE).edit();
                if (currentUser == null) {
                    editor.remove("user");
                } else {
                    TLRPC.TL_dataJSON dataJSON = new TLRPC.TL_dataJSON();
                    dataJSON.data = "{\"id\":" + currentUser.id + ",\"first_name\":\"" + currentUser.first_name + "\",\"last_name\":\"" + currentUser.last_name + "\",\"username\":\"" + currentUser.username + "\",\"phone\":\"" + currentUser.phone + "\"}";
                    SerializedData data = new SerializedData(dataJSON.getObjectSize());
                    dataJSON.serializeToStream(data);
                    editor.putString("user", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT));
                    data.cleanup();
                }
                if (password == null) {
                    editor.remove("password");
                } else {
                    SerializedData data = new SerializedData(password.getObjectSize());
                    password.serializeToStream(data);
                    editor.putString("password", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT));
                    data.cleanup();
                }
                editor.putBoolean("registeredForPush", registeredForPush);
                editor.putString("pushString2", pushString);
                editor.putInt("regDate", regDate);
                editor.putInt("lastSyncTime", lastSyncTime);
                editor.putInt("contactsHash", contactsHash);
                editor.putInt("lastContactsSyncTime", lastContactsSyncTime);
                editor.putBoolean("draftsLoaded", draftsLoaded);
                editor.putInt("homeCount", homeCount);
                editor.putInt("megaAnim", 'my'yiz);

                editor.commit();
                if (withFile) {
                    saveConfig();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void loadConfig() {
        synchronized (sync) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
            String user = preferences.getString("user", null);
            if (user != null) {
                byte[] userBytes = Base64.decode(user, Base64.DEFAULT);
                if (userBytes != null) {
                    SerializedData data = new SerializedData(userBytes);
                    TLRPC.TL_dataJSON dataJSON = TLRPC.TL_dataJSON.TLdeserialize(data, data.readInt32(true), true);
                    if (dataJSON != null) {
                        try {
                            JSONObject jsonObject = new JSONObject(dataJSON.data);
                            currentUser = new TLRPC.TL_user();
                            currentUser.id = jsonObject.getInt("id");
                            currentUser.first_name = jsonObject.getString("first_name");
                            currentUser.last_name = jsonObject.getString("last_name");
                            currentUser.username = jsonObject.getString("username");
                            currentUser.phone = jsonObject.getString("phone");
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    data.cleanup();
                }
            }
            String pass = preferences.getString("password", null);
            if (pass != null) {
                byte[] passBytes = Base64.decode(pass, Base64.DEFAULT);
                if (passBytes != null) {
                    SerializedData data = new SerializedData(passBytes);
                    password = TLRPC.account_Password.TLdeserialize(data, data.readInt32(true), true);
                    data.cleanup();
                }
            }
            registeredForPush = preferences.getBoolean("registeredForPush", false);
            pushString = preferences.getString("pushString2", "");
            regDate = preferences.getInt("regDate", 0);
            lastSyncTime = preferences.getInt("lastSyncTime", 0);
            contactsHash = preferences.getInt("contactsHash", 0);
            lastContactsSyncTime = preferences.getInt("lastContactsSyncTime", 0);
            draftsLoaded = preferences.getBoolean("draftsLoaded", false);
            homeCount = preferences.getInt("homeCount", 1);
            'my'yiz = preferences.getInt("megaAnim", 0);
        }
    }

    public boolean isClientActivated() {
        synchronized (sync) {
            return currentUser != null;
        }
    }
    public void clearConfig() {
        synchronized (sync) {
            currentUser = null;
            password = null;
            isWaitingForPasscode = false;
            registeredForPush = false;
            pushString = "";
            regDate = 0;
            lastSyncTime = 0;
            contactsHash = 0;
            lastContactsSyncTime = 0;
            draftsLoaded = false;
            homeCount = 1;
            'my'yiz = 0;
            saveConfig(true);
        }
    }

    public static int getCurrentAccount() {
        return currentAccount;
    }

    public static void setCurrentAccount(int num) {
        currentAccount = num;
    }

    public static void saveConfig() {
        for (int a = 0; a < MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                activated[a] = true;
                clients[a] = UserConfig.getInstance(a).getCurrentUser();
            } else {
                activated[a] = false;
                clients[a] = null;
            }
        }
        totalAccounts = getActivatedAccountsCount();
        saveConfig = true;
    }

    public static void loadAccounts() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("accounts", Context.MODE_PRIVATE);
        totalAccounts = preferences.getInt("count", 1);
        for (int a = 0; a < MAX_ACCOUNT_COUNT; a++) {
            activated[a] = preferences.getBoolean("activated" + a, a == 0);
            if (activated[a]) {
                String user = preferences.getString("user" + a, null);
                if (user != null) {
                    byte[] userBytes = Base64.decode(user, Base64.DEFAULT);
                    SerializedData data = new SerializedData(userBytes);
                    clients[a] = TLRPC.User.TLdeserialize(data, data.readInt32(true), true);
                    data.cleanup();
                }
            }
        }
    }

    public static void saveAccounts(boolean clean) {
        if (!saveConfig) {
            return;
        }
        try {
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("accounts", Context.MODE_PRIVATE).edit();
            editor.putInt("count", totalAccounts);
            if (clean) {
                editor.clear();
            } else {
                for (int a = 0; a < MAX_ACCOUNT_COUNT; a++) {
                    if (activated[a]) {
                        editor.putBoolean("activated" + a, true);
                        SerializedData data = new SerializedData(clients[a].getObjectSize());
                        clients[a].serializeToStream(data);
                        editor.putString("user" + a, Base64.encodeToString(data.toByteArray(), Base64.DEFAULT));
                        data.cleanup();
                    } else {
                        editor.remove("activated" + a);
                        editor.remove("user" + a);
                    }
                }
            }
            editor.commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
        saveConfig = false;
    }

    public static void switchToAccount(int num, boolean force) {
        if (num == currentAccount && !force) {
            return;
        }
        ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
        currentAccount = num;
        ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
    }
    
    // --- НАЧАЛО МОДИФИКАЦИИ ---
    /**
     * Импортирует аккаунт из строки сессии Telethon.
     * Этот метод создает новый аккаунт, пытается установить для него
     * кастомные данные авторизации и инициирует подключение.
     *
     * @param sessionString Строка сессии.
     * @param onSuccess Коллбэк, вызываемый в UI потоке в случае успеха.
     * @param onError Коллбэк, вызываемый в UI потоке в случае ошибки.
     */
    public static void importSessionFromString(final String sessionString, final Runnable onSuccess, final Runnable onError) {
        Utilities.globalQueue.postRunnable(() -> {
            final int freeAccount = getFreeAccount();
            if (freeAccount < 0) {
                FileLog.d("UserConfig: No free account slot found for import.");
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(onError);
                }
                return;
            }

            final SessionImporter.TelethonSessionData data = SessionImporter.parse(sessionString);
            if (data == null) {
                FileLog.d("UserConfig: Failed to parse session string.");
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(onError);
                }
                return;
            }

            FileLog.d("UserConfig: Starting import process for new account in slot " + freeAccount);

            final AccountInstance accountInstance = AccountInstance.getInstance(freeAccount);
            accountInstance.getConnectionsManager().setCustomAuthDataForNextInit(data.dcId, data.authKey);

            // Создаем временную заглушку для пользователя, чтобы аккаунт считался активным локально
            clients[freeAccount] = new TLRPC.TL_userEmpty();
            clients[freeAccount].id = 0; // ID будет получен после успешной авторизации
            activated[freeAccount] = true;
            
            // Сохраняем конфиг, чтобы новый аккаунт не пропал при перезапуске
            saveConfig();

            // Переключаемся на новый аккаунт, чтобы инициировать все процессы
            switchToAccount(freeAccount, true);

            // Инициируем соединение. ConnectionsManager должен подхватить наши кастомные данные.
            accountInstance.getConnectionsManager().init();

            // Устанавливаем наблюдателя, который сработает, когда TDLib успешно авторизуется
            // и получит информацию о пользователе.
            final NotificationCenter.NotificationCenterDelegate delegate = (id, account, args) -> {
                if (id == NotificationCenter.mainUserInfoChanged) {
                    FileLog.d("UserConfig: Received mainUserInfoChanged for account " + account + ". Import successful.");
                    // Убираем наблюдателя, чтобы избежать утечек памяти
                    NotificationCenter.getInstance(freeAccount).removeObserver(this, NotificationCenter.mainUserInfoChanged);
                    if (onSuccess != null) {
                        AndroidUtilities.runOnUIThread(onSuccess);
                    }
                }
            };
            NotificationCenter.getInstance(freeAccount).addObserver(delegate, NotificationCenter.mainUserInfoChanged);

            FileLog.d("UserConfig: Waiting for TDLib to authorize using imported session...");
        });
    }
    // --- КОНЕЦ МОДИФИКАЦИИ ---
}