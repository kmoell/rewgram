/*
 * This is the source code of Telegram for Android v. 7.0.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ThemeSwitch;

import java.util.ArrayList;

public class IntroActivity extends BaseFragment {

    private ViewPager viewPager;
    private BottomPagesView bottomPages;
    private TextView startMessagingButton;
    private TextView sessionLoginButton; // Наша новая кнопка
    private ThemeSwitch themeSwitch;
    private boolean justCreated;
    private boolean onLogout;
    private long lastTime;
    private int lastPage;

    private int[] icons;
    private String[] titles;
    private String[] messages;

    public IntroActivity() {
        super();
        icons = new int[]{
                R.drawable.intro1,
                R.drawable.intro2,
                R.drawable.intro3,
                R.drawable.intro4,
                R.drawable.intro5,
                R.drawable.intro6,
                R.drawable.intro7
        };
        titles = new String[]{
                LocaleController.getString("Page1Title", R.string.Page1Title),
                LocaleController.getString("Page2Title", R.string.Page2Title),
                LocaleController.getString("Page3Title", R.string.Page3Title),
                LocaleController.getString("Page4Title", R.string.Page4Title),
                LocaleController.getString("Page5Title", R.string.Page5Title),
                LocaleController.getString("Page6Title", R.string.Page6Title),
                LocaleController.getString("Page7Title", R.string.Page7Title)
        };
        messages = new String[]{
                LocaleController.getString("Page1Message", R.string.Page1Message),
                LocaleController.getString("Page2Message", R.string.Page2Message),
                LocaleController.getString("Page3Message", R.string.Page3Message),
                LocaleController.getString("Page4Message", R.string.Page4Message),
                LocaleController.getString("Page5Message", R.string.Page5Message),
                LocaleController.getString("Page6Message", R.string.Page6Message),
                LocaleController.getString("Page7Message", R.string.Page7Message)
        };
    }

    public IntroActivity setOnLogout() {
        onLogout = true;
        return this;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = frameLayout;

        viewPager = new ViewPager(context);
        viewPager.setAdapter(new IntroAdapter());
        viewPager.setPageMargin(0);
        viewPager.setOffscreenPageLimit(1);
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 178));

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    int newPage = viewPager.getCurrentItem();
                    if (lastPage != newPage) {
                        lastTime = SystemClock.elapsedRealtime();
                        lastPage = newPage;
                    }
                }
            }
        });

        startMessagingButton = new TextView(context);
        startMessagingButton.setText(LocaleController.getString("StartMessaging", R.string.StartMessaging).toUpperCase());
        startMessagingButton.setGravity(Gravity.CENTER);
        startMessagingButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        startMessagingButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        startMessagingButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        startMessagingButton.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        startMessagingButton.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        frameLayout.addView(startMessagingButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 10, 0, 10, 96));
        startMessagingButton.setOnClickListener(v -> {
            if (justCreated && Math.abs(System.currentTimeMillis() - getCreateTime()) < 1000) {
                return;
            }
            presentFragment(new LoginActivity(), true);
        });
        
        // --- НАЧАЛО: НАША НОВАЯ КНОПКА И ЛОГИКА ---
        sessionLoginButton = new TextView(context);
        sessionLoginButton.setText("LOGIN WITH SESSION STRING");
        sessionLoginButton.setGravity(Gravity.CENTER);
        sessionLoginButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        sessionLoginButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sessionLoginButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        sessionLoginButton.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        frameLayout.addView(sessionLoginButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 10, 0, 10, 42));

        sessionLoginButton.setOnClickListener(v -> {
            showSessionInputDialog();
        });
        // --- КОНЕЦ: НАША НОВАЯ КНОПКА И ЛОГИКА ---

        bottomPages = new BottomPagesView(context);
        frameLayout.addView(bottomPages, LayoutHelper.createFrame(66, 5, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 168));

        themeSwitch = new ThemeSwitch(context);
        frameLayout.addView(themeSwitch, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.RIGHT, 14, 14, 14, 0));

        if (onLogout) {
            viewPager.setCurrentItem(0, false);
            lastTime = SystemClock.elapsedRealtime();
            lastPage = 0;
        }

        return fragmentView;
    }
    
    // --- НАЧАЛО: ДИАЛОГ ДЛЯ ВВОДА СТРОКИ СЕССИИ ---
    private void showSessionInputDialog() {
        if (getParentActivity() == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Enter Session String");

        final EditText input = new EditText(getParentActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String sessionString = input.getText().toString().trim();
            if (!sessionString.isEmpty()) {
                loginWithSessionString(sessionString);
            } else {
                Toast.makeText(getParentActivity(), "Session string cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void loginWithSessionString(String sessionString) {
        // TODO: Implement session string login logic here.
        // This is a placeholder. Real implementation requires calling specific
        // methods of Telegram's core libraries (like TDLib's `addProxy` with a special secret,
        // or a custom method if you modify the auth flow).
        
        // Example of what needs to happen:
        // 1. Parse the session string.
        // 2. Extract auth key, DC ID, user ID etc.
        // 3. Set these parameters in the ConnectionsManager and UserConfig.
        // 4. Force a connection to the new DC.
        // 5. If successful, mark the client as activated and proceed to LaunchActivity.

        AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCancel(false);
        progressDialog.show();
        
        // This is a dummy flow to show the concept.
        // The real authorization won't happen here without deeper modifications.
        AndroidUtilities.runOnUIThread(() -> {
            progressDialog.dismiss();
            Toast.makeText(ApplicationLoader.applicationContext, "Session login logic not implemented yet!", Toast.LENGTH_LONG).show();
            
            // On a successful, real login, you would do something like this:
            // ConnectionsManager.getInstance(currentAccount).setClientActivated(true);
            // UserConfig.getInstance(currentAccount).setClientActivated(true);
            // UserConfig.getInstance(currentAccount).saveConfig(true);
            // needFinishActivity();
            // Intent intent = new Intent(getParentActivity(), LaunchActivity.class);
            // startActivity(intent);

        }, 2000);
    }
    // --- КОНЕЦ: ДИАЛОГ ДЛЯ ВВОДА СТРОКИ СЕССИИ ---

    @Override
    public void onResume() {
        super.onResume();
        justCreated = true;
        if (viewPager != null) {
            viewPager.setCurrentItem(0, false);
            lastTime = SystemClock.elapsedRealtime();
            lastPage = 0;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        justCreated = false;
    }

    @Override
    public boolean onBackPressed() {
        if (onLogout) {
            AndroidUtilities.showLogoutAlert(getParentActivity(), currentAccount);
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        descriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        descriptions.add(new ThemeDescription(viewPager, 0, new Class[]{IntroPage.class}, new String[]{"titleText"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(viewPager, 0, new Class[]{IntroPage.class}, new String[]{"messageText"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));

        descriptions.add(new ThemeDescription(startMessagingButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_featuredStickers_buttonText));
        descriptions.add(new ThemeDescription(startMessagingButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_featuredStickers_addButton));
        descriptions.add(new ThemeDescription(startMessagingButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_featuredStickers_addButtonPressed));
        
        // --- НАЧАЛО: ОПИСАНИЕ ТЕМЫ ДЛЯ НАШЕЙ КНОПКИ ---
        descriptions.add(new ThemeDescription(sessionLoginButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        // --- КОНЕЦ: ОПИСАНИЕ ТЕМЫ ДЛЯ НАШЕЙ КНОПКИ ---

        descriptions.add(new ThemeDescription(bottomPages, 0, null, null, null, null, Theme.key_intro_dot));
        descriptions.add(new ThemeDescription(bottomPages, 0, null, null, null, null, Theme.key_intro_selectedDot));

        return descriptions;
    }

    private class BottomPagesView extends View {

        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private int scrollPosition;
        private int pagesCount;

        public BottomPagesView(Context context) {
            super(context);
            paint.setColor(Theme.getColor(Theme.key_intro_dot));
            pagesCount = viewPager.getAdapter().getCount();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(pagesCount * 11 - 6), AndroidUtilities.dp(5));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int pos = viewPager.getCurrentItem();
            for (int i = 0; i < pagesCount; i++) {
                paint.setColor(i == pos ? Theme.getColor(Theme.key_intro_selectedDot) : Theme.getColor(Theme.key_intro_dot));
                canvas.drawCircle(AndroidUtilities.dp(i * 11 + 2.5f), AndroidUtilities.dp(2.5f), AndroidUtilities.dp(2.5f), paint);
            }
        }
    }

    private class IntroAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return icons.length;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            IntroPage view = new IntroPage(container.getContext());
            view.setData(icons[position], titles[position], messages[position]);
            container.addView(view);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            bottomPages.invalidate();
            themeSwitch.getImageView().setColorFilter(new PorterDuffColorFilter(Theme.getColor(position == 0 ? Theme.key_intro_cirlceTrack : Theme.key_intro_circle), PorterDuff.Mode.MULTIPLY));
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view.equals(object);
        }

        @Override
        public void restoreState(android.os.Parcelable arg0, ClassLoader arg1) {
        }

        @Override
        public android.os.Parcelable saveState() {
            return null;
        }

        @Override
        public void unregisterDataSetObserver(android.database.DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    private class IntroPage extends FrameLayout {

        private ImageView imageView;
        private TextView titleText;
        private TextView messageText;
        private int textWidth;
        private Rect rect = new Rect();

        public IntroPage(Context context) {
            super(context);
            setWillNotDraw(false);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(200, 200, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 96, 0, 0));

            titleText = new TextView(context);
            titleText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleText.setGravity(Gravity.CENTER_HORIZONTAL);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            titleText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addView(titleText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 18, 336, 18, 0));

            messageText = new TextView(context);
            messageText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            messageText.setGravity(Gravity.CENTER_HORIZONTAL);
            messageText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addView(messageText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 16, 380, 16, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            textWidth = width - AndroidUtilities.dp(16 * 2);
            titleText.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED));
            messageText.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED));
        }

        public void setData(int icon, String title, String message) {
            imageView.setImageResource(icon);
            titleText.setText(title);
            messageText.setText(message);
        }
    }
}