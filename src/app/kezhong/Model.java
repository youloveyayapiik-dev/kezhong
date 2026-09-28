package app.kezhong;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Model {
  public static final String[] WEEKDAY = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

  public static final class Period {
    public int index;
    public String start;
    public String end;

    public Period(int index, String start, String end) {
      this.index = index;
      this.start = start;
      this.end = end;
    }
  }

  public static final class WeekRule {
    public String kind = "all";
    public int from = 1;
    public int to = 16;
    public int[] list = new int[0];

    public boolean on(int week) {
      if ("list".equals(kind)) {
        for (int n : list) if (n == week) return true;
        return false;
      }
      if (week < from || week > to) return false;
      if ("odd".equals(kind)) return week % 2 == 1;
      if ("even".equals(kind)) return week % 2 == 0;
      return true;
    }

    public String label() {
      if ("list".equals(kind)) return compact(list) + "周";
      String range = from == to ? String.valueOf(from) : from + "-" + to;
      if ("odd".equals(kind)) return "单周 " + range;
      if ("even".equals(kind)) return "双周 " + range;
      return range + "周";
    }

    public static String compact(int[] list) {
      if (list == null || list.length == 0) return "";
      int[] xs = new int[list.length];
      int n = 0;
      for (int value : list) {
        if (value < 1 || value > 40) continue;
        boolean seen = false;
        for (int i = 0; i < n; i++) if (xs[i] == value) seen = true;
        if (!seen) xs[n++] = value;
      }
      for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
          if (xs[j] < xs[i]) {
            int t = xs[i];
            xs[i] = xs[j];
            xs[j] = t;
          }
        }
      }
      StringBuilder sb = new StringBuilder();
      int i = 0;
      while (i < n) {
        int start = xs[i];
        int end = start;
        while (i + 1 < n && xs[i + 1] == end + 1) {
          i++;
          end = xs[i];
        }
        if (sb.length() > 0) sb.append("、");
        sb.append(start == end ? String.valueOf(start) : start + "-" + end);
        i++;
      }
      return sb.toString();
    }

    public static WeekRule fromMask(boolean[] on) {
      WeekRule rule = new WeekRule();
      int count = 0;
      int from = 0;
      int to = 0;
      for (int week = 1; week < on.length; week++) {
        if (!on[week]) continue;
        if (from == 0) from = week;
        to = week;
        count++;
      }
      if (count == 0) return rule;
      if (count == to - from + 1) {
        rule.kind = "all";
        rule.from = from;
        rule.to = to;
        return rule;
      }
      rule.kind = "list";
      rule.from = from;
      rule.to = to;
      rule.list = new int[count];
      int i = 0;
      for (int week = 1; week < on.length; week++) if (on[week]) rule.list[i++] = week;
      return rule;
    }

    public JSONObject toJson() throws JSONException {
      JSONObject o = new JSONObject();
      o.put("kind", kind);
      if ("list".equals(kind)) {
        JSONArray arr = new JSONArray();
        for (int n : list) arr.put(n);
        o.put("weeks", arr);
      } else {
        o.put("from", from);
        o.put("to", to);
      }
      return o;
    }

    public static WeekRule fromJson(JSONObject o) {
      WeekRule rule = new WeekRule();
      rule.kind = o.optString("kind", "all");
      rule.from = o.optInt("from", 1);
      rule.to = o.optInt("to", 16);
      JSONArray arr = o.optJSONArray("weeks");
      if (arr != null) {
        rule.list = new int[arr.length()];
        for (int i = 0; i < arr.length(); i++) rule.list[i] = arr.optInt(i);
        if (!"odd".equals(rule.kind) && !"even".equals(rule.kind) && !"all".equals(rule.kind)) rule.kind = "list";
      }
      if (rule.from > rule.to) {
        int t = rule.from;
        rule.from = rule.to;
        rule.to = t;
      }
      return rule;
    }
  }

  public static final class Course {
    public String id = "";
    public String name = "";
    public String teacher = "";
    public String location = "";
    public int day = 1;
    public String start = "08:00";
    public String end = "09:40";
    public String section = "";
    public WeekRule weeks = new WeekRule();

    public JSONObject toJson() throws JSONException {
      JSONObject o = new JSONObject();
      o.put("name", name);
      o.put("teacher", teacher);
      o.put("location", location);
      o.put("day", day);
      o.put("start", start);
      o.put("end", end);
      o.put("section", section);
      o.put("weeks", weeks.toJson());
      return o;
    }

    public static Course fromJson(JSONObject o) {
      Course c = new Course();
      c.name = o.optString("name", "").trim();
      c.teacher = o.optString("teacher", "").trim();
      c.location = o.optString("location", "").trim();
      c.day = o.optInt("day", 1);
      c.start = o.optString("start", "08:00");
      c.end = o.optString("end", "09:40");
      c.section = o.optString("section", "");
      JSONObject weeks = o.optJSONObject("weeks");
      c.weeks = weeks == null ? new WeekRule() : WeekRule.fromJson(weeks);
      return c;
    }
  }

  public static final class Wake {
    public int wash = 30;
    public int commute = 45;
    public int buffer = 15;
    public int remind = 20;

    public int lead() {
      return Math.max(0, wash) + Math.max(0, commute) + Math.max(0, buffer);
    }
  }

  public static final class Settings {
    public String termStart = "2026-09-07";
    public int totalWeeks = 16;
    public Wake wake = new Wake();
    public List<Period> periods = defaultPeriods();
  }

  public static final class WakePlan {
    public Calendar wakeAt;
    public Calendar classDate;
    public Course course;
    public int lead;
    public boolean wakePassed;
  }

  public static final class Moment {
    public Calendar date;
    public Course course;
    public boolean live;
  }

  public static List<Period> defaultPeriods() {
    String[][] rows = {
      {"08:00", "08:45"}, {"08:55", "09:40"}, {"10:10", "10:55"}, {"11:05", "11:50"},
      {"14:00", "14:45"}, {"14:55", "15:40"}, {"16:10", "16:55"}, {"17:05", "17:50"},
      {"19:00", "19:45"}, {"19:55", "20:40"}, {"20:50", "21:35"}, {"21:45", "22:30"}
    };
    List<Period> list = new ArrayList<Period>();
    for (int i = 0; i < rows.length; i++) list.add(new Period(i + 1, rows[i][0], rows[i][1]));
    return list;
  }

  public static Period findPeriod(List<Period> periods, int index) {
    if (periods == null) return null;
    for (Period period : periods) if (period.index == index) return period;
    return null;
  }

  /** 返回 [上课, 下课, 节次]。节次写成 3 或 3-4。 */
  public static String[] sectionTimes(String spec, List<Period> periods) {
    if (spec == null || periods == null) return null;
    Matcher pair = Pattern.compile("(\\d+)\\s*[-~到至—–]\\s*(\\d+)").matcher(spec);
    if (pair.find()) {
      int lo = Math.min(Integer.parseInt(pair.group(1)), Integer.parseInt(pair.group(2)));
      int hi = Math.max(Integer.parseInt(pair.group(1)), Integer.parseInt(pair.group(2)));
      Period a = findPeriod(periods, lo);
      Period b = findPeriod(periods, hi);
      if (a != null && b != null && a.start.length() > 0 && b.end.length() > 0) {
        return new String[] {a.start, b.end, lo == hi ? String.valueOf(lo) : lo + "-" + hi};
      }
    }
    String one = spec.trim();
    if (one.matches("\\d+")) {
      Period period = findPeriod(periods, Integer.parseInt(one));
      if (period != null && period.start.length() > 0 && period.end.length() > 0) {
        return new String[] {period.start, period.end, String.valueOf(period.index)};
      }
    }
    return null;
  }

  public static int applySectionTimes(List<Course> courses, List<Period> periods) {
    int changed = 0;
    List<Period> defaults = defaultPeriods();
    for (Course course : courses) {
      String spec = course.section == null ? "" : course.section.trim();
      if (spec.length() == 0) spec = inferSection(course, defaults);
      String[] times = sectionTimes(spec, periods);
      if (times == null) continue;
      course.section = times[2];
      course.start = times[0];
      course.end = times[1];
      changed++;
    }
    return changed;
  }

  private static String inferSection(Course course, List<Period> defaults) {
    int start = parseMinutes(course.start);
    int end = parseMinutes(course.end);
    int lo = 0;
    int hi = 0;
    for (Period period : defaults) {
      if (parseMinutes(period.start) == start) lo = period.index;
      if (parseMinutes(period.end) == end) hi = period.index;
    }
    if (lo == 0 || hi == 0 || hi < lo) return "";
    return lo == hi ? String.valueOf(lo) : lo + "-" + hi;
  }

  public static List<Period> parsePeriodText(String text) {
    List<Period> list = new ArrayList<Period>();
    if (text == null) return list;
    int auto = 1;
    Matcher times = Pattern.compile("(\\d{1,2}[:：.]\\d{2})\\s*[-~到至—–,，\\s]+(\\d{1,2}[:：.]\\d{2})").matcher("");
    for (String raw : text.split("\\r?\\n")) {
      String line = raw.trim();
      if (line.length() == 0) continue;
      times.reset(line);
      if (!times.find()) continue;
      String start = normTime(times.group(1));
      String end = normTime(times.group(2));
      if (start.length() == 0 || end.length() == 0) continue;
      if (parseMinutes(start) >= parseMinutes(end)) continue;
      String head = line.substring(0, times.start());
      int lo = auto;
      int hi = auto;
      Matcher pair = Pattern.compile("(\\d+)\\s*[-~到至—–]\\s*(\\d+)").matcher(head);
      Matcher one = Pattern.compile("(\\d+)").matcher(head);
      if (pair.find()) {
        lo = Integer.parseInt(pair.group(1));
        hi = Integer.parseInt(pair.group(2));
        if (lo > hi) {
          int swap = lo;
          lo = hi;
          hi = swap;
        }
      } else if (one.find()) {
        lo = Integer.parseInt(one.group(1));
        hi = lo;
      }
      if (lo < 1 || hi > 20) continue;
      fillPeriods(list, lo, hi, start, end);
      auto = hi + 1;
    }
    Collections.sort(list, new Comparator<Period>() {
      public int compare(Period a, Period b) { return a.index - b.index; }
    });
    return list;
  }

  private static void fillPeriods(List<Period> list, int lo, int hi, String start, String end) {
    int from = parseMinutes(start);
    int to = parseMinutes(end);
    int count = hi - lo + 1;
    int gap = count == 1 ? 0 : 10;
    int each = count == 1 ? to - from : (to - from - gap * (count - 1)) / count;
    if (each < 20) {
      putPeriod(list, lo, start, end);
      if (hi != lo) putPeriod(list, hi, start, end);
      return;
    }
    int cursor = from;
    for (int index = lo; index <= hi; index++) {
      int stop = count == 1 ? to : cursor + each;
      putPeriod(list, index, formatMinutes(cursor), formatMinutes(stop));
      cursor = stop + gap;
    }
  }

  private static void putPeriod(List<Period> list, int index, String start, String end) {
    Period period = findPeriod(list, index);
    if (period == null) list.add(new Period(index, start, end));
    else {
      period.start = start;
      period.end = end;
    }
  }

  public static int parseMinutes(String hhmm) {
    if (hhmm == null) return 0;
    String[] p = hhmm.trim().split("[:：.]");
    if (p.length < 2) return 0;
    try {
      return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public static String formatMinutes(int mins) {
    int wrapped = ((mins % 1440) + 1440) % 1440;
    int h = wrapped / 60;
    int m = wrapped % 60;
    return pad(h) + ":" + pad(m);
  }

  public static String pad(int n) {
    return n < 10 ? "0" + n : String.valueOf(n);
  }

  public static String normTime(String raw) {
    if (raw == null) return "";
    String[] p = raw.trim().split("[:：.]");
    if (p.length < 2) return "";
    try {
      int h = Math.min(23, Integer.parseInt(p[0].replaceAll("\\D", "")));
      int m = Math.min(59, Integer.parseInt(p[1].replaceAll("\\D", "")));
      if (p[1].replaceAll("\\D", "").length() == 0) return "";
      return pad(h) + ":" + pad(m);
    } catch (NumberFormatException e) {
      return "";
    }
  }

  public static Calendar atNoon(Calendar src) {
    Calendar c = Calendar.getInstance();
    c.set(src.get(Calendar.YEAR), src.get(Calendar.MONTH), src.get(Calendar.DAY_OF_MONTH), 12, 0, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }

  public static Calendar parseDate(String iso) {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.MILLISECOND, 0);
    try {
      String[] p = iso.split("-");
      c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 12, 0, 0);
    } catch (Exception e) {
      c.set(2026, 8, 7, 12, 0, 0);
    }
    return c;
  }

  public static String formatDate(Calendar c) {
    return c.get(Calendar.YEAR) + "-" + pad(c.get(Calendar.MONTH) + 1) + "-" + pad(c.get(Calendar.DAY_OF_MONTH));
  }

  public static String monthDay(Calendar c) {
    return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
  }

  /** 0 早（12点前） 1 中（18点前） 2 晚 */
  public static int band(String start) {
    int mins = parseMinutes(start);
    if (mins < 12 * 60) return 0;
    if (mins < 18 * 60) return 1;
    return 2;
  }

  public static final String[] BAND = {"早", "中", "晚"};

  public static List<Calendar> classDates(Course course, Settings settings) {
    List<Calendar> dates = new ArrayList<Calendar>();
    Calendar start = parseDate(settings.termStart);
    int weeks = Math.max(1, settings.totalWeeks);
    for (int week = 1; week <= weeks; week++) {
      if (!course.weeks.on(week)) continue;
      dates.add(addDays(start, (week - 1) * 7 + Math.max(0, course.day - 1)));
    }
    return dates;
  }

  public static String formatDateList(List<Calendar> dates) {
    if (dates.isEmpty()) return "没有算出上课日期";
    StringBuilder sb = new StringBuilder();
    int limit = Math.min(10, dates.size());
    for (int i = 0; i < limit; i++) {
      if (i > 0) sb.append("、");
      sb.append(monthDay(dates.get(i)));
    }
    if (dates.size() > limit) sb.append(" 等");
    sb.append("  ·  共 ").append(dates.size()).append(" 次");
    return sb.toString();
  }

  public static int isoWeekday(Calendar c) {
    int day = c.get(Calendar.DAY_OF_WEEK);
    return day == Calendar.SUNDAY ? 7 : day - 1;
  }

  public static int daysBetween(Calendar a, Calendar b) {
    long diff = atNoon(b).getTimeInMillis() - atNoon(a).getTimeInMillis();
    return (int) Math.round(diff / 86400000.0);
  }

  public static Calendar addDays(Calendar date, int days) {
    Calendar c = atNoon(date);
    c.add(Calendar.DAY_OF_MONTH, days);
    return c;
  }

  public static Integer weekIndex(String termStart, Calendar date) {
    int diff = daysBetween(parseDate(termStart), date);
    if (diff < 0) return null;
    return Integer.valueOf(diff / 7 + 1);
  }

  public static int clampWeek(Settings settings, Calendar date) {
    Integer week = weekIndex(settings.termStart, date);
    if (week == null || week.intValue() < 1) return 1;
    if (week.intValue() > settings.totalWeeks) return settings.totalWeeks;
    return week.intValue();
  }

  public static List<Course> onDate(List<Course> courses, Settings settings, Calendar date) {
    Integer week = weekIndex(settings.termStart, date);
    List<Course> list = new ArrayList<Course>();
    if (week == null || week.intValue() < 1 || week.intValue() > settings.totalWeeks) return list;
    int day = isoWeekday(date);
    for (Course course : courses) {
      if (course.day == day && course.weeks.on(week.intValue())) list.add(course);
    }
    Collections.sort(list, new Comparator<Course>() {
      public int compare(Course a, Course b) {
        int d = parseMinutes(a.start) - parseMinutes(b.start);
        return d != 0 ? d : a.name.compareTo(b.name);
      }
    });
    return list;
  }

  public static WakePlan planWake(List<Course> courses, Settings settings, Calendar now) {
    int lead = settings.wake.lead();
    int horizon = settings.totalWeeks * 7 + 8;
    for (int i = 0; i < horizon; i++) {
      Calendar date = addDays(now, i);
      Integer week = weekIndex(settings.termStart, date);
      if (week == null) continue;
      if (week.intValue() > settings.totalWeeks) break;
      List<Course> list = onDate(courses, settings, date);
      if (list.isEmpty()) continue;
      if (i == 0) {
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        boolean still = false;
        for (Course course : list) if (parseMinutes(course.end) > nowMin) still = true;
        if (!still) continue;
      }
      Course first = list.get(0);
      Calendar wakeAt = atNoon(date);
      wakeAt.set(Calendar.HOUR_OF_DAY, 0);
      wakeAt.set(Calendar.MINUTE, 0);
      wakeAt.add(Calendar.MINUTE, parseMinutes(first.start) - lead);
      WakePlan plan = new WakePlan();
      plan.wakeAt = wakeAt;
      plan.classDate = date;
      plan.course = first;
      plan.lead = lead;
      plan.wakePassed = wakeAt.getTimeInMillis() <= now.getTimeInMillis();
      return plan;
    }
    return null;
  }

  public static Moment nextMoment(List<Course> courses, Settings settings, Calendar now) {
    int horizon = settings.totalWeeks * 7 + 8;
    for (int i = 0; i < horizon; i++) {
      Calendar date = addDays(now, i);
      Integer week = weekIndex(settings.termStart, date);
      if (week == null) continue;
      if (week.intValue() > settings.totalWeeks) break;
      int nowMin = i == 0 ? now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) : -1;
      for (Course course : onDate(courses, settings, date)) {
        int start = parseMinutes(course.start);
        int end = parseMinutes(course.end);
        if (i == 0 && nowMin >= start && nowMin < end) {
          Moment moment = new Moment();
          moment.date = date;
          moment.course = course;
          moment.live = true;
          return moment;
        }
        if (i > 0 || nowMin < start) {
          Moment moment = new Moment();
          moment.date = date;
          moment.course = course;
          moment.live = false;
          return moment;
        }
      }
    }
    return null;
  }

  public static int tone(String name) {
    int hash = 0;
    for (int i = 0; i < name.length(); i++) hash = (hash + name.charAt(i)) % 4;
    return hash;
  }

  public static String duration(int mins) {
    int n = Math.max(0, mins);
    if (n < 60) return n + " 分钟";
    int h = n / 60;
    int m = n % 60;
    return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分";
  }

  public static boolean sameDay(Calendar a, Calendar b) {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
        && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
  }
}
