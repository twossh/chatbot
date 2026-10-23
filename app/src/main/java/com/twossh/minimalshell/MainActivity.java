package com.twossh.minimalshell;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(17, 17, 15);
    private static final int SURFACE = Color.rgb(26, 26, 23);
    private static final int TEXT = Color.rgb(242, 239, 232);
    private static final int MUTED = Color.rgb(154, 151, 142);
    private static final int ACCENT = Color.rgb(210, 197, 165);
    private static final int DIVIDER = Color.rgb(48, 47, 42);

    private enum Page { HOME, APPS, RECENTS, NOTIFICATIONS, CONTROL, SETTINGS }

    private FrameLayout content;
    private Page currentPage = Page.HOME;
    private List<AppEntry> launchableApps = new ArrayList<>();

    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (currentPage == Page.NOTIFICATIONS) showNotifications();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        launchableApps = loadLaunchableApps();
        setContentView(buildRoot());
        showHome();
        registerNotificationReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isNotificationAccessGranted()) {
            NotificationListenerService.requestRebind(new ComponentName(this, NotificationCaptureService.class));
        }
        renderCurrentPage();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(notificationReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (currentPage != Page.HOME) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(content, contentParams);
        root.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        ));
        return root;
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(4), dp(8), dp(8));
        nav.setBackgroundColor(BG);

        addNavButton(nav, "INÍCIO", Page.HOME);
        addNavButton(nav, "APPS", Page.APPS);
        addNavButton(nav, "REC.", Page.RECENTS);
        addNavButton(nav, "ALERTAS", Page.NOTIFICATIONS);
        addNavButton(nav, "CONTROLE", Page.CONTROL);
        addNavButton(nav, "AJUSTES", Page.SETTINGS);
        return nav;
    }

    private void addNavButton(LinearLayout parent, String label, Page page) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(MUTED);
        button.setTextSize(10);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(2), dp(8), dp(2), dp(8));
        button.setOnClickListener(v -> {
            currentPage = page;
            renderCurrentPage();
        });
        parent.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void renderCurrentPage() {
        switch (currentPage) {
            case HOME -> showHome();
            case APPS -> showApps();
            case RECENTS -> showRecents();
            case NOTIFICATIONS -> showNotifications();
            case CONTROL -> showControlCenter();
            case SETTINGS -> showSettings();
        }
    }

    private void showHome() {
        currentPage = Page.HOME;
        LinearLayout body = pageColumn();
        body.setPadding(dp(28), dp(36), dp(28), dp(24));

        Date now = new Date();
        DateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        DateFormat dateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"));

        TextView time = text(timeFormat.format(now), 58, TEXT, Typeface.NORMAL);
        body.addView(time);
        TextView date = text(capitalize(dateFormat.format(now)), 16, MUTED, Typeface.NORMAL);
        body.addView(date);

        body.addView(space(40));
        body.addView(text("FAVORITOS", 11, MUTED, Typeface.BOLD));
        body.addView(space(12));

        List<AppEntry> favorites = usageSortedApps(7);
        if (favorites.isEmpty()) {
            favorites = launchableApps.subList(0, Math.min(7, launchableApps.size()));
        }
        for (AppEntry app : favorites) {
            body.addView(appRow(app, false));
        }

        body.addView(space(30));
        TextView hint = text("Deslize pelo sistema usando a barra inferior. O shell mantém a estética minimalista e abre as áreas protegidas do Android apenas quando necessário.", 13, MUTED, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.25f);
        body.addView(hint);

        setPage(scroll(body));
    }

    private void showApps() {
        currentPage = Page.APPS;
        LinearLayout body = pageColumn();
        body.setPadding(dp(24), dp(24), dp(24), dp(20));
        body.addView(title("Aplicativos"));
        body.addView(space(14));

        EditText search = new EditText(this);
        search.setHint("Buscar aplicativo");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(rounded(SURFACE, 18, DIVIDER));
        body.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        body.addView(space(14));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        renderAppsInto(list, "");

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderAppsInto(list, s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        setPage(scroll(body));
    }

    private void renderAppsInto(LinearLayout list, String query) {
        list.removeAllViews();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (AppEntry app : launchableApps) {
            if (!normalized.isEmpty() && !app.label.toLowerCase(Locale.ROOT).contains(normalized)) continue;
            list.addView(appRow(app, true));
        }
    }

    private void showRecents() {
        currentPage = Page.RECENTS;
        LinearLayout body = pageColumn();
        body.setPadding(dp(24), dp(24), dp(24), dp(20));
        body.addView(title("Recentes"));
        body.addView(subtitle("Histórico de uso integrado ao shell. Não substitui o Overview protegido do Android."));
        body.addView(space(18));

        if (!hasUsageAccess()) {
            body.addView(infoCard("Acesso ao uso necessário", "Permita o acesso para ordenar os aplicativos usados recentemente.", "PERMITIR", v -> openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        } else {
            List<AppEntry> recent = usageSortedApps(20);
            if (recent.isEmpty()) {
                body.addView(subtitle("Nenhum histórico recente disponível."));
            } else {
                for (AppEntry app : recent) {
                    body.addView(appRow(app, true));
                }
            }
        }
        setPage(scroll(body));
    }

    private void showNotifications() {
        currentPage = Page.NOTIFICATIONS;
        LinearLayout body = pageColumn();
        body.setPadding(dp(24), dp(24), dp(24), dp(20));
        body.addView(title("Notificações"));
        body.addView(subtitle("Central própria do launcher."));
        body.addView(space(18));

        if (!isNotificationAccessGranted()) {
            body.addView(infoCard("Acesso às notificações", "Ative o Minimal Shell para mostrar, abrir e dispensar notificações neste painel.", "ATIVAR", v -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            setPage(scroll(body));
            return;
        }

        List<NotificationCaptureService.Notice> notices = NotificationCaptureService.snapshot();
        if (notices.isEmpty()) {
            body.addView(subtitle("Sem notificações ativas."));
        } else {
            for (NotificationCaptureService.Notice notice : notices) {
                body.addView(notificationCard(notice));
                body.addView(space(10));
            }
        }
        setPage(scroll(body));
    }

    private void showControlCenter() {
        currentPage = Page.CONTROL;
        LinearLayout body = pageColumn();
        body.setPadding(dp(24), dp(24), dp(24), dp(20));
        body.addView(title("Controle"));
        body.addView(subtitle("Ações permitidas pelo Android sem root."));
        body.addView(space(18));

        body.addView(actionRow("Internet", "Wi‑Fi e rede móvel", v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) openSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            else openSettings(Settings.ACTION_WIFI_SETTINGS);
        }));
        body.addView(actionRow("Bluetooth", "Dispositivos e conexões", v -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)));
        body.addView(actionRow("Tela", "Brilho, tema e tempo limite", v -> openSettings(Settings.ACTION_DISPLAY_SETTINGS)));
        body.addView(actionRow("Som", "Volume e vibração", v -> openSettings(Settings.ACTION_SOUND_SETTINGS)));

        body.addView(space(20));
        body.addView(text("BRILHO", 11, MUTED, Typeface.BOLD));
        body.addView(space(8));

        SeekBar brightness = new SeekBar(this);
        brightness.setMax(255);
        brightness.setProgress(readBrightness());
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (Settings.System.canWrite(MainActivity.this)) {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!Settings.System.canWrite(MainActivity.this)) requestWriteSettings();
            }
        });
        body.addView(brightness, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        if (!Settings.System.canWrite(this)) {
            body.addView(smallAction("PERMITIR AJUSTE DE BRILHO", v -> requestWriteSettings()));
        }
        setPage(scroll(body));
    }

    private void showSettings() {
        currentPage = Page.SETTINGS;
        LinearLayout body = pageColumn();
        body.setPadding(dp(24), dp(24), dp(24), dp(20));
        body.addView(title("Ajustes"));
        body.addView(subtitle("Minimal Shell • versão 0.1.0"));
        body.addView(space(18));

        body.addView(actionRow("Launcher padrão", isDefaultLauncher() ? "Ativo" : "Definir Minimal Shell como tela inicial", v -> requestHomeRole()));
        body.addView(actionRow("Notificações", isNotificationAccessGranted() ? "Acesso ativo" : "Permissão necessária", v -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        body.addView(actionRow("Uso recente", hasUsageAccess() ? "Acesso ativo" : "Permissão necessária", v -> openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        body.addView(actionRow("Ajustes do sistema", Settings.System.canWrite(this) ? "Alterações permitidas" : "Permissão necessária", v -> requestWriteSettings()));
        body.addView(actionRow("Papel de parede", "Escolher pelo Android", v -> {
            Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
            startActivitySafely(intent);
        }));
        body.addView(actionRow("Configurações Android", "Abrir configurações completas do aparelho", v -> openSettings(Settings.ACTION_SETTINGS)));

        body.addView(space(24));
        TextView limitation = text("Sem root, o Android não permite substituir o SystemUI, o painel rápido nativo, a tela de bloqueio nem o Overview/Recentes real. Este build integra tudo o que pode ser feito com APIs públicas e encaminha somente as áreas protegidas.", 13, MUTED, Typeface.NORMAL);
        limitation.setLineSpacing(0, 1.3f);
        body.addView(limitation);
        setPage(scroll(body));
    }

    private View appRow(AppEntry app, boolean showPackage) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(2), dp(13), dp(2), dp(13));
        row.setBackgroundColor(BG);
        row.setOnClickListener(v -> launchApp(app.packageName));
        row.setOnLongClickListener(v -> {
            openAppInfo(app.packageName);
            return true;
        });

        TextView label = text(app.label, 21, TEXT, Typeface.NORMAL);
        row.addView(label);
        if (showPackage) {
            String secondary = app.lastUsed > 0
                    ? DateUtils.getRelativeTimeSpanString(app.lastUsed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    : app.packageName;
            row.addView(text(secondary, 11, MUTED, Typeface.NORMAL));
        }
        return row;
    }

    private View notificationCard(NotificationCaptureService.Notice notice) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(SURFACE, 18, DIVIDER));
        card.setOnClickListener(v -> openNotice(notice));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView app = text(notice.appName, 12, ACCENT, Typeface.BOLD);
        top.addView(app, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text("LIMPAR", 10, MUTED, Typeface.BOLD);
        close.setPadding(dp(8), dp(6), dp(8), dp(6));
        close.setOnClickListener(v -> {
            NotificationCaptureService.dismiss(notice.key);
            showNotifications();
        });
        top.addView(close);
        card.addView(top);

        if (!notice.title.isEmpty()) {
            card.addView(space(7));
            card.addView(text(notice.title, 17, TEXT, Typeface.BOLD));
        }
        if (!notice.text.isEmpty()) {
            card.addView(space(4));
            TextView message = text(notice.text, 14, TEXT, Typeface.NORMAL);
            message.setMaxLines(4);
            card.addView(message);
        }
        return card;
    }

    private View actionRow(String title, String detail, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(rounded(SURFACE, 16, DIVIDER));
        row.setOnClickListener(listener);
        row.addView(text(title, 17, TEXT, Typeface.NORMAL));
        row.addView(text(detail, 12, MUTED, Typeface.NORMAL));
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(0, 0, 0, dp(10));
        holder.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private View infoCard(String title, String text, String action, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(SURFACE, 18, DIVIDER));
        card.addView(text(title, 18, TEXT, Typeface.BOLD));
        card.addView(space(8));
        TextView description = text(text, 14, MUTED, Typeface.NORMAL);
        description.setLineSpacing(0, 1.2f);
        card.addView(description);
        card.addView(space(14));
        card.addView(smallAction(action, listener));
        return card;
    }

    private TextView smallAction(String label, View.OnClickListener listener) {
        TextView button = text(label, 11, ACCENT, Typeface.BOLD);
        button.setPadding(0, dp(10), 0, dp(10));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView title(String value) {
        return text(value, 31, TEXT, Typeface.NORMAL);
    }

    private TextView subtitle(String value) {
        TextView view = text(value, 13, MUTED, Typeface.NORMAL);
        view.setPadding(0, dp(4), 0, 0);
        return view;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private Space space(int dp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return space;
    }

    private LinearLayout pageColumn() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(BG);
        return body;
    }

    private ScrollView scroll(View body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void setPage(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private List<AppEntry> loadLaunchableApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        List<AppEntry> apps = new ArrayList<>();
        for (ResolveInfo info : infos) {
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName)) continue;
            CharSequence label = info.loadLabel(pm);
            apps.add(new AppEntry(label == null ? packageName : label.toString(), packageName, 0));
        }
        apps.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        return apps;
    }

    private List<AppEntry> usageSortedApps(int limit) {
        if (!hasUsageAccess()) return new ArrayList<>();
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - DateUtils.DAY_IN_MILLIS * 3, now);
        if (stats == null || stats.isEmpty()) return new ArrayList<>();

        Map<String, Long> lastUsed = new HashMap<>();
        for (UsageStats stat : stats) {
            if (stat.getLastTimeUsed() <= 0) continue;
            lastUsed.merge(stat.getPackageName(), stat.getLastTimeUsed(), (a, b) -> Math.max(a, b));
        }

        List<AppEntry> result = new ArrayList<>();
        for (AppEntry base : launchableApps) {
            Long used = lastUsed.get(base.packageName);
            if (used != null) result.add(new AppEntry(base.label, base.packageName, used));
        }
        result.sort(Comparator.comparingLong((AppEntry a) -> a.lastUsed).reversed());
        if (result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean isNotificationAccessGranted() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        String component = new ComponentName(this, NotificationCaptureService.class).flattenToString();
        return enabled.contains(component);
    }

    private boolean isDefaultLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null && getPackageName().equals(resolveInfo.activityInfo.packageName);
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivitySafely(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME));
                } else {
                    Toast.makeText(this, "Minimal Shell já é o launcher padrão.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
        openSettings(Settings.ACTION_HOME_SETTINGS);
    }

    private void requestWriteSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
        startActivitySafely(intent);
    }

    private int readBrightness() {
        try {
            return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException ignored) {
            return 128;
        }
    }

    private void launchApp(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Toast.makeText(this, "Não foi possível abrir o aplicativo.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivitySafely(intent);
    }

    private void openAppInfo(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
        startActivitySafely(intent);
    }

    private void openNotice(NotificationCaptureService.Notice notice) {
        try {
            if (notice.contentIntent != null) {
                notice.contentIntent.send();
                return;
            }
        } catch (PendingIntent.CanceledException ignored) {
        }
        launchApp(notice.packageName);
    }

    private void openSettings(String action) {
        startActivitySafely(new Intent(action));
    }

    private void startActivitySafely(Intent intent) {
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Ação indisponível neste aparelho.", Toast.LENGTH_SHORT).show();
        }
    }

    private void registerNotificationReceiver() {
        IntentFilter filter = new IntentFilter(NotificationCaptureService.ACTION_NOTIFICATIONS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationReceiver, filter);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final long lastUsed;

        AppEntry(String label, String packageName, long lastUsed) {
            this.label = label;
            this.packageName = packageName;
            this.lastUsed = lastUsed;
        }
    }
}
