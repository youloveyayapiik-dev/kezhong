package app.kezhong;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Parser {
  private static final Pattern TIME =
      Pattern.compile("(\\d{1,2})[:：.](\\d{2})\\s*[-~到至—–]\\s*(\\d{1,2})[:：.](\\d{2})");
  private static final Pattern SECTION = Pattern.compile("第?\\s*(\\d+)\\s*[-~到至—–]\\s*(\\d+)\\s*节?");
  private static final Pattern ONE_SECTION = Pattern.compile("第\\s*(\\d+)\\s*节");

  static List<Course> parse(String text, int totalWeeks) {
    ArrayList<Course> courses = new ArrayList<>();
    if (text == null) return courses;
    String[] lines = text.replace("\r", "").split("\n");
    for (String raw : lines) {
      String line = raw.trim();
      if (line.isEmpty()) continue;
      if (line.startsWith("课名") || line.startsWith("课程") || line.toLowerCase(Locale.ROOT).startsWith("name")) continue;
      Course course = line.contains(",") ? fromCsv(line, totalWeeks) : fromLine(line, totalWeeks);
      if (course == null && line.contains(",")) course = fromLine(line.replace(",", " "), totalWeeks);
      if (course != null) courses.add(course);
    }
    return courses;
  }

  private static Course fromCsv(String line, int totalWeeks) {
    String[] cells = splitCsv(line);
    if (cells.length < 5) return null;
    String name = cells[0].trim();
    if (name.isEmpty()) return null;
    Integer day = dayOf(cells.length > 3 ? cells[3] : "");
    if (day == null) day = dayOf(line);
    if (day == null) return null;
    String start = norm(cells.length > 4 ? cells[4] : "");
    String end = norm(cells.length > 5 ? cells[5] : "");
    if (start.isEmpty() || end.isEmpty()) {
      String[] slot = timesOf(line, totalWeeks);
      if (slot == null) return null;
      start = slot[0];
      end = slot[1];
    }
    Course course = Course.allWeeks(name, day, start, end, totalWeeks);
    course.teacher = cells.length > 1 ? cells[1].trim() : "";
    course.location = cells.length > 2 ? cells[2].trim() : "";
    if (cells.length > 6 && !cells[6].trim().isEmpty()) applyWeeks(course, cells[6], totalWeeks);
    if (cells.length > 7) course.section = cells[7].trim();
    return course;
  }

  private static Course fromLine(String line, int totalWeeks) {
    Integer day = dayOf(line);
    if (day == null) return null;
    String[] slot = timesOf(line, totalWeeks);
    if (slot == null) return null;
    Course course = Course.allWeeks("", day, slot[0], slot[1], totalWeeks);
    course.section = slot[2];
    applyWeeks(course, line, totalWeeks);
    String rest = line;
    rest = rest.replaceAll("星期[一二三四五六日天]|周[一二三四五六日天]|礼拜[一二三四五六日天]", " ");
    rest = TIME.matcher(rest).replaceAll(" ");
    rest = SECTION.matcher(rest).replaceAll(" ");
    rest = ONE_SECTION.matcher(rest).replaceAll(" ");
    rest = rest.replaceAll("(单周|双周)[^\\s,，]{0,16}", " ");
    rest = rest.replaceAll("\\d+\\s*[-~到至—–]\\s*\\d+\\s*周", " ");
    rest = rest.replaceAll("\\d+(?:\\s*[,、，]\\s*\\d+)+\\s*周?", " ");
    Matcher teacher = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z]{1,8}老师)").matcher(rest);
    if (teacher.find()) {
      course.teacher = teacher.group(1);
      rest = rest.replace(teacher.group(1), " ");
    }
    Matcher place = Pattern.compile("([A-Za-z0-9\\u4e00-\\u9fa5\\-]*?(?:楼|室|馆|场|厅)[A-Za-z0-9\\-]*)").matcher(rest);
    if (place.find() && place.group(1).length() <= 24) {
      course.location = place.group(1);
      rest = rest.replace(place.group(1), " ");
    }
    course.name = rest.replaceAll("[()（）\\[\\]【】,，]", " ").replaceAll("\\s+", " ").trim();
    if (course.name.isEmpty() || course.name.length() > 40) return null;
    return course;
  }

  private static String[] timesOf(String line, int totalWeeks) {
    Matcher time = TIME.matcher(line);
    if (time.find()) {
      return new String[] {norm(time.group(1) + ":" + time.group(2)), norm(time.group(3) + ":" + time.group(4)), ""};
    }
    Matcher section = SECTION.matcher(line);
    if (section.find()) {
      String packed = Schedule.sectionTimes(Integer.parseInt(section.group(1)), Integer.parseInt(section.group(2)));
      if (packed == null) return null;
      return packed.split("\\|");
    }
    Matcher one = ONE_SECTION.matcher(line);
    if (one.find()) {
      String packed = Schedule.sectionTimes(Integer.parseInt(one.group(1)), Integer.parseInt(one.group(1)));
      if (packed == null) return null;
      String[] parts = packed.split("\\|");
      parts[2] = one.group(1);
      return parts;
    }
    return null;
  }

  static void applyWeeks(Course course, String source, int totalWeeks) {
    String text = TIME.matcher(source).replaceAll(" ");
    text = SECTION.matcher(text).replaceAll(" ");
    text = ONE_SECTION.matcher(text).replaceAll(" ");
    boolean odd = text.contains("单周") || text.contains("奇数");
    boolean even = !odd && (text.contains("双周") || text.contains("偶数"));
    Matcher range = Pattern.compile("(\\d+)\\s*[-~到至—–]\\s*(\\d+)").matcher(text);
    if (odd || even) {
      int from = 1;
      int to = totalWeeks;
      if (range.find()) {
        from = Integer.parseInt(range.group(1));
        to = Integer.parseInt(range.group(2));
      }
      course.weekKind = odd ? "odd" : "even";
      course.from = Math.min(from, to);
      course.to = Math.max(from, to);
      course.weeks = new int[0];
      return;
    }
    ArrayList<Integer> list = new ArrayList<>();
    Matcher lists = Pattern.compile("(\\d+)\\s*[,、，]\\s*(\\d+(?:\\s*[,、，]\\s*\\d+)*)").matcher(text);
    if (lists.find()) {
      addNum(list, lists.group(1));
      for (String part : lists.group(2).split("[,、，]")) addNum(list, part.trim());
    }
    if (!list.isEmpty()) {
      course.weekKind = "list";
      course.weeks = Course.copyWeeks(list);
      course.from = course.weeks[0];
      course.to = course.weeks[course.weeks.length - 1];
      return;
    }
    if (text.contains("周") && range.find()) {
      course.weekKind = "all";
      course.from = Math.min(Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)));
      course.to = Math.max(Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)));
      course.weeks = new int[0];
    }
  }

  private static void addNum(ArrayList<Integer> list, String raw) {
    try {
      int n = Integer.parseInt(raw);
      if (n >= 1 && n <= 40) list.add(n);
    } catch (NumberFormatException ignored) {
    }
  }

  static Integer dayOf(String text) {
    Matcher matcher = Pattern.compile("星期([一二三四五六日天])|周([一二三四五六日天])|礼拜([一二三四五六日天])").matcher(text);
    if (!matcher.find()) return null;
    String ch = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
    if ("天".equals(ch) || "日".equals(ch)) return 7;
    int index = "一二三四五六".indexOf(ch);
    return index < 0 ? null : index + 1;
  }

  static String norm(String raw) {
    Matcher matcher = Pattern.compile("(\\d{1,2})[:：.](\\d{2})").matcher(raw.trim());
    if (!matcher.find()) return "";
    int hour = Math.min(23, Integer.parseInt(matcher.group(1)));
    int minute = Math.min(59, Integer.parseInt(matcher.group(2)));
    return String.format("%02d:%02d", hour, minute);
  }

  private static String[] splitCsv(String line) {
    ArrayList<String> cells = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        quoted = !quoted;
      } else if (ch == ',' && !quoted) {
        cells.add(current.toString());
        current.setLength(0);
      } else {
        current.append(ch);
      }
    }
    cells.add(current.toString());
    return cells.toArray(new String[0]);
  }
}
