package com.xtremelabs.robolectric.shadows;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import org.robolectric.internal.Implementation;
import org.robolectric.internal.Implements;
import org.robolectric.shadows.ShadowFrameLayout;

@Implements(AppWidgetHostView.class)
public class ShadowAppWidgetHostView extends ShadowFrameLayout {

    private int appWidgetId;
    private AppWidgetProviderInfo appWidgetInfo;
    private AppWidgetHost host;

    @Implementation
    public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
        this.appWidgetId = appWidgetId;
        this.appWidgetInfo = info;
    }

    @Implementation
    public int getAppWidgetId() {
        return appWidgetId;
    }

    @Implementation
    public AppWidgetProviderInfo getAppWidgetInfo() {
        return appWidgetInfo;
    }

    public AppWidgetHost getHost() {
        return host;
    }

    public void setHost(AppWidgetHost host) {
        this.host = host;
    }
}
