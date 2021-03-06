package org.robolectric.shadows;

import org.robolectric.TestRunners;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.Activity;
import android.view.inputmethod.InputMethodManager;

import org.robolectric.Robolectric;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(TestRunners.WithDefaults.class)
public class InputMethodManagerTest {
	
	private InputMethodManager manager;
	private ShadowInputMethodManager shadow;
	
    @Before
    public void setUp() throws Exception {
    	manager = (InputMethodManager) Robolectric.application.getSystemService(Activity.INPUT_METHOD_SERVICE);
    	shadow = Robolectric.shadowOf(manager);
    }
    
    @Test
    public void shouldRecordSoftInputVisibility() {
    	assertThat(shadow.isSoftInputVisible(), equalTo(false));
    	
    	manager.showSoftInput(null, 0);
       	assertThat(shadow.isSoftInputVisible(), equalTo(true));
    	
    	manager.hideSoftInputFromWindow(null, 0);
       	assertThat(shadow.isSoftInputVisible(), equalTo(false));
    }   
}
