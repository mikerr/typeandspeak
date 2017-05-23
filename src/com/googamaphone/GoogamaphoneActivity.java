
package com.googamaphone;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.view.Menu;
import android.view.MenuItem;

import com.googamaphone.typeandspeak.R;

public class GoogamaphoneActivity extends Activity {
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, OPTION_CONTACT, Menu.NONE, R.string.contact_dev).setIcon(
                R.drawable.ic_menu_send_feedback);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTION_CONTACT:
                contactDeveloper();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static final int OPTION_CONTACT = 100;

    protected void contactDeveloper() {
        String appVersion = "unknown";
        String appPackage = "unknown";
        final String phoneModel = android.os.Build.MODEL;
        final String osVersion = android.os.Build.VERSION.RELEASE;

        try {
            final PackageManager pm = getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            appVersion = pi.versionName;
            appPackage = pi.packageName;
        } catch (final NameNotFoundException e) {
            e.printStackTrace();
        }

        final String appName = getString(R.string.app_name);
        final String contactDev = getString(R.string.contact_dev);
        final String contactEmail = getString(R.string.contact_email);
        final String subject = getString(R.string.contact_subject, appName);
        final String body = getString(R.string.contact_body, appName, appPackage, appVersion,
                phoneModel, osVersion);

        final Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {
            contactEmail
        });
        sendIntent.putExtra(Intent.EXTRA_TEXT, body);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        sendIntent.setType("plain/text");

        startActivity(Intent.createChooser(sendIntent, contactDev));
    }
}
