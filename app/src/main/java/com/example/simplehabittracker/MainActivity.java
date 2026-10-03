package com.example.simplehabittracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "habit_tracker_data";
    private static final String HABITS_KEY = "habits";
    private static final String FOLDERS_KEY = "folders";
    private static final String SELECTED_HABIT_KEY = "selected_habit_id";
    private static final String DARK_MODE_KEY = "dark_mode";
    private static final String APP_FONT_KEY = "app_font";
    private static final String CENTER_HABIT_TITLE_KEY = "center_habit_title";
    private static final String HIDE_TITLE_EMOJI_KEY = "hide_title_emoji";
    private static final String UNSELECTED_DATE_STYLE_KEY = "unselected_date_style";
    private static final String SLEEP_TIME_MINUTES_KEY = "sleep_time_minutes";
    private static final String SINGLE_STATE_MIGRATION_KEY = "single_state_migration_complete";
    private static final String CALENDAR_BACKGROUND_FILE = "calendar_background.webp";
    private static final int PICK_CALENDAR_BACKGROUND_REQUEST = 4103;
    private static final int DEFAULT_SLEEP_TIME_MINUTES = 23 * 60;
    private static final String HABIT_TYPE_GOOD = "good";
    private static final String HABIT_TYPE_BAD = "bad";
    private static final int DEFAULT_HABIT_DATE_COLOR = Color.rgb(22, 163, 74);
    private static final int DEFAULT_FOLDER_COLOR = Color.rgb(22, 163, 74);
    private static final int DEFAULT_BAD_FOLDER_COLOR = Color.rgb(220, 38, 38);
    private static final int[] COLOR_OPTIONS = {
            Color.rgb(22, 163, 74),
            Color.rgb(13, 148, 136),
            Color.rgb(54, 139, 193),
            Color.rgb(135, 8, 80),
            Color.rgb(202, 138, 4),
            Color.rgb(220, 38, 38)
    };
    private static final String[] COLOR_OPTION_NAMES = {"Green", "Teal", "Blue", "Purple", "Yellow", "Red"};
    private static final int STATE_EMPTY = 0;
    private static final int STATE_DONE = 1;
    private static final int STATE_MISSED = 2;
    private static final int UNSELECTED_STYLE_BLANK = 0;
    private static final int UNSELECTED_STYLE_DIAGONAL = 1;
    private static final int UNSELECTED_STYLE_HORIZONTAL = 2;
    private static final String FONT_DEFAULT = "Default";
    private static final String FONT_SERIF = "Serif";
    private static final String FONT_TORONTO_SUBWAY = "Toronto Subway";

    private final DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);
    private final DateTimeFormatter sleepTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private final List<Habit> habits = new ArrayList<>();
    private final List<HabitFolder> folders = new ArrayList<>();
    private final Map<String, HorizontalScrollView> bottomFolderScrollers = new HashMap<>();
    private final Map<String, View> bottomHabitCards = new HashMap<>();
    private final Map<String, TextView> bottomHabitEmojis = new HashMap<>();
    private final Map<String, TextView> bottomHabitNames = new HashMap<>();
    private final Map<String, LinearLayout> drawerHabitRows = new HashMap<>();
    private final Map<String, TextView> drawerHabitNames = new HashMap<>();
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTicker = new Runnable() {
        @Override
        public void run() {
            renderSleepCountdown();
            countdownHandler.postDelayed(this, 1000);
        }
    };

    private SharedPreferences prefs;
    private YearMonth visibleMonth;
    private String selectedHabitId;
    private boolean isDarkMode;
    private String appFont;
    private boolean isHabitTitleCentered;
    private boolean hideTitleEmoji;
    private int unselectedDateStyle;
    private int sleepTimeMinutes;
    private Bitmap calendarBackgroundBitmap;

    private FrameLayout root;
    private LinearLayout page;
    private FrameLayout topBar;
    private TextView yearTitle;
    private TextView monthTitle;
    private TextView habitTitle;
    private Button habitEmojiButton;
    private FrameLayout calendarContainer;
    private GridLayout calendarGrid;
    private GridLayout monthPreviewGrid;
    private YearMonth monthPreview;
    private int monthPreviewDirection;
    private LinearLayout bottomPanel;
    private LinearLayout bottomPanelContent;
    private TextView sleepCountdownValue;
    private LinearLayout settingsLayer;
    private TextView settingsSleepTimeValue;
    private FrameLayout drawerLayer;
    private LinearLayout drawerContent;
    private ScrollView drawerScroll;
    private LinearLayout habitList;
    private float monthSwipeStartX;
    private float monthSwipeStartY;
    private VelocityTracker monthVelocityTracker;
    private boolean monthSwipeInProgress;
    private boolean isMonthAnimating;
    private boolean isDrawerAnimating;
    private boolean touchStartedInBottomPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        visibleMonth = YearMonth.now();
        isDarkMode = prefs.getBoolean(DARK_MODE_KEY, false);
        appFont = normalizedAppFont(prefs.getString(APP_FONT_KEY, FONT_DEFAULT));
        isHabitTitleCentered = prefs.getBoolean(CENTER_HABIT_TITLE_KEY, false);
        if (prefs.contains(HIDE_TITLE_EMOJI_KEY)) {
            hideTitleEmoji = prefs.getBoolean(HIDE_TITLE_EMOJI_KEY, false);
        } else if (prefs.contains("show_habit_emoji_button")) {
            hideTitleEmoji = !prefs.getBoolean("show_habit_emoji_button", true);
            prefs.edit()
                    .putBoolean(HIDE_TITLE_EMOJI_KEY, hideTitleEmoji)
                    .remove("show_habit_emoji_button")
                    .apply();
        } else {
            hideTitleEmoji = false;
        }
        unselectedDateStyle = prefs.getInt(UNSELECTED_DATE_STYLE_KEY, UNSELECTED_STYLE_DIAGONAL);
        sleepTimeMinutes = prefs.getInt(SLEEP_TIME_MINUTES_KEY, DEFAULT_SLEEP_TIME_MINUTES);
        deleteFile("today_photo.webp");
        calendarBackgroundBitmap = loadPrivateBitmap(CALENDAR_BACKGROUND_FILE);

        loadFolders();
        loadHabits();
        if (habits.isEmpty()) {
            habits.add(new Habit("habit-" + System.currentTimeMillis(), "Daily Habit", "", HABIT_TYPE_GOOD, defaultHabitColors(), new JSONObject(), new JSONObject(), null));
            selectedHabitId = habits.get(0).id;
            saveHabits();
        }

        selectedHabitId = prefs.getString(SELECTED_HABIT_KEY, habits.get(0).id);
        if (findSelectedHabit() == null) {
            selectedHabitId = habits.get(0).id;
        }
        migrateToSingleDateState();

        buildUi();
        renderAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        countdownHandler.removeCallbacks(countdownTicker);
        countdownTicker.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        countdownHandler.removeCallbacks(countdownTicker);
    }

    private void buildUi() {
        applySystemBarTheme();

        root = new FrameLayout(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(surfaceColor());
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(102)));

        FrameLayout habitTitleRow = new FrameLayout(this);
        habitTitleRow.setPadding(dp(20), 0, dp(20), 0);

        habitTitle = new TextView(this);
        habitTitle.setTextColor(textColor());
        habitTitle.setTextSize(26);
        habitTitle.setTypeface(Typeface.DEFAULT_BOLD);
        habitTitle.setGravity((isHabitTitleCentered ? Gravity.CENTER : Gravity.START) | Gravity.CENTER_VERTICAL);
        habitTitle.setOnClickListener(v -> {
            Habit habit = findSelectedHabit();
            if (habit != null) {
                showRenameDialog(habit);
            }
        });
        FrameLayout.LayoutParams habitTitleParams = new FrameLayout.LayoutParams(-1, -1);
        int emojiSpace = hideTitleEmoji ? 0 : dp(48);
        habitTitleParams.setMargins(0, 0, emojiSpace, 0);
        if (isHabitTitleCentered && !hideTitleEmoji) {
            habitTitleParams.setMargins(emojiSpace, 0, emojiSpace, 0);
        }
        habitTitleRow.addView(habitTitle, habitTitleParams);

        habitEmojiButton = iconButton("+");
        habitEmojiButton.setTextSize(22);
        habitEmojiButton.setOnClickListener(v -> showEmojiDialog());
        habitEmojiButton.setVisibility(hideTitleEmoji ? View.GONE : View.VISIBLE);
        FrameLayout.LayoutParams emojiButtonParams = new FrameLayout.LayoutParams(dp(44), dp(44));
        emojiButtonParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        emojiButtonParams.setMargins(0, 0, dp(2), 0);
        habitTitleRow.addView(habitEmojiButton, emojiButtonParams);

        page.addView(habitTitleRow, new LinearLayout.LayoutParams(-1, dp(52)));

        View mainDivider = new View(this);
        mainDivider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams mainDividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        mainDividerParams.setMargins(dp(20), 0, dp(20), dp(6));
        page.addView(mainDivider, mainDividerParams);

        FrameLayout calendarSection = new FrameLayout(this);
        if (calendarBackgroundBitmap != null) {
            ImageView backgroundImage = new ImageView(this);
            backgroundImage.setImageBitmap(calendarBackgroundBitmap);
            backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            calendarSection.addView(backgroundImage, new FrameLayout.LayoutParams(-1, -1));

            View backgroundOverlay = new View(this);
            backgroundOverlay.setBackgroundColor(Color.argb(isDarkMode ? 74 : 58, 0, 0, 0));
            calendarSection.addView(backgroundOverlay, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout calendarContent = new LinearLayout(this);
        calendarContent.setOrientation(LinearLayout.VERTICAL);
        calendarContent.addView(buildWeekdayHeader(), new LinearLayout.LayoutParams(-1, dp(34)));
        calendarContainer = new FrameLayout(this);
        calendarContainer.setClipChildren(true);
        calendarGrid = createCalendarGrid();
        calendarContainer.addView(calendarGrid, new FrameLayout.LayoutParams(-1, -1));
        calendarContent.addView(calendarContainer, new LinearLayout.LayoutParams(-1, dp(380)));
        calendarSection.addView(calendarContent, new FrameLayout.LayoutParams(-1, -1));
        page.addView(calendarSection, new LinearLayout.LayoutParams(-1, dp(414)));

        View gridBottomDivider = new View(this);
        gridBottomDivider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams gridBottomDividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        gridBottomDividerParams.setMargins(dp(20), dp(6), dp(20), 0);
        page.addView(gridBottomDivider, gridBottomDividerParams);

        bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setBackgroundColor(surfaceColor());
        bottomPanel.setPadding(dp(20), dp(14), dp(20), dp(18));
        page.addView(bottomPanel, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView bottomScroller = new ScrollView(this);
        bottomScroller.setVerticalScrollBarEnabled(false);
        bottomPanelContent = new LinearLayout(this);
        bottomPanelContent.setOrientation(LinearLayout.VERTICAL);
        bottomScroller.addView(bottomPanelContent, new ScrollView.LayoutParams(-1, -2));
        bottomPanel.addView(bottomScroller, new LinearLayout.LayoutParams(-1, -1));

        buildSettingsPage();
        buildDrawer();
        installInsetPanels();
        setContentView(root);
    }

    private View buildTopBar() {
        topBar = new FrameLayout(this);
        topBar.setPadding(dp(10), dp(18), dp(10), dp(10));
        topBar.setBackgroundColor(surfaceColor());

        Button menuButton = iconButton("☰");
        menuButton.setOnClickListener(v -> showDrawer());
        FrameLayout menuSlot = new FrameLayout(this);
        FrameLayout.LayoutParams menuButtonParams = new FrameLayout.LayoutParams(dp(52), dp(52));
        menuButtonParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        menuSlot.addView(menuButton, menuButtonParams);
        FrameLayout.LayoutParams menuSlotParams = new FrameLayout.LayoutParams(dp(72), dp(52));
        menuSlotParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        topBar.addView(menuSlot, menuSlotParams);

        LinearLayout dateHeading = new LinearLayout(this);
        dateHeading.setOrientation(LinearLayout.VERTICAL);
        dateHeading.setGravity(Gravity.CENTER);

        yearTitle = new TextView(this);
        yearTitle.setTextColor(textColor());
        yearTitle.setTextSize(28);
        yearTitle.setTypeface(Typeface.DEFAULT_BOLD);
        yearTitle.setGravity(Gravity.CENTER);
        yearTitle.setIncludeFontPadding(false);
        dateHeading.addView(yearTitle, new LinearLayout.LayoutParams(-1, dp(33)));

        monthTitle = new TextView(this);
        monthTitle.setTextColor(mutedTextColor());
        monthTitle.setTextSize(13);
        monthTitle.setTypeface(Typeface.DEFAULT);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setIncludeFontPadding(false);
        dateHeading.addView(monthTitle, new LinearLayout.LayoutParams(-1, dp(19)));

        FrameLayout.LayoutParams dateHeadingParams = new FrameLayout.LayoutParams(-1, dp(52));
        dateHeadingParams.gravity = Gravity.CENTER_VERTICAL;
        dateHeadingParams.setMargins(dp(72), 0, dp(72), 0);
        topBar.addView(dateHeading, dateHeadingParams);

        sleepCountdownValue = new TextView(this);
        sleepCountdownValue.setTextColor(accentColor());
        sleepCountdownValue.setTextSize(13);
        sleepCountdownValue.setTypeface(Typeface.DEFAULT_BOLD);
        sleepCountdownValue.setGravity(Gravity.CENTER);
        sleepCountdownValue.setSingleLine(true);
        sleepCountdownValue.setOnClickListener(v -> showSleepTimeDialog());
        FrameLayout.LayoutParams countdownParams = new FrameLayout.LayoutParams(dp(72), dp(52));
        countdownParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        topBar.addView(sleepCountdownValue, countdownParams);

        return topBar;
    }

    private LinearLayout addHabitTypeSection(LinearLayout parent, String titleText) {
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(mutedTextColor());
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(24));
        parent.addView(title, titleParams);

        HorizontalScrollView habitScroller = new HorizontalScrollView(this);
        habitScroller.setHorizontalScrollBarEnabled(false);
        habitScroller.setFillViewport(false);

        LinearLayout habitRow = new LinearLayout(this);
        habitRow.setOrientation(LinearLayout.HORIZONTAL);
        habitRow.setGravity(Gravity.CENTER_VERTICAL);
        habitScroller.addView(habitRow, new HorizontalScrollView.LayoutParams(-2, -1));

        LinearLayout.LayoutParams scrollerParams = new LinearLayout.LayoutParams(-1, dp(66));
        scrollerParams.setMargins(0, 0, 0, dp(8));
        parent.addView(habitScroller, scrollerParams);
        return habitRow;
    }

    private void buildSettingsPage() {
        settingsLayer = new LinearLayout(this);
        settingsLayer.setOrientation(LinearLayout.VERTICAL);
        settingsLayer.setBackgroundColor(surfaceColor());
        settingsLayer.setPadding(dp(10), dp(18), dp(10), dp(10));
        settingsLayer.setVisibility(View.GONE);
        root.addView(settingsLayer, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button backButton = iconButton("‹");
        backButton.setTextSize(32);
        backButton.setContentDescription("Back to calendar");
        backButton.setOnClickListener(v -> hideSettings());
        header.addView(backButton, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(textColor());
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        titleParams.setMargins(dp(8), 0, dp(52), 0);
        header.addView(title, titleParams);
        settingsLayer.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));

        addSettingsSectionTitle(content, "Appearance");
        LinearLayout themeRow = settingsRow("Dark Mode");
        Switch themeSwitch = new Switch(this);
        themeSwitch.setContentDescription("Dark mode");
        themeSwitch.setChecked(isDarkMode);
        themeSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked == isDarkMode) {
                return;
            }
            isDarkMode = checked;
            prefs.edit().putBoolean(DARK_MODE_KEY, isDarkMode).apply();
            buildUi();
            renderAll();
            showSettings();
        });
        themeRow.addView(themeSwitch, new LinearLayout.LayoutParams(-2, dp(48)));
        content.addView(themeRow, new LinearLayout.LayoutParams(-1, dp(58)));
        addSettingsDivider(content);

        LinearLayout fontRow = settingsRow("Font");
        TextView fontValue = new TextView(this);
        fontValue.setText(appFont + "  ▾");
        fontValue.setTextColor(accentColor());
        fontValue.setTextSize(15);
        fontValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        fontRow.addView(fontValue, new LinearLayout.LayoutParams(-2, dp(48)));
        fontRow.setOnClickListener(v -> showFontMenu(fontValue));
        content.addView(fontRow, new LinearLayout.LayoutParams(-1, dp(58)));
        addSettingsDivider(content);

        LinearLayout emojiButtonRow = settingsRow("Hide Title Emoji");
        Switch emojiButtonSwitch = new Switch(this);
        emojiButtonSwitch.setContentDescription("Hide Title Emoji");
        emojiButtonSwitch.setChecked(hideTitleEmoji);
        emojiButtonSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked == hideTitleEmoji) {
                return;
            }
            hideTitleEmoji = checked;
            prefs.edit().putBoolean(HIDE_TITLE_EMOJI_KEY, checked).apply();
            buildUi();
            renderAll();
            showSettings();
        });
        emojiButtonRow.addView(emojiButtonSwitch, new LinearLayout.LayoutParams(-2, dp(48)));
        content.addView(emojiButtonRow, new LinearLayout.LayoutParams(-1, dp(58)));
        addSettingsDivider(content);

        LinearLayout alignmentRow = settingsRow("Habit Title");
        TextView alignmentValue = new TextView(this);
        alignmentValue.setText(isHabitTitleCentered ? "Center  ▾" : "Left  ▾");
        alignmentValue.setTextColor(accentColor());
        alignmentValue.setTextSize(15);
        alignmentValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        alignmentRow.addView(alignmentValue, new LinearLayout.LayoutParams(-2, dp(48)));
        alignmentRow.setOnClickListener(v -> showHabitTitleAlignmentMenu(alignmentValue));
        content.addView(alignmentRow, new LinearLayout.LayoutParams(-1, dp(58)));
        addSettingsDivider(content);

        LinearLayout unselectedDateRow = settingsRow("Unselected Dates");
        TextView unselectedDateValue = new TextView(this);
        unselectedDateValue.setText(unselectedDateStyleName() + "  ▾");
        unselectedDateValue.setTextColor(accentColor());
        unselectedDateValue.setTextSize(15);
        unselectedDateValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        unselectedDateRow.addView(unselectedDateValue, new LinearLayout.LayoutParams(-2, dp(48)));
        unselectedDateRow.setOnClickListener(v -> showUnselectedDateStyleMenu(unselectedDateValue));
        content.addView(unselectedDateRow, new LinearLayout.LayoutParams(-1, dp(58)));
        addSettingsDivider(content);

        LinearLayout calendarBackgroundRow = settingsRow("Calendar Background");
        TextView calendarBackgroundValue = new TextView(this);
        calendarBackgroundValue.setText((calendarBackgroundBitmap == null ? "None" : "Custom") + "  ▾");
        calendarBackgroundValue.setTextColor(accentColor());
        calendarBackgroundValue.setTextSize(15);
        calendarBackgroundValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        calendarBackgroundRow.addView(calendarBackgroundValue, new LinearLayout.LayoutParams(-2, dp(48)));
        calendarBackgroundRow.setOnClickListener(v -> showCalendarBackgroundMenu(calendarBackgroundValue));
        content.addView(calendarBackgroundRow, new LinearLayout.LayoutParams(-1, dp(58)));
        addSettingsDivider(content);

        addSettingsSectionTitle(content, "Routine");
        LinearLayout sleepRow = settingsRow("Sleep Time");
        settingsSleepTimeValue = new TextView(this);
        settingsSleepTimeValue.setTextColor(accentColor());
        settingsSleepTimeValue.setTextSize(15);
        settingsSleepTimeValue.setTypeface(Typeface.DEFAULT_BOLD);
        settingsSleepTimeValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        sleepRow.addView(settingsSleepTimeValue, new LinearLayout.LayoutParams(-2, dp(48)));
        sleepRow.setOnClickListener(v -> showSleepTimeDialog());
        content.addView(sleepRow, new LinearLayout.LayoutParams(-1, dp(58)));
        addSettingsDivider(content);

        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        settingsLayer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        renderSettingsValues();
    }

    private void addSettingsSectionTitle(LinearLayout parent, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(mutedTextColor());
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(42));
        params.setMargins(dp(4), dp(8), dp(4), dp(6));
        parent.addView(title, params);
    }

    private LinearLayout settingsRow(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(4), 0);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(textColor());
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        return row;
    }

    private void addSettingsDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(borderColor());
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isMonthAnimating
                || (settingsLayer != null && settingsLayer.getVisibility() == View.VISIBLE)
                || (drawerLayer != null && drawerLayer.getVisibility() == View.VISIBLE)) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                monthSwipeInProgress = false;
                touchStartedInBottomPanel = false;
                recycleMonthVelocityTracker();
            }
            return super.dispatchTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                recycleMonthVelocityTracker();
                monthVelocityTracker = VelocityTracker.obtain();
                monthVelocityTracker.addMovement(event);
                monthSwipeStartX = event.getRawX();
                monthSwipeStartY = event.getRawY();
                monthSwipeInProgress = false;
                touchStartedInBottomPanel = isTouchInsideView(bottomPanel, event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (monthVelocityTracker == null) {
                    return super.dispatchTouchEvent(event);
                }
                monthVelocityTracker.addMovement(event);
                if (touchStartedInBottomPanel) {
                    return super.dispatchTouchEvent(event);
                }
                if (monthSwipeInProgress || isMonthDrag(event)) {
                    monthSwipeInProgress = true;
                    updateMonthDrag(event.getRawX() - monthSwipeStartX);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (monthVelocityTracker == null) {
                    monthSwipeInProgress = false;
                    touchStartedInBottomPanel = false;
                    return super.dispatchTouchEvent(event);
                }
                monthVelocityTracker.addMovement(event);
                if (touchStartedInBottomPanel) {
                    touchStartedInBottomPanel = false;
                    recycleMonthVelocityTracker();
                    return super.dispatchTouchEvent(event);
                }
                if (monthSwipeInProgress) {
                    float deltaX = event.getRawX() - monthSwipeStartX;
                    monthVelocityTracker.computeCurrentVelocity(1000);
                    finishMonthDrag(deltaX, monthVelocityTracker.getXVelocity());
                    recycleMonthVelocityTracker();
                    monthSwipeInProgress = false;
                    return true;
                }
                recycleMonthVelocityTracker();
                break;
            case MotionEvent.ACTION_CANCEL:
                touchStartedInBottomPanel = false;
                if (monthSwipeInProgress) {
                    finishMonthDrag(0, 0);
                }
                monthSwipeInProgress = false;
                recycleMonthVelocityTracker();
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isTouchInsideView(View view, MotionEvent event) {
        if (view == null) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= location[0]
                && rawX <= location[0] + view.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + view.getHeight();
    }

    private View buildWeekdayHeader() {
        GridLayout header = new GridLayout(this);
        header.setColumnCount(7);
        header.setPadding(dp(12), 0, dp(12), 0);
        String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (String day : days) {
            TextView label = new TextView(this);
            label.setText(day);
            label.setTextColor(calendarBackgroundBitmap == null ? mutedTextColor() : Color.WHITE);
            label.setTextSize(12);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = -1;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            header.addView(label, params);
        }
        return header;
    }

    private void buildDrawer() {
        drawerLayer = new FrameLayout(this);
        drawerLayer.setBackgroundColor(isDarkMode ? Color.argb(150, 0, 0, 0) : Color.argb(118, 15, 23, 42));
        drawerLayer.setAlpha(0f);
        drawerLayer.setVisibility(View.GONE);
        drawerLayer.setOnClickListener(v -> hideDrawer());
        root.addView(drawerLayer, new FrameLayout.LayoutParams(-1, -1));

        drawerContent = new LinearLayout(this);
        drawerContent.setOrientation(LinearLayout.VERTICAL);
        drawerContent.setPadding(dp(18), dp(26), dp(18), dp(18));
        drawerContent.setBackgroundColor(panelColor());
        drawerContent.setOnClickListener(v -> { });

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Lists");
        title.setTextColor(textColor());
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button settingsButton = secondaryButton("Settings");
        settingsButton.setContentDescription("Settings");
        settingsButton.setOnClickListener(v -> {
            showSettings();
            hideDrawer();
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(88), dp(44));
        settingsParams.setMargins(0, 0, dp(8), 0);
        titleRow.addView(settingsButton, settingsParams);

        Button addButton = primaryButton("+");
        addButton.setTextSize(20);
        addButton.setOnClickListener(v -> showCreateMenu(addButton));
        titleRow.addView(addButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        drawerContent.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(54)));

        View divider = new View(this);
        divider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(10), 0, dp(8));
        drawerContent.addView(divider, dividerParams);

        drawerScroll = new ScrollView(this);
        habitList = new LinearLayout(this);
        habitList.setOrientation(LinearLayout.VERTICAL);
        drawerScroll.addView(habitList, new ScrollView.LayoutParams(-1, -2));
        drawerContent.addView(drawerScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(304), -1);
        drawerParams.gravity = Gravity.START;
        drawerLayer.addView(drawerContent, drawerParams);
    }

    private void renderAll() {
        renderDrawerList();
        renderBottomHabitSelector();
        renderCalendar();
        renderSleepCountdown();
        applyAppFont(root, selectedAppTypeface());
    }

    private void renderSleepCountdown() {
        if (sleepCountdownValue == null) {
            return;
        }

        LocalTime sleepTime = LocalTime.of(sleepTimeMinutes / 60, sleepTimeMinutes % 60);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.toLocalDate().atTime(sleepTime);
        if (!target.isAfter(now)) {
            target = target.plusDays(1);
        }

        Duration remaining = Duration.between(now, target);
        long totalSeconds = Math.max(0, remaining.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        sleepCountdownValue.setText(String.format(Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes, seconds));
        sleepCountdownValue.setContentDescription("Time until sleep at " + sleepTime.format(sleepTimeFormatter));
        renderSettingsValues();
    }

    private void renderSettingsValues() {
        if (settingsSleepTimeValue == null) {
            return;
        }
        LocalTime sleepTime = LocalTime.of(sleepTimeMinutes / 60, sleepTimeMinutes % 60);
        settingsSleepTimeValue.setText(sleepTime.format(sleepTimeFormatter));
    }

    private void renderCalendar() {
        Habit habit = findSelectedHabit();
        if (habit == null) {
            return;
        }

        clampVisibleMonth(habit);

        yearTitle.setText(String.valueOf(visibleMonth.getYear()));
        monthTitle.setText(visibleMonth.format(monthFormatter));
        habitTitle.setText(habit.name);
        habitEmojiButton.setText(habit.emoji.isEmpty() ? "+" : habit.emoji);
        applyEmojiButtonStyle();
        populateCalendarGrid(calendarGrid, visibleMonth, habit, true);
    }

    private GridLayout createCalendarGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        grid.setRowCount(6);
        grid.setPadding(dp(12), dp(8), dp(12), dp(12));
        return grid;
    }

    private void populateCalendarGrid(GridLayout grid, YearMonth month, Habit habit, boolean interactive) {
        grid.removeAllViews();

        int startColumn = startColumn(month.atDay(1).getDayOfWeek());
        int daysInMonth = month.lengthOfMonth();
        int totalCells = 42;

        for (int cell = 0; cell < totalCells; cell++) {
            int dayNumber = cell - startColumn + 1;
            DayTextView dayView = new DayTextView(this);
            dayView.setGravity(Gravity.CENTER);
            dayView.setTextSize(16);
            dayView.setTypeface(Typeface.DEFAULT_BOLD);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(52);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));

            if (dayNumber >= 1 && dayNumber <= daysInMonth) {
                LocalDate date = month.atDay(dayNumber);
                String dateKey = date.toString();
                int state = habit.states.optInt(dateKey, STATE_EMPTY);
                boolean futureDate = date.isAfter(LocalDate.now());
                boolean beforeStartDate = habit.startDate != null && date.isBefore(habit.startDate);
                boolean unavailableDate = futureDate || beforeStartDate;
                dayView.setText(String.valueOf(dayNumber));
                dayView.setStartDateHatResource(date.equals(habit.startDate)
                        ? R.drawable.start_date_hat_crown
                        : 0);
                dayView.setHasNote(!habit.notes.optString(dateKey, "").trim().isEmpty());
                if (unavailableDate) {
                    applyFutureDayStyle(dayView);
                } else {
                    applyDayStyle(
                            dayView,
                            state != STATE_EMPTY,
                            date.equals(LocalDate.now()),
                            resolvedDateColor(habit, state)
                    );
                    dayView.setUnselectedDateStyle(state == STATE_EMPTY && date.isBefore(LocalDate.now())
                            ? unselectedDateStyle
                            : UNSELECTED_STYLE_BLANK);
                }
                if (interactive && !unavailableDate) {
                    dayView.setOnClickListener(v -> {
                        cycleDay(habit, dateKey);
                        saveHabits();
                        renderCalendar();
                    });
                    dayView.setOnLongClickListener(v -> {
                        showDateNoteDialog(habit, dateKey);
                        return true;
                    });
                }
            } else {
                dayView.setText("");
                dayView.setBackgroundColor(Color.TRANSPARENT);
            }

            grid.addView(dayView, params);
        }
    }

    private void showDateNoteDialog(Habit habit, String dateKey) {
        EditText input = new EditText(this);
        input.setText(habit.notes.optString(dateKey, ""));
        input.setHint("Add a note");
        applyDialogInputColors(input);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(1);
        input.setMaxLines(8);
        input.setHorizontallyScrolling(false);
        input.setVerticalScrollBarEnabled(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new AlertDialog.Builder(this)
                .setTitle("Note for " + dateKey)
                .setView(paddedDialogView(input))
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (dialog, which) -> {
                    habit.notes.remove(dateKey);
                    saveHabits();
                    renderCalendar();
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    String note = input.getText().toString().trim();
                    try {
                        if (note.isEmpty()) {
                            habit.notes.remove(dateKey);
                        } else {
                            habit.notes.put(dateKey, note);
                        }
                        saveHabits();
                        renderCalendar();
                    } catch (JSONException ignored) {
                        Toast.makeText(this, "Could not save that note", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void renderDrawerList() {
        habitList.removeAllViews();
        drawerHabitRows.clear();
        drawerHabitNames.clear();

        for (HabitFolder folder : folders) {
            addDrawerSectionHeading(folder);
            for (Habit habit : habits) {
                if (folder.id.equals(habit.type)) {
                    addDrawerHabitRow(habit);
                }
            }
        }
    }

    private void addDrawerSectionHeading(HabitFolder folder) {
        TextView heading = new TextView(this);
        heading.setText(folder.name);
        heading.setTextColor(mutedTextColor());
        heading.setTextSize(12);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(12), 0, dp(12), 0);
        heading.setContentDescription(folder.name + " folder options");
        heading.setOnClickListener(v -> showFolderMenu(heading, folder));

        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, dp(28));
        headingParams.setMargins(0, dp(5), 0, 0);
        habitList.addView(heading, headingParams);

        View divider = new View(this);
        divider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(dp(12), 0, dp(12), dp(5));
        habitList.addView(divider, params);
    }

    private void addDrawerHabitRow(Habit habit) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(2), dp(4), dp(2));
        applyDrawerHabitRowStyle(row, habit);

        TextView name = new TextView(this);
        name.setText(habit.emoji.isEmpty() ? habit.name : habit.emoji + "  " + habit.name);
        name.setTextColor(textColor());
        name.setTextSize(16);
        name.setTypeface(habit.id.equals(selectedHabitId) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        name.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));

        drawerHabitRows.put(habit.id, row);
        drawerHabitNames.put(habit.id, name);

        Button options = iconButton("⋮");
        options.setTextSize(22);
        options.setOnClickListener(v -> showHabitMenu(options, habit));
        row.addView(options, new LinearLayout.LayoutParams(dp(40), dp(40)));

        row.setOnClickListener(v -> {
            selectedHabitId = habit.id;
            prefs.edit().putString(SELECTED_HABIT_KEY, selectedHabitId).apply();
            renderCalendar();
            updateBottomHabitSelection();
            updateDrawerHabitSelection();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(1), 0, dp(1));
        habitList.addView(row, params);
    }

    private void renderBottomHabitSelector() {
        Map<String, Integer> savedScrollPositions = new HashMap<>();
        for (Map.Entry<String, HorizontalScrollView> entry : bottomFolderScrollers.entrySet()) {
            savedScrollPositions.put(entry.getKey(), entry.getValue().getScrollX());
        }

        bottomFolderScrollers.clear();
        bottomHabitCards.clear();
        bottomHabitEmojis.clear();
        bottomHabitNames.clear();
        bottomPanelContent.removeAllViews();
        for (HabitFolder folder : folders) {
            LinearLayout folderHabitList = addHabitTypeSection(bottomPanelContent, folder.name);
            HorizontalScrollView folderScroller = (HorizontalScrollView) folderHabitList.getParent();
            bottomFolderScrollers.put(folder.id, folderScroller);
            int count = 0;
            for (Habit habit : habits) {
                if (folder.id.equals(habit.type)) {
                    folderHabitList.addView(buildHabitSelectorCard(habit), habitSelectorCardParams());
                    count++;
                }
            }
            if (count == 0) {
                folderHabitList.addView(emptyHabitTypeLabel("No habits yet"), habitSelectorCardParams());
            }

            int savedScrollX = savedScrollPositions.containsKey(folder.id)
                    ? savedScrollPositions.get(folder.id)
                    : 0;
            folderScroller.post(() -> folderScroller.scrollTo(savedScrollX, 0));
        }
    }

    private View buildHabitSelectorCard(Habit habit) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        applyHabitSelectorStyle(card, habit);

        TextView emoji = new TextView(this);
        emoji.setText(habit.emoji.isEmpty() ? "+" : habit.emoji);
        emoji.setTextSize(20);
        emoji.setGravity(Gravity.CENTER);
        emoji.setTextColor(textColor());
        card.addView(emoji, new LinearLayout.LayoutParams(dp(28), -1));
        bottomHabitEmojis.put(habit.id, emoji);

        TextView name = new TextView(this);
        name.setText(habit.name);
        name.setTextColor(textColor());
        name.setTextSize(16);
        name.setTypeface(habit.id.equals(selectedHabitId) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -1);
        nameParams.setMargins(dp(8), 0, 0, 0);
        card.addView(name, nameParams);

        bottomHabitCards.put(habit.id, card);
        bottomHabitNames.put(habit.id, name);

        card.setOnClickListener(v -> {
            selectedHabitId = habit.id;
            prefs.edit().putString(SELECTED_HABIT_KEY, selectedHabitId).apply();
            renderCalendar();
            updateBottomHabitSelection();
            updateDrawerHabitSelection();
        });
        return card;
    }

    private void updateBottomHabitSelection() {
        for (Habit habit : habits) {
            View card = bottomHabitCards.get(habit.id);
            TextView name = bottomHabitNames.get(habit.id);
            if (card != null) {
                applyHabitSelectorStyle(card, habit);
            }
            if (name != null) {
                name.setTypeface(habit.id.equals(selectedHabitId) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
        }
    }

    private void updateDrawerHabitSelection() {
        for (Habit habit : habits) {
            LinearLayout row = drawerHabitRows.get(habit.id);
            TextView name = drawerHabitNames.get(habit.id);
            if (row != null) {
                applyDrawerHabitRowStyle(row, habit);
            }
            if (name != null) {
                name.setTypeface(habit.id.equals(selectedHabitId) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
        }
    }

    private void applyDrawerHabitRowStyle(View row, Habit habit) {
        if (!habit.id.equals(selectedHabitId)) {
            row.setBackgroundColor(Color.TRANSPARENT);
            return;
        }
        int folderColor = folderColorForHabit(habit);
        android.graphics.drawable.GradientDrawable selectedBackground = new android.graphics.drawable.GradientDrawable();
        selectedBackground.setColor(selectedFolderBackgroundColor(folderColor));
        selectedBackground.setCornerRadius(dp(14));
        row.setBackground(selectedBackground);
    }

    private View emptyHabitTypeLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(mutedTextColor());
        label.setTextSize(14);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private LinearLayout.LayoutParams habitSelectorCardParams() {
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-2, dp(50));
        cardParams.setMargins(0, dp(4), dp(10), dp(8));
        return cardParams;
    }

    private void cycleDay(Habit habit, String dateKey) {
        int current = habit.states.optInt(dateKey, STATE_EMPTY);
        int nextColor = habit.dateColors.get(0);
        boolean clearDate = false;
        if (current != STATE_EMPTY) {
            int currentColor = resolvedDateColor(habit, current);
            int colorIndex = habit.dateColors.indexOf(currentColor);
            if (colorIndex >= 0 && colorIndex < habit.dateColors.size() - 1) {
                nextColor = habit.dateColors.get(colorIndex + 1);
            } else if (colorIndex == habit.dateColors.size() - 1) {
                clearDate = true;
            }
        }
        try {
            if (clearDate) {
                habit.states.remove(dateKey);
            } else {
                habit.states.put(dateKey, nextColor);
            }
        } catch (JSONException ignored) {
            Toast.makeText(this, "Could not update that day", Toast.LENGTH_SHORT).show();
        }
    }

    private int resolvedDateColor(Habit habit, int storedValue) {
        if (storedValue == STATE_DONE || storedValue == STATE_MISSED) {
            return habit.dateColors.get(0);
        }
        return storedValue == STATE_EMPTY ? habit.dateColors.get(0) : storedValue;
    }

    private View paddedDialogView(View content) {
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(24), dp(8), dp(24), 0);
        wrapper.addView(content, new FrameLayout.LayoutParams(-1, -2));
        return wrapper;
    }

    private void applyDialogInputColors(EditText input) {
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.rgb(90, 90, 90));
    }

    private void updateMonthDrag(float requestedDeltaX) {
        int width = calendarContainer.getWidth();
        if (width == 0) {
            return;
        }
        float deltaX = Math.max(-width, Math.min(width, requestedDeltaX));
        int direction = deltaX < 0 ? 1 : -1;
        Habit habit = findSelectedHabit();
        if (habit == null || !canNavigateMonth(direction, habit)) {
            discardMonthPreview();
            calendarGrid.setTranslationX(0);
            return;
        }
        prepareMonthPreview(direction);
        if (monthPreviewGrid == null) {
            calendarGrid.setTranslationX(0);
            return;
        }
        calendarGrid.setTranslationX(deltaX);
        monthPreviewGrid.setTranslationX(deltaX + direction * width);
    }

    private void prepareMonthPreview(int direction) {
        Habit habit = findSelectedHabit();
        if (habit == null || !canNavigateMonth(direction, habit)) {
            discardMonthPreview();
            return;
        }
        if (monthPreviewGrid != null && monthPreviewDirection == direction) {
            return;
        }
        if (monthPreviewGrid != null) {
            calendarContainer.removeView(monthPreviewGrid);
        }
        monthPreviewDirection = direction;
        monthPreview = direction > 0 ? visibleMonth.plusMonths(1) : visibleMonth.minusMonths(1);
        monthPreviewGrid = createCalendarGrid();
        populateCalendarGrid(monthPreviewGrid, monthPreview, habit, false);
        monthPreviewGrid.setTranslationX(direction * calendarContainer.getWidth());
        calendarContainer.addView(monthPreviewGrid, new FrameLayout.LayoutParams(-1, -1));
    }

    private boolean canNavigateMonth(int direction, Habit habit) {
        if (direction > 0) {
            return visibleMonth.isBefore(YearMonth.now());
        }
        return habit.startDate == null || visibleMonth.isAfter(YearMonth.from(habit.startDate));
    }

    private void clampVisibleMonth(Habit habit) {
        YearMonth currentMonth = YearMonth.now();
        if (visibleMonth.isAfter(currentMonth)) {
            visibleMonth = currentMonth;
        }
        if (habit.startDate != null) {
            YearMonth startMonth = YearMonth.from(habit.startDate);
            if (visibleMonth.isBefore(startMonth)) {
                visibleMonth = startMonth;
            }
        }
    }

    private void discardMonthPreview() {
        if (monthPreviewGrid != null) {
            calendarContainer.removeView(monthPreviewGrid);
        }
        monthPreviewGrid = null;
        monthPreview = null;
        monthPreviewDirection = 0;
    }

    private void finishMonthDrag(float deltaX, float velocityX) {
        if (monthPreviewGrid == null) {
            calendarGrid.setTranslationX(0);
            return;
        }
        int width = calendarContainer.getWidth();
        boolean passedDistanceThreshold = Math.abs(deltaX) >= width * 0.3f;
        boolean flickedTowardPreview = Math.abs(velocityX) >= dp(600)
                && velocityX * monthPreviewDirection < 0;
        boolean complete = passedDistanceThreshold || flickedTowardPreview;
        float currentTarget = complete ? -monthPreviewDirection * width : 0;
        float previewTarget = complete ? 0 : monthPreviewDirection * width;
        isMonthAnimating = true;

        calendarGrid.animate()
                .translationX(currentTarget)
                .setDuration(180)
                .start();

        monthPreviewGrid.animate()
                .translationX(previewTarget)
                .setDuration(180)
                .withEndAction(() -> {
                    if (complete) {
                        visibleMonth = monthPreview;
                    }
                    calendarGrid.setTranslationX(0);
                    calendarContainer.removeView(monthPreviewGrid);
                    monthPreviewGrid = null;
                    monthPreview = null;
                    monthPreviewDirection = 0;
                    renderCalendar();
                    isMonthAnimating = false;
                })
                .start();
    }

    private void recycleMonthVelocityTracker() {
        if (monthVelocityTracker != null) {
            monthVelocityTracker.recycle();
            monthVelocityTracker = null;
        }
    }

    private void showCreateMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Add habit");
        menu.getMenu().add("Add folder");
        menu.setOnMenuItemClickListener(item -> {
            if ("Add folder".contentEquals(item.getTitle())) {
                showAddFolderDialog();
            } else {
                showAddHabitDialog();
            }
            return true;
        });
        menu.show();
    }

    private void showFolderMenu(View anchor, HabitFolder folder) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.CENTER_HORIZONTAL);
        int folderIndex = folders.indexOf(folder);
        if (folderIndex > 0) {
            menu.getMenu().add("Move up");
        }
        if (folderIndex >= 0 && folderIndex < folders.size() - 1) {
            menu.getMenu().add("Move down");
        }
        menu.getMenu().add("Folder color");
        menu.getMenu().add("Rename folder");
        menu.getMenu().add("Delete folder");
        menu.setOnMenuItemClickListener(item -> {
            if ("Move up".contentEquals(item.getTitle())) {
                moveFolder(folder, -1);
            } else if ("Move down".contentEquals(item.getTitle())) {
                moveFolder(folder, 1);
            } else if ("Folder color".contentEquals(item.getTitle())) {
                showFolderColorDialog(folder);
            } else if ("Rename folder".contentEquals(item.getTitle())) {
                showRenameFolderDialog(folder);
            } else {
                confirmDeleteFolder(folder);
            }
            return true;
        });
        menu.show();
    }

    private void moveFolder(HabitFolder folder, int direction) {
        int currentIndex = folders.indexOf(folder);
        int destinationIndex = currentIndex + direction;
        if (currentIndex < 0 || destinationIndex < 0 || destinationIndex >= folders.size()) {
            return;
        }
        folders.remove(currentIndex);
        folders.add(destinationIndex, folder);
        saveFolders();
        renderAll();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_CALENDAR_BACKGROUND_REQUEST
                || resultCode != RESULT_OK
                || data == null) {
            return;
        }
        Uri imageUri = data.getData();
        if (imageUri == null) {
            return;
        }
        Bitmap selectedImage = decodePickedImage(imageUri);
        if (selectedImage == null) {
            Toast.makeText(this, "Could not open that picture", Toast.LENGTH_SHORT).show();
            return;
        }
        showCalendarBackgroundCropDialog(selectedImage);
    }

    private void showAddFolderDialog() {
        EditText input = folderNameInput("New folder");
        new AlertDialog.Builder(this)
                .setTitle("Add folder")
                .setView(paddedDialogView(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!isValidFolderName(name, null)) {
                        return;
                    }
                    folders.add(new HabitFolder("folder-" + System.currentTimeMillis(), name, DEFAULT_FOLDER_COLOR));
                    saveFolders();
                    renderAll();
                })
                .show();
    }

    private void showRenameFolderDialog(HabitFolder folder) {
        EditText input = folderNameInput(folder.name);
        input.setText(folder.name);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Rename folder")
                .setView(paddedDialogView(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!isValidFolderName(name, folder)) {
                        return;
                    }
                    folder.name = name;
                    saveFolders();
                    renderAll();
                })
                .show();
    }

    private EditText folderNameInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        applyDialogInputColors(input);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return input;
    }

    private boolean isValidFolderName(String name, HabitFolder currentFolder) {
        if (name.isEmpty()) {
            Toast.makeText(this, "Name the folder first", Toast.LENGTH_SHORT).show();
            return false;
        }
        for (HabitFolder folder : folders) {
            if (folder != currentFolder && folder.name.equalsIgnoreCase(name)) {
                Toast.makeText(this, "That folder already exists", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void confirmDeleteFolder(HabitFolder folder) {
        if (folders.size() == 1) {
            Toast.makeText(this, "Keep at least one folder", Toast.LENGTH_SHORT).show();
            return;
        }
        HabitFolder destination = null;
        for (HabitFolder candidate : folders) {
            if (candidate != folder) {
                destination = candidate;
                break;
            }
        }
        HabitFolder moveDestination = destination;
        new AlertDialog.Builder(this)
                .setTitle("Delete " + folder.name + "?")
                .setMessage("Its habits and history will be moved to " + moveDestination.name + ".")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (Habit habit : habits) {
                        if (folder.id.equals(habit.type)) {
                            habit.type = moveDestination.id;
                        }
                    }
                    folders.remove(folder);
                    saveFolders();
                    saveHabits();
                    renderAll();
                })
                .show();
    }

    private void showHabitMenu(View anchor, Habit habit) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (findAdjacentHabitIndex(habit, -1) >= 0) {
            menu.getMenu().add("Move up");
        }
        if (findAdjacentHabitIndex(habit, 1) >= 0) {
            menu.getMenu().add("Move down");
        }
        menu.getMenu().add("Select color");
        menu.getMenu().add(habit.startDate == null ? "Set start date" : "Change start date");
        if (habit.startDate != null) {
            menu.getMenu().add("Remove start date");
        }
        menu.getMenu().add("Rename");
        for (HabitFolder folder : folders) {
            if (!folder.id.equals(habit.type)) {
                menu.getMenu().add("Move to " + folder.name);
            }
        }
        menu.getMenu().add("Delete");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Move up".equals(title)) {
                moveHabitWithinFolder(habit, -1);
            } else if ("Move down".equals(title)) {
                moveHabitWithinFolder(habit, 1);
            } else if ("Select color".equals(title)) {
                showHabitColorDialog(habit);
            } else if ("Set start date".equals(title) || "Change start date".equals(title)) {
                showHabitStartDatePicker(habit);
            } else if ("Remove start date".equals(title)) {
                habit.startDate = null;
                saveHabits();
                renderCalendar();
            } else if ("Rename".equals(title)) {
                showRenameDialog(habit);
            } else if ("Delete".equals(title)) {
                confirmDelete(habit);
            } else if (title.startsWith("Move to ")) {
                String folderName = title.substring("Move to ".length());
                for (HabitFolder folder : folders) {
                    if (folder.name.equals(folderName)) {
                        habit.type = folder.id;
                        saveHabits();
                        renderAll();
                        break;
                    }
                }
            }
            return true;
        });
        menu.show();
    }

    private void showHabitStartDatePicker(Habit habit) {
        LocalDate initialDate = habit.startDate == null ? LocalDate.now() : habit.startDate;
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    habit.startDate = LocalDate.of(year, month + 1, dayOfMonth);
                    visibleMonth = YearMonth.from(habit.startDate);
                    saveHabits();
                    discardMonthPreview();
                    renderCalendar();
                },
                initialDate.getYear(),
                initialDate.getMonthValue() - 1,
                initialDate.getDayOfMonth()
        );
        dialog.setTitle("Habit start date");
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void showHabitColorDialog(Habit habit) {
        List<Integer> selectedColors = new ArrayList<>();
        for (int color : COLOR_OPTIONS) {
            if (habit.dateColors.contains(color)) {
                selectedColors.add(color);
            }
        }
        if (selectedColors.isEmpty()) {
            selectedColors.add(DEFAULT_HABIT_DATE_COLOR);
        }

        GridLayout palette = new GridLayout(this);
        palette.setColumnCount(3);
        palette.setPadding(dp(8), dp(8), dp(8), dp(8));
        List<View> swatches = new ArrayList<>();
        for (int index = 0; index < COLOR_OPTIONS.length; index++) {
            int color = COLOR_OPTIONS[index];
            View swatch = createColorSwatch(color, COLOR_OPTION_NAMES[index], selectedColors.contains(color));
            swatch.setOnClickListener(v -> {
                if (selectedColors.contains(color)) {
                    if (selectedColors.size() == 1) {
                        Toast.makeText(this, "Keep at least one habit color", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedColors.remove(Integer.valueOf(color));
                } else {
                    selectedColors.add(color);
                }
                applyColorSwatchStyle(swatch, color, selectedColors.contains(color));
            });
            palette.addView(swatch, colorSwatchParams());
            swatches.add(swatch);
        }

        new AlertDialog.Builder(this)
                .setTitle("Habit colors")
                .setView(paddedDialogView(palette))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    habit.dateColors.clear();
                    for (int color : COLOR_OPTIONS) {
                        if (selectedColors.contains(color)) {
                            habit.dateColors.add(color);
                        }
                    }
                    saveHabits();
                    renderCalendar();
                })
                .show();
    }

    private void showFolderColorDialog(HabitFolder folder) {
        showColorDialog("Folder color", folder.color, color -> {
            folder.color = color;
            saveFolders();
            updateBottomHabitSelection();
            updateDrawerHabitSelection();
        });
    }

    private void showColorDialog(String title, int selectedColor, ColorPickedListener listener) {
        GridLayout palette = new GridLayout(this);
        palette.setColumnCount(3);
        palette.setPadding(dp(8), dp(8), dp(8), dp(8));
        List<View> swatches = new ArrayList<>();
        for (int index = 0; index < COLOR_OPTIONS.length; index++) {
            View swatch = createColorSwatch(
                    COLOR_OPTIONS[index],
                    COLOR_OPTION_NAMES[index],
                    COLOR_OPTIONS[index] == selectedColor
            );
            palette.addView(swatch, colorSwatchParams());
            swatches.add(swatch);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(paddedDialogView(palette))
                .setNegativeButton("Cancel", null)
                .create();
        for (int index = 0; index < swatches.size(); index++) {
            int color = COLOR_OPTIONS[index];
            swatches.get(index).setOnClickListener(v -> {
                listener.onColorPicked(color);
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    private View createColorSwatch(int color, String name, boolean selected) {
        View swatch = new View(this);
        swatch.setContentDescription(name);
        applyColorSwatchStyle(swatch, color, selected);
        return swatch;
    }

    private void applyColorSwatchStyle(View swatch, int color, boolean selected) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        background.setColor(color);
        background.setStroke(dp(selected ? 4 : 1), selected ? Color.BLACK : borderColor());
        swatch.setBackground(background);
    }

    private GridLayout.LayoutParams colorSwatchParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(52);
        params.height = dp(52);
        params.setMargins(dp(10), dp(8), dp(10), dp(8));
        return params;
    }

    private interface ColorPickedListener {
        void onColorPicked(int color);
    }

    private int findAdjacentHabitIndex(Habit habit, int direction) {
        int currentIndex = habits.indexOf(habit);
        for (int index = currentIndex + direction;
             index >= 0 && index < habits.size();
             index += direction) {
            if (habit.type.equals(habits.get(index).type)) {
                return index;
            }
        }
        return -1;
    }

    private void moveHabitWithinFolder(Habit habit, int direction) {
        int currentIndex = habits.indexOf(habit);
        int destinationIndex = findAdjacentHabitIndex(habit, direction);
        if (currentIndex < 0 || destinationIndex < 0) {
            return;
        }
        Habit displacedHabit = habits.get(destinationIndex);
        habits.set(destinationIndex, habit);
        habits.set(currentIndex, displacedHabit);
        saveHabits();
        renderDrawerList();
        renderBottomHabitSelector();
    }

    private void showRenameDialog(Habit habit) {
        EditText input = new EditText(this);
        input.setText(habit.name);
        applyDialogInputColors(input);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        new AlertDialog.Builder(this)
                .setTitle("Rename habit")
                .setView(paddedDialogView(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name the habit first", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    habit.name = name;
                    saveHabits();
                    renderAll();
                })
                .show();
    }

    private void showAddHabitDialog() {
        EditText input = new EditText(this);
        input.setHint("New habit");
        applyDialogInputColors(input);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        new AlertDialog.Builder(this)
                .setTitle("Habit Data")
                .setView(paddedDialogView(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name the habit first", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Habit selectedHabit = findSelectedHabit();
                    String folderId = selectedHabit == null ? folders.get(0).id : selectedHabit.type;
                    Habit habit = new Habit("habit-" + System.currentTimeMillis(), name, "", folderId, defaultHabitColors(), new JSONObject(), new JSONObject(), null);
                    habits.add(habit);
                    selectedHabitId = habit.id;
                    prefs.edit().putString(SELECTED_HABIT_KEY, selectedHabitId).apply();
                    saveHabits();
                    renderAll();
                })
                .show();
    }

    private void showEmojiDialog() {
        Habit habit = findSelectedHabit();
        if (habit == null) {
            return;
        }

        EditText input = new EditText(this);
        input.setText(habit.emoji);
        input.setHint("Emoji");
        applyDialogInputColors(input);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(26);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Habit Emoji")
                .setView(paddedDialogView(input))
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (dialog, which) -> {
                    habit.emoji = "";
                    saveHabits();
                    updateHabitEmojiViews(habit);
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    habit.emoji = firstEmojiLikeText(input.getText().toString().trim());
                    saveHabits();
                    updateHabitEmojiViews(habit);
                })
                .show();
    }

    private void updateHabitEmojiViews(Habit habit) {
        TextView bottomEmoji = bottomHabitEmojis.get(habit.id);
        if (bottomEmoji != null) {
            bottomEmoji.setText(habit.emoji.isEmpty() ? "+" : habit.emoji);
        }

        TextView drawerName = drawerHabitNames.get(habit.id);
        if (drawerName != null) {
            drawerName.setText(habit.emoji.isEmpty()
                    ? habit.name
                    : habit.emoji + "  " + habit.name);
        }

        if (habit.id.equals(selectedHabitId) && habitEmojiButton != null) {
            habitEmojiButton.setText(habit.emoji.isEmpty() ? "+" : habit.emoji);
        }
    }

    private void showSleepTimeDialog() {
        int hour = sleepTimeMinutes / 60;
        int minute = sleepTimeMinutes % 60;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    sleepTimeMinutes = selectedHour * 60 + selectedMinute;
                    prefs.edit().putInt(SLEEP_TIME_MINUTES_KEY, sleepTimeMinutes).apply();
                    renderSleepCountdown();
                    renderSettingsValues();
                },
                hour,
                minute,
                false
        );
        dialog.setTitle("Sleep time");
        dialog.show();
    }

    private void showSettings() {
        if (settingsLayer == null) {
            return;
        }
        recycleMonthVelocityTracker();
        monthSwipeInProgress = false;
        page.setVisibility(View.GONE);
        settingsLayer.setVisibility(View.VISIBLE);
    }

    private void hideSettings() {
        if (settingsLayer == null || settingsLayer.getVisibility() != View.VISIBLE) {
            return;
        }
        settingsLayer.setVisibility(View.GONE);
        page.setVisibility(View.VISIBLE);
    }

    private String normalizedAppFont(String value) {
        if (FONT_SERIF.equals(value) || FONT_TORONTO_SUBWAY.equals(value)) {
            return value;
        }
        return FONT_DEFAULT;
    }

    private Typeface selectedAppTypeface() {
        if (FONT_SERIF.equals(appFont)) {
            return Typeface.SERIF;
        }
        if (FONT_TORONTO_SUBWAY.equals(appFont)) {
            return getResources().getFont(R.font.toronto_subway);
        }
        return Typeface.create("sans-serif-rounded", Typeface.NORMAL);
    }

    private void applyAppFont(View view, Typeface appTypeface) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Typeface currentTypeface = textView.getTypeface();
            int style = currentTypeface == null ? Typeface.NORMAL : currentTypeface.getStyle();
            textView.setTypeface(appTypeface, style);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                applyAppFont(group.getChildAt(index), appTypeface);
            }
        }
    }

    private void showFontMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        menu.getMenu().add(FONT_DEFAULT);
        menu.getMenu().add(FONT_SERIF);
        menu.getMenu().add(FONT_TORONTO_SUBWAY);
        menu.setOnMenuItemClickListener(item -> {
            setAppFont(item.getTitle().toString());
            return true;
        });
        menu.show();
    }

    private void setAppFont(String selectedFont) {
        String normalizedFont = normalizedAppFont(selectedFont);
        if (normalizedFont.equals(appFont)) {
            return;
        }
        appFont = normalizedFont;
        prefs.edit().putString(APP_FONT_KEY, appFont).apply();
        buildUi();
        renderAll();
        showSettings();
    }

    private void setHabitTitleCentered(boolean centered) {
        if (isHabitTitleCentered == centered) {
            return;
        }
        isHabitTitleCentered = centered;
        prefs.edit().putBoolean(CENTER_HABIT_TITLE_KEY, centered).apply();
        buildUi();
        renderAll();
        showSettings();
    }

    private void showHabitTitleAlignmentMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        menu.getMenu().add("Left");
        menu.getMenu().add("Center");
        menu.setOnMenuItemClickListener(item -> {
            setHabitTitleCentered("Center".contentEquals(item.getTitle()));
            return true;
        });
        menu.show();
    }

    private String unselectedDateStyleName() {
        if (unselectedDateStyle == UNSELECTED_STYLE_BLANK) {
            return "Blank";
        }
        if (unselectedDateStyle == UNSELECTED_STYLE_HORIZONTAL) {
            return "Horizontal";
        }
        return "Diagonal";
    }

    private void showUnselectedDateStyleMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        menu.getMenu().add("Blank");
        menu.getMenu().add("Diagonal");
        menu.getMenu().add("Horizontal");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            int selectedStyle = "Blank".equals(title)
                    ? UNSELECTED_STYLE_BLANK
                    : "Horizontal".equals(title)
                    ? UNSELECTED_STYLE_HORIZONTAL
                    : UNSELECTED_STYLE_DIAGONAL;
            setUnselectedDateStyle(selectedStyle);
            return true;
        });
        menu.show();
    }

    private void setUnselectedDateStyle(int style) {
        if (unselectedDateStyle == style) {
            return;
        }
        unselectedDateStyle = style;
        prefs.edit().putInt(UNSELECTED_DATE_STYLE_KEY, style).apply();
        buildUi();
        renderAll();
        showSettings();
    }

    private void openImagePicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    private void showCalendarBackgroundMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        menu.getMenu().add(calendarBackgroundBitmap == null ? "Choose picture" : "Replace picture");
        if (calendarBackgroundBitmap != null) {
            menu.getMenu().add("Remove picture");
        }
        menu.setOnMenuItemClickListener(item -> {
            if ("Remove picture".contentEquals(item.getTitle())) {
                removeCalendarBackground();
            } else {
                openImagePicker(PICK_CALENDAR_BACKGROUND_REQUEST);
            }
            return true;
        });
        menu.show();
    }

    private void showCalendarBackgroundCropDialog(Bitmap selectedImage) {
        float calendarAspectRatio = 360f / 414f;
        PhotoCropView cropView = new PhotoCropView(this, selectedImage, calendarAspectRatio);
        new AlertDialog.Builder(this)
                .setTitle("Crop calendar background")
                .setView(paddedDialogView(cropView))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    Bitmap croppedImage = cropView.createCroppedBitmap(720, 828);
                    if (croppedImage == null || !savePrivateBitmap(CALENDAR_BACKGROUND_FILE, croppedImage)) {
                        if (croppedImage != null) {
                            croppedImage.recycle();
                        }
                        Toast.makeText(this, "Could not save that picture", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    calendarBackgroundBitmap = croppedImage;
                    rebuildUiKeepingSettingsOpen();
                })
                .show();
    }

    private Bitmap decodePickedImage(Uri imageUri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = getContentResolver().openInputStream(imageUri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        } catch (IOException ignored) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        int largestSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (largestSide / options.inSampleSize > 2048) {
            options.inSampleSize *= 2;
        }

        Bitmap decoded;
        try (InputStream stream = getContentResolver().openInputStream(imageUri)) {
            decoded = BitmapFactory.decodeStream(stream, null, options);
        } catch (IOException ignored) {
            return null;
        }
        if (decoded == null) {
            return null;
        }

        int rotation = readImageRotation(imageUri);
        if (rotation == 0) {
            return decoded;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
        if (rotated != decoded) {
            decoded.recycle();
        }
        return rotated;
    }

    private int readImageRotation(Uri imageUri) {
        try (InputStream stream = getContentResolver().openInputStream(imageUri)) {
            if (stream == null) {
                return 0;
            }
            int orientation = new ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                return 90;
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                return 180;
            }
            return orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (IOException ignored) {
            return 0;
        }
    }

    private boolean savePrivateBitmap(String fileName, Bitmap bitmap) {
        try (FileOutputStream stream = openFileOutput(fileName, Context.MODE_PRIVATE)) {
            return bitmap.compress(Bitmap.CompressFormat.WEBP, 88, stream);
        } catch (IOException ignored) {
            return false;
        }
    }

    private Bitmap loadPrivateBitmap(String fileName) {
        File photoFile = getFileStreamPath(fileName);
        return photoFile.exists() ? BitmapFactory.decodeFile(photoFile.getAbsolutePath()) : null;
    }

    private void removeCalendarBackground() {
        deleteFile(CALENDAR_BACKGROUND_FILE);
        calendarBackgroundBitmap = null;
        rebuildUiKeepingSettingsOpen();
    }

    private void rebuildUiKeepingSettingsOpen() {
        buildUi();
        renderAll();
        showSettings();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayer != null && drawerLayer.getVisibility() == View.VISIBLE) {
            hideDrawer();
            return;
        }
        if (settingsLayer != null && settingsLayer.getVisibility() == View.VISIBLE) {
            hideSettings();
            return;
        }
        super.onBackPressed();
    }

    private String firstEmojiLikeText(String value) {
        if (value.isEmpty()) {
            return "";
        }
        int firstCodePoint = value.codePointAt(0);
        int nextIndex = Character.charCount(firstCodePoint);
        if (nextIndex < value.length() && value.codePointAt(nextIndex) == 0xFE0F) {
            nextIndex += Character.charCount(0xFE0F);
        }
        return value.substring(0, nextIndex);
    }

    private void applyEmojiButtonStyle() {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        drawable.setColor(panelColor());
        drawable.setStroke(dp(1), borderColor());
        habitEmojiButton.setBackground(drawable);
        habitEmojiButton.setTextColor(textColor());
    }

    private void applyHabitSelectorStyle(View view, Habit habit) {
        boolean selected = habit.id.equals(selectedHabitId);
        int folderColor = folderColorForHabit(habit);
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(selected ? selectedFolderBackgroundColor(folderColor) : panelColor());
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), selected ? folderColor : borderColor());
        view.setBackground(drawable);
    }

    private void applyDayStyle(TextView view, boolean recorded, boolean isToday, int completedColor) {
        int background;
        int text;
        if (recorded) {
            background = completedColor;
            text = Color.WHITE;
        } else {
            int panel = panelColor();
            background = calendarBackgroundBitmap == null
                    ? panel
                    : Color.argb(142, Color.red(panel), Color.green(panel), Color.blue(panel));
            text = calendarBackgroundBitmap == null ? textColor() : Color.WHITE;
        }

        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(background);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(isToday ? dp(2) : dp(1), isToday ? accentColor() : borderColor());
        view.setBackground(drawable);
        view.setTextColor(text);
    }

    private void applyFutureDayStyle(TextView view) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        int panel = panelColor();
        drawable.setColor(calendarBackgroundBitmap == null
                ? panel
                : Color.argb(128, Color.red(panel), Color.green(panel), Color.blue(panel)));
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), borderColor());
        view.setBackground(drawable);
        view.setTextColor(mutedTextColor());
        view.setAlpha(0.48f);
        view.setClickable(false);
    }

    private void confirmDelete(Habit habit) {
        if (habits.size() == 1) {
            Toast.makeText(this, "Keep at least one habit", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete habit?")
                .setMessage(habit.name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    habits.remove(habit);
                    if (habit.id.equals(selectedHabitId)) {
                        selectedHabitId = habits.get(0).id;
                        prefs.edit().putString(SELECTED_HABIT_KEY, selectedHabitId).apply();
                    }
                    saveHabits();
                    renderAll();
                })
                .show();
    }

    private void showDrawer() {
        if (isDrawerAnimating || drawerLayer.getVisibility() == View.VISIBLE) {
            return;
        }
        isDrawerAnimating = true;
        drawerContent.setTranslationX(-dp(304));
        drawerLayer.setAlpha(0f);
        drawerLayer.setVisibility(View.VISIBLE);
        drawerLayer.animate()
                .alpha(1f)
                .setDuration(180)
                .start();
        drawerContent.animate()
                .translationX(0)
                .setDuration(220)
                .withEndAction(() -> isDrawerAnimating = false)
                .start();
    }

    private void restoreOpenDrawer(int scrollY) {
        drawerLayer.animate().cancel();
        drawerContent.animate().cancel();
        drawerLayer.setVisibility(View.VISIBLE);
        drawerLayer.setAlpha(1f);
        drawerContent.setTranslationX(0);
        isDrawerAnimating = false;
        drawerScroll.post(() -> drawerScroll.scrollTo(0, scrollY));
    }

    private void hideDrawer() {
        if (isDrawerAnimating || drawerLayer.getVisibility() != View.VISIBLE) {
            return;
        }
        isDrawerAnimating = true;
        drawerLayer.animate()
                .alpha(0f)
                .setDuration(180)
                .start();
        drawerContent.animate()
                .translationX(-dp(304))
                .setDuration(200)
                .withEndAction(() -> {
                    drawerLayer.setVisibility(View.GONE);
                    drawerContent.setTranslationX(0);
                    isDrawerAnimating = false;
                })
                .start();
    }

    private void installInsetPanels() {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();

            topBar.setPadding(dp(10), topInset + dp(18), dp(10), dp(10));
            ViewGroup.LayoutParams topParams = topBar.getLayoutParams();
            topParams.height = topInset + dp(84);
            topBar.setLayoutParams(topParams);

            settingsLayer.setPadding(dp(10), topInset + dp(18), dp(10), dp(10));

            bottomPanel.setMinimumHeight(bottomInset + dp(112));
            bottomPanel.setPadding(dp(20), dp(14), dp(20), bottomInset + dp(18));

            drawerContent.setPadding(dp(18), topInset + dp(26), dp(18), bottomInset + dp(18));
            return insets;
        });
    }

    private boolean isMonthDrag(MotionEvent event) {
        float deltaX = event.getRawX() - monthSwipeStartX;
        float deltaY = event.getRawY() - monthSwipeStartY;
        return Math.abs(deltaX) > dp(12) && Math.abs(deltaX) > Math.abs(deltaY) * 1.5f;
    }

    private Button iconButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(text.length() > 2 ? 13 : 24);
        button.setTextColor(textColor());
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(22);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(accentColor());
        drawable.setCornerRadius(dp(8));
        button.setBackground(drawable);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(textColor());
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(panelColor());
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), borderColor());
        button.setBackground(drawable);
        return button;
    }

    private int surfaceColor() {
        return isDarkMode ? Color.rgb(15, 23, 42) : Color.rgb(248, 250, 252);
    }

    private int panelColor() {
        return isDarkMode ? Color.rgb(30, 41, 59) : Color.WHITE;
    }

    private int textColor() {
        return isDarkMode ? Color.rgb(241, 245, 249) : Color.rgb(15, 23, 42);
    }

    private int mutedTextColor() {
        return isDarkMode ? Color.rgb(148, 163, 184) : Color.rgb(71, 85, 105);
    }

    private int borderColor() {
        return isDarkMode ? Color.rgb(51, 65, 85) : Color.rgb(226, 232, 240);
    }

    private int selectedRowColor() {
        return isDarkMode ? Color.rgb(20, 83, 45) : Color.rgb(220, 252, 231);
    }

    private int accentColor() {
        return isDarkMode ? Color.rgb(45, 212, 191) : Color.rgb(15, 118, 110);
    }

    private int folderColorForHabit(Habit habit) {
        for (HabitFolder folder : folders) {
            if (folder.id.equals(habit.type)) {
                return folder.color;
            }
        }
        return DEFAULT_FOLDER_COLOR;
    }

    private int selectedFolderBackgroundColor(int folderColor) {
        return blendColors(panelColor(), folderColor, isDarkMode ? 0.38f : 0.18f);
    }

    private int blendColors(int baseColor, int overlayColor, float overlayAmount) {
        float baseAmount = 1f - overlayAmount;
        return Color.rgb(
                Math.round(Color.red(baseColor) * baseAmount + Color.red(overlayColor) * overlayAmount),
                Math.round(Color.green(baseColor) * baseAmount + Color.green(overlayColor) * overlayAmount),
                Math.round(Color.blue(baseColor) * baseAmount + Color.blue(overlayColor) * overlayAmount)
        );
    }

    private void applySystemBarTheme() {
        getWindow().setStatusBarColor(surfaceColor());
        getWindow().setNavigationBarColor(panelColor());
        getWindow().getDecorView().setSystemUiVisibility(isDarkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private Habit findSelectedHabit() {
        for (Habit habit : habits) {
            if (habit.id.equals(selectedHabitId)) {
                return habit;
            }
        }
        return null;
    }

    private String normalizedHabitType(String type) {
        for (HabitFolder folder : folders) {
            if (folder.id.equals(type)) {
                return type;
            }
        }
        return folders.get(0).id;
    }

    private int startColumn(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue() % 7;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void loadFolders() {
        folders.clear();
        String raw = prefs.getString(FOLDERS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String id = item.optString("id", "").trim();
                String name = item.optString("name", "").trim();
                if (!id.isEmpty() && !name.isEmpty()) {
                    int defaultColor = HABIT_TYPE_BAD.equals(id) ? DEFAULT_BAD_FOLDER_COLOR : DEFAULT_FOLDER_COLOR;
                    folders.add(new HabitFolder(id, name, item.optInt("color", defaultColor)));
                }
            }
        } catch (JSONException ignored) {
            folders.clear();
        }
        if (folders.isEmpty()) {
            folders.add(new HabitFolder(HABIT_TYPE_GOOD, "Good Habits", DEFAULT_FOLDER_COLOR));
            folders.add(new HabitFolder(HABIT_TYPE_BAD, "Bad Habits", DEFAULT_BAD_FOLDER_COLOR));
            saveFolders();
        }
    }

    private void saveFolders() {
        JSONArray array = new JSONArray();
        try {
            for (HabitFolder folder : folders) {
                JSONObject item = new JSONObject();
                item.put("id", folder.id);
                item.put("name", folder.name);
                item.put("color", folder.color);
                array.put(item);
            }
        } catch (JSONException ignored) {
            Toast.makeText(this, "Could not save folders", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putString(FOLDERS_KEY, array.toString()).apply();
    }

    private void migrateToSingleDateState() {
        if (prefs.getBoolean(SINGLE_STATE_MIGRATION_KEY, false)) {
            return;
        }
        for (Habit habit : habits) {
            Iterator<String> dates = habit.states.keys();
            while (dates.hasNext()) {
                String dateKey = dates.next();
                if (habit.states.optInt(dateKey, STATE_EMPTY) == STATE_MISSED) {
                    try {
                        habit.states.put(dateKey, STATE_DONE);
                    } catch (JSONException ignored) {
                        Toast.makeText(this, "Could not migrate a date", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        saveHabits();
        prefs.edit().putBoolean(SINGLE_STATE_MIGRATION_KEY, true).apply();
    }

    private static List<Integer> defaultHabitColors() {
        List<Integer> colors = new ArrayList<>();
        colors.add(DEFAULT_HABIT_DATE_COLOR);
        return colors;
    }

    private List<Integer> readHabitColors(JSONObject item) {
        List<Integer> colors = new ArrayList<>();
        JSONArray storedColors = item.optJSONArray("dateColors");
        if (storedColors != null) {
            for (int index = 0; index < storedColors.length() && colors.size() < COLOR_OPTIONS.length; index++) {
                int color = storedColors.optInt(index, 0);
                if (color != 0 && !colors.contains(color)) {
                    colors.add(color);
                }
            }
        }
        if (colors.isEmpty()) {
            colors.add(item.optInt("dateColor", DEFAULT_HABIT_DATE_COLOR));
        }
        return colors;
    }

    private LocalDate parseHabitStartDate(String storedDate) {
        if (storedDate == null || storedDate.trim().isEmpty()) {
            return null;
        }
        try {
            LocalDate startDate = LocalDate.parse(storedDate);
            return startDate.isAfter(LocalDate.now()) ? LocalDate.now() : startDate;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void loadHabits() {
        habits.clear();
        String raw = prefs.getString(HABITS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                habits.add(new Habit(
                        item.getString("id"),
                        item.getString("name"),
                        item.optString("emoji", ""),
                        normalizedHabitType(item.optString("type", HABIT_TYPE_GOOD)),
                        readHabitColors(item),
                        item.optJSONObject("states") == null ? new JSONObject() : item.optJSONObject("states"),
                        item.optJSONObject("notes") == null ? new JSONObject() : item.optJSONObject("notes"),
                        parseHabitStartDate(item.optString("startDate", ""))
                ));
            }
        } catch (JSONException ignored) {
            habits.clear();
        }
    }

    private void saveHabits() {
        JSONArray array = new JSONArray();
        try {
            for (Habit habit : habits) {
                JSONObject item = new JSONObject();
                item.put("id", habit.id);
                item.put("name", habit.name);
                item.put("emoji", habit.emoji);
                item.put("type", habit.type);
                item.put("dateColor", habit.dateColors.get(0));
                JSONArray dateColors = new JSONArray();
                for (int color : habit.dateColors) {
                    dateColors.put(color);
                }
                item.put("dateColors", dateColors);
                item.put("states", habit.states);
                item.put("notes", habit.notes);
                if (habit.startDate != null) {
                    item.put("startDate", habit.startDate.toString());
                }
                array.put(item);
            }
        } catch (JSONException ignored) {
            Toast.makeText(this, "Could not save habits", Toast.LENGTH_SHORT).show();
        }
        prefs.edit()
                .putString(HABITS_KEY, array.toString())
                .putString(SELECTED_HABIT_KEY, selectedHabitId)
                .apply();
    }

    private static class Habit {
        final String id;
        String name;
        String emoji;
        String type;
        final List<Integer> dateColors;
        final JSONObject states;
        final JSONObject notes;
        LocalDate startDate;

        Habit(String id, String name, String emoji, String type, List<Integer> dateColors, JSONObject states, JSONObject notes, LocalDate startDate) {
            this.id = id;
            this.name = name;
            this.emoji = emoji;
            this.type = type;
            this.dateColors = dateColors;
            this.states = states;
            this.notes = notes;
            this.startDate = startDate;
        }
    }

    private static class HabitFolder {
        final String id;
        String name;
        int color;

        HabitFolder(String id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    private class PhotoCropView extends View {
        private final Bitmap bitmap;
        private final float aspectRatio;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float minimumScale;
        private float imageScale;
        private float offsetX;
        private float offsetY;
        private float lastX;
        private float lastY;
        private float pinchStartDistance;
        private float pinchStartScale;
        private float pinchFocusX;
        private float pinchFocusY;
        private float pinchStartOffsetX;
        private float pinchStartOffsetY;

        PhotoCropView(Context context, Bitmap bitmap, float aspectRatio) {
            super(context);
            this.bitmap = bitmap;
            this.aspectRatio = aspectRatio;
            setBackgroundColor(Color.BLACK);
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int availableWidth = View.MeasureSpec.getSize(widthMeasureSpec);
            int width = Math.min(dp(320), availableWidth > 0 ? availableWidth : dp(320));
            int height = Math.round(width / aspectRatio);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            minimumScale = Math.max(
                    width / (float) bitmap.getWidth(),
                    height / (float) bitmap.getHeight()
            );
            imageScale = minimumScale;
            offsetX = (width - bitmap.getWidth() * imageScale) * 0.5f;
            offsetY = (height - bitmap.getHeight() * imageScale) * 0.5f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.translate(offsetX, offsetY);
            canvas.scale(imageScale, imageScale);
            canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
            canvas.restore();
            canvas.drawRect(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1), borderPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2) {
                        pinchStartDistance = pointerDistance(event);
                        pinchStartScale = imageScale;
                        pinchFocusX = (event.getX(0) + event.getX(1)) * 0.5f;
                        pinchFocusY = (event.getY(0) + event.getY(1)) * 0.5f;
                        pinchStartOffsetX = offsetX;
                        pinchStartOffsetY = offsetY;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() >= 2 && pinchStartDistance > 0) {
                        float focusX = (event.getX(0) + event.getX(1)) * 0.5f;
                        float focusY = (event.getY(0) + event.getY(1)) * 0.5f;
                        float requestedScale = pinchStartScale * pointerDistance(event) / pinchStartDistance;
                        imageScale = Math.max(minimumScale, Math.min(minimumScale * 6f, requestedScale));
                        float scaleRatio = imageScale / pinchStartScale;
                        offsetX = focusX - (pinchFocusX - pinchStartOffsetX) * scaleRatio;
                        offsetY = focusY - (pinchFocusY - pinchStartOffsetY) * scaleRatio;
                    } else {
                        offsetX += event.getX() - lastX;
                        offsetY += event.getY() - lastY;
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                    constrainImage();
                    invalidate();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    pinchStartDistance = 0;
                    int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
                    if (remainingIndex < event.getPointerCount()) {
                        lastX = event.getX(remainingIndex);
                        lastY = event.getY(remainingIndex);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pinchStartDistance = 0;
                    return true;
                default:
                    return true;
            }
        }

        Bitmap createCroppedBitmap(int outputWidth, int outputHeight) {
            if (getWidth() <= 0 || getHeight() <= 0 || bitmap.isRecycled()) {
                return null;
            }
            Bitmap output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            float outputScaleX = outputWidth / (float) getWidth();
            float outputScaleY = outputHeight / (float) getHeight();
            canvas.translate(offsetX * outputScaleX, offsetY * outputScaleY);
            canvas.scale(imageScale * outputScaleX, imageScale * outputScaleY);
            canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
            return output;
        }

        private float pointerDistance(MotionEvent event) {
            float deltaX = event.getX(0) - event.getX(1);
            float deltaY = event.getY(0) - event.getY(1);
            return (float) Math.hypot(deltaX, deltaY);
        }

        private void constrainImage() {
            float imageWidth = bitmap.getWidth() * imageScale;
            float imageHeight = bitmap.getHeight() * imageScale;
            offsetX = Math.min(0, Math.max(getWidth() - imageWidth, offsetX));
            offsetY = Math.min(0, Math.max(getHeight() - imageHeight, offsetY));
        }
    }

    private class DayTextView extends TextView {
        private final Paint unselectedMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int unselectedMarkStyle = UNSELECTED_STYLE_BLANK;
        private boolean hasNote;
        private Drawable startDateHat;

        DayTextView(Context context) {
            super(context);
            unselectedMarkPaint.setStrokeWidth(dp(4));
            unselectedMarkPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setUnselectedDateStyle(int style) {
            unselectedMarkStyle = style;
            invalidate();
        }

        void setStartDateHatResource(int drawableResource) {
            startDateHat = drawableResource == 0 ? null : getDrawable(drawableResource);
            invalidate();
        }

        void setHasNote(boolean show) {
            hasNote = show;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (unselectedMarkStyle == UNSELECTED_STYLE_DIAGONAL) {
                unselectedMarkPaint.setColor(calendarBackgroundBitmap == null ? mutedTextColor() : Color.WHITE);
                canvas.drawLine(
                        getWidth() * 0.12f,
                        getHeight() * 0.84f,
                        getWidth() * 0.88f,
                        getHeight() * 0.16f,
                        unselectedMarkPaint
                );
            } else if (unselectedMarkStyle == UNSELECTED_STYLE_HORIZONTAL) {
                unselectedMarkPaint.setColor(calendarBackgroundBitmap == null ? mutedTextColor() : Color.WHITE);
                canvas.drawLine(
                        getWidth() * 0.10f,
                        getHeight() * 0.50f,
                        getWidth() * 0.90f,
                        getHeight() * 0.50f,
                        unselectedMarkPaint
                );
            }
            drawStartDateHat(canvas);
            drawNoteIndicator(canvas);
        }

        private void drawStartDateHat(Canvas canvas) {
            if (startDateHat == null) {
                return;
            }
            int size = dp(22);
            int left = dp(1);
            int top = -dp(2);
            startDateHat.setBounds(left, top, left + size, top + size);
            int saveCount = canvas.save();
            canvas.rotate(-12f, left + size * 0.5f, top + size * 0.5f);
            startDateHat.draw(canvas);
            canvas.restoreToCount(saveCount);
        }

        private void drawNoteIndicator(Canvas canvas) {
            if (!hasNote) {
                return;
            }
            float centerX = getWidth() - dp(6);
            float centerY = dp(6);
            notePaint.setColor(Color.argb(150, 0, 0, 0));
            canvas.drawCircle(centerX, centerY, dp(4), notePaint);
            notePaint.setColor(Color.WHITE);
            canvas.drawCircle(centerX, centerY, dp(3), notePaint);
        }
    }
}
