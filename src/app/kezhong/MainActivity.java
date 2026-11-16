package app.kezhong;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
  private static final int PAPER = 0xFFF3F0E8;
  private static final int INK = 0xFF1C1915;
  private static final int MUTED = 0xFF6F675C;
  private static final int PINE = 0xFF1E332C;
  private static final int CREAM = 0xFFF4F1EA;
  private static final int LINE = 0xFFE4DDD0;
  private static final int CARD = 0xFFFBFBF8;
  private static final int[] TONES = {0xFFE7EFEA, 0xFFF3EBE1, 0xFFE8EEF2, 0xFFF6EFE6};
  private static final int REQ_FILE = 41;
  private static final int REQ_CALENDAR = 42;
  private static final int REQ_IMAGE = 43;

  private Store store;
  private int tab;
  private int week = 1;
  private boolean weekReady;
  private FrameLayout body;
  private LinearLayout nav;
  private TextView subtitle;
  private LinearLayout banner;
  private EditText paste;
  private List<Model.Course> pending = new ArrayList<Model.Course>();
  private List<String> pendingWarnings = new ArrayList<String>();
  private List<Model.Course> calendarQueue;
  private boolean calendarClear;
  private String pendingNote = "把表格粘进来，或从文件选择 xls、xlsx、csv。";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    store = new Store(this);
    buildUi();
  }

  private void buildUi() {
    FrameLayout stage = new FrameLayout(this);
    if (glass()) stage.addView(glassBackdrop(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    LinearLayout root = vertical(glass() ? 0 : PAPER);
    root.addView(header());
    subtitle = text(13, MUTED);
    pad(subtitle, 20, 0, 20, 8);
    root.addView(subtitle);
    banner = sampleBanner();
    root.addView(banner);
    body = new FrameLayout(this);
    root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    LinearLayout dock = vertical(0);
    pad(dock, 20, 2, 20, 16);
    nav = nav();
    dock.addView(nav);
    root.addView(dock);
    stage.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    setContentView(stage);
    show(tab);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (store != null) show(tab);
  }

  private void show(int index) {
    int from = tab;
    boolean slide = body != null && body.getChildCount() > 0 && index != from;
    int direction = index > from ? 1 : -1;
    tab = index;
    Calendar now = Calendar.getInstance();
    if (!weekReady) {
      week = Model.clampWeek(store.settings, now);
      weekReady = true;
    }
    Integer thisWeek = Model.weekIndex(store.settings.termStart, now);
    String weekText = thisWeek == null ? "学期还没开始" : "第 " + thisWeek + " 周";
    subtitle.setText(weekText + "  ·  " + Model.WEEKDAY[Model.isoWeekday(now)] + " " + Model.monthDay(now));
    banner.setVisibility(store.sample ? View.VISIBLE : View.GONE);
    View page = index == 0 ? pageToday(now) : index == 1 ? pageWeek(now) : index == 2 ? pageCourses() : pageImport();
    View old = body.getChildCount() == 0 ? null : body.getChildAt(body.getChildCount() - 1);
    if (old != null) {
      old.clearFocus();
      old.animate().cancel();
    }
    if (!slide || old == null || "none".equals(store.settings.motion)) {
      body.removeAllViews();
      body.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    } else {
      body.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      animatePage(page, old, direction);
    }
    paintNav();
  }

  private void animatePage(final View page, final View leaving, int direction) {
    final int dx = dp(36) * direction;
    page.setAlpha(0f);
    page.setTranslationX(dx);
    page.animate().translationX(0).alpha(1f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
    leaving.animate().translationX(-dx).alpha(0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
      public void run() { drop(leaving); }
    }).start();
  }

  private void drop(View leaving) {
    leaving.setRotationY(0f);
    leaving.setTranslationX(0f);
    leaving.setAlpha(1f);
    if (leaving.getParent() == body) body.removeView(leaving);
  }

  private LinearLayout header() {
    LinearLayout row = horizontal();
    row.setGravity(Gravity.CENTER_VERTICAL);
    pad(row, 20, 18, 12, 0);
    TextView title = text(28, INK);
    title.setText("课钟");
    title.setTypeface(Typeface.SERIF);
    row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    Button gear = button("作息", false);
    gear.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openSettings(); }
    });
    row.addView(gear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    Button motion = button("风格", false);
    motion.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openStyle(); }
    });
    row.addView(motion, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return row;
  }

  private LinearLayout sampleBanner() {
    LinearLayout box = vertical(0xFFE7EFEA);
    pad(box, 16, 10, 16, 10);
    TextView label = text(13, PINE);
    label.setText("现在是示例课表，用来看今天的课和起床时间。导入或自己添加后会换掉。");
    box.addView(label);
    Button clear = button("清掉示例", false);
    clear.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        store.courses.clear();
        store.sample = false;
        store.save();
        show(tab);
      }
    });
    box.addView(clear);
    return box;
  }

  private View pageToday(final Calendar now) {
    ScrollView scroll = new ScrollView(this);
    LinearLayout col = vertical(0);
    pad(col, 16, 4, 16, 108);
    String todayOff = Holidays.off(now);
    if (todayOff != null) {
      TextView banner = text(16, 0xFF9A3412);
      banner.setText(wish(todayOff) + "  " + face());
      banner.setTypeface(Typeface.DEFAULT_BOLD);
      pad(banner, 4, 4, 4, 8);
      col.addView(banner);
    } else if (Holidays.work(now) != null) {
      TextView banner = text(14, MUTED);
      banner.setText("调休上班  " + face());
      pad(banner, 4, 4, 4, 8);
      col.addView(banner);
    }
    final Model.WakePlan plan = Model.planWake(store.courses, store.settings, now);
    if (plan == null) {
      col.addView(cardText("这个学期还没有下一节课。", "先在「课程」里添加，或到「设置」导入课表。"));
    } else {
      col.addView(wakeCard(plan, now));
      Slot next = firstCatchable(now);
      Model.Course shown = next != null ? next.course : plan.course;
      Calendar shownDate = next != null ? next.date : plan.classDate;
      Slot later = classAfter(shown, shownDate);
      if (later != null) col.addView(nextCard(later, now));
      col.addView(restCard(shown, shownDate, later, now));
    }
    Button allCal = button("一键写入日历并提醒", false);
    allCal.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { askCalendar(new ArrayList<Model.Course>(store.courses)); }
    });
    col.addView(allCal);
    Button clearCal = button("清掉日历里的课", false);
    clearCal.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { confirmClearCalendar(); }
    });
    col.addView(clearCal);
    scroll.addView(col);
    return scroll;
  }

  private View wakeCard(final Model.WakePlan plan, Calendar now) {
    Slot next = firstCatchable(now);
    final Model.Course course = next != null ? next.course : plan.course;
    final Calendar date = next != null ? next.date : plan.classDate;
    boolean same = next != null && course == plan.course && Model.sameDay(date, plan.classDate);
    boolean skipped = next != null && !same;
    LinearLayout card = vertical(PINE);
    round(card, 22);
    pad(card, 20, 18, 20, 16);
    TextView kicker = text(13, 0xFFD5DDD6);
    String day;
    if (Model.sameDay(date, now)) day = skipped ? "下一节" : "今天";
    else if (Model.daysBetween(now, date) == 1) day = "明天";
    else day = Model.WEEKDAY[Model.isoWeekday(date)];
    kicker.setText(day);
    TextView time = text(32, CREAM);
    time.setTypeface(Typeface.SERIF);
    time.setText(span(course));
    TextView name = text(18, CREAM);
    name.setTypeface(Typeface.DEFAULT_BOLD);
    name.setText(course.name);
    card.addView(kicker);
    card.addView(time);
    card.addView(name);
    if (course.location.length() > 0) {
      TextView place = text(16, CREAM);
      place.setText(course.location);
      card.addView(place);
    }
    final Calendar ring = alarmMoment(course, date, now);
    if (ring != null) {
      final int hour = ring.get(Calendar.HOUR_OF_DAY);
      final int minute = ring.get(Calendar.MINUTE);
      TextView note = text(13, 0xFFD5DDD6);
      note.setText(Model.formatMinutes(hour * 60 + minute) + " 响");
      card.addView(note);
      Button alarm = lightButton("写入系统闹钟");
      alarm.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          setAlarm(hour, minute, course.name);
        }
      });
      card.addView(alarm);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(8);
    card.setLayoutParams(lp);
    return card;
  }

  private View nextCard(Slot later, Calendar now) {
    LinearLayout card = vertical(0xFFD7E8DE);
    round(card, 22);
    pad(card, 20, 16, 20, 16);
    TextView kicker = text(13, PINE);
    kicker.setText("下一节");
    kicker.setTypeface(Typeface.DEFAULT_BOLD);
    TextView time = text(26, PINE);
    time.setTypeface(Typeface.SERIF);
    String day = "";
    if (!Model.sameDay(later.date, now)) {
      day = Model.daysBetween(now, later.date) == 1 ? "明天  " : Model.WEEKDAY[Model.isoWeekday(later.date)] + "  ";
    }
    time.setText(day + span(later.course));
    TextView name = text(17, INK);
    name.setTypeface(Typeface.DEFAULT_BOLD);
    name.setText(later.course.name);
    card.addView(kicker);
    card.addView(time);
    card.addView(name);
    if (later.course.location.length() > 0) {
      TextView place = text(15, 0xFF3E5248);
      place.setText(later.course.location);
      card.addView(place);
    }
    final Calendar ring = alarmMoment(later.course, later.date, now);
    if (ring != null) {
      final int hour = ring.get(Calendar.HOUR_OF_DAY);
      final int minute = ring.get(Calendar.MINUTE);
      final TextView toggle = text(14, PINE);
      toggle.setText("提前设闹钟");
      toggle.setTypeface(Typeface.DEFAULT_BOLD);
      pad(toggle, 0, 12, 0, 0);
      final LinearLayout panel = vertical(0);
      panel.setVisibility(View.GONE);
      TextView when = text(14, 0xFF3E5248);
      when.setText(Model.formatMinutes(hour * 60 + minute) + " 响");
      pad(when, 0, 6, 0, 0);
      Button alarm = button("写入系统闹钟", true);
      alarm.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          setAlarm(hour, minute, later.course.name);
        }
      });
      panel.addView(when);
      panel.addView(alarm);
      toggle.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          boolean open = panel.getVisibility() != View.VISIBLE;
          panel.setVisibility(open ? View.VISIBLE : View.GONE);
          toggle.setText(open ? "收起" : "提前设闹钟");
        }
      });
      card.addView(toggle);
      card.addView(panel);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(12);
    card.setLayoutParams(lp);
    return card;
  }

  private View restCard(Model.Course shown, Calendar shownDate, Slot later, Calendar now) {
    List<Model.Course> rest = new ArrayList<Model.Course>();
    for (Model.Course course : Model.onDate(store.courses, store.settings, now)) {
      if (isShown(course, shown, shownDate, now)) continue;
      if (later != null && isShown(course, later.course, later.date, now)) continue;
      rest.add(course);
    }
    if (rest.isEmpty()) return greetCard(now, Model.sameDay(shownDate, now) || (later != null && Model.sameDay(later.date, now)));
    LinearLayout card = vertical(CARD);
    round(card, 22);
    stroke(card);
    pad(card, 18, 14, 18, 14);
    TextView title = text(15, INK);
    title.setText("今天的课");
    title.setTypeface(Typeface.DEFAULT_BOLD);
    card.addView(title);
    int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    for (Model.Course course : rest) {
      boolean over = Model.parseMinutes(course.end) <= nowMin;
      TextView line = text(15, over ? MUTED : INK);
      String place = course.location.length() == 0 ? "" : "   " + course.location;
      line.setText(span(course) + "   " + course.name + place + (over ? "   已上" : ""));
      pad(line, 0, 10, 0, 0);
      card.addView(line);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(12);
    card.setLayoutParams(lp);
    return card;
  }

  private boolean isShown(Model.Course course, Model.Course shown, Calendar shownDate, Calendar now) {
    if (shown == null || shownDate == null || !Model.sameDay(shownDate, now)) return false;
    return course == shown || (course.name.equals(shown.name) && course.start.equals(shown.start));
  }

  private View greetCard(Calendar now, boolean hadClass) {
    int hour = now.get(Calendar.HOUR_OF_DAY);
    String hello = hour < 11 ? "早上好" : hour < 18 ? "下午好" : "晚上好";
    String off = Holidays.off(now);
    if (off != null) hello = wish(off);
    LinearLayout card = vertical(0xFFF7F1E6);
    round(card, 22);
    pad(card, 20, 16, 20, 16);
    TextView title = text(18, INK);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    title.setText(hello + "  " + face());
    TextView sub = text(14, MUTED);
    sub.setText(hadClass ? "今天的课到这里了" : "今天没课");
    pad(sub, 0, 4, 0, 0);
    card.addView(title);
    card.addView(sub);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(12);
    card.setLayoutParams(lp);
    return card;
  }

  private void addLater(LinearLayout col, Model.Course shown, Calendar date, Calendar now) {
    if (!Model.sameDay(date, now)) return;
    List<Model.Course> day = Model.onDate(store.courses, store.settings, date);
    boolean after = false;
    List<Model.Course> rest = new ArrayList<Model.Course>();
    for (Model.Course course : day) {
      if (!after) {
        if (course == shown || (course.name.equals(shown.name) && course.start.equals(shown.start))) after = true;
        continue;
      }
      rest.add(course);
    }
    if (rest.isEmpty()) return;
    rest.remove(0);
    if (rest.isEmpty()) return;
    TextView heading = text(15, INK);
    heading.setText("今天还剩");
    heading.setTypeface(Typeface.DEFAULT_BOLD);
    pad(heading, 4, 16, 4, 0);
    col.addView(heading);
    for (Model.Course course : rest) col.addView(courseCard(course, "", false));
  }

  private void dropDuplicates() {
    List<Model.Course> kept = new ArrayList<Model.Course>();
    int removed = 0;
    for (Model.Course course : store.courses) {
      boolean dup = false;
      for (Model.Course other : kept) {
        if (sameCourse(other, course)) dup = true;
      }
      if (dup) removed++;
      else kept.add(course);
    }
    if (removed == 0) {
      toast("没有重复的课");
      return;
    }
    store.courses.clear();
    store.courses.addAll(kept);
    store.sample = false;
    store.save();
    toast("去掉了 " + removed + " 节重复的课");
    show(tab);
  }

  private boolean sameCourse(Model.Course a, Model.Course b) {
    if (a.day != b.day) return false;
    if (!a.name.trim().equals(b.name.trim())) return false;
    if (!a.start.equals(b.start) || !a.end.equals(b.end)) return false;
    String left = a.location == null ? "" : a.location.trim();
    String right = b.location == null ? "" : b.location.trim();
    if (left.length() > 0 && right.length() > 0 && !left.equals(right)) return false;
    return a.weeks.label().equals(b.weeks.label());
  }

  private TextView bandLabel(String name) {
    TextView view = text(13, PINE);
    view.setText(name);
    view.setTypeface(Typeface.DEFAULT_BOLD);
    pad(view, 4, 12, 4, 0);
    return view;
  }

  private View wakePrefsCard() {
    LinearLayout card = vertical(CARD);
    round(card, 18);
    stroke(card);
    pad(card, 16, 14, 16, 14);
    TextView title = text(16, INK);
    title.setText("起床怎么算");
    TextView hint = text(12, MUTED);
    hint.setText("第一节课的时间，减去洗漱、路程和容错。");
    card.addView(title);
    card.addView(hint);
    final EditText wash = numberField(String.valueOf(store.settings.wake.wash), "洗漱分钟");
    final EditText commute = numberField(String.valueOf(store.settings.wake.commute), "路程分钟");
    final EditText buffer = numberField(String.valueOf(store.settings.wake.buffer), "容错分钟");
    card.addView(labeled("洗漱", wash));
    card.addView(labeled("路程", commute));
    card.addView(labeled("容错", buffer));
    Button save = button("按这个重算", true);
    save.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        store.settings.wake.wash = Math.max(0, parseInt(wash.getText().toString(), store.settings.wake.wash));
        store.settings.wake.commute = Math.max(0, parseInt(commute.getText().toString(), store.settings.wake.commute));
        store.settings.wake.buffer = Math.max(0, parseInt(buffer.getText().toString(), store.settings.wake.buffer));
        store.save();
        toast("起床时间已重算");
        show(0);
      }
    });
    card.addView(save);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(12);
    card.setLayoutParams(lp);
    return card;
  }

  private LinearLayout labeled(String name, EditText field) {
    LinearLayout row = horizontal();
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView label = text(14, INK);
    label.setText(name);
    row.addView(label, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));
    row.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return row;
  }

  private EditText numberField(String value, String hint) {
    EditText edit = field(value, hint);
    edit.setInputType(InputType.TYPE_CLASS_NUMBER);
    return edit;
  }

  private View momentCard(Model.Moment moment, Calendar now) {
    int mins;
    String title;
    if (moment.live) {
      mins = Model.parseMinutes(moment.course.end) - (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
      title = "正在上  " + moment.course.name;
    } else if (Model.sameDay(moment.date, now)) {
      mins = Model.parseMinutes(moment.course.start) - (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
      title = "下一节  " + moment.course.name;
    } else {
      int days = Model.daysBetween(now, moment.date);
      mins = days * 1440 + Model.parseMinutes(moment.course.start) - (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
      title = (days == 1 ? "明天" : Model.WEEKDAY[Model.isoWeekday(moment.date)]) + " " + span(moment.course) + "  " + moment.course.name;
    }
    LinearLayout card = vertical(CARD);
    round(card, 18);
    stroke(card);
    pad(card, 16, 14, 16, 14);
    TextView a = text(16, INK);
    a.setText(title);
    TextView b = text(13, MUTED);
    String place = moment.course.location.length() == 0 ? "" : "  ·  " + moment.course.location;
    b.setText((moment.live ? "还有 " + Model.duration(mins) + " 下课" : "还有 " + Model.duration(mins)) + place);
    card.addView(a);
    card.addView(b);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(12);
    card.setLayoutParams(lp);
    return card;
  }

  private View pageWeek(final Calendar now) {
    LinearLayout col = vertical(0);
    TextView label = text(16, INK);
    label.setText("第 " + week + " 周");
    label.setTypeface(Typeface.DEFAULT_BOLD);
    pad(label, 16, 8, 16, 4);
    col.addView(label);
    final HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
    chipsScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = horizontal();
    pad(chips, 12, 0, 12, 4);
    final int todayWeek = Model.clampWeek(store.settings, now);
    for (int w = 1; w <= store.settings.totalWeeks; w++) {
      final int value = w;
      String caption = String.valueOf(w);
      if (w == todayWeek) caption = w + "今";
      Button chip = chip(caption, w == week);
      chip.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          week = value;
          weekReady = true;
          show(1);
        }
      });
      chips.addView(chip);
    }
    chipsScroll.addView(chips);
    col.addView(chipsScroll);
    chipsScroll.post(new Runnable() {
      public void run() {
        if (week < 1 || week > chips.getChildCount()) return;
        View child = chips.getChildAt(week - 1);
        chipsScroll.scrollTo(Math.max(0, child.getLeft() - dp(16)), 0);
      }
    });
    if (!weekHas(week)) {
      TextView note = text(13, MUTED);
      note.setText(emptyWeekNote(week));
      pad(note, 16, 0, 16, 8);
      col.addView(note);
    }
    int earliest = 24 * 60;
    int latest = 0;
    for (Model.Course course : store.courses) {
      if (!course.weeks.on(week)) continue;
      earliest = Math.min(earliest, Model.parseMinutes(course.start));
      latest = Math.max(latest, Model.parseMinutes(course.end));
    }
    final int startMin = earliest == 24 * 60 ? 8 * 60 : (earliest / 60) * 60;
    int end = latest == 0 ? 18 * 60 : ((latest + 59) / 60) * 60;
    if (end <= startMin) end = startMin + 6 * 60;
    final int endMin = end;
    final Calendar stamp = (Calendar) now.clone();
    final ZoomPane pane = new ZoomPane();
    col.addView(pane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    pane.post(new Runnable() {
      int tries;
      public void run() {
        if (pane.getWidth() < 10 && tries++ < 6) {
          pane.post(this);
          return;
        }
        int nowMin = stamp.get(Calendar.HOUR_OF_DAY) * 60 + stamp.get(Calendar.MINUTE);
        View board = weekGrid(stamp, startMin, endMin);
        int focus = 0;
        if (nowMin >= startMin && nowMin <= endMin) focus = (nowMin - startMin) * dp(58) / 60;
        pane.show(board, focus);
      }
    });
    return col;
  }

  private View weekGrid(Calendar now, int startMin, int endMin) {
    int todayWeek = Model.clampWeek(store.settings, now);
    int gutter = dp(42);
    int headerH = dp(36);
    int dayW = dp(96);
    int hourPx = dp(58);
    int span = Math.max(60, endMin - startMin);
    int gridH = span * hourPx / 60;
    int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    boolean showNow = week == todayWeek && nowMin >= startMin && nowMin <= endMin;
    int step = hourPx < dp(20) ? 120 : 60;
    LinearLayout board = horizontal();
    LinearLayout left = vertical(0);
    left.addView(new View(this), new LinearLayout.LayoutParams(gutter, headerH));
    FrameLayout hours = new FrameLayout(this);
    for (int min = startMin; min <= endMin; min += step) {
      int y = (min - startMin) * hourPx / 60;
      if (showNow && Math.abs(min - nowMin) < step / 2) continue;
      TextView tick = text(10, MUTED);
      tick.setText(Model.formatMinutes(min));
      FrameLayout.LayoutParams tickLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      tickLp.topMargin = Math.max(0, y - dp(7));
      hours.addView(tick, tickLp);
    }
    if (showNow) {
      int y = (nowMin - startMin) * hourPx / 60;
      TextView nowLabel = text(10, 0xFFFFFFFF);
      nowLabel.setText(Model.formatMinutes(nowMin));
      nowLabel.setTypeface(Typeface.DEFAULT_BOLD);
      nowLabel.setGravity(Gravity.CENTER);
      GradientDrawable badge = new GradientDrawable();
      badge.setColor(0xFFE11D2E);
      badge.setCornerRadius(dp(5));
      nowLabel.setBackground(badge);
      nowLabel.setPadding(dp(2), 0, dp(2), 0);
      FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      badgeLp.topMargin = Math.max(0, y - dp(8));
      hours.addView(nowLabel, badgeLp);
    }
    left.addView(hours, new LinearLayout.LayoutParams(gutter, gridH));
    board.addView(left);
    LinearLayout right = vertical(0);
    LinearLayout head = horizontal();
    Calendar monday = Model.addDays(Model.parseDate(store.settings.termStart), (week - 1) * 7);
    for (int d = 1; d <= 7; d++) {
      Calendar date = Model.addDays(monday, d - 1);
      boolean isToday = week == todayWeek && Model.sameDay(date, now);
      String off = Holidays.off(date);
      LinearLayout cell = vertical(0);
      cell.setGravity(Gravity.CENTER_HORIZONTAL);
      TextView dayName = text(11, isToday ? PINE : MUTED);
      dayName.setText(Model.WEEKDAY[d].substring(1));
      dayName.setGravity(Gravity.CENTER);
      TextView dayNum = text(11, isToday ? CREAM : (off != null ? 0xFF9A3412 : MUTED));
      dayNum.setText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)));
      dayNum.setGravity(Gravity.CENTER);
      dayNum.setIncludeFontPadding(false);
      LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(isToday ? dp(18) : ViewGroup.LayoutParams.WRAP_CONTENT, isToday ? dp(18) : ViewGroup.LayoutParams.WRAP_CONTENT);
      numLp.gravity = Gravity.CENTER_HORIZONTAL;
      if (isToday) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(PINE);
        dayNum.setBackground(dot);
      }
      cell.addView(dayName);
      cell.addView(dayNum, numLp);
      head.addView(cell, new LinearLayout.LayoutParams(dayW, headerH));
    }
    right.addView(head);
    FrameLayout canvas = new FrameLayout(this);
    for (int min = startMin; min <= endMin; min += 60) {
      int y = (min - startMin) * hourPx / 60;
      View line = new View(this);
      line.setBackgroundColor(0xFFE6E1D8);
      FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(dayW * 7, 1);
      lineLp.topMargin = y;
      canvas.addView(line, lineLp);
    }
    if (showNow) {
      int y = (nowMin - startMin) * hourPx / 60;
      View nowLine = new View(this);
      nowLine.setBackgroundColor(0xFFE11D2E);
      FrameLayout.LayoutParams nowLp = new FrameLayout.LayoutParams(dayW * 7, dp(2));
      nowLp.topMargin = y;
      canvas.addView(nowLine, nowLp);
    }
    for (int d = 1; d <= 7; d++) {
      FrameLayout column = new FrameLayout(this);
      for (Model.Course course : store.courses) {
        if (course.day == d && course.weeks.on(week)) column.addView(placedCourse(course, startMin, hourPx));
      }
      FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(dayW, gridH);
      colLp.leftMargin = (d - 1) * dayW;
      canvas.addView(column, colLp);
    }
    right.addView(canvas, new LinearLayout.LayoutParams(dayW * 7, gridH));
    board.addView(right);
    board.setLayoutParams(new FrameLayout.LayoutParams(gutter + dayW * 7, headerH + gridH));
    return board;
  }

  private View placedCourse(final Model.Course course, int startMin, int hourPx) {
    int start = Model.parseMinutes(course.start);
    int end = Model.parseMinutes(course.end);
    int top = Math.max(0, (start - startMin) * hourPx / 60);
    int height = Math.max(dp(22), (end - start) * hourPx / 60 - dp(2));
    int ink = courseInk(course.name);
    LinearLayout box = vertical(courseColor(course.name));
    round(box, 8);
    pad(box, 4, 3, 4, 3);
    TextView time = text(11, ink);
    time.setTypeface(Typeface.DEFAULT_BOLD);
    time.setSingleLine(true);
    time.setText(span(course));
    box.addView(time);
    int nameLines = Math.max(1, (height - dp(32)) / dp(16));
    TextView name = text(13, INK);
    name.setTypeface(Typeface.DEFAULT_BOLD);
    name.setMaxLines(nameLines);
    name.setText(course.name);
    box.addView(name);
    if (course.location.length() > 0) {
      TextView place = text(11, 0xFF5C675F);
      place.setMaxLines(2);
      place.setText(course.location);
      box.addView(place);
    }
    box.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openEditor(course); }
    });
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    lp.topMargin = top;
    lp.leftMargin = dp(1);
    lp.rightMargin = dp(1);
    box.setLayoutParams(lp);
    return box;
  }

  private View miniCourse(final Model.Course course, final Calendar date) {
    int ink = courseInk(course.name);
    LinearLayout box = vertical(courseColor(course.name));
    round(box, 8);
    pad(box, 3, 3, 3, 3);
    TextView time = text(8, ink);
    time.setTypeface(Typeface.DEFAULT_BOLD);
    time.setText(span(course));
    time.setMaxLines(2);
    TextView name = text(10, INK);
    name.setTypeface(Typeface.DEFAULT_BOLD);
    name.setMaxLines(2);
    name.setText(course.name);
    box.addView(time);
    box.addView(name);
    if (course.location.length() > 0) {
      TextView place = text(8, 0xFF5C675F);
      place.setMaxLines(1);
      place.setText(course.location);
      box.addView(place);
    }
    box.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openEditor(course); }
    });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(4);
    lp.leftMargin = dp(2);
    lp.rightMargin = dp(2);
    box.setLayoutParams(lp);
    return box;
  }

  private int courseColor(String name) {
    int[] colors = {
      0xFFD5E8DC, 0xFFF4DCC8, 0xFFD7E3F4, 0xFFF6D5D8,
      0xFFE4D7F2, 0xFFF6E7B8, 0xFFCDE8E2, 0xFFE8DDD0
    };
    int hash = 0;
    if (name != null) {
      for (int i = 0; i < name.length(); i++) hash = hash * 31 + name.charAt(i);
    }
    return colors[(hash & 0x7fffffff) % colors.length];
  }

  private int courseInk(String name) {
    int[] colors = {
      0xFF1F6B4A, 0xFF8A4E28, 0xFF2A4E86, 0xFF8A3E4A,
      0xFF5A3E86, 0xFF7A5E10, 0xFF1A6A5C, 0xFF5A4E42
    };
    int hash = 0;
    if (name != null) {
      for (int i = 0; i < name.length(); i++) hash = hash * 31 + name.charAt(i);
    }
    return colors[(hash & 0x7fffffff) % colors.length];
  }

  private View pageCourses() {
    ScrollView scroll = new ScrollView(this);
    LinearLayout col = vertical(0);
    pad(col, 16, 4, 16, 108);
    Button add = button("添加课程", true);
    add.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openEditor(null); }
    });
    col.addView(add);
    if (!store.courses.isEmpty()) {
      Button clear = button("清空课表", false);
      clear.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { confirmClear(); }
      });
      col.addView(clear);
      Button dup = button("去掉重复", false);
      dup.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { dropDuplicates(); }
      });
      col.addView(dup);
    }
    if (store.courses.isEmpty()) {
      TextView empty = text(14, MUTED);
      empty.setText("还没有课。");
      pad(empty, 4, 12, 4, 0);
      col.addView(empty);
    }
    int lastDay = 0;
    int lastBand = -1;
    for (final Model.Course course : store.courses) {
      if (course.day != lastDay) {
        TextView head = text(13, MUTED);
        head.setText(Model.WEEKDAY[course.day]);
        pad(head, 4, 14, 4, 4);
        col.addView(head);
        lastDay = course.day;
        lastBand = -1;
      }
      int band = Model.band(course.start);
      if (band != lastBand) {
        col.addView(bandLabel(Model.BAND[band]));
        lastBand = band;
      }
      col.addView(courseCard(course, courseDetail(course), false));
    }
    scroll.addView(col);
    return scroll;
  }

  private View courseCard(final Model.Course course, String extra, boolean withAlarm) {
    LinearLayout row = horizontal();
    row.setBackgroundColor(TONES[Model.tone(course.name)]);
    round(row, 16);
    View stripe = new View(this);
    stripe.setBackgroundColor(PINE);
    row.addView(stripe, new LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT));
    LinearLayout col = vertical(0);
    pad(col, 12, 10, 12, 10);
    TextView name = text(16, INK);
    name.setText(course.name);
    TextView meta = text(13, MUTED);
    String teacher = course.teacher.length() == 0 ? "" : "  ·  " + course.teacher;
    String place = course.location.length() == 0 ? "" : "  ·  " + course.location;
    meta.setText(span(course) + teacher + place);
    TextView sub = text(12, MUTED);
    sub.setText(extra);
    col.addView(name);
    col.addView(meta);
    if (extra != null && extra.length() > 0) col.addView(sub);
    if (withAlarm) {
      Button alarm = button("课前 " + store.settings.wake.remind + " 分钟闹钟", false);
      final int remind = store.settings.wake.remind;
      alarm.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          int mins = Model.parseMinutes(course.start) - remind;
          setAlarm(floorMod(mins, 1440) / 60, floorMod(mins, 1440) % 60, course.name);
        }
      });
      col.addView(alarm);
    }
    row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openEditor(course); }
    });
    row.setOnLongClickListener(new View.OnLongClickListener() {
      public boolean onLongClick(View v) {
        new AlertDialog.Builder(MainActivity.this)
            .setTitle("删除这节课")
            .setMessage(course.name)
            .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
              public void onClick(android.content.DialogInterface dialog, int which) {
                store.courses.remove(course);
                store.sample = false;
                store.save();
                show(tab);
              }
            })
            .setNegativeButton("取消", null)
            .show();
        return true;
      }
    });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(8);
    row.setLayoutParams(lp);
    return row;
  }

  private View pageImport() {
    ScrollView scroll = new ScrollView(this);
    LinearLayout col = vertical(0);
    pad(col, 16, 4, 16, 28);
    TextView title = text(18, INK);
    title.setText("设置");
    title.setTypeface(Typeface.DEFAULT_BOLD);
    pad(title, 4, 4, 4, 8);
    col.addView(title);
    Button times = button(periodSummary(), false);
    times.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openPeriods(); }
    });
    col.addView(times);
    TextView help = text(14, MUTED);
    help.setText("支持 xls、xlsx 和 csv。可以是周一到周日的课表，或带「课程、教师、地点、星期、开始、结束」的清单。教务系统把网页另存成 xls 也可以。");
    col.addView(help);
    if (paste == null) {
      paste = new EditText(this);
      paste.setMinLines(5);
      paste.setHint("贴在这里");
      paste.setBackgroundColor(CARD);
      paste.setTextColor(INK);
      paste.setHintTextColor(MUTED);
      paste.setGravity(Gravity.TOP);
    }
    if (paste.getParent() instanceof ViewGroup) ((ViewGroup) paste.getParent()).removeView(paste);
    LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
    pasteLp.topMargin = dp(10);
    col.addView(paste, pasteLp);
    Button parse = button("识别这段文字", true);
    parse.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { review(TableParser.fromText(paste.getText().toString(), store.settings), null); }
    });
    col.addView(parse);
    Button file = button("从表格文件导入", false);
    file.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
          "text/csv", "text/plain", "text/comma-separated-values", "application/json",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        });
        startActivityForResult(intent, REQ_FILE);
      }
    });
    col.addView(file);
    Button picture = button("从图片识别", false);
    picture.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_IMAGE);
      }
    });
    col.addView(picture);
    TextView note = text(14, pending.isEmpty() ? MUTED : PINE);
    note.setText(pendingNote);
    pad(note, 4, 8, 4, 4);
    col.addView(note);
    for (String warning : pendingWarnings) {
      TextView line = text(13, 0xFF9A3412);
      line.setText(warning);
      pad(line, 4, 2, 4, 2);
      col.addView(line);
    }
    if (!pending.isEmpty()) {
      Button replace = button("用这份替换课表", true);
      replace.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { adopt(true); }
      });
      Button merge = button("追加到现有课表", false);
      merge.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { adopt(false); }
      });
      col.addView(replace);
      col.addView(merge);
    }
    for (Model.Course course : pending) {
      TextView line = text(14, INK);
      line.setText(Model.WEEKDAY[course.day] + " " + course.start + "  " + course.name + "  " + course.weeks.label());
      col.addView(line);
    }
    TextView exportTitle = text(18, INK);
    exportTitle.setText("导出");
    exportTitle.setTypeface(Typeface.DEFAULT_BOLD);
    pad(exportTitle, 4, 18, 4, 4);
    col.addView(exportTitle);
    Button ics = button("导出日历（带提醒）", false);
    ics.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { share("课钟.ics", Export.ics(store), "text/calendar"); }
    });
    Button csv = button("导出表格", false);
    csv.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { share("课钟.csv", "\uFEFF" + Export.csv(store.courses), "text/csv"); }
    });
    Button json = button("导出备份", false);
    json.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { share("课钟.json", Export.json(store), "application/json"); }
    });
    col.addView(ics);
    col.addView(csv);
    col.addView(json);
    scroll.addView(col);
    return scroll;
  }

  private void adopt(boolean replace) {
    List<Model.Course> courses = pending;
    if (TableParser.foundTermStart != null && TableParser.foundTermStart.matches("\\d{4}-\\d{2}-\\d{2}")) {
      store.settings.termStart = TableParser.foundTermStart;
    }
    int max = store.settings.totalWeeks;
    for (Model.Course course : courses) {
      if ("list".equals(course.weeks.kind)) {
        for (int n : course.weeks.list) if (n > max) max = n;
      } else if (course.weeks.to > max) max = course.weeks.to;
    }
    if (TableParser.foundTotalWeeks > max) max = TableParser.foundTotalWeeks;
    store.settings.totalWeeks = Math.min(40, Math.max(1, max));
    if (replace) store.replaceCourses(courses);
    else store.addCourses(courses);
    pending = new ArrayList<Model.Course>();
    pendingWarnings = new ArrayList<String>();
    pendingNote = "已经换成刚识别的课表。";
    Calendar now = Calendar.getInstance();
    int current = Model.clampWeek(store.settings, now);
    week = current;
    if (!weekHas(current)) {
      int found = -1;
      for (int w = current; w <= store.settings.totalWeeks; w++) if (weekHas(w)) { found = w; break; }
      if (found < 0) for (int w = 1; w < current; w++) if (weekHas(w)) found = w;
      if (found > 0) week = found;
    }
    weekReady = true;
    toast("识别到 " + courses.size() + " 门课");
    show(1);
  }

  private void confirmClear() {
    new AlertDialog.Builder(this)
        .setTitle("清空课表")
        .setMessage("现有课程都会删掉，开学日期和洗漱、路程、容错还留着。")
        .setPositiveButton("清空", new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface dialog, int which) {
            store.courses.clear();
            store.sample = false;
            store.save();
            pending = new ArrayList<Model.Course>();
            pendingWarnings = new ArrayList<String>();
            pendingNote = "课表已清空。";
            toast("课表已清空");
            show(2);
          }
        })
        .setNegativeButton("取消", null)
        .show();
  }

  private String emptyWeekNote(int value) {
    StringBuilder sb = new StringBuilder();
    sb.append("第 ").append(value).append(" 周这张表里没有排课。");
    sb.append("\n6-8周(双) 只上双周，也就是第 6 周和第 8 周，不上第 7 周。");
    int shown = 0;
    for (Model.Course course : store.courses) {
      if (shown == 0) sb.append("\n现在读到的周次：");
      if (shown >= 5) {
        sb.append("\n…");
        break;
      }
      sb.append("\n").append(course.name).append("  ").append(course.weeks.label());
      shown++;
    }
    if (shown == 0) sb.append("\n先重新导入一次课表。");
    return sb.toString();
  }

  private static final class Trip {
    int start;
    int end;
    List<Model.Course> courses = new ArrayList<Model.Course>();
  }

  private static final class Slot {
    Calendar date;
    Model.Course course;
  }

  private View insightCard(Calendar now) {
    Calendar focus = null;
    int offset = -1;
    for (int i = 0; i < 8; i++) {
      Calendar date = Model.addDays(now, i);
      if (!Model.onDate(store.courses, store.settings, date).isEmpty()) {
        focus = date;
        offset = i;
        break;
      }
    }
    if (focus == null) return null;
    String dayWord = offset == 0 ? "今天" : offset == 1 ? "明天" : Model.WEEKDAY[Model.isoWeekday(focus)];
    List<Model.Course> courses = Model.onDate(store.courses, store.settings, focus);
    List<Trip> trips = trips(courses);
    LinearLayout card = vertical(CARD);
    round(card, 18);
    stroke(card);
    pad(card, 16, 12, 16, 12);
    TextView title = text(15, INK);
    title.setText("出门  " + face());
    title.setTypeface(Typeface.DEFAULT_BOLD);
    card.addView(title);
    addPoint(card, dayWord, trips.size() + " 次");
    int shown = 0;
    for (Trip trip : trips) {
      if (shown >= 3) break;
      addPoint(card, clockMinutes(trip.start), trip.courses.size() + " 节");
      shown++;
    }
    for (Model.Course course : courses) {
      String shift = previousRoom(course, focus);
      if (shift.length() == 0) continue;
      addPoint(card, "换教室", shortName(course.name) + "  " + shift);
    }
    StringBuilder rareNames = new StringBuilder();
    for (Model.Course course : store.courses) {
      if (!rare(course)) continue;
      Calendar next = nextDate(course, now);
      if (next == null) continue;
      int days = Model.daysBetween(now, next);
      if (days < 0 || days > 1) continue;
      if (rareNames.length() > 0) rareNames.append("、");
      rareNames.append(shortName(course.name));
      if (rareNames.length() > 18) break;
    }
    if (rareNames.length() > 0) addPoint(card, "别忘", rareNames.toString());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(12);
    card.setLayoutParams(lp);
    return card;
  }

  private void addPoint(LinearLayout card, String key, String rest) {
    TextView line = text(14, INK);
    String raw = key + "   " + rest;
    SpannableString span = new SpannableString(raw);
    span.setSpan(new ForegroundColorSpan(PINE), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    span.setSpan(new StyleSpan(Typeface.BOLD), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    line.setText(span);
    pad(line, 0, 5, 0, 0);
    card.addView(line);
  }

  private String span(Model.Course course) {
    return clockMinutes(Model.parseMinutes(course.start)) + "-" + clockMinutes(Model.parseMinutes(course.end));
  }

  private String clockMinutes(int mins) {
    int wrapped = ((mins % 1440) + 1440) % 1440;
    return (wrapped / 60) + ":" + Model.pad(wrapped % 60);
  }

  private String shortName(String name) {
    if (name == null) return "";
    return name.length() > 8 ? name.substring(0, 8) : name;
  }

  private String face() {
    String[] faces = {
      "(๑•̀ㅂ•́)و✧",
      "(*´▽`*)",
      "(｡•̀ᴗ-)✧",
      "（￣▽￣）",
      "( •̀ ω •́ )✧",
      "(๑˃̵ᴗ˂̵)و"
    };
    return faces[new java.util.Random().nextInt(faces.length)];
  }

  private String wish(String holiday) {
    if ("中秋节".equals(holiday)) return "中秋快乐";
    if ("国庆节".equals(holiday)) return "国庆快乐";
    if ("元旦".equals(holiday)) return "新年快乐";
    return holiday + "快乐";
  }

  private List<Trip> trips(List<Model.Course> courses) {
    List<Trip> trips = new ArrayList<Trip>();
    for (Model.Course course : courses) {
      int start = Model.parseMinutes(course.start);
      int end = Model.parseMinutes(course.end);
      Trip last = trips.isEmpty() ? null : trips.get(trips.size() - 1);
      if (last == null || start - last.end > 60) {
        last = new Trip();
        last.start = start;
        last.end = end;
        trips.add(last);
      }
      if (end > last.end) last.end = end;
      last.courses.add(course);
    }
    return trips;
  }

  private String previousRoom(Model.Course course, Calendar date) {
    if (course.location.length() == 0) return "";
    long target = stamp(date, course.start);
    String where = "";
    long best = -1;
    for (Model.Course other : store.courses) {
      if (!other.name.equals(course.name) || other.location.length() == 0) continue;
      for (Calendar when : Model.classDates(other, store.settings)) {
        long at = stamp(when, other.start);
        if (at < target && at > best) {
          best = at;
          where = other.location;
        }
      }
    }
    if (where.length() == 0 || where.equals(course.location)) return "";
    return where + " → " + course.location;
  }

  private long stamp(Calendar date, String hhmm) {
    Calendar copy = (Calendar) date.clone();
    copy.set(Calendar.HOUR_OF_DAY, 0);
    copy.set(Calendar.MINUTE, 0);
    copy.set(Calendar.SECOND, 0);
    copy.set(Calendar.MILLISECOND, 0);
    return copy.getTimeInMillis() + Model.parseMinutes(hhmm) * 60000L;
  }

  private boolean rare(Model.Course course) {
    int count = Model.classDates(course, store.settings).size();
    return count > 0 && count <= 4;
  }

  private Calendar nextDate(Model.Course course, Calendar now) {
    long nowMs = now.getTimeInMillis();
    for (Calendar date : Model.classDates(course, store.settings)) {
      if (stamp(date, course.end) > nowMs) return date;
    }
    return null;
  }

  private boolean canArrive(Calendar date, Model.Course course, Calendar now) {
    long start = stamp(date, course.start);
    long commute = store.settings.wake.commute * 60000L;
    return now.getTimeInMillis() + commute <= start;
  }

  private Slot firstCatchable(Calendar now) {
    Slot best = null;
    long bestAt = Long.MAX_VALUE;
    for (Model.Course course : store.courses) {
      for (Calendar date : Model.classDates(course, store.settings)) {
        if (!canArrive(date, course, now)) continue;
        long at = stamp(date, course.start);
        if (at < bestAt) {
          bestAt = at;
          best = new Slot();
          best.date = date;
          best.course = course;
        }
      }
    }
    return best;
  }

  private Slot classAfter(Model.Course shown, Calendar date) {
    long after = stamp(date, shown.start);
    Slot best = null;
    long bestAt = Long.MAX_VALUE;
    for (Model.Course course : store.courses) {
      for (Calendar when : Model.classDates(course, store.settings)) {
        long at = stamp(when, course.start);
        if (at <= after) continue;
        if (at < bestAt) {
          bestAt = at;
          best = new Slot();
          best.date = when;
          best.course = course;
        }
      }
    }
    return best;
  }

  private String missLine(Model.WakePlan plan, Calendar now) {
    if (canArrive(plan.classDate, plan.course, now)) return "";
    Slot next = firstCatchable(now);
    if (next == null) return "后面没有还能赶上的课";
    String when = Model.sameDay(next.date, now) ? "" : Model.WEEKDAY[Model.isoWeekday(next.date)] + " ";
    return "还能赶上  " + when + span(next.course) + "  " + next.course.name;
  }

  private List<String> audit(List<Model.Course> courses) {
    List<String> notes = new ArrayList<String>();
    int total = store.settings.totalWeeks;
    for (Model.Course course : courses) if (course.weeks.to > total) total = course.weeks.to;
    if (total > 40) total = 40;
    for (int i = 0; i < courses.size(); i++) {
      for (int j = i + 1; j < courses.size(); j++) {
        Model.Course a = courses.get(i);
        Model.Course b = courses.get(j);
        if (a.day != b.day) continue;
        int as = Model.parseMinutes(a.start);
        int ae = Model.parseMinutes(a.end);
        int bs = Model.parseMinutes(b.start);
        int be = Model.parseMinutes(b.end);
        if (as >= be || bs >= ae) continue;
        boolean share = false;
        for (int week = 1; week <= total; week++) {
          if (a.weeks.on(week) && b.weeks.on(week)) share = true;
        }
        if (!share) continue;
        notes.add(Model.WEEKDAY[a.day] + " " + a.start + " " + a.name + " 和 " + b.name + " 时间撞了");
        if (notes.size() >= 6) return notes;
      }
    }
    boolean[] has = new boolean[41];
    int first = 0;
    int last = 0;
    for (Model.Course course : courses) {
      for (int week = 1; week <= total && week < has.length; week++) {
        if (!course.weeks.on(week)) continue;
        has[week] = true;
        if (first == 0 || week < first) first = week;
        if (week > last) last = week;
      }
    }
    StringBuilder empty = new StringBuilder();
    int emptyCount = 0;
    for (int week = first + 1; week < last && week < has.length; week++) {
      if (has[week]) continue;
      if (emptyCount >= 6) break;
      if (emptyCount > 0) empty.append("、");
      empty.append(week);
      emptyCount++;
    }
    if (emptyCount > 0) notes.add("第 " + empty + " 周整周没课。双周或假期会这样，对一下原表。");
    java.util.LinkedHashMap<String, String> rooms = new java.util.LinkedHashMap<String, String>();
    for (Model.Course course : courses) {
      if (course.location.length() == 0) continue;
      String got = rooms.get(course.name);
      if (got == null) rooms.put(course.name, course.location);
      else if (got.indexOf(course.location) < 0) rooms.put(course.name, got + "、" + course.location);
    }
    for (java.util.Map.Entry<String, String> entry : rooms.entrySet()) {
      if (entry.getValue().indexOf("、") < 0) continue;
      notes.add(entry.getKey() + " 不止一间教室：" + entry.getValue());
      if (notes.size() >= 8) break;
    }
    return notes;
  }

  private boolean weekHas(int value) {
    for (Model.Course course : store.courses) if (course.weeks.on(value)) return true;
    return false;
  }

  private void review(List<Model.Course> found, String fail) {
    if ((found == null || found.isEmpty()) && fail == null && TableParser.foundPeriodSets != null && TableParser.foundPeriodSets.size() > 1) {
      choosePeriods(TableParser.foundPeriodSets);
      return;
    }
    if ((found == null || found.isEmpty()) && fail == null && TableParser.foundPeriods != null && TableParser.foundPeriods.size() >= 3) {
      adoptPeriods(TableParser.foundPeriods, "");
      return;
    }
    pending = found == null ? new ArrayList<Model.Course>() : found;
    pendingWarnings = fail != null || pending.isEmpty() ? new ArrayList<String>() : audit(pending);
    String base = fail != null ? fail : (pending.isEmpty() ? "没有识别到课程。检查是不是周一到周日的表头，或课程、星期、时间这几列。" : "识别到 " + pending.size() + " 门课，确认后替换或追加。");
    if (!pendingWarnings.isEmpty()) base = base + " 下面有 " + pendingWarnings.size() + " 处先对一下原表。";
    pendingNote = base;
    show(3);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQ_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
      readImage(data.getData());
      return;
    }
    if (requestCode != REQ_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    try {
      InputStream in = getContentResolver().openInputStream(uri);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
      in.close();
      byte[] bytes = bos.toByteArray();
      if (TableParser.looksZip(bytes)) {
        File tmp = new File(getCacheDir(), "import.xlsx");
        FileOutputStream out = new FileOutputStream(tmp);
        out.write(bytes);
        out.close();
        review(TableParser.fromXlsx(tmp, store.settings), null);
        return;
      }
      if (TableParser.looksOle(bytes)) {
        File tmp = new File(getCacheDir(), "import.xls");
        FileOutputStream out = new FileOutputStream(tmp);
        out.write(bytes);
        out.close();
        review(TableParser.fromXls(tmp, store.settings), null);
        return;
      }
      String text = TableParser.asText(bytes).trim();
      if (text.startsWith("{")) {
        importJson(text);
        return;
      }
      if (TableParser.looksMarkup(text)) {
        List<Model.Course> markup = TableParser.fromMarkup(text, store.settings);
        if (!markup.isEmpty()) {
          review(markup, null);
          return;
        }
      }
      review(TableParser.fromText(text, store.settings), null);
    } catch (Exception e) {
      review(new ArrayList<Model.Course>(), "这个文件读不了。xls、xlsx、csv 或图片都可以。");
    }
  }

  private void importJson(String text) {
    try {
      JSONObject root = new JSONObject(text);
      JSONArray arr = root.optJSONArray("courses");
      if (arr == null) {
        review(new ArrayList<Model.Course>(), "备份里没有课程。");
        return;
      }
      JSONObject s = root.optJSONObject("settings");
      if (s != null) {
        store.settings.termStart = s.optString("termStart", store.settings.termStart);
        store.settings.totalWeeks = s.optInt("totalWeeks", store.settings.totalWeeks);
        JSONObject wake = s.optJSONObject("wake");
        if (wake != null) {
          store.settings.wake.wash = wake.optInt("washMin", store.settings.wake.wash);
          store.settings.wake.commute = wake.optInt("commuteMin", store.settings.wake.commute);
          store.settings.wake.buffer = wake.optInt("bufferMin", store.settings.wake.buffer);
          store.settings.wake.remind = wake.optInt("classRemindMin", store.settings.wake.remind);
        }
      }
      List<Model.Course> list = new ArrayList<Model.Course>();
      for (int i = 0; i < arr.length(); i++) {
        Model.Course course = Model.Course.fromJson(arr.getJSONObject(i));
        course.id = Store.newId();
        if (course.name.length() > 0) list.add(course);
      }
      review(list, null);
    } catch (Exception e) {
      review(new ArrayList<Model.Course>(), "备份格式不对。");
    }
  }

  private void openEditor(final Model.Course existing) {
    final Model.Course course = existing == null ? new Model.Course() : existing;
    ScrollView scroll = new ScrollView(this);
    LinearLayout col = vertical(0);
    pad(col, 16, 8, 16, 8);
    final EditText name = field(course.name, "课程名");
    final EditText teacher = field(course.teacher, "教师");
    final EditText location = field(course.location, "地点");
    final EditText start = field(course.start, "开始 08:00");
    final EditText end = field(course.end, "结束 09:40");
    final EditText from = field(String.valueOf(course.weeks.from), "起始周");
    final EditText to = field(String.valueOf(course.weeks.to), "结束周");
    from.setInputType(InputType.TYPE_CLASS_NUMBER);
    to.setInputType(InputType.TYPE_CLASS_NUMBER);
    final int[] day = new int[] {course.day};
    final String[] kind = new String[] {course.weeks.kind == null ? "all" : course.weeks.kind};
    col.addView(name);
    col.addView(teacher);
    col.addView(location);
    col.addView(dayPicker(day));
    col.addView(start);
    col.addView(end);
    col.addView(kindPicker(kind));
    col.addView(from);
    col.addView(to);
    scroll.addView(col);
    AlertDialog.Builder builder = new AlertDialog.Builder(this)
        .setTitle(existing == null ? "添加课程" : "编辑课程")
        .setView(scroll)
        .setPositiveButton("保存", null)
        .setNegativeButton("取消", null);
    if (existing != null) {
      builder.setNeutralButton("删除", new android.content.DialogInterface.OnClickListener() {
        public void onClick(android.content.DialogInterface dialog, int which) {
          store.courses.remove(existing);
          store.sample = false;
          store.save();
          show(tab);
        }
      });
    }
    final AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
      public void onShow(android.content.DialogInterface d) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {
            String title = name.getText().toString().trim();
            String st = Model.normTime(start.getText().toString());
            String en = Model.normTime(end.getText().toString());
            if (title.length() == 0 || st.length() == 0 || en.length() == 0) {
              toast("需要课程名和开始、结束时间");
              return;
            }
            course.name = title;
            course.teacher = teacher.getText().toString().trim();
            course.location = location.getText().toString().trim();
            course.day = day[0];
            course.start = st;
            course.end = en;
            course.weeks.kind = "list".equals(kind[0]) ? "all" : kind[0];
            course.weeks.from = parseInt(from.getText().toString(), 1);
            course.weeks.to = parseInt(to.getText().toString(), store.settings.totalWeeks);
            if (course.weeks.from > course.weeks.to) {
              int tmp = course.weeks.from;
              course.weeks.from = course.weeks.to;
              course.weeks.to = tmp;
            }
            if (existing == null) {
              course.id = Store.newId();
              store.courses.add(course);
            }
            store.sample = false;
            java.util.Collections.sort(store.courses, new java.util.Comparator<Model.Course>() {
              public int compare(Model.Course a, Model.Course b) {
                if (a.day != b.day) return a.day - b.day;
                return Model.parseMinutes(a.start) - Model.parseMinutes(b.start);
              }
            });
            store.save();
            dialog.dismiss();
            show(tab);
          }
        });
      }
    });
    dialog.show();
  }

  private LinearLayout dayPicker(final int[] day) {
    LinearLayout row = horizontal();
    final Button[] buttons = new Button[7];
    for (int i = 1; i <= 7; i++) {
      final int value = i;
      Button b = button(Model.WEEKDAY[i].substring(1), day[0] == i);
      buttons[i - 1] = b;
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          day[0] = value;
          for (int n = 0; n < buttons.length; n++) styleButton(buttons[n], n + 1 == value);
        }
      });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      row.addView(b, lp);
    }
    return row;
  }

  private LinearLayout kindPicker(final String[] kind) {
    LinearLayout row = horizontal();
    final String[] keys = {"all", "odd", "even"};
    final String[] labels = {"每周", "单周", "双周"};
    final Button[] buttons = new Button[3];
    for (int i = 0; i < 3; i++) {
      final int index = i;
      Button b = button(labels[i], keys[i].equals(kind[0]));
      buttons[i] = b;
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          kind[0] = keys[index];
          for (int n = 0; n < buttons.length; n++) styleButton(buttons[n], n == index);
        }
      });
      row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }
    return row;
  }

  private String periodSummary() {
    if (store.settings.periods.isEmpty()) return "上课时间";
    Model.Period first = store.settings.periods.get(0);
    return "上课时间  " + first.index + "  " + clockMinutes(Model.parseMinutes(first.start)) + "-" + clockMinutes(Model.parseMinutes(first.end));
  }

  private void openPeriods() {
    ScrollView scroll = new ScrollView(this);
    LinearLayout col = vertical(0);
    pad(col, 8, 4, 8, 4);
    TextView hint = text(13, MUTED);
    hint.setText("课表没写钟点时，按节次套这张表。一行一节，例如 1 08:00-08:45，或 1-2 08:00-09:40。");
    col.addView(hint);
    final EditText paste = new EditText(this);
    paste.setHint("贴时间表");
    paste.setMinLines(3);
    paste.setTextColor(INK);
    paste.setHintTextColor(MUTED);
    paste.setGravity(Gravity.TOP);
    col.addView(paste);
    final List<EditText> starts = new ArrayList<EditText>();
    final List<EditText> ends = new ArrayList<EditText>();
    List<Model.Period> periods = store.settings.periods;
    int count = Math.max(12, periods.size());
    for (int i = 1; i <= count; i++) {
      Model.Period period = Model.findPeriod(periods, i);
      LinearLayout row = horizontal();
      row.setGravity(Gravity.CENTER_VERTICAL);
      TextView index = text(14, INK);
      index.setText(String.valueOf(i));
      index.setGravity(Gravity.CENTER);
      EditText start = field(period == null ? "" : period.start, "08:00");
      EditText end = field(period == null ? "" : period.end, "08:45");
      starts.add(start);
      ends.add(end);
      row.addView(index, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));
      LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      box.leftMargin = dp(6);
      start.setLayoutParams(box);
      end.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      row.addView(start);
      row.addView(end);
      col.addView(row);
    }
    scroll.addView(col);
    final AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("上课时间")
        .setView(scroll)
        .setPositiveButton("保存并套到课表", null)
        .setNeutralButton("填入粘贴", null)
        .setNegativeButton("取消", null)
        .create();
    dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
      public void onShow(android.content.DialogInterface d) {
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {
            List<Model.PeriodSet> parsed = Model.parsePeriodSets(paste.getText().toString());
            if (parsed.isEmpty()) {
              toast("没读到时间。一行写成 1 08:00-08:45，或 第一、二节 8:30-9:50");
              return;
            }
            if (parsed.size() > 1) {
              dialog.dismiss();
              choosePeriods(parsed);
              return;
            }
            fillPeriodFields(starts, ends, parsed.get(0).periods);
            toast("填了 " + parsed.get(0).periods.size() + " 节，确认后保存");
          }
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) { savePeriods(starts, ends, dialog); }
        });
      }
    });
    dialog.show();
  }

  private void savePeriods(List<EditText> starts, List<EditText> ends, AlertDialog dialog) {
    List<Model.Period> next = new ArrayList<Model.Period>();
    for (int i = 0; i < starts.size(); i++) {
      String start = Model.normTime(starts.get(i).getText().toString());
      String end = Model.normTime(ends.get(i).getText().toString());
      if (start.length() == 0 && end.length() == 0) continue;
      if (start.length() == 0 || end.length() == 0 || Model.parseMinutes(start) >= Model.parseMinutes(end)) {
        toast("第 " + (i + 1) + " 节时间不对");
        return;
      }
      next.add(new Model.Period(i + 1, start, end));
    }
    if (next.isEmpty()) {
      toast("至少写一节课的时间");
      return;
    }
    store.settings.periods = next;
    int changed = Model.applySectionTimes(store.courses, next);
    store.save();
    dialog.dismiss();
    toast(changed == 0 ? "已保存上课时间" : "已套到 " + changed + " 门课");
    show(tab);
  }

  private void fillPeriodFields(List<EditText> starts, List<EditText> ends, List<Model.Period> parsed) {
    for (int i = 0; i < starts.size(); i++) {
      Model.Period period = Model.findPeriod(parsed, i + 1);
      starts.get(i).setText(period == null ? "" : period.start);
      ends.get(i).setText(period == null ? "" : period.end);
    }
  }

  private void adoptPeriods(List<Model.Period> periods, String name) {
    store.settings.periods = periods;
    int changed = Model.applySectionTimes(store.courses, periods);
    store.save();
    pending = new ArrayList<Model.Course>();
    pendingWarnings = new ArrayList<String>();
    String title = name.length() == 0 ? "" : name + "  ";
    pendingNote = title + "读到 " + periods.size() + " 节上课时间，已套到 " + changed + " 门课。";
    show(3);
  }

  private void choosePeriods(final List<Model.PeriodSet> sets) {
    String[] labels = new String[sets.size()];
    for (int i = 0; i < sets.size(); i++) {
      Model.PeriodSet set = sets.get(i);
      Model.Period first = Model.findPeriod(set.periods, 1);
      Model.Period second = Model.findPeriod(set.periods, 2);
      String range = first != null && second != null ? clockMinutes(Model.parseMinutes(first.start)) + "-" + clockMinutes(Model.parseMinutes(second.end)) : "";
      labels[i] = (set.name.length() == 0 ? "方案 " + (i + 1) : set.name) + "    " + range;
    }
    boolean campus = false;
    for (Model.PeriodSet set : sets) if (set.name.contains("校区")) campus = true;
    new AlertDialog.Builder(this)
        .setTitle(campus ? "选你的校区" : "选上课时间")
        .setItems(labels, new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface dialog, int which) {
            adoptPeriods(sets.get(which).periods, sets.get(which).name);
          }
        })
        .setNegativeButton("取消", null)
        .show();
  }

  private void readImage(final Uri uri) {
    toast("正在识别图片");
    new Thread(new Runnable() {
      public void run() {
        try {
          InputStream probe = getContentResolver().openInputStream(uri);
          BitmapFactory.Options bounds = new BitmapFactory.Options();
          bounds.inJustDecodeBounds = true;
          BitmapFactory.decodeStream(probe, null, bounds);
          probe.close();
          int sample = 1;
          int max = Math.max(bounds.outWidth, bounds.outHeight);
          while (max / sample > 1800) sample *= 2;
          BitmapFactory.Options opts = new BitmapFactory.Options();
          opts.inSampleSize = sample;
          InputStream in = getContentResolver().openInputStream(uri);
          final Bitmap bitmap = BitmapFactory.decodeStream(in, null, opts);
          in.close();
          if (bitmap == null) throw new IllegalStateException("图片打不开");
          final String text = Ocr.read(MainActivity.this, bitmap);
          bitmap.recycle();
          runOnUiThread(new Runnable() {
            public void run() { useOcr(text); }
          });
        } catch (Exception e) {
          runOnUiThread(new Runnable() {
            public void run() { toast("图片没认出来"); }
          });
        }
      }
    }).start();
  }

  private void useOcr(String text) {
    String raw = text == null ? "" : text.trim();
    if (raw.length() == 0) {
      toast("图片里没有认出字");
      return;
    }
    List<Model.Course> courses = TableParser.fromText(raw, store.settings);
    if (courses != null && !courses.isEmpty()) {
      review(courses, null);
      return;
    }
    List<Model.PeriodSet> sets = Model.parsePeriodSets(raw);
    if (sets.size() > 0) {
      choosePeriods(sets);
      return;
    }
    pending = new ArrayList<Model.Course>();
    pendingWarnings = new ArrayList<String>();
    pendingNote = "没对上上课时间。点上面的上课时间自己填，或把表格拍得更正一点再试。";
    show(3);
  }

  private void openStyle() {
    final String[] keys = {"glass", "plain"};
    final String[] names = {"柔光玻璃", "普通"};
    final int[] picked = new int[] {"plain".equals(store.settings.style) ? 1 : 0};
    LinearLayout wrap = vertical(0);
    pad(wrap, 16, 8, 16, 4);
    TextView title = text(18, INK);
    title.setText("界面风格");
    title.setGravity(Gravity.CENTER);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    pad(title, 4, 0, 4, 14);
    wrap.addView(title);
    final LinearLayout row = horizontal();
    final LinearLayout[] boxes = new LinearLayout[2];
    for (int i = 0; i < 2; i++) {
      final int index = i;
      LinearLayout item = vertical(0);
      item.setGravity(Gravity.CENTER_HORIZONTAL);
      FrameLayout card = new FrameLayout(this);
      card.addView(stylePreview(i == 0), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      item.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
      TextView label = text(15, MUTED);
      label.setText(names[i]);
      label.setGravity(Gravity.CENTER);
      pad(label, 0, 10, 0, 0);
      item.addView(label);
      item.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          picked[0] = index;
          for (int n = 0; n < boxes.length; n++) paintStyle(boxes[n], n == index);
        }
      });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      if (i > 0) lp.leftMargin = dp(12);
      boxes[i] = item;
      row.addView(item, lp);
    }
    for (int i = 0; i < boxes.length; i++) paintStyle(boxes[i], i == picked[0]);
    wrap.addView(row);
    new AlertDialog.Builder(this)
        .setView(wrap)
        .setPositiveButton("完成", new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface dialog, int which) {
            store.settings.style = keys[picked[0]];
            store.save();
            buildUi();
          }
        })
        .show();
  }

  private void paintStyle(LinearLayout item, boolean on) {
    FrameLayout card = (FrameLayout) item.getChildAt(0);
    GradientDrawable ring = new GradientDrawable();
    ring.setCornerRadius(dp(26));
    ring.setColor(0x00000000);
    ring.setStroke(dp(on ? 3 : 0), on ? 0xFF2F6BFF : 0x00000000);
    card.setBackground(ring);
    card.setPadding(dp(on ? 3 : 0), dp(on ? 3 : 0), dp(on ? 3 : 0), dp(on ? 3 : 0));
    TextView label = (TextView) item.getChildAt(1);
    label.setTextColor(on ? 0xFF2F6BFF : INK);
    label.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
  }

  private View stylePreview(boolean glass) {
    FrameLayout card = new FrameLayout(this);
    GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TL_BR, glass
        ? new int[] {0xFFD5E6F8, 0xFFF4F8FC, 0xFFB9D4F2}
        : new int[] {0xFFF6F1E6, 0xFFEFE8DA, 0xFFE4DCCE});
    sky.setCornerRadius(dp(22));
    card.setBackground(sky);
    View pill = new View(this);
    GradientDrawable glassPill = new GradientDrawable();
    glassPill.setCornerRadius(dp(18));
    if (glass) {
      glassPill.setColors(new int[] {0xE6FFFFFF, 0x66D7E8F8, 0x99FFFFFF});
      glassPill.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
      glassPill.setStroke(Math.max(1, dp(1)), 0xEEFFFFFF);
    } else {
      glassPill.setColor(0xFFF7F3EA);
      glassPill.setStroke(Math.max(1, dp(1)), 0xFFE4DDD0);
    }
    pill.setBackground(glassPill);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(96), dp(30), Gravity.CENTER);
    card.addView(pill, lp);
    return card;
  }

  private boolean glass() {
    return store == null || store.settings == null || !"plain".equals(store.settings.style);
  }

  private View glassBackdrop() {
    FrameLayout box = new FrameLayout(this);
    GradientDrawable wash = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFFC9DFF6, 0xFFF6E7D6, 0xFFD5EDE4});
    box.setBackground(wash);
    box.addView(orb(0xFF7EB6F2, dp(340), dp(-80), dp(-20)));
    box.addView(orb(0xFFF3B48A, dp(280), dp(160), dp(180)));
    box.addView(orb(0xFF8ED4C0, dp(300), dp(-60), dp(460)));
    box.addView(orb(0xFFC9B6F0, dp(240), dp(140), dp(700)));
    if (Build.VERSION.SDK_INT >= 31) {
      box.setRenderEffect(RenderEffect.createBlurEffect(46f, 46f, Shader.TileMode.CLAMP));
    }
    return box;
  }

  private View orb(int color, int size, int left, int top) {
    View view = new View(this);
    GradientDrawable light = new GradientDrawable();
    light.setShape(GradientDrawable.OVAL);
    light.setGradientType(GradientDrawable.RADIAL_GRADIENT);
    light.setGradientRadius(size / 2f);
    int edge = color & 0x00FFFFFF;
    light.setColors(new int[] {color, edge});
    view.setBackground(light);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
    lp.leftMargin = left;
    lp.topMargin = top;
    view.setLayoutParams(lp);
    return view;
  }

  private int frost(int color) {
    return glassPlateColor(color);
  }

  private int glassPlateColor(int tint) {
    int r = (tint >> 16) & 0xFF;
    int g = (tint >> 8) & 0xFF;
    int b = tint & 0xFF;
    int lum = (r * 3 + g * 6 + b) / 10;
    if (lum < 110) return (0xA6 << 24) | (tint & 0x00FFFFFF);
    int rr = (r + 255 * 2) / 3;
    int gg = (g + 255 * 2) / 3;
    int bb = (b + 255 * 2) / 3;
    return (0x73 << 24) | (rr << 16) | (gg << 8) | bb;
  }

  private GradientDrawable glassPlate(int radius, int tint) {
    int r = (tint >> 16) & 0xFF;
    int g = (tint >> 8) & 0xFF;
    int b = tint & 0xFF;
    int lum = (r * 3 + g * 6 + b) / 10;
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(radius));
    bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
    if (lum < 110) {
      int hi = (0xD4 << 24) | (((r + 255) / 2) << 16) | (((g + 255) / 2) << 8) | ((b + 255) / 2);
      int lo = (0x8C << 24) | (r << 16) | (g << 8) | b;
      bg.setColors(new int[] {hi, lo});
    } else {
      int mid = (0x78 << 24) | (((r + 255) / 2) << 16) | (((g + 255) / 2) << 8) | ((b + 255) / 2);
      int lo = (0x55 << 24) | (((r * 2 + 255) / 3) << 16) | (((g * 2 + 255) / 3) << 8) | ((b * 2 + 255) / 3);
      bg.setColors(new int[] {0xEEFFFFFF, mid, lo});
    }
    bg.setStroke(Math.max(1, dp(1)), 0xF7FFFFFF);
    return bg;
  }

  private void openSettings() {
    LinearLayout col = vertical(0);
    pad(col, 16, 8, 16, 8);
    final EditText term = field(store.settings.termStart, "开学周一 2026-09-07");
    final EditText weeks = field(String.valueOf(store.settings.totalWeeks), "总周数");
    final EditText wash = field(String.valueOf(store.settings.wake.wash), "洗漱分钟");
    final EditText commute = field(String.valueOf(store.settings.wake.commute), "路程分钟");
    final EditText buffer = field(String.valueOf(store.settings.wake.buffer), "容错分钟");
    final EditText remind = field(String.valueOf(store.settings.wake.remind), "课前提醒分钟");
    weeks.setInputType(InputType.TYPE_CLASS_NUMBER);
    wash.setInputType(InputType.TYPE_CLASS_NUMBER);
    commute.setInputType(InputType.TYPE_CLASS_NUMBER);
    buffer.setInputType(InputType.TYPE_CLASS_NUMBER);
    remind.setInputType(InputType.TYPE_CLASS_NUMBER);
    col.addView(label("开学第一周的周一"));
    col.addView(term);
    col.addView(label("学期周数"));
    col.addView(weeks);
    col.addView(label("起床 = 第一节课 − 洗漱 − 路程 − 容错"));
    col.addView(wash);
    col.addView(commute);
    col.addView(buffer);
    col.addView(remind);
    new AlertDialog.Builder(this)
        .setTitle("作息")
        .setView(col)
        .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface dialog, int which) {
            String date = term.getText().toString().trim();
            if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
              toast("开学日期写成 2026-09-07");
              return;
            }
            store.settings.termStart = date;
            store.settings.totalWeeks = Math.max(1, Math.min(40, parseInt(weeks.getText().toString(), 16)));
            store.settings.wake.wash = parseInt(wash.getText().toString(), 30);
            store.settings.wake.commute = parseInt(commute.getText().toString(), 45);
            store.settings.wake.buffer = parseInt(buffer.getText().toString(), 15);
            store.settings.wake.remind = parseInt(remind.getText().toString(), 20);
            store.save();
            weekReady = false;
            show(tab);
          }
        })
        .setNegativeButton("取消", null)
        .show();
  }

  private List<Model.Course> single(Model.Course course) {
    List<Model.Course> list = new ArrayList<Model.Course>();
    list.add(course);
    return list;
  }

  private String courseDetail(Model.Course course) {
    int count = Model.classDates(course, store.settings).size();
    String text = course.weeks.label() + "  ·  " + count + " 次";
    String holidays = holidayLine(course);
    if (holidays.length() > 0) text = text + "  ·  " + holidays;
    return text;
  }

  private String holidayLine(Model.Course course) {
    StringBuilder sb = new StringBuilder();
    java.util.LinkedHashMap<String, Boolean> seen = new java.util.LinkedHashMap<String, Boolean>();
    for (Calendar date : Model.classDates(course, store.settings)) {
      String name = Holidays.off(date);
      if (name != null) seen.put(name, Boolean.TRUE);
    }
    for (String name : seen.keySet()) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(wish(name));
    }
    return sb.toString();
  }

  private void confirmClearCalendar() {
    if (store.courses.isEmpty()) {
      toast("课表是空的，没有要清的日历");
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("清掉日历里的课")
        .setMessage("只删和课表同名的日程，包括重复写入的。其他事情不动。")
        .setPositiveButton("清掉", new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface dialog, int which) {
            calendarClear = true;
            if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
              finishClearCalendar();
            } else {
              requestPermissions(new String[] {Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CALENDAR);
            }
          }
        })
        .setNegativeButton("取消", null)
        .show();
  }

  private void finishClearCalendar() {
    calendarClear = false;
    int removed = clearCourseEvents();
    if (removed < 0) toast("没找到可写的系统日历");
    else if (removed == 0) toast("日历里没有这些课");
    else toast("已从日历清掉 " + removed + " 节");
  }

  private int clearCourseEvents() {
    List<Long> calendars = new ArrayList<Long>();
    Cursor cursor = null;
    try {
      cursor = getContentResolver().query(
          Calendars.CONTENT_URI,
          new String[] {Calendars._ID, Calendars.CALENDAR_ACCESS_LEVEL},
          Calendars.VISIBLE + "=1",
          null,
          null);
      if (cursor == null) return -1;
      while (cursor.moveToNext()) {
        if (cursor.getInt(1) >= Calendars.CAL_ACCESS_CONTRIBUTOR) calendars.add(Long.valueOf(cursor.getLong(0)));
      }
    } catch (Exception e) {
      return -1;
    } finally {
      if (cursor != null) cursor.close();
    }
    if (calendars.isEmpty()) return -1;
    List<String> titles = new ArrayList<String>();
    for (Model.Course course : store.courses) {
      String name = course.name == null ? "" : course.name.trim();
      if (name.length() == 0 || titles.contains(name)) continue;
      titles.add(name);
    }
    int removed = 0;
    for (Long calId : calendars) {
      for (String title : titles) {
        try {
          removed += getContentResolver().delete(
              Events.CONTENT_URI,
              Events.CALENDAR_ID + "=? AND " + Events.TITLE + "=?",
              new String[] {String.valueOf(calId.longValue()), title});
        } catch (Exception ignored) {
        }
      }
    }
    return removed;
  }

  private void askCalendar(List<Model.Course> courses) {
    if (courses == null || courses.isEmpty()) {
      toast("还没有课可以写入");
      return;
    }
    calendarQueue = courses;
    if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
      finishCalendar();
      return;
    }
    requestPermissions(new String[] {Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CALENDAR);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode != REQ_CALENDAR) return;
    boolean ok = grantResults.length >= 2
        && grantResults[0] == PackageManager.PERMISSION_GRANTED
        && grantResults[1] == PackageManager.PERMISSION_GRANTED;
    if (!ok) {
      calendarClear = false;
      toast("没有日历权限");
      return;
    }
    if (calendarClear) finishClearCalendar();
    else finishCalendar();
  }

  private void finishCalendar() {
    List<Model.Course> courses = calendarQueue;
    if (courses == null) return;
    int added = writeCourses(courses);
    if (added < 0) toast("没找到可写的系统日历，先在日历里登录一个账号");
    else if (added == 0) toast("剩下的课都已经在日历里");
    else toast("已写入 " + added + " 节，每节提前 " + remindMinutes() + " 分钟提醒");
  }

  private int remindMinutes() {
    return store.settings.wake.remind > 0 ? store.settings.wake.remind : 20;
  }

  private int writeCourses(List<Model.Course> courses) {
    long calId = calendarId();
    if (calId < 0) return -1;
    int added = 0;
    long nowMs = System.currentTimeMillis();
    String zone = TimeZone.getDefault().getID();
    for (Model.Course course : courses) {
      for (Calendar date : Model.classDates(course, store.settings)) {
        long start = atMillis(date, course.start);
        long end = atMillis(date, course.end);
        if (end <= nowMs) continue;
        if (eventExists(calId, course.name, start)) continue;
        ContentValues values = new ContentValues();
        values.put(Events.CALENDAR_ID, calId);
        values.put(Events.TITLE, course.name);
        values.put(Events.EVENT_LOCATION, course.location);
        String off = Holidays.off(date);
        String desc = course.teacher;
        if (off != null) desc = (desc.length() == 0 ? "" : desc + " · ") + off;
        values.put(Events.DESCRIPTION, desc);
        values.put(Events.DTSTART, start);
        values.put(Events.DTEND, end);
        values.put(Events.EVENT_TIMEZONE, zone);
        values.put(Events.HAS_ALARM, 1);
        Uri uri;
        try {
          uri = getContentResolver().insert(Events.CONTENT_URI, values);
        } catch (Exception e) {
          return added == 0 ? -1 : added;
        }
        if (uri == null) continue;
        long eventId = Long.parseLong(uri.getLastPathSegment());
        ContentValues reminder = new ContentValues();
        reminder.put(Reminders.EVENT_ID, eventId);
        reminder.put(Reminders.MINUTES, remindMinutes());
        reminder.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        try {
          getContentResolver().insert(Reminders.CONTENT_URI, reminder);
        } catch (Exception ignored) {
        }
        if (rare(course)) {
          ContentValues early = new ContentValues();
          early.put(Reminders.EVENT_ID, eventId);
          early.put(Reminders.MINUTES, 24 * 60);
          early.put(Reminders.METHOD, Reminders.METHOD_ALERT);
          try {
            getContentResolver().insert(Reminders.CONTENT_URI, early);
          } catch (Exception ignored) {
          }
        }
        added++;
      }
    }
    return added;
  }

  private long atMillis(Calendar date, String hhmm) {
    Calendar copy = (Calendar) date.clone();
    int mins = Model.parseMinutes(hhmm);
    copy.set(Calendar.HOUR_OF_DAY, mins / 60);
    copy.set(Calendar.MINUTE, mins % 60);
    copy.set(Calendar.SECOND, 0);
    copy.set(Calendar.MILLISECOND, 0);
    return copy.getTimeInMillis();
  }

  private boolean eventExists(long calId, String title, long start) {
    Cursor cursor = null;
    try {
      cursor = getContentResolver().query(
          Events.CONTENT_URI,
          new String[] {Events._ID},
          Events.CALENDAR_ID + "=? AND " + Events.TITLE + "=? AND " + Events.DTSTART + "=?",
          new String[] {String.valueOf(calId), title, String.valueOf(start)},
          null);
      return cursor != null && cursor.moveToFirst();
    } catch (Exception e) {
      return false;
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  private long calendarId() {
    Cursor cursor = null;
    try {
      cursor = getContentResolver().query(
          Calendars.CONTENT_URI,
          new String[] {Calendars._ID, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.IS_PRIMARY},
          Calendars.VISIBLE + "=1",
          null,
          null);
      if (cursor == null) return -1;
      long fallback = -1;
      long primary = -1;
      while (cursor.moveToNext()) {
        int access = cursor.getInt(1);
        if (access < Calendars.CAL_ACCESS_CONTRIBUTOR) continue;
        long id = cursor.getLong(0);
        if (fallback < 0) fallback = id;
        if (cursor.getInt(2) == 1) primary = id;
      }
      return primary >= 0 ? primary : fallback;
    } catch (Exception e) {
      return -1;
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  private Button chip(String label, boolean filled) {
    Button button = new Button(this);
    button.setText(label);
    button.setAllCaps(false);
    button.setTextSize(13);
    styleButton(button, filled);
    button.setMinWidth(dp(48));
    button.setMinimumWidth(dp(48));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.rightMargin = dp(6);
    button.setLayoutParams(lp);
    return button;
  }

  private Calendar alarmMoment(Model.Course course, Calendar date, Calendar now) {
    int start = Model.parseMinutes(course.start);
    int[] leads = new int[] {store.settings.wake.lead(), store.settings.wake.remind, 0};
    for (int i = 0; i < leads.length; i++) {
      Calendar at = (Calendar) date.clone();
      at.set(Calendar.SECOND, 0);
      at.set(Calendar.MILLISECOND, 0);
      at.set(Calendar.HOUR_OF_DAY, 0);
      at.set(Calendar.MINUTE, 0);
      at.add(Calendar.MINUTE, start - Math.max(0, leads[i]));
      if (at.getTimeInMillis() > now.getTimeInMillis()) return at;
    }
    return null;
  }

  private void setAlarm(int hour, int minute, String message) {
    int state = enableExisting(hour, minute);
    if (state == 1) {
      toast("这个时间已经有闹钟了");
      return;
    }
    if (state == 2) {
      toast("已打开原来的闹钟");
      return;
    }
    if (state == 3) {
      toast("这个时间已经有闹钟，去时钟里打开");
      try {
        startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
      } catch (Exception e) {
        toast("这台手机没有可用的系统时钟");
      }
      return;
    }
    try {
      Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
      intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
      intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
      intent.putExtra(AlarmClock.EXTRA_MESSAGE, message);
      intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
      startActivity(intent);
    } catch (Exception e) {
      toast("这台手机没有可用的系统时钟");
    }
  }

  private int enableExisting(int hour, int minute) {
    String[] urls = new String[] {
      "content://com.android.deskclock/alarms",
      "content://com.google.android.deskclock/alarms",
      "content://com.android.deskclock/alarm",
      "content://com.google.android.deskclock/alarm",
      "content://com.coloros.alarmclock/alarm",
      "content://com.oplus.alarmclock/alarm",
      "content://com.oppo.alarmclock/alarm",
      "content://com.miui.clock/alarm",
      "content://com.android.BBKClock/alarm",
      "content://com.huawei.deskclock/alarm",
      "content://com.sec.android.app.clockpackage/alarm"
    };
    for (String url : urls) {
      Cursor cursor = null;
      try {
        cursor = getContentResolver().query(Uri.parse(url), null, null, null, null);
        if (cursor == null) continue;
        int hourCol = firstCol(cursor, "hour", "alarm_hour");
        int minCol = firstCol(cursor, "minutes", "minute", "alarm_minutes");
        int onCol = firstCol(cursor, "enabled", "alarm_enabled", "enable");
        int idCol = cursor.getColumnIndex("_id");
        if (hourCol < 0 || minCol < 0) {
          cursor.close();
          continue;
        }
        long turnOn = -1;
        String onName = onCol < 0 ? null : cursor.getColumnName(onCol);
        while (cursor.moveToNext()) {
          if (cursor.getInt(hourCol) != hour || cursor.getInt(minCol) != minute) continue;
          if (onCol < 0 || cursor.getInt(onCol) != 0) {
            cursor.close();
            return 1;
          }
          if (turnOn < 0 && idCol >= 0) turnOn = cursor.getLong(idCol);
        }
        cursor.close();
        cursor = null;
        if (turnOn >= 0) {
          if (onName == null) return 3;
          ContentValues values = new ContentValues();
          values.put(onName, 1);
          int updated = getContentResolver().update(Uri.parse(url), values, "_id=?", new String[] {String.valueOf(turnOn)});
          return updated > 0 ? 2 : 3;
        }
      } catch (Exception ignored) {
        if (cursor != null) cursor.close();
      }
    }
    return 0;
  }

  private int firstCol(Cursor cursor, String a, String b, String c) {
    int i = cursor.getColumnIndex(a);
    if (i >= 0) return i;
    i = cursor.getColumnIndex(b);
    if (i >= 0) return i;
    return cursor.getColumnIndex(c);
  }

  private int firstCol(Cursor cursor, String a, String b) {
    int i = cursor.getColumnIndex(a);
    return i >= 0 ? i : cursor.getColumnIndex(b);
  }

  private void share(String filename, String content, String mime) {
    try {
      File file = new File(getCacheDir(), filename);
      FileOutputStream out = new FileOutputStream(file);
      out.write(content.getBytes("UTF-8"));
      out.close();
      Uri uri = Uri.parse("content://app.kezhong.share/" + Uri.encode(filename));
      Intent send = new Intent(Intent.ACTION_SEND);
      send.setType(mime);
      send.putExtra(Intent.EXTRA_STREAM, uri);
      send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(send, "保存"));
    } catch (Exception e) {
      toast("导出失败");
    }
  }

  private LinearLayout nav() {
    LinearLayout bar = horizontal();
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setBackgroundColor(glass() ? 0xFFFFFFFF : CARD);
    round(bar, 28);
    if (glass() && bar.getBackground() instanceof GradientDrawable) {
      ((GradientDrawable) bar.getBackground()).setStroke(Math.max(1, dp(1)), 0xEEFFFFFF);
    }
    pad(bar, 4, 4, 4, 4);
    String[] labels = {"主页", "周课", "课程", "设置"};
    for (int i = 0; i < labels.length; i++) {
      final int index = i;
      TextView item = text(14, MUTED);
      item.setText(labels[i]);
      item.setGravity(Gravity.CENTER);
      item.setPadding(dp(4), dp(11), dp(4), dp(11));
      item.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { show(index); }
      });
      bar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }
    return bar;
  }

  private void paintNav() {
    for (int i = 0; i < nav.getChildCount(); i++) {
      TextView item = (TextView) nav.getChildAt(i);
      boolean on = i == tab;
      item.setTextColor(on ? CREAM : MUTED);
      item.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(dp(22));
      bg.setColor(on ? PINE : 0x00000000);
      item.setBackground(bg);
    }
  }

  private View cardText(String title, String body) {
    LinearLayout card = vertical(CARD);
    round(card, 18);
    stroke(card);
    pad(card, 16, 14, 16, 14);
    TextView a = text(16, INK);
    a.setText(title);
    TextView b = text(13, MUTED);
    b.setText(body);
    card.addView(a);
    card.addView(b);
    return card;
  }

  private TextView label(String value) {
    TextView view = text(12, MUTED);
    view.setText(value);
    pad(view, 2, 8, 2, 2);
    return view;
  }

  private EditText field(String value, String hint) {
    EditText edit = new EditText(this);
    edit.setText(value);
    edit.setHint(hint);
    edit.setTextColor(INK);
    edit.setHintTextColor(MUTED);
    edit.setSingleLine(true);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(6);
    edit.setLayoutParams(lp);
    return edit;
  }

  private Button lightButton(String label) {
    Button button = new Button(this);
    button.setText(label);
    button.setAllCaps(false);
    button.setTextSize(14);
    button.setMinHeight(0);
    button.setMinimumHeight(dp(40));
    if (glass()) {
      button.setBackground(glassPlate(16, 0xFFFFFFFF));
    } else {
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(dp(14));
      bg.setColor(0xFFFFFFFF);
      button.setBackground(bg);
    }
    button.setBackgroundTintList(null);
    button.setStateListAnimator(null);
    button.setTextColor(PINE);
    button.setPadding(dp(8), dp(8), dp(8), dp(8));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(14);
    button.setLayoutParams(lp);
    return button;
  }

  private Button button(String label, boolean filled) {
    Button button = new Button(this);
    button.setText(label);
    button.setAllCaps(false);
    button.setTextSize(14);
    styleButton(button, filled);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(8);
    button.setLayoutParams(lp);
    return button;
  }

  private void styleButton(Button button, boolean filled) {
    if (glass() && !filled) {
      button.setBackground(glassPlate(16, 0xFFFFFFFF));
    } else {
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(dp(12));
      bg.setColor(filled ? PINE : 0x00000000);
      if (!filled) bg.setStroke(dp(1), LINE);
      button.setBackground(bg);
    }
    button.setTextColor(filled ? CREAM : INK);
    button.setPadding(dp(8), dp(6), dp(8), dp(6));
    button.setMinHeight(0);
    button.setMinimumHeight(dp(40));
  }

  private TextView text(int sp, int color) {
    TextView view = new TextView(this);
    view.setTextSize(sp);
    view.setTextColor(color);
    return view;
  }

  private LinearLayout vertical(int color) {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    if (color != 0) layout.setBackgroundColor(color);
    return layout;
  }

  private LinearLayout horizontal() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.HORIZONTAL);
    return layout;
  }

  private void round(View view, int radius) {
    int color = CARD;
    if (view.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
      color = ((android.graphics.drawable.ColorDrawable) view.getBackground()).getColor();
    }
    if (glass()) {
      view.setBackground(glassPlate(radius, color));
      return;
    }
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(radius));
    bg.setColor(color);
    view.setBackground(bg);
  }

  private void stroke(View view) {
    if (view.getBackground() instanceof GradientDrawable) {
      ((GradientDrawable) view.getBackground()).setStroke(Math.max(1, dp(1)), glass() ? 0xCCFFFFFF : LINE);
    }
  }

  private void pad(View view, int l, int t, int r, int b) {
    view.setPadding(dp(l), dp(t), dp(r), dp(b));
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private int parseInt(String raw, int fallback) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception e) {
      return fallback;
    }
  }

  private int floorMod(int value, int mod) {
    int r = value % mod;
    return r < 0 ? r + mod : r;
  }

  private void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  private final class ZoomPane extends FrameLayout {
    private float scale = 1f;
    private float tx;
    private float ty;
    private float lastX;
    private float lastY;
    private boolean dragged;
    private boolean pinched;
    private final ScaleGestureDetector scaler;

    ZoomPane() {
      super(MainActivity.this);
      setClipChildren(true);
      scaler = new ScaleGestureDetector(MainActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        public boolean onScale(ScaleGestureDetector detector) {
          pinched = true;
          float next = Math.max(0.6f, Math.min(2.4f, scale * detector.getScaleFactor()));
          if (next == scale) return true;
          float focusX = detector.getFocusX();
          float focusY = detector.getFocusY();
          tx = focusX - (focusX - tx) * (next / scale);
          ty = focusY - (focusY - ty) * (next / scale);
          scale = next;
          clamp();
          apply();
          return true;
        }
      });
    }

    void show(View board, int focusY) {
      removeAllViews();
      FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) board.getLayoutParams();
      addView(board, lp);
      final int contentW = lp.width;
      final int contentH = lp.height;
      post(new Runnable() {
        public void run() {
          float five = dp(42) + dp(96) * 5f;
          scale = getWidth() / five;
          if (scale < 0.6f) scale = 0.6f;
          if (scale > 1.4f) scale = 1.4f;
          tx = 0f;
          ty = getHeight() * 0.28f - (dp(36) + focusY) * scale;
          clamp();
          apply();
        }
      });
    }

    private void apply() {
      View child = getChildCount() == 0 ? null : getChildAt(0);
      if (child == null) return;
      child.setPivotX(0f);
      child.setPivotY(0f);
      child.setScaleX(scale);
      child.setScaleY(scale);
      child.setTranslationX(tx);
      child.setTranslationY(ty);
    }

    private void clamp() {
      View child = getChildCount() == 0 ? null : getChildAt(0);
      if (child == null || !(child.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
      FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
      float cw = lp.width * scale;
      float ch = lp.height * scale;
      if (cw <= getWidth()) tx = (getWidth() - cw) / 2f;
      else tx = Math.min(0f, Math.max(getWidth() - cw, tx));
      if (ch <= getHeight()) ty = 0f;
      else ty = Math.min(0f, Math.max(getHeight() - ch, ty));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
      return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      scaler.onTouchEvent(event);
      if (event.getPointerCount() > 1) return true;
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          lastX = event.getX();
          lastY = event.getY();
          dragged = false;
          pinched = false;
          return true;
        case MotionEvent.ACTION_MOVE:
          if (scaler.isInProgress()) return true;
          float dx = event.getX() - lastX;
          float dy = event.getY() - lastY;
          if (Math.abs(dx) + Math.abs(dy) > dp(3)) dragged = true;
          tx += dx;
          ty += dy;
          lastX = event.getX();
          lastY = event.getY();
          clamp();
          apply();
          return true;
        case MotionEvent.ACTION_UP:
          if (!dragged && !pinched) tap(event.getX(), event.getY());
          return true;
        default:
          return true;
      }
    }

    private void tap(float x, float y) {
      View child = getChildCount() == 0 ? null : getChildAt(0);
      if (child == null) return;
      float localX = (x - tx) / scale;
      float localY = (y - ty) / scale;
      long time = eventTime();
      MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, localX, localY, 0);
      MotionEvent up = MotionEvent.obtain(time, time, MotionEvent.ACTION_UP, localX, localY, 0);
      child.dispatchTouchEvent(down);
      child.dispatchTouchEvent(up);
      down.recycle();
      up.recycle();
    }

    private long eventTime() {
      return android.os.SystemClock.uptimeMillis();
    }
  }
}
