package app.kezhong;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** 国务院办公厅 2026 年节假日安排里，落在学期内的日期。2027 年元旦调休尚未公布，只标法定当天。 */
final class Holidays {
  private static final Map<String, String> OFF = new HashMap<String, String>();
  private static final Map<String, String> WORK = new HashMap<String, String>();

  static {
    putOff("2026-09-25", "中秋节");
    putOff("2026-09-26", "中秋节");
    putOff("2026-09-27", "中秋节");
    putOff("2026-10-01", "国庆节");
    putOff("2026-10-02", "国庆节");
    putOff("2026-10-03", "国庆节");
    putOff("2026-10-04", "国庆节");
    putOff("2026-10-05", "国庆节");
    putOff("2026-10-06", "国庆节");
    putOff("2026-10-07", "国庆节");
    putOff("2027-01-01", "元旦");
    WORK.put("2026-09-20", "调休上班");
    WORK.put("2026-10-10", "调休上班");
  }

  private static void putOff(String day, String name) {
    OFF.put(day, name);
  }

  static String off(Calendar date) {
    return OFF.get(key(date));
  }

  static String work(Calendar date) {
    return WORK.get(key(date));
  }

  static String key(Calendar date) {
    return date.get(Calendar.YEAR) + "-" + Model.pad(date.get(Calendar.MONTH) + 1) + "-" + Model.pad(date.get(Calendar.DAY_OF_MONTH));
  }
}
