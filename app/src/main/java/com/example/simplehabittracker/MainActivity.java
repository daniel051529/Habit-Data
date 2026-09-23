package com.example.simplehabittracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
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
    private static final String SLEEP_TIME_MINUTES_KEY = "sleep_time_minutes";
    private static final String SINGLE_STATE_MIGRATION_KEY = "single_state_migration_complete";
    private static final int DEFAULT_SLEEP_TIME_MINUTES = 23 * 60;
    private static final String HABIT_TYPE_GOOD = "good";
    private static final String HABIT_TYPE_BAD = "bad";
    private static final int DEFAULT_HABIT_DATE_COLOR = Color.rgb(22, 163, 74);
    private static final int DEFAULT_FOLDER_COLOR = Color.rgb(22, 163, 74);
    private static final int DEFAULT_BAD_FOLDER_COLOR = Color.rgb(220, 38, 38);
    private static final int STATE_EMPTY = 0;
    private static final int STATE_DONE = 1;
    private static final int STATE_MISSED = 2;

    private final DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
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
    private int sleepTimeMinutes;

    private FrameLayout root;
    private LinearLayout page;
    private LinearLayout topBar;
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
        sleepTimeMinutes = prefs.getInt(SLEEP_TIME_MINUTES_KEY, DEFAULT_SLEEP_TIME_MINUTES);

        loadFolders();
        loadHabits();
        if (habits.isEmpty()) {
            habits.add(new Habit("habit-" + System.currentTimeMillis(), "Daily Habit", "", HABIT_TYPE_GOOD, DEFAULT_HABIT_DATE_COLOR, new JSONObject(), new JSONObject()));
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

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(92)));

        LinearLayout habitTitleRow = new LinearLayout(this);
        habitTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        habitTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        habitTitleRow.setPadding(dp(20), 0, dp(20), 0);

        habitTitle = new TextView(this);
        habitTitle.setTextColor(textColor());
        habitTitle.setTextSize(26);
        habitTitle.setTypeface(Typeface.DEFAULT_BOLD);
        habitTitle.setGravity(Gravity.CENTER_VERTICAL);
        habitTitle.setOnClickListener(v -> {
            Habit habit = findSelectedHabit();
            if (habit != null) {
                showRenameDialog(habit);
            }
        });
        habitTitleRow.addView(habitTitle, new LinearLayout.LayoutParams(0, -1, 1));

        habitEmojiButton = iconButton("+");
        habitEmojiButton.setTextSize(22);
        habitEmojiButton.setOnClickListener(v -> showEmojiDialog());
        LinearLayout.LayoutParams emojiButtonParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        emojiButtonParams.setMargins(0, 0, dp(2), 0);
        habitTitleRow.addView(habitEmojiButton, emojiButtonParams);

        page.addView(habitTitleRow, new LinearLayout.LayoutParams(-1, dp(56)));

        View mainDivider = new View(this);
        mainDivider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams mainDividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        mainDividerParams.setMargins(dp(20), dp(4), dp(20), dp(10));
        page.addView(mainDivider, mainDividerParams);

        page.addView(buildWeekdayHeader(), new LinearLayout.LayoutParams(-1, dp(34)));

        calendarContainer = new FrameLayout(this);
        calendarContainer.setClipChildren(true);
        calendarGrid = createCalendarGrid();
        calendarContainer.addView(calendarGrid, new FrameLayout.LayoutParams(-1, -1));
        page.addView(calendarContainer, new LinearLayout.LayoutParams(-1, dp(380)));

        View gridBottomDivider = new View(this);
        gridBottomDivider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams gridBottomDividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        gridBottomDividerParams.setMargins(dp(20), 0, dp(20), 0);
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

        buildDrawer();
        installInsetPanels();
        setContentView(root);
    }

    private View buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(18), dp(10), dp(10));
        topBar.setBackgroundColor(surfaceColor());

        Button menuButton = iconButton("☰");
        menuButton.setOnClickListener(v -> showDrawer());
        topBar.addView(menuButton, new LinearLayout.LayoutParams(dp(52), dp(52)));

        monthTitle = new TextView(this);
        monthTitle.setTextColor(mutedTextColor());
        monthTitle.setTextSize(16);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        topBar.addView(monthTitle, new LinearLayout.LayoutParams(0, -1, 1));

        sleepCountdownValue = new TextView(this);
        sleepCountdownValue.setTextColor(accentColor());
        sleepCountdownValue.setTextSize(13);
        sleepCountdownValue.setTypeface(Typeface.DEFAULT_BOLD);
        sleepCountdownValue.setGravity(Gravity.CENTER);
        sleepCountdownValue.setSingleLine(true);
        sleepCountdownValue.setOnClickListener(v -> showSleepTimeDialog());
        topBar.addView(sleepCountdownValue, new LinearLayout.LayoutParams(dp(72), dp(52)));

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

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isMonthAnimating
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
            label.setTextColor(mutedTextColor());
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
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button themeButton = secondaryButton(isDarkMode ? "Light" : "Dark");
        themeButton.setOnClickListener(v -> {
            int savedDrawerScrollY = drawerScroll == null ? 0 : drawerScroll.getScrollY();
            isDarkMode = !isDarkMode;
            prefs.edit().putBoolean(DARK_MODE_KEY, isDarkMode).apply();
            buildUi();
            renderAll();
            restoreOpenDrawer(savedDrawerScrollY);
        });
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(dp(78), dp(44));
        themeParams.setMargins(0, 0, dp(8), 0);
        titleRow.addView(themeButton, themeParams);

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
    }

    private void renderCalendar() {
        Habit habit = findSelectedHabit();
        if (habit == null) {
            return;
        }

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
                dayView.setText(String.valueOf(dayNumber));
                dayView.setTodayHatResource(date.equals(LocalDate.now())
                        ? R.drawable.today_hat_crown
                        : 0);
                dayView.setHasNote(!habit.notes.optString(dateKey, "").trim().isEmpty());
                if (futureDate) {
                    applyFutureDayStyle(dayView);
                } else {
                    applyDayStyle(dayView, state, date.equals(LocalDate.now()), habit.dateColor);
                    dayView.setShowUnrecordedSlash(state == STATE_EMPTY && date.isBefore(LocalDate.now()));
                }
                if (interactive && !futureDate) {
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
        input.setTextColor(textColor());
        input.setHintTextColor(mutedTextColor());
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(4);
        input.setMaxLines(8);
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
        heading.setGravity(Gravity.CENTER);
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
        int next = current == STATE_EMPTY ? STATE_DONE : STATE_EMPTY;
        try {
            if (next == STATE_EMPTY) {
                habit.states.remove(dateKey);
            } else {
                habit.states.put(dateKey, next);
            }
        } catch (JSONException ignored) {
            Toast.makeText(this, "Could not update that day", Toast.LENGTH_SHORT).show();
        }
    }

    private View paddedDialogView(View content) {
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(24), dp(8), dp(24), 0);
        wrapper.addView(content, new FrameLayout.LayoutParams(-1, -2));
        return wrapper;
    }

    private void updateMonthDrag(float requestedDeltaX) {
        int width = calendarContainer.getWidth();
        if (width == 0) {
            return;
        }
        float deltaX = Math.max(-width, Math.min(width, requestedDeltaX));
        int direction = deltaX < 0 ? 1 : -1;
        if (direction > 0 && !visibleMonth.isBefore(YearMonth.now())) {
            discardMonthPreview();
            calendarGrid.setTranslationX(0);
            return;
        }
        prepareMonthPreview(direction);
        calendarGrid.setTranslationX(deltaX);
        monthPreviewGrid.setTranslationX(deltaX + direction * width);
    }

    private void prepareMonthPreview(int direction) {
        if (direction > 0 && !visibleMonth.isBefore(YearMonth.now())) {
            discardMonthPreview();
            return;
        }
        if (monthPreviewGrid != null && monthPreviewDirection == direction) {
            return;
        }
        if (monthPreviewGrid != null) {
            calendarContainer.removeView(monthPreviewGrid);
        }
        Habit habit = findSelectedHabit();
        if (habit == null) {
            return;
        }
        monthPreviewDirection = direction;
        monthPreview = direction > 0 ? visibleMonth.plusMonths(1) : visibleMonth.minusMonths(1);
        monthPreviewGrid = createCalendarGrid();
        populateCalendarGrid(monthPreviewGrid, monthPreview, habit, false);
        monthPreviewGrid.setTranslationX(direction * calendarContainer.getWidth());
        calendarContainer.addView(monthPreviewGrid, new FrameLayout.LayoutParams(-1, -1));
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
        input.setTextColor(textColor());
        input.setHintTextColor(mutedTextColor());
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
        menu.getMenu().add("Habit color");
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
            } else if ("Habit color".equals(title)) {
                showHabitColorDialog(habit);
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

    private void showHabitColorDialog(Habit habit) {
        showColorDialog("Habit color", habit.dateColor, color -> {
            habit.dateColor = color;
            saveHabits();
            renderCalendar();
        });
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
        int[] colors = {
                Color.rgb(22, 163, 74),
                Color.rgb(13, 148, 136),
                Color.rgb(54, 139, 193),
                Color.rgb(135, 8, 80),
                Color.rgb(202, 138, 4),
                Color.rgb(220, 38, 38)
        };
        String[] names = {"Green", "Teal", "Blue", "Purple", "Yellow", "Red"};

        GridLayout palette = new GridLayout(this);
        palette.setColumnCount(3);
        palette.setPadding(dp(8), dp(8), dp(8), dp(8));
        List<View> swatches = new ArrayList<>();
        for (int index = 0; index < colors.length; index++) {
            View swatch = new View(this);
            swatch.setContentDescription(names[index]);
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            background.setColor(colors[index]);
            background.setStroke(
                    dp(colors[index] == selectedColor ? 4 : 1),
                    colors[index] == selectedColor ? textColor() : borderColor()
            );
            swatch.setBackground(background);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(52);
            params.height = dp(52);
            params.setMargins(dp(10), dp(8), dp(10), dp(8));
            palette.addView(swatch, params);
            swatches.add(swatch);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(paddedDialogView(palette))
                .setNegativeButton("Cancel", null)
                .create();
        for (int index = 0; index < swatches.size(); index++) {
            int color = colors[index];
            swatches.get(index).setOnClickListener(v -> {
                listener.onColorPicked(color);
                dialog.dismiss();
            });
        }
        dialog.show();
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
        input.setTextColor(textColor());
        input.setHintTextColor(mutedTextColor());
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
                    Habit habit = new Habit("habit-" + System.currentTimeMillis(), name, "", folderId, DEFAULT_HABIT_DATE_COLOR, new JSONObject(), new JSONObject());
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
        input.setTextColor(textColor());
        input.setHintTextColor(mutedTextColor());
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
                },
                hour,
                minute,
                false
        );
        dialog.setTitle("Sleep time");
        dialog.show();
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

    private void applyDayStyle(TextView view, int state, boolean isToday, int completedColor) {
        int background;
        int text;
        if (state == STATE_DONE) {
            background = completedColor;
            text = Color.WHITE;
        } else if (state == STATE_MISSED) {
            background = Color.rgb(220, 38, 38);
            text = Color.WHITE;
        } else {
            background = panelColor();
            text = textColor();
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
        drawable.setColor(panelColor());
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
            topParams.height = topInset + dp(74);
            topBar.setLayoutParams(topParams);

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
                        item.optInt("dateColor", DEFAULT_HABIT_DATE_COLOR),
                        item.optJSONObject("states") == null ? new JSONObject() : item.optJSONObject("states"),
                        item.optJSONObject("notes") == null ? new JSONObject() : item.optJSONObject("notes")
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
                item.put("dateColor", habit.dateColor);
                item.put("states", habit.states);
                item.put("notes", habit.notes);
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
        int dateColor;
        final JSONObject states;
        final JSONObject notes;

        Habit(String id, String name, String emoji, String type, int dateColor, JSONObject states, JSONObject notes) {
            this.id = id;
            this.name = name;
            this.emoji = emoji;
            this.type = type;
            this.dateColor = dateColor;
            this.states = states;
            this.notes = notes;
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

    private class DayTextView extends TextView {
        private final Paint slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean showUnrecordedSlash;
        private boolean hasNote;
        private Drawable todayHat;

        DayTextView(Context context) {
            super(context);
            slashPaint.setStrokeWidth(dp(4));
            slashPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setShowUnrecordedSlash(boolean show) {
            showUnrecordedSlash = show;
            invalidate();
        }

        void setTodayHatResource(int drawableResource) {
            todayHat = drawableResource == 0 ? null : getDrawable(drawableResource);
            invalidate();
        }

        void setHasNote(boolean show) {
            hasNote = show;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (showUnrecordedSlash) {
                slashPaint.setColor(mutedTextColor());
                canvas.drawLine(
                        getWidth() * 0.12f,
                        getHeight() * 0.84f,
                        getWidth() * 0.88f,
                        getHeight() * 0.16f,
                        slashPaint
                );
            }
            drawTodayHat(canvas);
            drawNoteIndicator(canvas);
        }

        private void drawTodayHat(Canvas canvas) {
            if (todayHat == null) {
                return;
            }
            int size = dp(22);
            int left = dp(1);
            int top = -dp(2);
            todayHat.setBounds(left, top, left + size, top + size);
            int saveCount = canvas.save();
            canvas.rotate(12f, left + size * 0.5f, top + size * 0.5f);
            todayHat.draw(canvas);
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
