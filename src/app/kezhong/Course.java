package app.kezhong;

import java.util.ArrayList;
import java.util.Arrays;

final class Course {
  String id;
  String name;
  String teacher;
  String location;
  int day;
  String start;
  String end;
  String section;
  String weekKind;
  int from;
  int to;
  int[] weeks;

  boolean onWeek(int week) {
    if ("list".equals(weekKind)) {
      if (weeks == null) return false;
      for (int item : weeks) if (item == week) return true;
      return false;
    }
    int lo = Math.min(from, to);
    int hi = Math.max(from, to);
    if (week < lo || week > hi) return false;
    if ("odd".equals(weekKind)) return week % 2 == 1;
    if ("even".equals(weekKind)) return week % 2 == 0;
    return true;
  }

  String weeksLabel() {
    if ("list".equals(weekKind)) {
      if (weeks == null || weeks.length == 0) return "未设周次";
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < weeks.length; i++) {
        if (i > 0) text.append("、");
        text.append(weeks[i]);
      }
      return text.append("周").toString();
    }
    String range = from == to ? String.valueOf(from) : from + "-" + to;
    if ("odd".equals(weekKind)) return "单周 " + range;
    if ("even".equals(weekKind)) return "双周 " + range;
    return range + "周";
  }

  static Course allWeeks(String name, int day, String start, String end, int total) {
    Course course = new Course();
    course.id = "c" + System.nanoTime();
    course.name = name;
    course.teacher = "";
    course.location = "";
    course.day = day;
    course.start = start;
    course.end = end;
    course.section = "";
    course.weekKind = "all";
    course.from = 1;
    course.to = total;
    course.weeks = new int[0];
    return course;
  }

  static int[] copyWeeks(ArrayList<Integer> values) {
    int[] out = new int[values.size()];
    for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
    Arrays.sort(out);
    return out;
  }
}
