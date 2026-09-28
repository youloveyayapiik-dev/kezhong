package app.kezhong;

import android.util.Xml;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import org.xmlpull.v1.XmlPullParser;

public final class TableParser {
  public static String foundTermStart;
  public static int foundTotalWeeks;
  public static List<Model.Period> foundPeriods;
  public static List<Model.PeriodSet> foundPeriodSets;
  private static final Map<String, Integer> DAYS = new HashMap<String, Integer>();

  static {
    put("周一", 1); put("星期一", 1); put("礼拜一", 1); put("一", 1); put("mon", 1); put("monday", 1);
    put("周二", 2); put("星期二", 2); put("礼拜二", 2); put("二", 2); put("tue", 2); put("tuesday", 2);
    put("周三", 3); put("星期三", 3); put("礼拜三", 3); put("三", 3); put("wed", 3); put("wednesday", 3);
    put("周四", 4); put("星期四", 4); put("礼拜四", 4); put("四", 4); put("thu", 4); put("thursday", 4);
    put("周五", 5); put("星期五", 5); put("礼拜五", 5); put("五", 5); put("fri", 5); put("friday", 5);
    put("周六", 6); put("星期六", 6); put("礼拜六", 6); put("六", 6); put("sat", 6); put("saturday", 6);
    put("周日", 7); put("周天", 7); put("星期日", 7); put("星期天", 7); put("日", 7); put("天", 7); put("sun", 7); put("sunday", 7);
  }

  private static void put(String k, int v) { DAYS.put(k, Integer.valueOf(v)); }

  public static List<Model.Course> fromText(String text, Model.Settings settings) {
    foundTermStart = null;
    foundTotalWeeks = 0;
    String raw = text == null ? "" : text.replace("\uFEFF", "").trim();
    if (raw.startsWith("{") || raw.startsWith("[")) return new ArrayList<Model.Course>();
    List<String[]> rows = split(raw);
    List<Model.Course> courses = assign(parseMatrix(rows, settings), settings);
    sniffPeriods(rows, courses);
    return courses;
  }

  public static List<Model.Course> fromXlsx(File file, Model.Settings settings) throws Exception {
    foundTermStart = null;
    foundTotalWeeks = 0;
    ZipFile zip = new ZipFile(file);
    try {
      List<String> shared = sharedStrings(zip);
      ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
      if (sheet == null) {
        for (java.util.Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
          ZipEntry entry = e.nextElement();
          if (entry.getName().startsWith("xl/worksheets/sheet") && entry.getName().endsWith(".xml")) {
            sheet = entry;
            break;
          }
        }
      }
      if (sheet == null) return new ArrayList<Model.Course>();
      List<String[]> rows = sheetRows(zip, sheet, shared);
      List<Model.Course> courses = assign(parseMatrix(rows, settings), settings);
      sniffPeriods(rows, courses);
      return courses;
    } finally {
      zip.close();
    }
  }

  public static List<Model.Course> fromXls(File file, Model.Settings settings) throws Exception {
    foundTermStart = null;
    foundTotalWeeks = 0;
    List<Model.Course> first = readXls(file, settings, null);
    if (!first.isEmpty()) return first;
    if (foundPeriodSets != null && foundPeriodSets.size() > 1) return first;
    if (foundPeriods != null && foundPeriods.size() >= 3) return first;
    WorkbookSettings encoding = new WorkbookSettings();
    encoding.setEncoding("GBK");
    return readXls(file, settings, encoding);
  }

