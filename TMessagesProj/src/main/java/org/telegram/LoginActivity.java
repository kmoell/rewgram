/*
 * This is the source code of Telegram for Android v. 7.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CodeFieldContainer;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CountrySelectActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SLSImageView;
import org.telegram.ui.Components.SendingFileExDrawable;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.SlidingView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class LaunchActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static ArrayList<TLRPC.TL_lang> languages;
    private static HashMap<String, String> languageMap;
    private static boolean languagesLoaded;

    private int currentViewNum;
    private TLRPC.TL_auth_sentCode sentCode;

    private LinearLayout phoneView;
    private SlidingView slidingView;
    private FrameLayout grayBackground;
    private ScrollView scrollView;
    private RLottieImageView introView;
    private TextView titleTextView;
    private TextView subtitleTextView;
    private FrameLayout bottomLayout;
    private TextView startMessagingButton;

    private ArrayList<String> countriesArray = new ArrayList<>();
    private HashMap<String, String> countriesMap = new HashMap<>();
    private HashMap<String, String> codesMap = new HashMap<>();
    private HashMap<String, String> phoneFormatMap = new HashMap<>();

    private EditTextBoldCursor[] codeField;
    private CodeFieldContainer codeFieldContainer;
    private int codeFieldNum;
    private boolean ignoreOnTextChange;
    private boolean ignoreOnPhoneChange;

    private EditTextBoldCursor phoneField;
    private TextView countryButton;
    private TextView wrongCountryButton;
    private EditTextBoldCursor codeField2;
    private OutlineTextContainerView codeOutline;
    private int countryState;

    private Timer timer;
    private int time = 60000;
    private int openTime;
    private final Object timerSync = new Object();
    private TextView timeText;

    private String phone;
    private String registeredUserFirstName;
    private String registeredUserLastName;
    private TLRPC.User registeredUser;
    private boolean userRegistered;
    private boolean passwordEntered;
    private String passwordHash;
    private byte[] srp_B;
    private long srp_id;
    private String email_unconfirmed_pattern;

    private boolean syncContacts;
    private AlertDialog progressDialog;

    private boolean justCreated;
    private boolean justOpened;

    private String stringToCopy;
    private String callingCountry;
    private boolean callReceived;

    private static final int done_button = 1;
    private ActionBarMenuItem doneItem;

    private boolean newAccount;
    private TLRPC.TL_auth_authorizationSignUp newAccountAuthorization;
    private SendingFileExDrawable sendingFileExDrawable;

    // --- НАЧАЛО МОДИФИКАЦИИ ---
    private EditText sessionEditText;
    // --- КОНЕЦ МОДИФИКАЦИИ ---

    public LaunchActivity() {
        super();
        Theme.createDialogsResources(null);
    }

    @Override
    public void onFragmentCreated() {
        super.onFragmentCreated();
        getNotificationCenter().addObserver(this, NotificationCenter.countriesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.reloadInterface);
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateConnectionState);
        getNotificationCenter().addObserver(this, NotificationCenter.needShowAlert);
        getNotificationCenter().addObserver(this, NotificationCenter.wasErrorOnLogin);
        getNotificationCenter().addObserver(this, NotificationCenter.openArticle);
        getNotificationCenter().addObserver(this, NotificationCenter.appUpdateAvailable);

        justCreated = true;

        if (BuildVars.IS_SECRET_THEME) {
            getConnectionsManager().setPushConnectionEnabled(false);
        }

        if (languages == null) {
            languages = new ArrayList<>();
            languageMap = new HashMap<>();
            TLRPC.TL_langpack_getLanguages req = new TLRPC.TL_langpack_getLanguages();
            req.lang_pack = "";
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        languagesLoaded = true;
                        TLRPC.Vector vector = (TLRPC.Vector) response;
                        for (int a = 0; a < vector.objects.size(); a++) {
                            TLRPC.TL_lang lang = (TLRPC.TL_lang) vector.objects.get(a);
                            languages.add(lang);
                            languageMap.put(lang.lang_code.replace('-', '_').toLowerCase(), lang.name);
                        }
                        if (parentLayout != null) {
                            rebuildAllFragments(true);
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagWithoutLogin);
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.countriesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.reloadInterface);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateConnectionState);
        getNotificationCenter().removeObserver(this, NotificationCenter.needShowAlert);
        getNotificationCenter().removeObserver(this, NotificationCenter.wasErrorOnLogin);
        getNotificationCenter().removeObserver(this, NotificationCenter.openArticle);
        getNotificationCenter().removeObserver(this, NotificationCenter.appUpdateAvailable);
        try {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            progressDialog = null;
        }
    }

    @Override
    public View createView(Context context) {
        if (fragmentView != null) {
            return fragmentView;
        }
        sendingFileExDrawable = new SendingFileExDrawable();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == done_button) {
                    onNextPressed();
                } else if (id == -1) {
                    onBackPressed();
                }
            }
        });

        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout;
            private int keyboardSize;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                int width = MeasureSpec.getSize(widthMeasureSpec);
                if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    if (AndroidUtilities.isTablet()) {
                        height = (int) (height * 0.7f);
                    } else {
                        height = (int) (height * 0.8f);
                    }
                }

                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                if (keyboardSize > 0) {
                    setPadding(0, 0, 0, keyboardSize);
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    int contentHeight = getMeasuredHeight() - AndroidUtilities.dp(40);
                    int buttonsHeight = bottomLayout.getMeasuredHeight();
                    int newContentHeight = contentHeight - buttonsHeight;

                    int scrollHeight = scrollView.getChildAt(0).getMeasuredHeight();
                    if (scrollHeight > newContentHeight) {
                        scrollView.setPadding(0, 0, 0, 0);
                    } else {
                        int diff = newContentHeight - scrollHeight;
                        scrollView.setPadding(0, diff / 2, 0, 0);
                    }
                    if (grayBackground != null) {
                        grayBackground.setPadding(0, 0, 0, buttonsHeight);
                    }
                }
                super.onLayout(changed, l, t, r, b);
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (grayBackground != null && grayBackground.getVisibility() == VISIBLE) {
                    if (w > h) {
                        grayBackground.setPadding(0, 0, 0, bottomLayout.getMeasuredHeight());
                    } else {
                        grayBackground.setPadding(0, 0, 0, 0);
                    }
                }
            }

            @Override
            public void onConfigurationChanged(android.content.res.Configuration newConfig) {
                super.onConfigurationChanged(newConfig);
                if (grayBackground != null && grayBackground.getVisibility() == VISIBLE) {
                    if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                        grayBackground.setPadding(0, 0, 0, bottomLayout.getMeasuredHeight());
                    } else {
                        grayBackground.setPadding(0, 0, 0, 0);
                    }
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                Theme.loadWallpaper();
            }

            @Override
            protected boolean isActionBarVisible() {
                return false;
            }
        };
        fragmentView = contentView;
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        contentView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        FrameLayout container = new FrameLayout(context);
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        introView = new RLottieImageView(context);
        introView.setAutoRepeat(true);
        introView.setAnimation(R.raw.logo_telegram, 120, 120);
        introView.playAnimation();
        container.addView(introView, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL, 0, 30, 0, 0));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setText(LocaleController.getString("AppName", R.string.AppName));
        container.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 172, 0, 0));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        subtitleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        subtitleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        container.addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 212, 20, 0));

        slidingView = new SlidingView(context) {
            @Override
            public void setTranslationX(float translationX) {
                super.setTranslationX(translationX);
                if (grayBackground != null) {
                    grayBackground.setAlpha(1.0f - Math.abs(translationX) / getMeasuredWidth());
                }
            }
        };
        container.addView(slidingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 0, 276, 0, 0));

        grayBackground = new FrameLayout(context);
        grayBackground.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        grayBackground.setAlpha(0.0f);
        contentView.addView(grayBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bottomLayout = new FrameLayout(context);
        contentView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 128, Gravity.BOTTOM));

        TextView wrongCountryButton = new TextView(context);
        wrongCountryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
        wrongCountryButton.setGravity(Gravity.CENTER);
        wrongCountryButton.setTextColor(Theme.getColor(Theme.key_switchTrackChecked));
        wrongCountryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        wrongCountryButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        wrongCountryButton.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        bottomLayout.addView(wrongCountryButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, -28, 0, 0));
        wrongCountryButton.setOnClickListener(v -> {
            if (parentLayout == null) {
                return;
            }
            CountrySelectActivity activity = new CountrySelectActivity(true);
            activity.setCountrySelectActivityDelegate((name, shortName) -> selectCountry(name, shortName));
            presentFragment(activity);
        });
        this.wrongCountryButton = wrongCountryButton;
              startMessagingButton = new TextView(context);
        startMessagingButton.setText(LocaleController.getString("StartMessaging", R.string.StartMessaging));
        startMessagingButton.setGravity(Gravity.CENTER);
        startMessagingButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        startMessagingButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        startMessagingButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        startMessagingButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        bottomLayout.addView(startMessagingButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 26, 0, 26, 26));
        startMessagingButton.setOnClickListener(v -> {
            if (parentLayout == null) {
                return;
            }
            grayBackground.setVisibility(View.VISIBLE);
            ObjectAnimator.ofFloat(grayBackground, View.ALPHA, 1.0f).start();
            scrollView.smoothScrollTo(0, 0);
            showNextView(phoneView, true);
        });

        phoneView = new LinearLayout(context);
        phoneView.setOrientation(LinearLayout.VERTICAL);
        slidingView.addView(phoneView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView phoneTextView = new TextView(context);
        phoneTextView.setText(LocaleController.getString("YourPhone", R.string.YourPhone));
        phoneTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        phoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        phoneTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        phoneView.addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 29));

        countryButton = new TextView(context);
        countryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        countryButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), 0);
        countryButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        countryButton.setMaxLines(1);
        countryButton.setSingleLine(true);
        countryButton.setEllipsize(TextUtils.TruncateAt.END);
        countryButton.setGravity(Gravity.LEFT);
        countryButton.setBackgroundResource(R.drawable.spinner_states);
        phoneView.addView(countryButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 40, 0, 40, 29));
        countryButton.setOnClickListener(v -> {
            if (parentLayout == null) {
                return;
            }
            CountrySelectActivity activity = new CountrySelectActivity(true);
            activity.setCountrySelectActivityDelegate((name, shortName) -> selectCountry(name, shortName));
            presentFragment(activity);
        });

        View view = new View(context);
        view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        phoneView.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.TOP | Gravity.LEFT, 40, 0, 40, 0));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        phoneView.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 40, 0, 40, 0));

        codeField2 = new EditTextBoldCursor(context);
        codeField2.setInputType(InputType.TYPE_CLASS_PHONE);
        codeField2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField2.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        codeField2.setHint(LocaleController.getString("CountryCode", R.string.CountryCode));
        codeField2.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField2.setCursorSize(AndroidUtilities.dp(20));
        codeField2.setCursorWidth(1.5f);
        codeField2.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        codeField2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        codeField2.setMaxLines(1);
        codeField2.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        codeField2.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        linearLayout.addView(codeField2, LayoutHelper.createLinear(0, 50, 0.3f));
        codeField2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreOnTextChange) {
                    return;
                }
                ignoreOnTextChange = true;
                String text = PhoneFormat.stripExceptNumbers(s.toString());
                s.replace(0, s.length(), text);
                if (text.length() == 0) {
                    countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                    phoneField.setHint(null);
                    countryState = 1;
                } else {
                    String country = codesMap.get(text);
                    if (country != null) {
                        int index = countriesArray.indexOf(country);
                        if (index != -1) {
                            ignoreOnPhoneChange = true;
                            selectCountry(country, countriesMap.get(country));
                            if (text.length() > 4) {
                                s.delete(0, s.length());
                                phoneField.setText(text.substring(4));
                                phoneField.setSelection(phoneField.length());
                            } else {
                                codeField2.setText(text);
                                codeField2.setSelection(codeField2.length());
                            }
                            ignoreOnPhoneChange = false;
                        } else {
                            countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                            phoneField.setHint(null);
                            countryState = 2;
                        }
                    } else {
                        countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                        phoneField.setHint(null);
                        countryState = 2;
                    }
                }
                ignoreOnTextChange = false;
            }
        });
        codeField2.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                phoneField.requestFocus();
                return true;
            }
            return false;
        });

        phoneField = new EditTextBoldCursor(context);
        phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        phoneField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        phoneField.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        phoneField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        phoneField.setCursorSize(AndroidUtilities.dp(20));
        phoneField.setCursorWidth(1.5f);
        phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        phoneField.setMaxLines(1);
        phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        phoneField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        linearLayout.addView(phoneField, LayoutHelper.createLinear(0, 50, 0.7f));
        phoneField.addTextChangedListener(new TextWatcher() {
            private int actionPosition;
            private int lastPhoneLength;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (ignoreOnPhoneChange) {
                    return;
                }
                lastPhoneLength = s.length();
                actionPosition = phoneField.getSelectionStart();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreOnPhoneChange) {
                    return;
                }
                String phone = phoneField.getText().toString();
                String format = phoneFormatMap.get(codeField2.getText().toString());
                if (format != null) {
                    PhoneFormat.Formatter formatter = new PhoneFormat.Formatter();
                    formatter.setFormat(format);
                    String newPhone = formatter.format(phone);
                    if (!newPhone.equals(phone)) {
                        int sel;
                        if (actionPosition == lastPhoneLength) {
                            sel = newPhone.length();
                        } else {
                            sel = phoneField.getSelectionStart();
                        }
                        phoneField.setText(newPhone);
                        phoneField.setSelection(sel);
                    }
                }
            }
        });
        phoneField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                onNextPressed();
                return true;
            }
            return false;
        });

        // --- НАЧАЛО МОДИФИКАЦИИ ---
        // Добавляем наш UI для входа по строке сессии
        TextView sessionHeader = new TextView(context);
        sessionHeader.setText("Или войти по строке сессии (Telethon)");
        sessionHeader.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        sessionHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sessionHeader.setGravity(Gravity.CENTER_HORIZONTAL);
        phoneView.addView(sessionHeader, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 34, 0, 10));

        sessionEditText = new EditText(context);
        sessionEditText.setHint("Session String");
        sessionEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        sessionEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        sessionEditText.setBackground(Theme.createEditTextDrawable(context, true));
        sessionEditText.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        phoneView.addView(sessionEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 20, 0, 20, 0));

        TextView loginBySessionButton = new TextView(context);
        loginBySessionButton.setFocusable(true);
        loginBySessionButton.setText("Войти по строке");
        loginBySessionButton.setGravity(Gravity.CENTER);
        loginBySessionButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        loginBySessionButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        loginBySessionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        loginBySessionButton.setOnClickListener(v -> {
            String session = sessionEditText.getText().toString().trim();
            if (!session.isEmpty()) {
                needShowProgress();
                UserConfig.importSessionFromString(session,
                        () -> { // onSuccess
                            needHideProgress();
                            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                if (UserConfig.getInstance(a).isClientActivated()) {
                                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.mainUserInfoChanged);
                                }
                            }
                            finishFragment();
                        },
                        () -> { // onError
                            needHideProgress();
                            AlertsCreator.showSimpleAlert(LaunchActivity.this, "Ошибка", "Не удалось войти с помощью строки сессии. Проверьте строку или убедитесь, что в файле ConnectionsManager.java реализована нативная часть.");
                        }
                );
            }
        });
        phoneView.addView(loginBySessionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 20, 15, 20, 20));
        // --- КОНЕЦ МОДИФИКАЦИИ ---

        if (getArguments() != null) {
            String phone = getArguments().getString("phone");
            if (phone != null) {
                int plus = phone.indexOf('+');
                if (plus != -1) {
                    phone = phone.substring(plus + 1);
                }
                phoneField.setText(phone);
            }
            int error = getArguments().getInt("error");
            if (error != 0) {
                String errorText;
                if (error == 1) {
                    errorText = LocaleController.getString("LoginError", R.string.LoginError);
                } else {
                    errorText = getArguments().getString("errorText");
                }
                AlertsCreator.showSimpleAlert(this, LocaleController.getString("AppName", R.string.AppName), errorText);
            }
        }
        return fragmentView;
                                            }
      private void onNextPressed() {
        if (currentViewNum == 0) {
            String phone = phoneField.getText().toString();
            String code = codeField2.getText().toString();
            if (TextUtils.isEmpty(code) || TextUtils.isEmpty(phone)) {
                AlertsCreator.showSimpleAlert(this, LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                return;
            }
            TLRPC.TL_auth_sendCode req = new TLRPC.TL_auth_sendCode();
            req.phone_number = "+" + code + phone;
            this.phone = req.phone_number;
            req.api_id = BuildVars.API_ID;
            req.api_hash = BuildVars.API_HASH;
            req.settings = new TLRPC.TL_codeSettings();
            req.settings.allow_flashcall = true;
            req.settings.current_number = userRegistered;
            if (justCreated) {
                req.settings.flags |= TLRPC.TL_codeSettings.FLAG_ALLOW_APP_HASH;
            }

            final int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress();
                if (error == null) {
                    fillNextCodeView(req, (TLRPC.TL_auth_sentCode) response);
                } else {
                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("InvalidCode", R.string.InvalidCode));
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("CodeExpired", R.string.CodeExpired));
                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                        AlertsCreator.showFloodWaitAlert(error.text, this);
                    } else {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                    }
                }
            }));
            needShowProgress();
            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        } else if (currentViewNum == 1) { // code view
            // ... остальная часть метода ...
        }
        // ... и так далее для всех остальных условий
    }

    private void fillNextCodeView(TLRPC.TL_auth_sendCode req, TLRPC.TL_auth_sentCode res) {
        sentCode = res;
        // ... остальной код метода ...
    }

    private void selectCountry(String name, String shortName) {
        countryButton.setText(name);
        String code = codesMap.get(name);
        if (code != null) {
            codeField2.setText(code);
        }
        phoneField.requestFocus();
        phoneField.setSelection(phoneField.length());
    }

    private void showNextView(View view, boolean animated) {
        // ... остальной код метода ...
    }

    public void onBackPressed() {
        if (currentViewNum == 0) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    finishFragment();
                    return;
                }
            }
            if (slidingView.getTranslationX() != 0) {
                showNextView(introView, true);
                grayBackground.setVisibility(View.GONE);
                ObjectAnimator.ofFloat(grayBackground, View.ALPHA, 0.0f).start();
            } else {
                finishFragment();
            }
        } else {
            // ... остальной код метода ...
        }
    }

    public void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.show();
    }

    public void needHideProgress() {
        if (progressDialog == null) {
            return;
        }
        try {
            progressDialog.dismiss();
        } catch (Exception e) {
            FileLog.e(e);
        }
        progressDialog = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (justOpened) {
            justOpened = false;
        } else {
            AndroidUtilities.runOnUIThread(() -> {
                if (slidingView != null && slidingView.getCurrentView() != null) {
                    AndroidUtilities.showKeyboard(slidingView.getCurrentView().findFocus());
                }
            }, 100);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        justOpened = false;
        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.countriesDidLoad) {
            countriesArray = (ArrayList<String>) args[0];
            countriesMap = (HashMap<String, String>) args[1];
            codesMap = (HashMap<String, String>) args[2];
            phoneFormatMap = (HashMap<String, String>) args[3];
            if (justCreated) {
                selectDefaultCountry();
                justCreated = false;
            }
        } else if (id == NotificationCenter.reloadInterface) {
            rebuildAllFragments(true);
        }
    }

    private void selectDefaultCountry() {
        if (LocaleController.getInstance().getCountryCode() != null) {
            String country = countriesMap.get(LocaleController.getInstance().getCountryCode());
            if (country != null) {
                selectCountry(country, LocaleController.getInstance().getCountryCode());
            }
        }
    }

    private void rebuildAllFragments(boolean last) {
        if (parentLayout == null) {
            return;
        }
        if (last) {
            parentLayout.rebuildAllFragmentViews(false, false);
        } else {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
    }
}
