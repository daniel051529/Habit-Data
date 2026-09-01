package com.example.simplehabittracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "habit_tracker_data";
    private static final String HABITS_KEY = "habits";
    private static final String SELECTED_HABIT_KEY = "selected_habit_id";
    private static final String DARK_MODE_KEY = "dark_mode";
    private static final int STATE_EMPTY = 0;
    private static final int STATE_DONE = 1;
    private static final int STATE_MISSED = 2;

    private final DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private final List<Habit> habits = new ArrayList<>();

    private SharedPreferences prefs;
    private YearMonth visibleMonth;
    private String selectedHabitId;
    private boolean isDarkMode;

    private FrameLayout root;
    private LinearLayout page;
    private LinearLayout topBar;
    private TextView monthTitle;
    private TextView habitTitle;
    private Button habitEmojiButton;
    private GridLayout calendarGrid;
    private LinearLayout bottomPanel;
    private LinearLayout bottomHabitList;
    private FrameLayout drawerLayer;
    private LinearLayout drawerContent;
    private LinearLayout habitList;
    private float monthSwipeStartX;
    private float monthSwipeStartY;
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

        loadHabits();
        if (habits.isEmpty()) {
            habits.add(new Habit("habit-" + System.currentTimeMillis(), "Daily Habit", "", new JSONObject(), new JSONObject()));
            selectedHabitId = habits.get(0).id;
            saveHabits();
        }

        selectedHabitId = prefs.getString(SELECTED_HABIT_KEY, habits.get(0).id);
        if (findSelectedHabit() == null) {
            selectedHabitId = habits.get(0).id;
        }

        buildUi();
        renderAll();
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

        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setRowCount(6);
        calendarGrid.setPadding(dp(12), dp(8), dp(12), dp(12));
        page.addView(calendarGrid, new LinearLayout.LayoutParams(-1, -2));

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

        HorizontalScrollView habitScroller = new HorizontalScrollView(this);
        habitScroller.setHorizontalScrollBarEnabled(false);
        habitScroller.setFillViewport(false);

        bottomHabitList = new LinearLayout(this);
        bottomHabitList.setOrientation(LinearLayout.HORIZONTAL);
        bottomHabitList.setGravity(Gravity.CENTER_VERTICAL);
        habitScroller.addView(bottomHabitList, new HorizontalScrollView.LayoutParams(-2, -1));
        bottomPanel.addView(habitScroller, new LinearLayout.LayoutParams(-1, dp(82)));

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
        monthTitle.setTextColor(textColor());
        monthTitle.setTextSize(20);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        topBar.addView(monthTitle, new LinearLayout.LayoutParams(0, -1, 1));

        return topBar;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isMonthAnimating
                || (drawerLayer != null && drawerLayer.getVisibility() == View.VISIBLE)) {
            return super.dispatchTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                monthSwipeStartX = event.getRawX();
                monthSwipeStartY = event.getRawY();
                monthSwipeInProgress = false;
                touchStartedInBottomPanel = isTouchInsideView(bottomPanel, event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (touchStartedInBottomPanel) {
                    return super.dispatchTouchEvent(event);
                }
                if (isMonthSwipe(event)) {
                    monthSwipeInProgress = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (touchStartedInBottomPanel) {
                    touchStartedInBottomPanel = false;
                    return super.dispatchTouchEvent(event);
                }
                if (monthSwipeInProgress || isMonthSwipe(event)) {
                    float deltaX = event.getRawX() - monthSwipeStartX;
                    animateMonthChange(deltaX < 0 ? 1 : -1);
                    monthSwipeInProgress = false;
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                touchStartedInBottomPanel = false;
                monthSwipeInProgress = false;
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
            isDarkMode = !isDarkMode;
            prefs.edit().putBoolean(DARK_MODE_KEY, isDarkMode).apply();
            buildUi();
            renderAll();
            showDrawer();
        });
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(dp(78), dp(44));
        themeParams.setMargins(0, 0, dp(8), 0);
        titleRow.addView(themeButton, themeParams);

        Button addButton = primaryButton("+");
        addButton.setTextSize(20);
        addButton.setOnClickListener(v -> showAddHabitDialog());
        titleRow.addView(addButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        drawerContent.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(54)));

        View divider = new View(this);
        divider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(10), 0, dp(8));
        drawerContent.addView(divider, dividerParams);

        ScrollView scroll = new ScrollView(this);
        habitList = new LinearLayout(this);
        habitList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(habitList, new ScrollView.LayoutParams(-1, -2));
        drawerContent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(304), -1);
        drawerParams.gravity = Gravity.START;
        drawerLayer.addView(drawerContent, drawerParams);
    }

    private void renderAll() {
        renderDrawerList();
        renderBottomHabitSelector();
        renderCalendar();
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
        calendarGrid.removeAllViews();

        int startColumn = startColumn(visibleMonth.atDay(1).getDayOfWeek());
        int daysInMonth = visibleMonth.lengthOfMonth();
        int totalCells = 42;

        for (int cell = 0; cell < totalCells; cell++) {
            int dayNumber = cell - startColumn + 1;
            TextView dayView = new TextView(this);
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
                LocalDate date = visibleMonth.atDay(dayNumber);
                String dateKey = date.toString();
                int state = habit.states.optInt(dateKey, STATE_EMPTY);
                dayView.setText(String.valueOf(dayNumber));
                applyDayStyle(dayView, state, date.equals(LocalDate.now()));
                dayView.setOnClickListener(v -> {
                    cycleDay(habit, dateKey);
                    saveHabits();
                    renderCalendar();
                });
            } else {
                dayView.setText("");
                dayView.setBackgroundColor(Color.TRANSPARENT);
            }

            calendarGrid.addView(dayView, params);
        }
    }

    private void renderDrawerList() {
        habitList.removeAllViews();
        for (Habit habit : habits) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(6), dp(6), dp(6));
            if (habit.id.equals(selectedHabitId)) {
                android.graphics.drawable.GradientDrawable selectedBackground = new android.graphics.drawable.GradientDrawable();
                selectedBackground.setColor(selectedRowColor());
                selectedBackground.setCornerRadius(dp(28));
                row.setBackground(selectedBackground);
            } else {
                row.setBackgroundColor(Color.TRANSPARENT);
            }

            TextView name = new TextView(this);
            name.setText(habit.name);
            name.setTextColor(textColor());
            name.setTextSize(17);
            name.setTypeface(habit.id.equals(selectedHabitId) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            name.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));

            Button options = iconButton("⋮");
            options.setTextSize(24);
            options.setOnClickListener(v -> showHabitMenu(options, habit));
            row.addView(options, new LinearLayout.LayoutParams(dp(46), dp(46)));

            row.setOnClickListener(v -> {
                selectedHabitId = habit.id;
                prefs.edit().putString(SELECTED_HABIT_KEY, selectedHabitId).apply();
                hideDrawer();
                renderAll();
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
            params.setMargins(0, dp(4), 0, dp(4));
            habitList.addView(row, params);
        }
    }

    private void renderBottomHabitSelector() {
        bottomHabitList.removeAllViews();
        for (Habit habit : habits) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(8), dp(14), dp(8));
            applyHabitSelectorStyle(card, habit.id.equals(selectedHabitId));

            TextView emoji = new TextView(this);
            emoji.setText(habit.emoji.isEmpty() ? "+" : habit.emoji);
            emoji.setTextSize(20);
            emoji.setGravity(Gravity.CENTER);
            emoji.setTextColor(textColor());
            card.addView(emoji, new LinearLayout.LayoutParams(dp(28), -1));

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

            card.setOnClickListener(v -> {
                selectedHabitId = habit.id;
                prefs.edit().putString(SELECTED_HABIT_KEY, selectedHabitId).apply();
                renderAll();
            });

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-2, dp(58));
            cardParams.setMargins(0, dp(8), dp(10), dp(8));
            bottomHabitList.addView(card, cardParams);
        }
    }

    private void cycleDay(Habit habit, String dateKey) {
        int current = habit.states.optInt(dateKey, STATE_EMPTY);
        int next = current == STATE_EMPTY ? STATE_DONE : current == STATE_DONE ? STATE_MISSED : STATE_EMPTY;
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

    private void animateMonthChange(int direction) {
        if (isMonthAnimating) {
            return;
        }
        isMonthAnimating = true;
        int width = calendarGrid.getWidth();
        if (width == 0) {
            visibleMonth = direction > 0 ? visibleMonth.plusMonths(1) : visibleMonth.minusMonths(1);
            renderCalendar();
            isMonthAnimating = false;
            return;
        }

        calendarGrid.animate()
                .translationX(-direction * width)
                .alpha(0.35f)
                .setDuration(160)
                .withEndAction(() -> {
                    visibleMonth = direction > 0 ? visibleMonth.plusMonths(1) : visibleMonth.minusMonths(1);
                    renderCalendar();
                    calendarGrid.setTranslationX(direction * width);
                    calendarGrid.setAlpha(0.35f);
                    monthTitle.setTranslationX(direction * dp(48));
                    monthTitle.setAlpha(0.25f);
                    calendarGrid.animate()
                            .translationX(0)
                            .alpha(1f)
                            .setDuration(190)
                            .withEndAction(() -> isMonthAnimating = false)
                            .start();
                    monthTitle.animate()
                            .translationX(0)
                            .alpha(1f)
                            .setDuration(190)
                            .start();
                })
                .start();

        monthTitle.animate()
                .translationX(-direction * dp(48))
                .alpha(0.25f)
                .setDuration(160)
                .start();
    }

    private void showHabitMenu(View anchor, Habit habit) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Rename");
        menu.getMenu().add("Delete");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Rename".equals(title)) {
                showRenameDialog(habit);
            } else if ("Delete".equals(title)) {
                confirmDelete(habit);
            }
            return true;
        });
        menu.show();
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
                    Habit habit = new Habit("habit-" + System.currentTimeMillis(), name, "", new JSONObject(), new JSONObject());
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
                    renderCalendar();
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    habit.emoji = firstEmojiLikeText(input.getText().toString().trim());
                    saveHabits();
                    renderCalendar();
                })
                .show();
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

    private void applyHabitSelectorStyle(View view, boolean selected) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(selected ? selectedRowColor() : panelColor());
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), selected ? accentColor() : borderColor());
        view.setBackground(drawable);
    }

    private void applyDayStyle(TextView view, int state, boolean isToday) {
        int background;
        int text;
        if (state == STATE_DONE) {
            background = Color.rgb(22, 163, 74);
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

    private boolean isMonthSwipe(MotionEvent event) {
        float deltaX = event.getRawX() - monthSwipeStartX;
        float deltaY = event.getRawY() - monthSwipeStartY;
        return Math.abs(deltaX) > dp(72) && Math.abs(deltaX) > Math.abs(deltaY) * 1.5f;
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

    private int startColumn(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue() % 7;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
        final JSONObject states;
        final JSONObject notes;

        Habit(String id, String name, String emoji, JSONObject states, JSONObject notes) {
            this.id = id;
            this.name = name;
            this.emoji = emoji;
            this.states = states;
            this.notes = notes;
        }
    }
}
