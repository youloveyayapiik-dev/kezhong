package app.kezhong;

import java.util.Calendar;
import java.util.List;

public final class Export {
  public static String csv(List<Model.Course> courses) {
    StringBuilder sb = new StringBuilder();
    sb.append("课程,教师,地点,星期,开始,结束,周次,节次\n");
    for (Model.Course course : courses) {
      sb.append(cell(course.name)).append(',')
          .append(cell(course.teacher)).append(',')
          .append(cell(course.location)).append(',')
          .append(cell(Model.WEEKDAY[Math.max(1, Math.min(7, course.day))])).append(',')
          .append(cell(course.start)).append(',')
          .append(cell(course.end)).append(',')
          .append(cell(course.weeks.label())).append(',')
          .append(cell(course.section)).append('\n');
    }
    return sb.toString();
  }

  public static String json(Store store) {
    try {
      org.json.JSONObject root = new org.json.JSONObject();
      org.json.JSONObject s = new org.json.JSONObject();
      s.put("termStart", store.settings.termStart);
      s.put("totalWeeks", store.settings.totalWeeks);
      org.json.JSONObject wake = new org.json.JSONObject();
      wake.put("washMin", store.settings.wake.wash);
      wake.put("commuteMin", store.settings.wake.commute);
      wake.put("bufferMin", store.settings.wake.buffer);
      wake.put("classRemindMin", store.settings.wake.remind);
      s.put("wake", wake);
      root.put("settings", s);
      org.json.JSONArray arr = new org.json.JSONArray();
      for (Model.Course course : store.courses) arr.put(course.toJson());
      root.put("courses", arr);
      return root.toString(2);
    } catch (Exception e) {
      return "{}";
    }
  }

  public static String ics(Store store) {
    StringBuilder sb = new StringBuilder();
    sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//课钟//CN\r\nCALSCALE:GREGORIAN\r\n");
    Calendar start = Model.parseDate(store.settings.termStart);
    int days = store.settings.totalWeeks * 7;
    int lead = store.settings.wake.lead();
    int remind = Math.max(0, store.settings.wake.remind);
    for (int i = 0; i < days; i++) {
      Calendar date = Model.addDays(start, i);
      List<Model.Course> list = Model.onDate(store.courses, store.settings, date);
      if (list.isEmpty()) continue;
      Model.Course first = list.get(0);
      Calendar wake = Model.atNoon(date);
      wake.set(Calendar.HOUR_OF_DAY, 0);
      wake.set(Calendar.MINUTE, 0);
      wake.add(Calendar.MINUTE, Model.parseMinutes(first.start) - lead);
      event(sb, "wake-" + i, wake, 15, "起床 · " + first.name, first.location);
      for (Model.Course course : list) {
        Calendar at = Model.atNoon(date);
        at.set(Calendar.HOUR_OF_DAY, 0);
        at.set(Calendar.MINUTE, 0);
        at.add(Calendar.MINUTE, Model.parseMinutes(course.start) - remind);
        int length = Math.max(20, Model.parseMinutes(course.end) - Model.parseMinutes(course.start) + remind);
        event(sb, "c-" + i + "-" + course.id, at, length, course.name, course.location);
      }
    }
    sb.append("END:VCALENDAR\r\n");
    return sb.toString();
  }

  private static void event(StringBuilder sb, String uid, Calendar start, int minutes, String title, String place) {
    Calendar end = (Calendar) start.clone();
    end.add(Calendar.MINUTE, minutes);
    sb.append("BEGIN:VEVENT\r\n");
    sb.append("UID:").append(uid).append("@kezhong\r\n");
    sb.append("DTSTAMP:").append(stamp(Calendar.getInstance())).append("\r\n");
    sb.append("DTSTART:").append(stamp(start)).append("\r\n");
    sb.append("DTEND:").append(stamp(end)).append("\r\n");
    sb.append("SUMMARY:").append(escape(title)).append("\r\n");
    if (place != null && place.length() > 0) sb.append("LOCATION:").append(escape(place)).append("\r\n");
    sb.append("BEGIN:VALARM\r\nTRIGGER:PT0S\r\nACTION:DISPLAY\r\nDESCRIPTION:").append(escape(title)).append("\r\nEND:VALARM\r\n");
    sb.append("END:VEVENT\r\n");
  }

  private static String stamp(Calendar c) {
    return c.get(Calendar.YEAR)
        + Model.pad(c.get(Calendar.MONTH) + 1)
        + Model.pad(c.get(Calendar.DAY_OF_MONTH))
        + "T"
        + Model.pad(c.get(Calendar.HOUR_OF_DAY))
        + Model.pad(c.get(Calendar.MINUTE))
        + Model.pad(c.get(Calendar.SECOND));
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("\n", " ").replace(",", "\\,").replace(";", "\\;");
  }

  private static String cell(String value) {
    String v = value == null ? "" : value;
    return "\"" + v.replace("\"", "\"\"") + "\"";
  }
}
