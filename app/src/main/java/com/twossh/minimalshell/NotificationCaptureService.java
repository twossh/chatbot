package com.twossh.minimalshell;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationCaptureService extends NotificationListenerService {
    public static final String ACTION_NOTIFICATIONS_CHANGED = "com.twossh.minimalshell.NOTIFICATIONS_CHANGED";

    private static final Map<String, Notice> NOTICES = new ConcurrentHashMap<>();
    private static volatile NotificationCaptureService instance;

    @Override
    public void onListenerConnected() {
        instance = this;
        syncActiveNotifications();
        sendChangedBroadcast();
    }

    @Override
    public void onListenerDisconnected() {
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        NOTICES.put(sbn.getKey(), toNotice(sbn));
        sendChangedBroadcast();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        NOTICES.remove(sbn.getKey());
        sendChangedBroadcast();
    }

    private void syncActiveNotifications() {
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return;
        NOTICES.clear();
        for (StatusBarNotification sbn : active) {
            NOTICES.put(sbn.getKey(), toNotice(sbn));
        }
    }

    private Notice toNotice(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String title = safeText(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = safeText(extras.getCharSequence(Notification.EXTRA_TEXT));
        String packageName = sbn.getPackageName();
        String appName = resolveAppName(packageName);
        String channelId = notification.getChannelId();
        PendingIntent contentIntent = notification.contentIntent;
        return new Notice(sbn.getKey(), packageName, appName, title, text, channelId, sbn.getPostTime(), contentIntent);
    }

    private String resolveAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }

    private static String safeText(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private void sendChangedBroadcast() {
        sendBroadcast(new Intent(ACTION_NOTIFICATIONS_CHANGED).setPackage(getPackageName()));
    }

    public static List<Notice> snapshot() {
        List<Notice> list = new ArrayList<>(NOTICES.values());
        list.sort(Comparator.comparingLong((Notice n) -> n.postTime).reversed());
        return list;
    }

    public static boolean dismiss(String key) {
        NotificationCaptureService current = instance;
        if (current == null || key == null) return false;
        current.cancelNotification(key);
        return true;
    }

    public static final class Notice {
        public final String key;
        public final String packageName;
        public final String appName;
        public final String title;
        public final String text;
        public final String channelId;
        public final long postTime;
        public final PendingIntent contentIntent;

        Notice(String key, String packageName, String appName, String title, String text, String channelId, long postTime, PendingIntent contentIntent) {
            this.key = key;
            this.packageName = packageName;
            this.appName = appName;
            this.title = title;
            this.text = text;
            this.channelId = channelId == null ? "" : channelId;
            this.postTime = postTime;
            this.contentIntent = contentIntent;
        }
    }
}
