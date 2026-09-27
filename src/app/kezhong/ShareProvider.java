package app.kezhong;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;

public class ShareProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    File file = fileFor(uri);
    MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
    cursor.addRow(new Object[] {file.getName(), file.exists() ? file.length() : 0});
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    String name = fileFor(uri).getName().toLowerCase();
    if (name.endsWith(".ics")) return "text/calendar";
    if (name.endsWith(".csv")) return "text/csv";
    if (name.endsWith(".json")) return "application/json";
    return "text/plain";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) {
    try {
      return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    } catch (Exception error) {
      return null;
    }
  }

  private File fileFor(Uri uri) {
    String name = uri.getLastPathSegment();
    if (name == null) name = "课钟.txt";
    name = Uri.decode(name).replaceAll("[\\\\/]", "_");
    return new File(getContext().getCacheDir(), name);
  }
}
