package br.com.rbwone.web;

import android.app.Application;

public final class RbwApplication extends Application {
    private NotificationCoordinator notifications;
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannels.ensure(this);
        notifications = new NotificationCoordinator(this);
    }
    public NotificationCoordinator notifications() { return notifications; }
}
