package org.robolectric.shadows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Application;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.TestRunners;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

@RunWith(TestRunners.WithDefaults.class)
public class AccountManagerTest {

    Application app;

    @Before
    public void setUp() throws Exception {
        app = Robolectric.application;
    }

    @Test
    public void testGet() {
        AccountManager appAM = AccountManager.get(app);
        assertThat(appAM, notNullValue());
        assertSame(AccountManager.get(app), appAM);

        Activity a = new Activity();
        assertThat(AccountManager.get(a), notNullValue());
        assertThat(AccountManager.get(a), not(sameInstance(appAM)));
    }

    @Test
    public void testGetAccounts() {
        AccountManager am = AccountManager.get(app);
        assertThat(am.getAccounts(), notNullValue());
        assertThat(am.getAccounts().length, equalTo(0));

        Account a1 = new Account("name_a", "type_a");
        Robolectric.shadowOf(am).addAccount(a1);
        assertThat(am.getAccounts(), notNullValue());
        assertThat(am.getAccounts().length, equalTo(1));
        assertThat(am.getAccounts()[0], sameInstance(a1));

        Account a2 = new Account("name_b", "type_b");
        Robolectric.shadowOf(am).addAccount(a2);
        assertThat(am.getAccounts(), notNullValue());
        assertThat(am.getAccounts().length, equalTo(2));
        assertThat(am.getAccounts()[1], sameInstance(a2));
    }

    @Test
    public void testGetAccountsByType() {
        AccountManager am = AccountManager.get(app);
        assertThat(am.getAccountsByType("name_a"), notNullValue());
        assertThat(am.getAccounts().length, equalTo(0));

        Account a1 = new Account("name_a", "type_a");
        Robolectric.shadowOf(am).addAccount(a1);
        Account[] accounts = am.getAccountsByType("type_a");
        assertThat(accounts, notNullValue());
        assertThat(accounts.length, equalTo(1));
        assertThat(accounts[0], sameInstance(a1));

        Account a2 = new Account("name_b", "type_b");
        Robolectric.shadowOf(am).addAccount(a2);
        accounts = am.getAccountsByType("type_a");
        assertThat(accounts, notNullValue());
        assertThat(accounts.length, equalTo(1));
        assertThat(accounts[0], sameInstance(a1));

        Account a3 = new Account("name_c", "type_a");
        Robolectric.shadowOf(am).addAccount(a3);
        accounts = am.getAccountsByType("type_a");
        assertThat(accounts, notNullValue());
        assertThat(accounts.length, equalTo(2));
        assertThat(accounts[0], sameInstance(a1));
        assertThat(accounts[1], sameInstance(a3));
    }

}
