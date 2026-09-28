package app.kezhong;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class Ocr {
  private Ocr() {}

  static String read(Context context, Bitmap bitmap) throws Exception {
    File data = new File(context.getFilesDir(), "tessdata/chi_sim.traineddata");
    if (!data.exists() || data.length() < 100000) {
      data.getParentFile().mkdirs();
      InputStream in = context.getAssets().open("tessdata/chi_sim.traineddata");
      FileOutputStream out = new FileOutputStream(data);
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
      out.close();
      in.close();
    }
    TessBaseAPI api = new TessBaseAPI();
    try {
      if (!api.init(context.getFilesDir().getAbsolutePath(), "chi_sim", TessBaseAPI.OEM_LSTM_ONLY)) {
        throw new IllegalStateException("文字识别初始化失败");
      }
      api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
      api.setImage(bitmap);
      String lined = lines(api);
      if (lined.length() > 0) return lined;
      String text = api.getUTF8Text();
      return text == null ? "" : text;
    } finally {
      api.recycle();
    }
  }

  private static String lines(TessBaseAPI api) {
    ResultIterator it = api.getResultIterator();
    if (it == null) return "";
    List<Line> lines = new ArrayList<Line>();
    try {
      it.begin();
      do {
        String text = it.getUTF8Text(2);
        Rect box = it.getBoundingRect(2);
        if (text == null || box == null) continue;
        text = text.replace('\n', ' ').trim();
        if (text.length() == 0) continue;
        Line line = new Line();
        line.text = text;
        line.left = box.left;
        line.top = box.top;
        line.bottom = box.bottom;
        lines.add(line);
      } while (it.next(2));
    } catch (Exception ignored) {
      return "";
    } finally {
      it.delete();
    }
    if (lines.isEmpty()) return "";
    Collections.sort(lines, new Comparator<Line>() {
      public int compare(Line a, Line b) {
        int dy = a.top - b.top;
        if (Math.abs(dy) > 12) return dy;
        return a.left - b.left;
      }
    });
    StringBuilder sb = new StringBuilder();
    int rowTop = lines.get(0).top;
    int rowBottom = lines.get(0).bottom;
    List<Line> row = new ArrayList<Line>();
    for (Line line : lines) {
      int mid = (line.top + line.bottom) / 2;
      if (mid > rowBottom + 8) {
        writeRow(sb, row);
        row.clear();
        rowTop = line.top;
        rowBottom = line.bottom;
      } else {
        rowBottom = Math.max(rowBottom, line.bottom);
        rowTop = Math.min(rowTop, line.top);
      }
      row.add(line);
    }
    writeRow(sb, row);
    return sb.toString();
  }

  private static void writeRow(StringBuilder sb, List<Line> row) {
    Collections.sort(row, new Comparator<Line>() {
      public int compare(Line a, Line b) { return a.left - b.left; }
    });
    for (int i = 0; i < row.size(); i++) {
      if (i > 0) sb.append(' ');
      sb.append(row.get(i).text);
    }
    sb.append('\n');
  }

  private static final class Line {
    String text;
    int left;
    int top;
    int bottom;
  }
}
