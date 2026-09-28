package app.kezhong;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Store {
  private final SharedPreferences prefs;
  public final List<Model.Course> courses = new ArrayList<Model.Course>();
  public Model.Settings settings = new Model.Settings();
  public boolean sample;

  public Store(Context context) {
    prefs = context.getSharedPreferences("kezhong", Context.MODE_PRIVATE);
    load();
  }

  public void load() {
    courses.clear();
    String raw = prefs.getString("state", "");
    if (raw == null || raw.length() == 0) {
      fillSample();
      return;
    }
    try {
      JSONObject root = new JSONObject(raw);
      sample = root.optBoolean("sample", false);
      JSONObject s = root.optJSONObject("settings");
      settings = readSettings(s);
      JSONArray arr = root.optJSONArray("courses");
      if (arr != null) {
        for (int i = 0; i < arr.length(); i++) {
          Model.Course course = Model.Course.fromJson(arr.getJSONObject(i));
          course.id = arr.getJSONObject(i).optString("id", newId());
          if (course.name.length() > 0) courses.add(course);
        }
      }
      if (courses.isEmpty()) fillSample();
    } catch (Exception e) {
      fillSample();
    }
  }

  private Model.Settings readSettings(JSONObject s) {
    Model.Settings settings = new Model.Settings();
    if (s == null) return settings;
    settings.termStart = s.optString("termStart", settings.termStart);
    settings.totalWeeks = Math.max(1, Math.min(40, s.optInt("totalWeeks", 16)));
    JSONObject wake = s.optJSONObject("wake");
    if (wake != null) {
      settings.wake.wash = wake.optInt("washMin", 30);
      settings.wake.commute = wake.optInt("commuteMin", 45);
      settings.wake.buffer = wake.optInt("bufferMin", 15);
      settings.wake.remind = wake.optInt("classRemindMin", 20);
    }
    JSONArray periods = s.optJSONArray("periods");
    if (periods != null && periods.length() > 0) {
      settings.periods = new ArrayList<Model.Period>();
      for (int i = 0; i < periods.length(); i++) {
        JSONObject item = periods.optJSONObject(i);
        if (item == null) continue;
        String start = item.optString("start", "");
        String end = item.optString("end", "");
        int index = item.optInt("index", i + 1);
        if (start.length() == 0 || end.length() == 0) continue;
        settings.periods.add(new Model.Period(index, start, end));
      }
      if (settings.periods.isEmpty()) settings.periods = Model.defaultPeriods();
    }
    return settings;
  }

  public void fillSample() {
    courses.clear();
    sample = true;
    settings = new Model.Settings();
    add("高等数学", "陈予安", "理科楼 A201", 1, "08:00", "09:40", "1-2", "all", 1, 16);
    add("大学英语", "林夏", "文科楼 B105", 1, "10:10", "11:50", "3-4", "all", 1, 16);
    add("程序设计", "周衡", "信息楼 C302", 2, "08:00", "09:40", "1-2", "all", 1, 16);
    add("程序设计实验", "周衡", "机房 3", 2, "14:00", "16:30", "5-7", "even", 1, 16);
    add("线性代数", "陈予安", "理科楼 A201", 3, "08:00", "09:40", "1-2", "all", 1, 16);
    add("大学物理", "赵启明", "理科楼 A105", 3, "10:10", "11:50", "3-4", "all", 1, 16);
    add("数据结构", "周衡", "信息楼 C210", 4, "08:00", "09:40", "1-2", "all", 1, 16);
    add("体育", "马莉", "田径场", 4, "16:10", "17:50", "7-8", "all", 1, 16);
    add("思想道德", "吴静", "文科楼 B201", 5, "08:00", "09:40", "1-2", "all", 1, 16);
    add("专业导论", "吴静", "文科楼 B201", 5, "14:00", "15:40", "5-6", "all", 1, 8);
    save();
  }

  private void add(String name, String teacher, String location, int day, String start, String end, String section, String kind, int from, int to) {
    Model.Course c = new Model.Course();
    c.id = newId();
    c.name = name;
    c.teacher = teacher;
    c.location = location;
    c.day = day;
    c.start = start;
    c.end = end;
    c.section = section;
    c.weeks.kind = kind;
    c.weeks.from = from;
    c.weeks.to = to;
    courses.add(c);
  }

  public void save() {
    try {
      JSONObject root = new JSONObject();
      root.put("sample", sample);
      JSONObject s = new JSONObject();
      s.put("termStart", settings.termStart);
      s.put("totalWeeks", settings.totalWeeks);
      JSONObject wake = new JSONObject();
      wake.put("washMin", settings.wake.wash);
      wake.put("commuteMin", settings.wake.commute);
      wake.put("bufferMin", settings.wake.buffer);
      wake.put("classRemindMin", settings.wake.remind);
      s.put("wake", wake);
      JSONArray periods = new JSONArray();
      for (Model.Period period : settings.periods) {
        JSONObject item = new JSONObject();
        item.put("index", period.index);
        item.put("start", period.start);
        item.put("end", period.end);
        periods.put(item);
      }
      s.put("periods", periods);
      root.put("settings", s);
      JSONArray arr = new JSONArray();
      for (Model.Course course : courses) {
        JSONObject o = course.toJson();
        o.put("id", course.id);
        arr.put(o);
      }
      root.put("courses", arr);
      prefs.edit().putString("state", root.toString()).apply();
    } catch (Exception ignored) {
    }
  }

  public void replaceCourses(List<Model.Course> next) {
    courses.clear();
    courses.addAll(next);
    sample = false;
    save();
  }

  public void addCourses(List<Model.Course> next) {
    courses.addAll(next);
    sample = false;
    save();
  }

  public static String newId() {
    return "c" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(new Random().nextInt(46656), 36);
  }
}
