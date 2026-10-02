package com.haniokasai.app.pmmp_srv;

import android.app.Application;

/**
 * Minimal {@link Application} subclass.
 *
 * Its only job is to publish a process-wide application context in
 * {@link AppSettings.AppHolder} so that Context-free preference getters (used by
 * the download helpers running on background threads, e.g. to decide whether to
 * route a GitHub download through a proxy) can still reach SharedPreferences.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppSettings.AppHolder.ctx = getApplicationContext();
    }
}