  private static List<Model.Course> readXls(File file, Model.Settings settings, WorkbookSettings encoding) throws Exception {
    Workbook workbook = encoding == null ? Workbook.getWorkbook(file) : Workbook.getWorkbook(file, encoding);
    try {
      List<Model.Course> best = new ArrayList<Model.Course>();
      List<Model.PeriodSet> bestSets = null;
      for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
        Sheet sheet = workbook.getSheet(s);
        int cols = sheet.getColumns();
        List<String[]> rows = new ArrayList<String[]>();
        for (int r = 0; r < sheet.getRows(); r++) {
          String[] cells = new String[Math.max(cols, 1)];
          boolean any = false;
          for (int c = 0; c < cols; c++) {
            String value = "";
            try {
              value = sheet.getCell(c, r).getContents();
            } catch (Exception ignored) {
            }
            if (value == null) value = "";
            cells[c] = value.replace('\u00a0', ' ').trim();
            if (cells[c].length() > 0) any = true;
          }
          if (any) rows.add(cells);
        }
        List<Model.Course> parsed = parseMatrix(rows, settings);
        if (parsed.size() > best.size()) best = parsed;
        if (parsed.isEmpty()) {
          List<Model.PeriodSet> sets = Model.parsePeriodSets(joinCells(rows));
          int count = 0;
          for (Model.PeriodSet set : sets) count += set.periods.size();
          int bestCount = 0;
          if (bestSets != null) for (Model.PeriodSet set : bestSets) bestCount += set.periods.size();
          if (count > bestCount) bestSets = sets;
        }
      }
      foundPeriodSets = best.isEmpty() ? bestSets : null;
      if (foundPeriodSets != null && foundPeriodSets.size() == 1 && foundPeriodSets.get(0).periods.size() >= 3) {
        foundPeriods = foundPeriodSets.get(0).periods;
      } else {
        foundPeriods = null;
      }
      return assign(best, settings);
    } finally {
      workbook.close();
    }
  }

  public static List<Model.Course> fromMarkup(String text, Model.Settings settings) {
    foundTermStart = null;
    foundTotalWeeks = 0;
    boolean xmlRow = text.toLowerCase().contains("<row");
    String rowTag = xmlRow ? "row" : "tr";
    String cellPattern = xmlRow ? "cell" : "t[dh]";
    List<String[]> rows = new ArrayList<String[]>();
    Matcher rowMatcher = Pattern.compile("<" + rowTag + "\\b[^>]*>(.*?)</" + rowTag + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
    while (rowMatcher.find()) {
      List<String> cells = new ArrayList<String>();
      Matcher cellMatcher = Pattern.compile("<" + cellPattern + "\\b([^>]*)>(.*?)</" + cellPattern + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(rowMatcher.group(1));
      while (cellMatcher.find()) {
        int index = attrInt(cellMatcher.group(1), "Index");
        if (index > cells.size() + 1) {
          while (cells.size() < index - 1) cells.add("");
        }
        int span = attrInt(cellMatcher.group(1), "colspan");
        if (span < 1) span = 1;
        if (span > 12) span = 12;
        cells.add(cellText(cellMatcher.group(2)));
        for (int i = 1; i < span; i++) cells.add("");
      }
      if (!cells.isEmpty()) rows.add(cells.toArray(new String[cells.size()]));
    }
    return assign(parseMatrix(rows, settings), settings);
  }

  private static int attrInt(String attrs, String name) {
    if (attrs == null) return 0;
    Matcher matcher = Pattern.compile("(?i)(?:ss:)?" + name + "\\s*=\\s*['\"]?(\\d+)").matcher(attrs);
    if (!matcher.find()) return 0;
    try {
      return Integer.parseInt(matcher.group(1));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String cellText(String html) {
    String text = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p>", "\n");
    text = text.replaceAll("<[^>]+>", " ");
    text = text.replace("\u0026nbsp;", " ").replace("\u0026#160;", " ").replace("\u0026amp;", "\u0026").replace("\u0026lt;", "<").replace("\u0026gt;", ">").replace("\u0026quot;", "\"");
    return text.replace('\u00a0', ' ').replaceAll("[ \\t]+", " ").trim();
  }

  private static List<Model.Course> assign(List<Model.Course> list, Model.Settings settings) {
    for (Model.Course course : list) course.id = Store.newId();
    return list;
  }

  private static List<String> sharedStrings(ZipFile zip) throws Exception {
    List<String> list = new ArrayList<String>();
    ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
    if (entry == null) return list;
    XmlPullParser p = Xml.newPullParser();
    p.setInput(zip.getInputStream(entry), "UTF-8");
    StringBuilder current = null;
    int type;
    while ((type = p.next()) != XmlPullParser.END_DOCUMENT) {
      if (type == XmlPullParser.START_TAG && "si".equals(p.getName())) current = new StringBuilder();
      else if (type == XmlPullParser.TEXT && current != null) current.append(p.getText());
      else if (type == XmlPullParser.END_TAG && "si".equals(p.getName())) {
        list.add(current == null ? "" : current.toString());
        current = null;
      }
    }
    return list;
  }

  private static List<String[]> sheetRows(ZipFile zip, ZipEntry sheet, List<String> shared) throws Exception {
    Map<Integer, Map<Integer, String>> grid = new HashMap<Integer, Map<Integer, String>>();
    int maxRow = 0;
    int maxCol = 0;
    XmlPullParser p = Xml.newPullParser();
    p.setInput(zip.getInputStream(sheet), "UTF-8");
    String ref = "";
    String kind = "";
    StringBuilder value = null;
    boolean inV = false;
    boolean inT = false;
    int type;
    while ((type = p.next()) != XmlPullParser.END_DOCUMENT) {
      if (type == XmlPullParser.START_TAG && "c".equals(p.getName())) {
        ref = p.getAttributeValue(null, "r");
        kind = p.getAttributeValue(null, "t");
        value = new StringBuilder();
      } else if (type == XmlPullParser.START_TAG && "v".equals(p.getName())) {
        inV = true;
      } else if (type == XmlPullParser.START_TAG && "t".equals(p.getName())) {
        inT = true;
      } else if (type == XmlPullParser.TEXT && value != null && (inV || inT)) {
        value.append(p.getText());
      } else if (type == XmlPullParser.END_TAG && ("v".equals(p.getName()) || "t".equals(p.getName()))) {
        inV = false;
        inT = false;
      } else if (type == XmlPullParser.END_TAG && "c".equals(p.getName()) && ref != null && value != null) {
        String text = value.toString();
        if ("s".equals(kind)) {
          try {
            int idx = Integer.parseInt(text.trim());
            text = idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
          } catch (NumberFormatException ignored) {
            text = "";
          }
        }
        int row = rowIndex(ref);
        int col = colIndex(ref);
        if (row >= 0 && col >= 0 && row < 80 && col < 20) {
          Map<Integer, String> line = grid.get(Integer.valueOf(row));
          if (line == null) {
            line = new HashMap<Integer, String>();
            grid.put(Integer.valueOf(row), line);
          }
          line.put(Integer.valueOf(col), text == null ? "" : text.trim());
          if (row > maxRow) maxRow = row;
          if (col > maxCol) maxCol = col;
        }
        value = null;
      }
    }
    List<String[]> rows = new ArrayList<String[]>();
    for (int r = 0; r <= maxRow; r++) {
      Map<Integer, String> line = grid.get(Integer.valueOf(r));
      if (line == null) continue;
      String[] cells = new String[maxCol + 1];
      boolean any = false;
      for (int c = 0; c <= maxCol; c++) {
        String cell = line.get(Integer.valueOf(c));
        cells[c] = cell == null ? "" : cell;
        if (cells[c].length() > 0) any = true;
      }
      if (any) rows.add(cells);
    }
    return rows;
  }

  private static int colIndex(String ref) {
    int col = 0;
    for (int i = 0; i < ref.length(); i++) {
      char c = ref.charAt(i);
      if (c >= 'A' && c <= 'Z') col = col * 26 + (c - 'A' + 1);
      else if (c >= 'a' && c <= 'z') col = col * 26 + (c - 'a' + 1);
      else break;
    }
    return col - 1;
  }

  private static int rowIndex(String ref) {
    int i = 0;
    while (i < ref.length() && !Character.isDigit(ref.charAt(i))) i++;
    try {
      return Integer.parseInt(ref.substring(i)) - 1;
    } catch (Exception e) {
      return -1;
    }
  }

  static List<Model.Course> parseMatrix(List<String[]> rows, Model.Settings settings) {
    List<String[]> cleaned = new ArrayList<String[]>();
    for (String[] row : rows) {
      boolean any = false;
      String[] next = new String[row.length];
      for (int i = 0; i < row.length; i++) {
        next[i] = row[i] == null ? "" : row[i].replace('\u00a0', ' ').trim();
        if (next[i].length() > 0) any = true;
      }
      if (any) cleaned.add(next);
    }
    if (cleaned.isEmpty()) return new ArrayList<Model.Course>();
    StringBuilder blob = new StringBuilder();
    for (String[] row : cleaned) {
      for (String cell : row) blob.append(cell).append('\n');
    }
    noteTerm(blob.toString());
    boolean grid = false;
    int limit = Math.min(6, cleaned.size());
    for (int i = 0; i < limit; i++) if (isDayHeader(cleaned.get(i))) grid = true;
    List<Model.Course> courses = grid ? parseGrid(cleaned, settings) : parseRecords(cleaned, settings);
    return dedupe(courses);
  }

  private static boolean isDayHeader(String[] row) {
    int count = 0;
    for (String cell : row) if (dayFromHeader(cell) != null) count++;
    return count >= 3;
  }

  private static void noteTerm(String text) {
    Matcher matcher = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})\\s*正式上课").matcher(text);
    if (matcher.find()) foundTermStart = matcher.group(1);
    Matcher weeks = Pattern.compile("共\\s*(\\d+)\\s*周").matcher(text);
    int best = 0;
    while (weeks.find()) {
      int n = Integer.parseInt(weeks.group(1));
      if (n > best && n <= 40) best = n;
    }
    if (best > 0) foundTotalWeeks = best;
  }

  private static Integer dayFromHeader(String cell) {
    if (cell == null) return null;
    String key = cell.replace(" ", "").replace("\u3000", "").replace("\n", "").replace("\r", "").trim().toLowerCase();
    return DAYS.get(key);
  }

  private static Integer dayFromText(String text) {
    String[][] rules = {
      {"星期[一1]|周[一1]|礼拜一", "1"},
      {"星期[二2]|周[二2]|礼拜二", "2"},
      {"星期[三3]|周[三3]|礼拜三", "3"},
      {"星期[四4]|周[四4]|礼拜四", "4"},
      {"星期[五5]|周[五5]|礼拜五", "5"},
      {"星期[六6]|周[六6]|礼拜六", "6"},
      {"星期[日天7]|周[日天7]|礼拜[日天]", "7"}
    };
    for (String[] rule : rules) {
      if (Pattern.compile(rule[0]).matcher(text).find()) return Integer.valueOf(rule[1]);
    }
    return null;
  }

  private static String normalizeWeekText(String raw) {
    if (raw == null) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c >= '０' && c <= '９') c = (char) ('0' + (c - '０'));
      if ("－—–―−﹣~～〜".indexOf(c) >= 0) c = '-';
      if (c == '，' || c == '、' || c == '；' || c == ';') c = ',';
      sb.append(c);
    }
    return sb.toString().replace("到", "-").replace("至", "-");
  }

  private static String weekSpan(String text) {
    String norm = normalizeWeekText(text);
    Matcher range = Pattern.compile("\\d+\\s*-\\s*\\d+\\s*周").matcher(norm);
    StringBuilder sb = new StringBuilder();
    while (range.find()) {
      if (sb.length() > 0) sb.append(',');
      sb.append(range.group());
    }
    if (sb.length() > 0) return sb.toString();
    if (norm.contains("单周")) return "单周";
    if (norm.contains("双周")) return "双周";
    Matcher list = Pattern.compile("\\d+(?:\\s*,\\s*\\d+)+\\s*周?").matcher(norm);
    if (list.find()) return list.group();
    return "";
  }

  private static Model.WeekRule parseWeeks(String raw, int total) {
    Model.WeekRule rule = new Model.WeekRule();
    rule.to = total;
    String source = normalizeWeekText(raw).replace(" ", "");
    if (source.length() == 0 || source.matches("(?i)全|全部|每周|每|all")) return rule;
    if (source.matches("单周?") || "单".equals(source)) {
      rule.kind = "odd";
      return rule;
    }
    if (source.matches("双周?") || "双".equals(source)) {
      rule.kind = "even";
      return rule;
    }
    boolean[] on = new boolean[41];
    boolean saw = false;
    String[] parts = source.split(",");
    for (int p = 0; p < parts.length; p++) {
      String part = parts[p];
      if (part.length() == 0) continue;
      boolean odd = part.contains("单");
      boolean even = part.contains("双") && !odd;
      Matcher range = Pattern.compile("(\\d+)-(\\d+)").matcher(part);
      boolean ranged = false;
      while (range.find()) {
        int a = Integer.parseInt(range.group(1));
        int b = Integer.parseInt(range.group(2));
        int lo = Math.max(1, Math.min(a, b));
        int hi = Math.min(40, Math.max(a, b));
        for (int n = lo; n <= hi; n++) {
          if (odd && n % 2 == 0) continue;
          if (even && n % 2 == 1) continue;
          on[n] = true;
        }
        ranged = true;
        saw = true;
      }
      if (ranged) continue;
      Matcher lone = Pattern.compile("\\d+").matcher(part);
      while (lone.find()) {
        int n = Integer.parseInt(lone.group());
        if (n < 1 || n > 40) continue;
        if (odd && n % 2 == 0) continue;
        if (even && n % 2 == 1) continue;
        on[n] = true;
        saw = true;
      }
    }
    if (!saw) return rule;
    return Model.WeekRule.fromMask(on);
  }

  private static Model.WeekRule mergeWeeks(Model.WeekRule left, Model.WeekRule right) {
    boolean[] on = new boolean[41];
    for (int week = 1; week <= 40; week++) {
      if (left.on(week) || right.on(week)) on[week] = true;
    }
    return Model.WeekRule.fromMask(on);
  }

  private static int countRanges(String source) {
    Matcher range = Pattern.compile("(\\d+)\\s*[-~到至—–]\\s*(\\d+)").matcher(source);
    int n = 0;
    while (range.find()) n++;
    return n;
  }

  private static String[] timeSlot(String cell, List<Model.Period> periods) {
    if (cell == null) return null;
    Matcher range = Pattern.compile("(\\d{1,2}[:：.]\\d{2})\\s*[-~到至—–]\\s*(\\d{1,2}[:：.]\\d{2})").matcher(cell);
    if (range.find()) {
      String start = Model.normTime(range.group(1));
      String end = Model.normTime(range.group(2));
      if (start.length() > 0 && end.length() > 0) return new String[] {start, end, ""};
    }
    Matcher section = Pattern.compile("第?\\s*(\\d+)\\s*[-~到至—–]\\s*(\\d+)\\s*节?").matcher(cell);
    if (section.find()) return sectionRange(periods, section.group(1) + "-" + section.group(2));
    Matcher one = Pattern.compile("第\\s*(\\d+)\\s*节").matcher(cell);
    if (one.find()) return sectionRange(periods, one.group(1));
    return null;
  }

  private static String joinCells(List<String[]> rows) {
    StringBuilder sb = new StringBuilder();
    for (String[] row : rows) {
      for (String cell : row) sb.append(cell).append('\n');
    }
    return sb.toString();
  }

  private static void sniffPeriods(List<String[]> rows, List<Model.Course> courses) {
    if (courses != null && !courses.isEmpty()) {
      foundPeriods = null;
      foundPeriodSets = null;
      return;
    }
    foundPeriodSets = Model.parsePeriodSets(joinCells(rows));
    if (foundPeriodSets.size() == 1 && foundPeriodSets.get(0).periods.size() >= 3) {
      foundPeriods = foundPeriodSets.get(0).periods;
    } else if (foundPeriodSets.size() > 1) {
      foundPeriods = null;
    } else {
      foundPeriodSets = null;
      foundPeriods = null;
    }
  }

  private static String[] sectionRange(List<Model.Period> periods, String spec) {
    return Model.sectionTimes(spec, periods);
  }

  private static Model.Period period(List<Model.Period> periods, int index) {
    for (Model.Period p : periods) if (p.index == index) return p;
    return null;
  }

  private static Model.Course fromCell(String cell, int day, String[] slot, int total, List<Model.Period> periods) {
    String text = cell.replace('\u00a0', ' ').replace('／', '/').trim();
    if (text.length() == 0 || text.matches("[-—/无空]+")) return null;
    if (text.startsWith("注") || text.startsWith("其他课程") || text.contains("正式上课")) return null;
    Model.Course slash = slashCourse(text, day, slot, total, periods);
    if (slash != null) return slash;
    String blob = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    String weekRaw = weekSpan(blob);
    Model.WeekRule weeks = parseWeeks(weekRaw, total);
    String rest = blob;
    Matcher strip = Pattern.compile("\\d+\\s*[-~到至—–－―−]\\s*\\d+\\s*周").matcher(rest);
    StringBuffer stripped = new StringBuffer();
    while (strip.find()) strip.appendReplacement(stripped, " ");
    strip.appendTail(stripped);
    rest = stripped.toString();
    String[] own = timeSlot(rest, new ArrayList<Model.Period>());
    if (own != null) rest = rest.replaceAll("(\\d{1,2}[:：.]\\d{2})\\s*[-~到至—–]\\s*(\\d{1,2}[:：.]\\d{2})", " ");
    String teacher = "";
    Matcher teacherMatch = Pattern.compile("([\\u4e00-\\u9fa5]{1,6}老师)").matcher(rest);
    if (teacherMatch.find()) {
      teacher = teacherMatch.group(1);
      rest = rest.replace(teacher, " ");
    } else {
      String[] parts = rest.trim().split("\\s+");
      if (parts.length >= 2) {
        String last = parts[parts.length - 1];
        if (last.matches("[\\u4e00-\\u9fa5]{2,3}")) {
          teacher = last;
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts[i]);
          }
          rest = sb.toString();
        }
      }
    }
    String location = "";
    Matcher loc = Pattern.compile("([A-Za-z0-9\\u4e00-\\u9fa5\\-]*?(?:楼|室|馆|场|厅|实验室|机房)[A-Za-z0-9\\-]*)").matcher(rest);
    if (loc.find() && loc.group(1).length() <= 24) {
      location = loc.group(1);
      rest = rest.replace(location, " ");
    }
    String name = rest
        .replaceAll("星期[一二三四五六日天]|周[一二三四五六日天]|礼拜[一二三四五六日天]", " ")
        .replaceAll("[()（）\\[\\]【】]", " ")
        .replaceAll("第?\\d+\\s*[-~到至—–]\\s*\\d+\\s*节|第\\s*\\d+\\s*节", " ")
        .replaceAll("\\s+", " ")
        .trim();
    if (name.length() == 0) return null;
    if (name.length() > 40) name = name.substring(0, 40).trim();
    String start = own != null && own[0].length() > 0 ? own[0] : slot[0];
    String end = own != null && own[1].length() > 0 ? own[1] : slot[1];
    if (start.length() == 0 || end.length() == 0 || start.equals(end)) return null;
    Model.Course course = new Model.Course();
    course.name = name;
    course.teacher = teacher.replace("老师", "");
    course.location = location;
    course.day = day;
    course.start = start;
    course.end = end;
    course.section = slot[2];
    course.weeks = weeks;
    return course;
  }

  private static Model.Course slashCourse(String text, int day, String[] slot, int total, List<Model.Period> periods) {
    String line = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    Matcher matcher = Pattern.compile("^(.+?)/[（(]([^）)]+)[）)]\\s*([^/]*)/([^/]*)/([^/]*)$").matcher(line);
    if (!matcher.find()) return null;
    String name = matcher.group(1).trim();
    String section = matcher.group(2).trim();
    String weeks = matcher.group(3).trim();
    String location = matcher.group(4).trim();
    String teacher = matcher.group(5).trim().replace("老师", "");
    if (name.length() == 0 || name.startsWith("注")) return null;
    String[] mapped = sectionRange(periods, section);
    String start = mapped != null ? mapped[0] : slot[0];
    String end = mapped != null ? mapped[1] : slot[1];
    String sectionLabel = mapped != null ? mapped[2] : slot[2];
    if ((start.length() == 0 || end.length() == 0) && slot[0].length() > 0) {
      start = slot[0];
      end = slot[1];
      if (sectionLabel.length() == 0) sectionLabel = slot[2];
    }
    if (start.length() == 0 || end.length() == 0 || start.equals(end)) return null;
    Model.Course course = new Model.Course();
    course.name = name.length() > 40 ? name.substring(0, 40) : name;
    course.teacher = teacher;
    course.location = location;
    course.day = day;
    course.start = start;
    course.end = end;
    course.section = sectionLabel;
    course.weeks = parseWeeks(weeks, total);
    return course;
  }

  private static String[] bigPeriod(String cell, List<Model.Period> periods) {
    if (cell == null) return null;
    String text = cell.trim();
    if (text.length() == 0 || text.length() > 3) return null;
    String spec = null;
    if ("一".equals(text) || "1".equals(text)) spec = "1-2";
    else if ("二".equals(text) || "2".equals(text)) spec = "3-4";
    else if ("三".equals(text) || "3".equals(text)) spec = "5-6";
    else if ("四".equals(text) || "4".equals(text)) spec = "7-8";
    else if ("五".equals(text) || "5".equals(text)) spec = "9-10";
    else if ("六".equals(text) || "6".equals(text)) spec = "11-12";
    if (spec == null) return null;
    return sectionRange(periods, spec);
  }

  private static List<Model.Course> parseGrid(List<String[]> rows, Model.Settings settings) {
    int headerAt = -1;
    for (int i = 0; i < rows.size(); i++) {
      if (isDayHeader(rows.get(i))) {
        headerAt = i;
        break;
      }
    }
    List<Model.Course> courses = new ArrayList<Model.Course>();
    if (headerAt < 0) return courses;
    String[] header = rows.get(headerAt);
    List<int[]> cols = new ArrayList<int[]>();
    for (int i = 0; i < header.length; i++) {
      Integer day = dayFromHeader(header[i]);
      if (day != null) cols.add(new int[] {i, day.intValue()});
    }
    for (int r = headerAt + 1; r < rows.size(); r++) {
      String[] row = rows.get(r);
      String[] slot = null;
      for (int i = 0; i < row.length; i++) {
        boolean dayCol = false;
        for (int[] col : cols) if (col[0] == i) dayCol = true;
        if (dayCol || row[i].length() == 0) continue;
        slot = timeSlot(row[i], settings.periods);
        if (slot == null) slot = bigPeriod(row[i], settings.periods);
        if (slot != null) break;
      }
      if (slot == null) slot = new String[] {"", "", ""};
      for (int[] col : cols) {
        if (col[0] >= row.length) continue;
        String cell = row[col[0]];
        String[] chunks = cell.split("\\r?\\n+");
        if (chunks.length == 0) chunks = new String[] {cell};
        for (String chunk : chunks) {
          Model.Course course = fromCell(chunk, col[1], slot, settings.totalWeeks, settings.periods);
          if (course != null) courses.add(course);
        }
      }
    }
    return courses;
  }

  private static String fieldOf(String cell) {
    String key = cell == null ? "" : cell.trim().toLowerCase().replace(" ", "");
    if ("课程".equals(key) || "课程名".equals(key) || "科目".equals(key) || "名称".equals(key) || "name".equals(key) || "course".equals(key)) return "name";
    if ("教师".equals(key) || "老师".equals(key) || "任课教师".equals(key) || "teacher".equals(key)) return "teacher";
    if ("地点".equals(key) || "教室".equals(key) || "上课地点".equals(key) || "location".equals(key) || "room".equals(key)) return "location";
    if ("星期".equals(key) || "周几".equals(key) || "上课日".equals(key) || "day".equals(key) || "weekday".equals(key)) return "day";
    if ("开始".equals(key) || "开始时间".equals(key) || "上课时间".equals(key) || "start".equals(key)) return "start";
    if ("结束".equals(key) || "结束时间".equals(key) || "end".equals(key)) return "end";
    if ("周次".equals(key) || "上课周".equals(key) || "weeks".equals(key)) return "weeks";
    if ("节次".equals(key) || "节".equals(key) || "section".equals(key)) return "section";
    return null;
  }

  private static List<Model.Course> parseRecords(List<String[]> rows, Model.Settings settings) {
    List<Model.Course> courses = new ArrayList<Model.Course>();
    if (rows.isEmpty()) return courses;
    String[] header = rows.get(0);
    String[] fields = new String[header.length];
    int named = 0;
    boolean hasName = false;
    for (int i = 0; i < header.length; i++) {
      fields[i] = fieldOf(header[i]);
      if (fields[i] != null) named++;
      if ("name".equals(fields[i])) hasName = true;
    }
    boolean hasHeader = named >= 2 && hasName;
    int startRow = hasHeader ? 1 : 0;
    for (int r = startRow; r < rows.size(); r++) {
      String[] row = rows.get(r);
      if (!hasHeader) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
          if (i > 0) line.append(' ');
          line.append(row[i]);
        }
        Model.Course loose = loose(line.toString(), settings);
        if (loose != null) courses.add(loose);
        continue;
      }
      Map<String, String> bag = new HashMap<String, String>();
      for (int i = 0; i < fields.length && i < row.length; i++) if (fields[i] != null) bag.put(fields[i], row[i]);
      String name = bag.containsKey("name") ? bag.get("name").trim() : "";
      if (name.length() == 0) continue;
      Integer day = dayFromText(bag.containsKey("day") ? bag.get("day") : "");
      if (day == null) {
        try {
          int n = Integer.parseInt((bag.containsKey("day") ? bag.get("day") : "").trim());
          if (n >= 1 && n <= 7) day = Integer.valueOf(n);
        } catch (Exception ignored) {
        }
      }
      if (day == null) continue;
      String start = Model.normTime(bag.containsKey("start") ? bag.get("start") : "");
      String end = Model.normTime(bag.containsKey("end") ? bag.get("end") : "");
      String section = bag.containsKey("section") ? bag.get("section").trim() : "";
      if ((start.length() == 0 || end.length() == 0) && section.length() > 0) {
        String[] mapped = sectionRange(settings.periods, section);
        if (mapped != null) {
          if (start.length() == 0) start = mapped[0];
          if (end.length() == 0) end = mapped[1];
          if (section.length() == 0) section = mapped[2];
        }
      }
      if (start.length() == 0 || end.length() == 0) {
        String[] slot = timeSlot(bag.containsKey("start") ? bag.get("start") : "", settings.periods);
        if (slot != null) {
          start = slot[0];
          end = slot[1];
        }
      }
      if (start.length() == 0 || end.length() == 0 || start.equals(end)) continue;
      Model.Course course = new Model.Course();
      course.name = name;
      course.teacher = bag.containsKey("teacher") ? bag.get("teacher").trim() : "";
      course.location = bag.containsKey("location") ? bag.get("location").trim() : "";
      course.day = day.intValue();
      course.start = start;
      course.end = end;
      course.section = section;
      course.weeks = parseWeeks(bag.containsKey("weeks") ? bag.get("weeks") : "", settings.totalWeeks);
      courses.add(course);
    }
    return courses;
  }

  private static Model.Course loose(String line, Model.Settings settings) {
    String text = line.trim();
    if (text.length() == 0 || text.startsWith("#")) return null;
    Integer day = dayFromText(text);
    String[] slot = timeSlot(text, settings.periods);
    if (day == null || slot == null) return null;
    return fromCell(text, day.intValue(), slot, settings.totalWeeks, settings.periods);
  }

  private static List<Model.Course> dedupe(List<Model.Course> courses) {
    List<Model.Course> out = new ArrayList<Model.Course>();
    for (Model.Course course : courses) {
      if (course.start.compareTo(course.end) >= 0) continue;
      boolean seen = false;
      for (Model.Course other : out) {
        if (other.name.equals(course.name) && other.day == course.day && other.start.equals(course.start)) {
          other.weeks = mergeWeeks(other.weeks, course.weeks);
          if (other.location.length() == 0) other.location = course.location;
          if (other.teacher.length() == 0) other.teacher = course.teacher;
          seen = true;
        }
      }
      if (!seen) out.add(course);
    }
    Collections.sort(out, new Comparator<Model.Course>() {
      public int compare(Model.Course a, Model.Course b) {
        if (a.day != b.day) return a.day - b.day;
        return Model.parseMinutes(a.start) - Model.parseMinutes(b.start);
      }
    });
    return out;
  }

  private static List<String[]> split(String text) {
    String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
    List<String> kept = new ArrayList<String>();
    for (String line : lines) {
      if (line.trim().length() > 0 && !line.trim().matches("[-|: ]+")) kept.add(line);
    }
    if (kept.isEmpty()) return new ArrayList<String[]>();
    boolean matrix = false;
    for (String line : kept) {
      if (line.indexOf('\t') >= 0 || line.indexOf(',') >= 0 || line.indexOf('|') >= 0 || line.indexOf('，') >= 0) matrix = true;
    }
    if (!matrix) {
      List<String[]> rows = new ArrayList<String[]>();
      for (String line : kept) rows.add(new String[] {line.trim()});
      return rows;
    }
    String sample = kept.get(0);
    char delimiter = sample.indexOf('\t') >= 0 ? '\t' : sample.indexOf('|') >= 0 ? '|' : ',';
    List<String[]> rows = new ArrayList<String[]>();
    for (String line : kept) {
      if (delimiter == '|') {
        String trimmed = line.replaceAll("^\\|", "").replaceAll("\\|$", "");
        String[] parts = trimmed.split("\\|");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        rows.add(parts);
      } else if (delimiter == '\t') {
        String[] parts = line.split("\t", -1);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        rows.add(parts);
      } else {
        rows.add(splitCsv(line.replace('，', ',')));
      }
    }
    return rows;
  }

  private static String[] splitCsv(String line) {
    List<String> cells = new ArrayList<String>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') quoted = !quoted;
      else if (ch == ',' && !quoted) {
        cells.add(current.toString().trim());
        current.setLength(0);
      } else current.append(ch);
    }
    cells.add(current.toString().trim());
    return cells.toArray(new String[cells.size()]);
  }

  public static byte[] utf8(String text) {
    try {
      return text.getBytes("UTF-8");
    } catch (Exception e) {
      return new byte[0];
    }
  }

  public static boolean looksZip(byte[] data) {
    return data != null && data.length > 4 && data[0] == 'P' && data[1] == 'K';
  }

  public static boolean looksOle(byte[] data) {
    return data != null && data.length > 8
        && (data[0] & 0xFF) == 0xD0
        && (data[1] & 0xFF) == 0xCF
        && (data[2] & 0xFF) == 0x11
        && (data[3] & 0xFF) == 0xE0;
  }

  public static boolean looksMarkup(String text) {
    String lower = text.toLowerCase();
    return lower.contains("<table") || lower.contains("<tr") || lower.contains("<row") || lower.contains("<worksheet");
  }

  public static String asText(byte[] data) {
    try {
      return new String(data, "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }

  public static ByteArrayInputStream stream(byte[] data) {
    return new ByteArrayInputStream(data);
  }
}
