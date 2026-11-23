package cn.qishui.helper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private EditText rounds, minutes;
    private CheckBox dry;
    private TextView current, permission;
    private final Runnable update = new Runnable() {
        @Override public void run() {
            HelperService service = HelperService.instance;
            permission.setText(service == null ? "未开启 · 请先授权无障碍服务" : "已就绪 · 无障碍服务已连接");
            current.setText(service == null ? "等待授权\n开启服务后即可使用悬浮控制按钮。" : service.statusText());
            handler.postDelayed(this, 1000);
        }
    };
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xFFF3F6FC);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20), dp(22), dp(20), dp(24)); scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        setContentView(scroll);
        text(page, "汽水听歌助手", 27, 0xFF173456);
        text(page, "独立安卓版 · 等待广告，自动处理领奖", 14, 0xFF61708A);

        LinearLayout access = card(page);
        text(access, "先完成一次授权", 18, 0xFF173456);
        permission = text(access, "正在检查服务", 14, 0xFF245FDC);
        text(access, "开启后，助手可读取屏幕并执行点击。仅在你开始任务后处理汽水音乐页面，文字识别在本机完成。", 14, 0xFF61708A);
        button(access, "开启无障碍服务", true, () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        text(access, "进入“已下载的应用/已安装的服务”，选择“汽水听歌助手”并开启。若系统提示受限，请到本应用信息页允许受限设置后重试。", 12, 0xFF61708A);
        if (Build.VERSION.SDK_INT >= 33) button(access, "允许任务状态通知（可选）", false, () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10));

        LinearLayout status = card(page);
        text(status, "任务状态", 18, 0xFF173456);
        current = text(status, "待开始", 15, 0xFF245FDC);
        text(status, "打开此设置页会暂停任务；回到汽水后可从悬浮菜单继续。", 12, 0xFF61708A);

        LinearLayout options = card(page);
        text(options, "运行设置", 18, 0xFF173456);
        FlowEngine.Settings values = AppData.load(this);
        rounds = number(options, "最多完成几轮（1～50）", values.maxRounds);
        minutes = number(options, "最多运行几分钟（1～60）", values.maxMinutes);
        dry = new CheckBox(this); dry.setText("只识别，不执行点击"); dry.setTextSize(15); dry.setChecked(values.dryRun); options.addView(dry);
        text(options, "首次建议保留只识别。识别正常后关闭此开关，先试跑 1 轮。", 12, 0xFF61708A);
        button(options, "保存设置", false, () -> { if (save()) toast("设置已保存，重新开始时生效"); });
        button(options, "开始 / 重新开始", true, () -> {
            if (!save()) return;
            HelperService service = requireService();
            if (service != null && targetAvailable()) { service.startTask(AppData.load(this)); openTarget(); }
        });
        button(options, "继续任务", false, () -> { HelperService s = requireService(); if (s != null && targetAvailable()) { s.resumeTask(); openTarget(); } });
        button(options, "停止任务", false, () -> { HelperService s = HelperService.instance; if (s != null) s.stopTask(); });
        button(options, "只打开汽水音乐", false, () -> { if (targetAvailable()) openTarget(); });

        LinearLayout guide = card(page);
        text(guide, "怎么使用", 18, 0xFF173456);
        text(guide, "1. 开启上方无障碍服务并保存设置。\n2. 打开汽水，手动进入第一条奖励广告。\n3. 点击左侧悬浮“助手”按钮，先做屏幕自检。\n4. 选择重新开始，或继续暂停的任务。\n5. 结束后回到汽水核对免费听权益。", 14, 0xFF405A75);
        text(guide, "出现直播预告会优先点取消；已进入直播间时，会识别右上角 X 返回广告。广告正常播放，程序不修改计时或会员权益。", 14, 0xFF405A75);
        text(guide, "按音量减键或打开悬浮菜单可暂停。请保持亮屏；切换应用后会暂停。按钮按当前画面定位，换设备或调整字体大小后先试跑。", 14, 0xFF405A75);
        text(guide, "支持 Android 11 及以上；无需安装 AScript。识别模型已包含在安装包内。", 12, 0xFF61708A);
        text(page, "版本 " + BuildConfig.VERSION_NAME + " · 改版4 · 只认右上角\n日志仅存本机，用于检查识别和点击结果。", 12, 0xFF61708A);
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), dp(14), dp(16), dp(14)); box.setBackground(background(Color.WHITE, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(14); parent.addView(box, p); return box;
    }
    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setPadding(0, dp(3), 0, dp(7)); view.setLineSpacing(dp(3), 1); parent.addView(view); return view;
    }
    private EditText number(LinearLayout parent, String label, int value) {
        text(parent, label, 14, 0xFF405A75); EditText input = new EditText(this); input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); input.setSingleLine(); input.setText(String.valueOf(value)); input.setTextSize(17); parent.addView(input); return input;
    }
    private void button(LinearLayout parent, String label, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(15); button.setAllCaps(false); button.setMinHeight(dp(48));
        button.setTextColor(primary ? Color.WHITE : 0xFF245FDC); button.setBackground(background(primary ? 0xFF245FDC : 0xFFEDF2FC, 10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(9); parent.addView(button, p); button.setOnClickListener(v -> action.run());
    }
    private boolean save() {
        try { AppData.save(this, new FlowEngine.Settings(Integer.parseInt(rounds.getText().toString()), Integer.parseInt(minutes.getText().toString()), dry.isChecked())); return true; }
        catch (Exception e) { toast("请输入 1～50 轮、1～60 分钟"); return false; }
    }
    private HelperService requireService() { HelperService s = HelperService.instance; if (s == null) toast("请先开启汽水听歌助手无障碍服务"); return s; }
    private boolean targetAvailable() { if (getPackageManager().getLaunchIntentForPackage(FlowEngine.TARGET) == null) { toast("请先安装汽水音乐"); return false; } return true; }
    private void openTarget() { Intent launch = getPackageManager().getLaunchIntentForPackage(FlowEngine.TARGET); if (launch != null) { startActivity(launch); toast("请手动进入奖励广告，再从悬浮菜单开始或继续"); } }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); HelperService s = HelperService.instance; if (s != null && s.isActive()) s.pause("设置页已打开，任务暂停"); handler.post(update); }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
}
