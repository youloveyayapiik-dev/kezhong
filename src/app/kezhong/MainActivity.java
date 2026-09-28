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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
  private String pendingNote = "把表格粘进来，或从文件选择 xls、xlsx、csv。";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    store = new Store(this);
    LinearLayout root = vertical(PAPER);
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
    setContentView(root);
    show(0);
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
    String motion = store.settings.motion == null ? "slide" : store.settings.motion;
    final int w = Math.max(body.getWidth(), 1);
    final int h = Math.max(body.getHeight(), 1);
    float distance = 8000f * getResources().getDisplayMetrics().density;
    page.setAlpha(0f);
    if ("fade".equals(motion)) {
      page.animate().alpha(1f).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
      leaving.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
        public void run() { drop(leaving); }
      }).start();
      return;
    }
    if ("turn".equals(motion)) {
      page.setCameraDistance(distance);
      leaving.setCameraDistance(distance);
      page.setPivotX(w / 2f);
      page.setPivotY(h / 2f);
      leaving.setPivotX(w / 2f);
      leaving.setPivotY(h / 2f);
      page.setRotationY(direction * 75f);
      page.animate().rotationY(0f).alpha(1f).setDuration(360).setInterpolator(new DecelerateInterpolator()).start();
      leaving.animate().rotationY(-direction * 75f).alpha(0f).setDuration(320).withEndAction(new Runnable() {
        public void run() { drop(leaving); }
      }).start();
      return;
    }
    if ("flip".equals(motion)) {
      page.setCameraDistance(distance);
      leaving.setCameraDistance(distance);
      page.setPivotX(direction > 0 ? 0 : w);
      page.setPivotY(h / 2f);
      leaving.setPivotX(direction > 0 ? w : 0);
      leaving.setPivotY(h / 2f);
      page.setRotationY(direction > 0 ? 88f : -88f);
      page.animate().rotationY(0f).alpha(1f).setDuration(380).setInterpolator(new DecelerateInterpolator()).start();
      leaving.animate().rotationY(direction > 0 ? -88f : 88f).alpha(0f).setDuration(320).withEndAction(new Runnable() {
        public void run() { drop(leaving); }
      }).start();
      return;
    }
    final int dx = dp(36) * direction;
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
    Button motion = button("翻页", false);
    motion.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openMotion(); }
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
    if (skipped) {
      TextView note = text(13, 0xFFD5DDD6);
      note.setText(span(plan.course) + " 那节过了");
      card.addView(note);
    } else if (!plan.wakePassed && same) {
      TextView note = text(13, 0xFFD5DDD6);
      note.setText(Model.formatMinutes(plan.wakeAt.get(Calendar.HOUR_OF_DAY) * 60 + plan.wakeAt.get(Calendar.MINUTE)) + " 起");
      card.addView(note);
    }
    Button alarm = lightButton(plan.wakePassed ? "仍写入系统闹钟" : "写入系统闹钟");
    alarm.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        setAlarm(plan.wakeAt.get(Calendar.HOUR_OF_DAY), plan.wakeAt.get(Calendar.MINUTE), "起床 · " + plan.course.name);
      }
    });
    card.addView(alarm);
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
    title.setText("今天还剩");
    title.setTypeface(Typeface.DEFAULT_BOLD);
    card.addView(title);
    for (Model.Course course : rest) {
      TextView line = text(15, INK);
      String place = course.location.length() == 0 ? "" : "   " + course.location;
      line.setText(span(course) + "   " + course.name + place);
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
    label.setText("第 " + week + " 周  ·  点周次直接跳");
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
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    LinearLayout days = horizontal();
    pad(days, 12, 4, 12, 20);
    Calendar monday = Model.addDays(Model.parseDate(store.settings.termStart), (week - 1) * 7);
    for (int d = 1; d <= 7; d++) {
      Calendar date = Model.addDays(monday, d - 1);
      boolean isToday = Model.sameDay(date, now);
      String off = Holidays.off(date);
      int columnColor = isToday ? 0xFFE7EFEA : (off != null ? 0xFFF8EBE3 : CARD);
      LinearLayout column = vertical(columnColor);
      round(column, 16);
      stroke(column);
      pad(column, 8, 8, 8, 8);
      TextView head = text(13, isToday ? PINE : MUTED);
      head.setText(Model.WEEKDAY[d] + "  " + Model.monthDay(date));
      head.setTypeface(Typeface.DEFAULT_BOLD);
      column.addView(head);
      if (off != null) {
        TextView tag = text(11, 0xFF9A3412);
        tag.setText(off);
        column.addView(tag);
      } else if (Holidays.work(date) != null) {
        TextView tag = text(11, MUTED);
        tag.setText("调休上班");
        column.addView(tag);
      }
      List<Model.Course> list = new ArrayList<Model.Course>();
      for (Model.Course course : store.courses) {
        if (course.day == d && course.weeks.on(week)) list.add(course);
      }
      java.util.Collections.sort(list, new java.util.Comparator<Model.Course>() {
        public int compare(Model.Course a, Model.Course b) {
          return Model.parseMinutes(a.start) - Model.parseMinutes(b.start);
        }
      });
      if (list.isEmpty()) {
        TextView empty = text(12, MUTED);
        empty.setText("没课");
        column.addView(empty);
      }
      int lastBand = -1;
      for (final Model.Course course : list) {
        int band = Model.band(course.start);
        if (band != lastBand) {
          TextView mark = text(11, PINE);
          mark.setText(Model.BAND[band]);
          mark.setTypeface(Typeface.DEFAULT_BOLD);
          pad(mark, 2, 8, 2, 0);
          column.addView(mark);
          lastBand = band;
        }
        column.addView(miniCourse(course, date));
      }
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(156), ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.rightMargin = dp(8);
      days.addView(column, lp);
    }
    scroll.addView(days);
    col.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return col;
  }

  private View miniCourse(final Model.Course course, final Calendar date) {
    LinearLayout box = vertical(TONES[Model.tone(course.name)]);
    round(box, 12);
    pad(box, 8, 8, 8, 8);
    TextView time = text(11, MUTED);
    time.setText(span(course));
    TextView name = text(14, INK);
    name.setText(course.name);
    TextView place = text(11, MUTED);
    place.setText(course.location);
    box.addView(time);
    box.addView(name);
    if (course.location.length() > 0) box.addView(place);
    box.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { openEditor(course); }
    });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(8);
    box.setLayoutParams(lp);
    return box;
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

  private void openMotion() {
    final String[] keys = {"slide", "fade", "turn", "flip"};
    final String[] names = {"经典", "淡入淡出", "转盘", "翻页"};
    final int[] picked = new int[] {0};
    String current = store.settings.motion == null ? "slide" : store.settings.motion;
    for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) picked[0] = i;
    LinearLayout wrap = vertical(0);
    pad(wrap, 16, 8, 16, 4);
    TextView title = text(16, INK);
    title.setText("翻页效果");
    pad(title, 4, 0, 4, 12);
    wrap.addView(title);
    final LinearLayout row = horizontal();
    final LinearLayout[] boxes = new LinearLayout[4];
    for (int i = 0; i < 4; i++) {
      final int index = i;
      LinearLayout item = vertical(0);
      item.setGravity(Gravity.CENTER_HORIZONTAL);
      FrameLayout card = new FrameLayout(this);
      card.addView(motionPage(keys[i]));
      pad(card, 8, 10, 8, 10);
      item.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
      TextView label = text(14, MUTED);
      label.setText(names[i]);
      label.setGravity(Gravity.CENTER);
      pad(label, 0, 8, 0, 0);
      item.addView(label);
      item.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          picked[0] = index;
          for (int n = 0; n < boxes.length; n++) paintMotion(boxes[n], n == index);
        }
      });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      if (i > 0) lp.leftMargin = dp(8);
      boxes[i] = item;
      row.addView(item, lp);
    }
    for (int i = 0; i < boxes.length; i++) paintMotion(boxes[i], i == picked[0]);
    wrap.addView(row);
    new AlertDialog.Builder(this)
        .setView(wrap)
        .setPositiveButton("完成", new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface dialog, int which) {
            store.settings.motion = keys[picked[0]];
            store.save();
          }
        })
        .show();
  }

  private void paintMotion(LinearLayout item, boolean on) {
    FrameLayout card = (FrameLayout) item.getChildAt(0);
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(18));
    bg.setColor(0xFFF7F4EE);
    bg.setStroke(dp(on ? 3 : 1), on ? 0xFF2563EB : LINE);
    card.setBackground(bg);
    TextView label = (TextView) item.getChildAt(1);
    label.setTextColor(on ? 0xFF2563EB : MUTED);
    label.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
  }

  private View motionPage(String kind) {
    View page = new View(this);
    GradientDrawable paper = new GradientDrawable();
    paper.setCornerRadius(dp(8));
    paper.setColor(0xFFD9D3C7);
    page.setBackground(paper);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(36), dp(52), Gravity.CENTER);
    page.setLayoutParams(lp);
    if ("fade".equals(kind)) page.setAlpha(0.4f);
    if ("turn".equals(kind)) page.setRotation(16f);
    if ("flip".equals(kind)) {
      page.setPivotX(0f);
      page.setRotationY(55f);
      page.setCameraDistance(8000f * getResources().getDisplayMetrics().density);
    }
    return page;
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
    if (ok) finishCalendar();
    else toast("没有日历权限，写不进去");
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

  private void setAlarm(int hour, int minute, String message) {
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
    bar.setBackgroundColor(0xFFF7F4EE);
    round(bar, 28);
    stroke(bar);
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
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(14));
    bg.setColor(0xFFFFFFFF);
    button.setBackground(bg);
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
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(12));
    bg.setColor(filled ? PINE : 0x00000000);
    if (!filled) bg.setStroke(dp(1), LINE);
    button.setBackground(bg);
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
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(radius));
    if (view.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
      bg.setColor(((android.graphics.drawable.ColorDrawable) view.getBackground()).getColor());
    } else {
      bg.setColor(CARD);
    }
    view.setBackground(bg);
  }

  private void stroke(View view) {
    if (view.getBackground() instanceof GradientDrawable) {
      ((GradientDrawable) view.getBackground()).setStroke(dp(1), LINE);
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
}
