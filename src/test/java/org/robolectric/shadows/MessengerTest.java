package org.robolectric.shadows;

import static org.junit.Assert.*;

import org.robolectric.TestRunners;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.os.Handler;
import android.os.Message;
import android.os.Messenger;

import static org.robolectric.Robolectric.shadowOf;

@RunWith(TestRunners.WithDefaults.class)
public class MessengerTest {

    @Test
    public void testMessengerSend() throws Exception {
        Handler handler = new Handler();
        Messenger messenger = new Messenger(handler);

        ShadowLooper.pauseMainLooper();
        Message msg = Message.obtain(null, 123);
        messenger.send(msg);

        assertTrue(handler.hasMessages(123));
        ShadowHandler.runMainLooperOneTask();
        assertFalse(handler.hasMessages(123));
    }
}
