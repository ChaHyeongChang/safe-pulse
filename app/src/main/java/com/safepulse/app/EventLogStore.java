package com.safepulse.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * 이벤트 로그 저장소. 앱 내부 저장소(files/events)에만 쓰고, 운전자 본인이 앱에서 보거나
 * adb pull로 내보낼 수 있다. 설정에서 전부 삭제할 수 있다.
 */
final class EventLogStore {

    private static final String TAG = "SafePulse/Log";
    private final File dir;

    EventLogStore(Context context) {
        dir = new File(context.getFilesDir(), "events");
    }

    File save(String json) {
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "cannot create " + dir);
            return null;
        }
        String name = "event_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            Log.i(TAG, "saved " + f.getAbsolutePath());
            return f;
        } catch (IOException e) {
            Log.w(TAG, "save failed", e);
            return null;
        }
    }

    File[] list() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        return files;
    }

    String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    int deleteAll() {
        int n = 0;
        for (File f : list()) {
            if (f.delete()) n++;
        }
        return n;
    }
}
