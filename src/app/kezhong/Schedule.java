package app.kezhong;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Settings {
  String termStart = "2026-09-07";
  int totalWeeks = 16;
  int wash = 30;
  int commute = 45;
  int buffer = 15;
  int remind = 20;

  int lead() {
    return Math.max(0, wash) + Math.max(0, commute) + Math.max(0, buffer);
  }
}

final class WakePlan {
  LocalDateTime wakeAt;
  LocalDate classDate;
  Course course;
  boolean passed;
}

final class Moment {
  LocalDate date;
  Course course;
  boolean live;
}

final class Schedule {
  static final String[] WEEKDAY = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
  static final int[][] PERIODS = {
    {8 * 60, 8 * 60 + 45},
    {8 * 60 + 55, 9 * 60 + 40},
    {10 * 60 + 10, 10 * 60 + 55},
    {11 * 60 + 5, 11 * 60 + 50},
    {14 * 60, 14 * 60 + 45},
    {14 * 60 + 55, 15 * 60 + 40},
    {16 * 60 + 10, 16 * 60 + 55},
    {17 * 60 + 5, 17 * 60 + 50},
    {19 * 60, 19 * 60 + 45},
    {19 * 60 + 55, 20 * 60 + 40},
    {20 * 60 + 50, 21 * 60 + 35},
    {21 * 60 + 45, 22 * 60 + 30},
  };

  static int parseMinutes(String hhmm) {
    if (hhmm == null) return 0;
    String[] parts = hhmm.trim().split(":");
    if (parts.length < 2) return 0;
    try {
      return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    } catch (NumberFormatException error) {
      return 0;
    }
  }

  static String formatMinutes(int mins) {
    int wrapped = Math.floorMod(mins, 1440);
    return String.format("%02d:%02d", wrapped / 60, wrapped % 60);
  }

  static LocalDate termStart(Settings settings) {
    try {
      return LocalDate.parse(settings.termStart);
    } catch (Exception error) {
      return LocalDate.of(2026, 9, 7);
    }
  }

  static Integer weekIndex(Settings settings, LocalDate date) {
    long diff = ChronoUnit.DAYS.between(termStart(settings), date);
    if (diff < 0) return null;
    return (int) (diff / 7) + 1;
  }

  static int clampWeek(Settings settings, LocalDate date) {
    Integer week = weekIndex(settings, date);
    if (week == null || week < 1) return 1;
    if (week > settings.totalWeeks) return settings.totalWeeks;
    return week;
  }

  static List<Course> onDate(List<Course> courses, Settings settings, LocalDate date) {
    Integer week = weekIndex(settings, date);
    ArrayList<Course> list = new ArrayList<>();
    if (week == null || week < 1 || week > settings.totalWeeks) return list;
    int day = date.getDayOfWeek().getValue();
    for (Course course : courses) {
      if (course.day == day && course.onWeek(week)) list.add(course);
    }
    list.sort(Comparator.comparingInt((Course course) -> parseMinutes(course.start)).thenComparing(course -> course.name));
    return list;
  }

  static WakePlan planWake(List<Course> courses, Settings settings, LocalDateTime now) {
    int lead = settings.lead();
    int horizon = settings.totalWeeks * 7 + 8;
    LocalDate today = now.toLocalDate();
    int nowMin = now.getHour() * 60 + now.getMinute();
    for (int i = 0; i < horizon; i++) {
      LocalDate date = today.plusDays(i);
      Integer week = weekIndex(settings, date);
      if (week == null) continue;
      if (week > settings.totalWeeks) break;
      List<Course> list = onDate(courses, settings, date);
      if (list.isEmpty()) continue;
      if (i == 0) {
        boolean still = false;
        for (Course course : list) {
          if (parseMinutes(course.end) > nowMin) still = true;
        }
        if (!still) continue;
      }
      Course first = list.get(0);
      LocalDateTime wakeAt = date.atStartOfDay().plusMinutes(parseMinutes(first.start) - lead);
      WakePlan plan = new WakePlan();
      plan.wakeAt = wakeAt;
      plan.classDate = date;
      plan.course = first;
      plan.passed = !wakeAt.isAfter(now);
      return plan;
    }
    return null;
  }

  static Moment nextMoment(List<Course> courses, Settings settings, LocalDateTime now) {
    int horizon = settings.totalWeeks * 7 + 8;
    LocalDate today = now.toLocalDate();
    int nowMin = now.getHour() * 60 + now.getMinute();
    for (int i = 0; i < horizon; i++) {
      LocalDate date = today.plusDays(i);
      Integer week = weekIndex(settings, date);
      if (week == null) continue;
      if (week > settings.totalWeeks) break;
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

  static String monthDay(LocalDate date) {
    return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
  }

  static String rangeLabel(Settings settings, int week) {
    LocalDate start = termStart(settings).plusDays((week - 1L) * 7);
    LocalDate end = start.plusDays(6);
    return monthDay(start) + " – " + monthDay(end);
  }

  static String clock(LocalDateTime time) {
    return time.format(DateTimeFormatter.ofPattern("HH:mm"));
  }

  static int[] clockParts(LocalDateTime time) {
    return new int[] {time.getHour(), time.getMinute()};
  }

  static String sectionTimes(int a, int b) {
    int lo = Math.min(a, b);
    int hi = Math.max(a, b);
    if (lo < 1 || hi > PERIODS.length) return null;
    return formatMinutes(PERIODS[lo - 1][0]) + "|" + formatMinutes(PERIODS[hi - 1][1]) + "|" + lo + "-" + hi;
  }

  static String ics(List<Course> courses, Settings settings) {
    StringBuilder out = new StringBuilder();
    out.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//kezhong//CN\r\nCALSCALE:GREGORIAN\r\n");
    LocalDate start = termStart(settings);
    for (int week = 1; week <= settings.totalWeeks; week++) {
      for (int day = 1; day <= 7; day++) {
        LocalDate date = start.plusDays((week - 1L) * 7 + (day - 1));
        List<Course> list = new ArrayList<>();
        for (Course course : courses) {
          if (course.day == day && course.onWeek(week)) list.add(course);
        }
        list.sort(Comparator.comparingInt(course -> parseMinutes(course.start)));
        if (list.isEmpty()) continue;
        Course first = list.get(0);
        LocalDateTime classStart = date.atTime(LocalTime.of(parseMinutes(first.start) / 60, parseMinutes(first.start) % 60));
        event(out, classStart.minusMinutes(settings.lead()), classStart.minusMinutes(settings.lead()).plusMinutes(15), "起床 · " + first.name);
        for (Course course : list) {
          int begin = parseMinutes(course.start);
          int end = parseMinutes(course.end);
          LocalDateTime at = date.atTime(begin / 60, begin % 60);
          LocalDateTime until = date.atTime(Math.min(23, end / 60), end % 60);
          event(out, at.minusMinutes(Math.max(0, settings.remind)), at, "上课 · " + course.name + (course.location.isEmpty() ? "" : " " + course.location));
          if (until.isBefore(at)) until = at.plusMinutes(45);
        }
      }
    }
    out.append("END:VCALENDAR\r\n");
    return out.toString();
  }

  private static void event(StringBuilder out, LocalDateTime start, LocalDateTime end, String title) {
    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
    out.append("BEGIN:VEVENT\r\n");
    out.append("DTSTAMP:").append(stamp).append("\r\n");
    out.append("DTSTART:").append(start.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))).append("\r\n");
    out.append("DTEND:").append(end.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))).append("\r\n");
    out.append("SUMMARY:").append(title.replace("\n", " ")).append("\r\n");
    out.append("END:VEVENT\r\n");
  }
}
