package com.samstenner.instantunlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ExpandableListActivity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import java.io.File;

public class UnlockInterface extends AppCompatActivity {

    private Switch switchEnabled;
    private Switch switchFast;
    private RadioGroup radioGroup;
    private CheckBox boxMusic;
    private CheckBox boxDyanmic;
    private CheckBox boxStatic;
    private CheckBox[] boxNotifsGroup;
    private Switch switchSensitive;
    private CheckBox boxVibrate;
    private SeekBar skVibDuration;
    private Switch switchDelayNotifs;
    private Spinner spinnerDelay;
    private Switch switchHide;

    private boolean darkTheme;
    private boolean isPixel;
    private String radioTag;
    private String prefFile;
    private String manufacturer;
    private String model;
    private FloatingActionButton fabTwitter;
    private SwipeRefreshLayout swipeRefresh;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefFile = getString(R.string.pref_file);
        radioTag = "FORCE";
        manufacturer = Build.MANUFACTURER;
        model = Build.MODEL;
        makeTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        updateInterface();

    }

    private void updateInterface() {

        radioGroup = (RadioGroup) findViewById(R.id.radioGrpAgro);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
                radioTag = (findViewById(radioGroup.getCheckedRadioButtonId())).getTag().toString();
                updatePrefs("mode", radioTag);
                if (radioTag.equals("FORCE")){
                    setExceptions(false);
                } else {
                    setExceptions(true);
                }
            }
        });

        switchEnabled = (Switch) findViewById(R.id.switchEnabled);
        switchEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updatePrefs("enabled", isChecked);
                setExceptions(isChecked);
                setMain(isChecked);
            }
        });

        switchFast = (Switch) findViewById(R.id.switchFast);
        switchFast.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                updatePrefs("fast", isChecked);
            }
        });

        switchSensitive = (Switch) findViewById(R.id.switchSensitive);
        switchSensitive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                updatePrefs("sensitive", isChecked);
            }
        });

        boxMusic = (CheckBox) findViewById(R.id.boxMusic);
        boxMusic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updatePrefs("music", isChecked);

            }
        });

        boxDyanmic = (CheckBox) findViewById(R.id.boxDismiss);
        boxDyanmic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updatePrefs("dynamic", isChecked);
            }
        });

        boxStatic = (CheckBox) findViewById(R.id.boxNonDismiss);
        boxStatic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updatePrefs("static", isChecked);
            }
        });

        boxNotifsGroup = new CheckBox[] { boxMusic, boxDyanmic, boxStatic };

        spinnerDelay = (Spinner) findViewById(R.id.spinnerDelay);
        spinnerDelay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePrefs("delay", position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        boxVibrate = (CheckBox) findViewById(R.id.boxVibrate);
        boxVibrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updatePrefs("vibrate", isChecked);
                skVibDuration.setEnabled(isChecked);
            }
        });

        skVibDuration = (SeekBar) findViewById(R.id.skVibDuration);
        skVibDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updatePrefs("vib_duration", seekBar.getProgress());
                Toast.makeText(getApplicationContext(), seekBar.getProgress() + "ms", Toast.LENGTH_SHORT).show();
            }
        });

        switchDelayNotifs = (Switch) findViewById(R.id.switchDelayNotifs);
        switchDelayNotifs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                updatePrefs("delay_notifs", isChecked);
            }
        });

        fabTwitter = (FloatingActionButton) findViewById(R.id.fabTwitter);
        fabTwitter.setVisibility(View.GONE);
       /*   fabTwitter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWebsite(R.string.twitter);
            }
        }); */

        swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(isPixel ? R.color.colorPixelBlue : R.color.colorTeal);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateInterface();
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getApplicationContext(), "Refreshed preferences from file!", Toast.LENGTH_SHORT).show();
            }
        });


        drawerLayout = (DrawerLayout) findViewById(R.id.layoutDrawer);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        setTitle(R.string.app_name);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setSubtitle("by " + getString(R.string.app_developer));
        navigationView = (NavigationView) findViewById(R.id.nav_menu);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener(){
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()){
                    case R.id.nav_help:
                        createDialog("Help", "OK", getString(R.string.info));
                        break;
                    case R.id.nav_xda:
                        openWebsite(R.string.xda_thread);
                        break;
                    case R.id.nav_theme:
                        updatePrefs("dark", !darkTheme);
                        Intent intent = getIntent();
                        finish();
                        startActivity(intent);
                        break;
                    case R.id.nav_reset:
                        updatePrefs("reset", null);
                        Toast.makeText(getApplicationContext(), getString(R.string.settings_reset), Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.nav_donate:
                        openWebsite(R.string.donate);
                        break;
                    case R.id.nav_code:
                        openWebsite(R.string.github);
                        break;
                    case R.id.nav_website:
                        openWebsite(R.string.website);
                        break;
                    case R.id.nav_device:
                        String deviceInfo = getDeviceInfo();
                        createDialog("Device Information", "OK", deviceInfo);
                        break;
                    case R.id.nav_twitter:
                        openWebsite(R.string.twitter);
                        break;
                    case R.id.nav_hide:
                        ((Switch)menuItem.getActionView()).toggle();
                        break;
                 }
                drawerLayout.closeDrawers();
                return true;
            }
        });

        handleHidingApp();

        readPrefs();
        checkStorage();
    }

    private void setExceptions(boolean isEnabled){
        if (radioTag.equals("FORCE")) {
            isEnabled = false;
        }
        for (CheckBox box : boxNotifsGroup){
            box.setEnabled(isEnabled);
        }
        switchSensitive.setEnabled(isEnabled);
        switchDelayNotifs.setEnabled(isEnabled);
    }

    private void setMain(boolean isEnabled){
        for (int i = 0; i < radioGroup.getChildCount(); i++){
            (radioGroup.getChildAt(i)).setEnabled(isEnabled);
        }
        switchFast.setEnabled(isEnabled);
        radioGroup.setEnabled(isEnabled);
        boxVibrate.setEnabled(isEnabled);
        skVibDuration.setEnabled(isEnabled);
        spinnerDelay.setEnabled(isEnabled);
        if (!isEnabled && !switchSensitive.isEnabled()) {
           switchSensitive.setEnabled(true);
        }
    }

    private void readPrefs(){
        SharedPreferences prefs = getSharedPreferences(prefFile, Context.MODE_PRIVATE);
        switchEnabled.setChecked(prefs.getBoolean("enabled", true));
        switchFast.setChecked(prefs.getBoolean("fast", true));
        switchSensitive.setChecked(prefs.getBoolean("sensitive", false));
        switchDelayNotifs.setChecked(prefs.getBoolean("delay_notifs", true));
        boxVibrate.setChecked(prefs.getBoolean("vibrate", false));
        skVibDuration.setProgress(prefs.getInt("vib_duration", 120));
        spinnerDelay.setSelection(prefs.getInt("delay", 0));
        switchHide.setChecked(prefs.getBoolean("hidden", false));
        if (!boxVibrate.isChecked()) skVibDuration.setEnabled(false);
        for (int i = 0; i < radioGroup.getChildCount(); i++){
            RadioButton radioBtn = (RadioButton) radioGroup.getChildAt(i);
            if (radioBtn.getTag().equals(prefs.getString("mode", "FORCE"))){
                radioBtn.setChecked(true);
            }
        }
        boxMusic.setChecked(prefs.getBoolean("music", false));
        boxDyanmic.setChecked(prefs.getBoolean("dynamic", true));
        boxStatic.setChecked(prefs.getBoolean("static", false));
    }

    private void updatePrefs(String pref, Object value) {
        SharedPreferences prefs = getSharedPreferences(prefFile, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (pref.equals("reset")){
            editor.clear();
            editor.commit();
            readPrefs();
        } else {
            switch (value.getClass().getSimpleName()) {
                case "String":
                    editor.putString(pref, (String) value);
                    break;
                case "Boolean":
                    editor.putBoolean(pref, (Boolean) value);
                    break;
                case "Integer":
                    editor.putInt(pref, (Integer) value);
                    break;
            }
        }
        editor.commit();
        String dir = getBaseContext().getApplicationInfo().dataDir + "/shared_prefs/" + prefFile + ".xml";
        //String dir = getDataDir().toString() + "/shared_prefs/" + prefFile + ".xml";
        File prefsFile = new File(dir);
        if (prefsFile.exists()) {
            prefsFile.setWritable(true, false);
            prefsFile.setReadable(true, false);
        }
    }

    private void makeTheme(){
        SharedPreferences prefs = getSharedPreferences(prefFile, Context.MODE_PRIVATE);
        boolean isGoogle = manufacturer.toLowerCase().contains("google") ? true : false
                        || model.toLowerCase().contains("google") ? true : false;
        isPixel = (model.toLowerCase().contains("pixel") ? true : false) && isGoogle;
        if (prefs.getBoolean("dark", true)) {
            darkTheme = true;
            if (isPixel) {
                setTheme(R.style.AppTheme_Dark_Pixel);
            } else {
                setTheme(R.style.AppTheme_Dark);
            }
        } else {
            darkTheme = false;
            if (isPixel) {
                setTheme(R.style.AppTheme_Light_Pixel);
            } else {
                setTheme(R.style.AppTheme_Light);
            }
        }

    }

    private void checkStorage(){
        ApplicationInfo io = getApplicationInfo();
        if (!io.sourceDir.startsWith("/data")) {
            new AlertDialog.Builder(this)
                    .setTitle("Bad Installation")
                    .setMessage(getString(R.string.sd_card_error))
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finishAndRemoveTask();
                        }
                    }).setCancelable(false).show();
        }
        try {
            String directory = getBaseContext().getApplicationInfo().dataDir.toString();
            File main = new File(directory);
            main.setReadable(true, false);
            main.setWritable(true, false);
            main.setExecutable(true, false);
            File sub = new File(directory + "/shared_prefs");
            sub.setReadable(true, false);
            sub.setWritable(true, false);
            sub.setExecutable(true, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createDialog(String title, String button, String message){
        int theme = darkTheme ? android.R.style.Theme_Material_Dialog : android.R.style.Theme_Material_Light_Dialog;
        AlertDialog.Builder builderHelp = new AlertDialog.Builder(UnlockInterface.this, theme);
        builderHelp.setTitle(title)
                .setMessage(message)
                .setPositiveButton(button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).show();
    }

    private void openWebsite(int link) {
        Intent browserXDA = new Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(link)));
        startActivity(browserXDA);
    }

    private String getDeviceInfo() {
        try {
            Integer build = Build.VERSION.SDK_INT;
            String IFUVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            String androidVersion = "";
            String buildVersion = Build.VERSION.RELEASE;
            if (build >= 26)
                androidVersion += buildVersion + " - Oreo";
            else if (build >= 24)
                androidVersion += buildVersion + " - Nougat";
            else if (build == 23)
                androidVersion += buildVersion + " - Marshmallow";
            else if (build >= 21)
                androidVersion += buildVersion + " - Lollipop";
            else {
                androidVersion += buildVersion + " - THIS VERSION IS NOT SUPPORTED!";
            }
            String allInfo = "IFU Version: " + IFUVersion + "\n" +
                    "Android Version: " + androidVersion + "\n" +
                    "Manufacturer: " + manufacturer + "\n" +
                    "Model: " + model;
            return allInfo;
        } catch (Exception e) {
            return "Unable to retrieve device information!";
        }
    }

    private void handleHidingApp(){
        switchHide = new Switch(getApplicationContext());
        navigationView.getMenu().findItem(R.id.nav_hide).setActionView(switchHide);
        switchHide.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                PackageManager packageManager = getPackageManager();
                int state = isChecked ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                ComponentName aliasName = new ComponentName(getApplicationContext(), "com.samstenner.instantunlock.UnlockInterfaceAlias");
                packageManager.setComponentEnabledSetting(aliasName, state, PackageManager.DONT_KILL_APP);
                updatePrefs("hidden", isChecked);
                Toast.makeText(getApplicationContext(), "Launcher will update in a few seconds...", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
