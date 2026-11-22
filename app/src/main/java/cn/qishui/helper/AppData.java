package cn.qishui.helper;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class AppData {
    static FlowEngine.Settings load(Context c) {
        SharedPreferences p = c.getSharedPreferences("settings", 0);
        return new FlowEngine.Settings(p.getInt("rounds", 3), p.getInt("minutes", 15), p.getBoolean("dry", true));
    }
    static void save(Context c, FlowEngine.Settings s) {
        c.getSharedPreferences("settings", 0).edit().putInt("rounds", s.maxRounds).putInt("minutes", s.maxMinutes).putBoolean("dry", s.dryRun).apply();
    }
    static File directory(Context c) {
        File external = c.getExternalFilesDir(null);
        File dir = new File(external == null ? c.getFilesDir() : external, "logs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
    static void write(Context c, String name, JSONObject json, boolean append) {
        try (FileOutputStream out = new FileOutputStream(new File(directory(c), name), append)) {
            out.write((json.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { android.util.Log.e("QishuiHelper", "Cannot write local log", e); }
    }
}
