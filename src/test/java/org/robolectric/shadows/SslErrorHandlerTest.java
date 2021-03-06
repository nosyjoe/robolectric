package org.robolectric.shadows;

import org.robolectric.TestRunners;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.webkit.SslErrorHandler;

import org.robolectric.Robolectric;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;


@RunWith(TestRunners.WithDefaults.class)
public class SslErrorHandlerTest {

    private SslErrorHandler handler;
    private ShadowSslErrorHandler shadow;

    @Before
    public void setUp() throws Exception {
        handler = Robolectric.newInstanceOf(SslErrorHandler.class);
        shadow = Robolectric.shadowOf(handler);
    }

    @Test
    public void shouldInheritFromShadowHandler() {
        assertThat(shadow, instanceOf(ShadowHandler.class));
    }

    @Test
    public void shouldRecordCancel() {
        assertThat(shadow.wasCancelCalled(), equalTo(false));
        handler.cancel();
        assertThat(shadow.wasCancelCalled(), equalTo(true));
    }

    @Test
    public void shouldRecordProceed() {
        assertThat(shadow.wasProceedCalled(), equalTo(false));
        handler.proceed();
        assertThat(shadow.wasProceedCalled(), equalTo(true));
    }
}
