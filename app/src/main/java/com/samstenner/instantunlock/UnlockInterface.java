package com.samstenner.instantunlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.util.Log;
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

import java.io.File;

import de.robv.android.xposed.XposedBridge;

public class UnlockInterface extends Activity {

    private Switch switchEnabled;
    private RadioGroup radioGroup;
    private CheckBox boxMusic;
    private CheckBox boxDyanmic;
    private CheckBox boxStatic;
    private CheckBox[] boxNotifsGroup;
    private CheckBox boxVibrate;
    private int checkedCount;
    private SeekBar skVibDuration;
    private Spinner spinnerDelay;
    private boolean darkTheme;
    private String radioTag;
    private boolean spinnerTouched;
    private String prefFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        checkedCount = 0;
        radioTag = "FORCE";
        spinnerTouched = false;
        prefFile = getString(R.string.pref_file);

        makeTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        radioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
                radioTag = (findViewById(radioGroup.getCheckedRadioButtonId())).getTag().toString();
                updatePrefs("mode", radioTag);
                if (radioTag.equals("FORCE")){
                    setExceptions(false);
                } else {
                    setExceptions(true);
                    boxMusic.setChecked(false);
                    boxDyanmic.setChecked(true);
                    boxStatic.setChecked(false);
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

        boxMusic = (CheckBox) findViewById(R.id.boxMusic);
        boxMusic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                handleChecked("music", isChecked);
            }
        });

        boxDyanmic = (CheckBox) findViewById(R.id.boxDismiss);
        boxDyanmic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                handleChecked("dynamic", isChecked);
            }
        });

        boxStatic = (CheckBox) findViewById(R.id.boxNonDismiss);
        boxStatic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                handleChecked("static", isChecked);
            }
        });
        boxNotifsGroup = new CheckBox[] { boxMusic, boxDyanmic, boxStatic };

        spinnerDelay = (Spinner) findViewById(R.id.spinnerDelay);
        spinnerDelay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePrefs("delay", position);
                spinnerTouched = true;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        boxVibrate = (CheckBox) findViewById(R.id.boxVibrate);
        boxVibrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                handleChecked("vibrate", isChecked);
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

        Button btnTheme = (Button) findViewById(R.id.btnTheme);
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatePrefs("dark", !darkTheme);
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }
        });

        (findViewById(R.id.btnForum)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.xda_thread)));
                startActivity(browserIntent);
            }
        });

        (findViewById(R.id.btnInfo)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(UnlockInterface.this)
                        .setTitle("Help")
                        .setMessage(getString(R.string.info))
                        .setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        }).show();
            }
        });

        (findViewById(R.id.fabTwitter)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.twitter)));
                startActivity(browserIntent);
            }
        });

        (findViewById(R.id.fabDonate)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.donate)));
                startActivity(browserIntent);
            }
        });

        (findViewById(R.id.btnReset)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatePrefs("reset", null);
                Toast.makeText(getApplicationContext(), getString(R.string.settings_reset), Toast.LENGTH_SHORT).show();
            }
        });

        try {
            String version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            ((TextView)findViewById(R.id.lblNameVersion)).setText("Version: " + version);
        } catch (Exception e) {
            e.printStackTrace();
        }

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
    }

    private void setMain(boolean isEnabled){
        for (int i = 0; i < radioGroup.getChildCount(); i++){
            (radioGroup.getChildAt(i)).setEnabled(isEnabled);
        }
        radioGroup.setEnabled(isEnabled);
        boxVibrate.setEnabled(isEnabled);
        skVibDuration.setEnabled(isEnabled);
        spinnerDelay.setEnabled(isEnabled);
    }

    private void handleChecked(String pref, boolean isChecked){
        checkedCount = isChecked ? checkedCount + 1 : checkedCount -1;
        if (checkedCount == 0){
            ((RadioButton)radioGroup.findViewWithTag("FORCE")).setChecked(true);
        }
        updatePrefs(pref, isChecked);
    }

    private void readPrefs(){
        SharedPreferences prefs = getSharedPreferences(prefFile, Context.MODE_PRIVATE);
        spinnerDelay.setSelection(prefs.getInt("delay", 0));
        switchEnabled.setChecked((prefs.getBoolean("enabled", true)));
        boxVibrate.setChecked(prefs.getBoolean("vibrate", false));
        skVibDuration.setProgress(prefs.getInt("vib_duration", 120));
        if (!boxVibrate.isChecked()) skVibDuration.setEnabled(false);
        for (int i = 0; i < radioGroup.getChildCount(); i++){
            RadioButton radioBtn = (RadioButton) radioGroup.getChildAt(i);
            if (radioBtn.getTag().equals(prefs.getString("mode", "FORCE"))){
                radioBtn.setChecked(true);
            }
        }
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
        File prefsFile = new File(dir);
        if (prefsFile.exists()) {
            prefsFile.setWritable(true, false);
            prefsFile.setReadable(true, false);
        }

    }

    private void makeTheme(){
        SharedPreferences prefs = getSharedPreferences(prefFile, Context.MODE_PRIVATE);
        if (prefs.getBoolean("dark", true)) {
            darkTheme = true;
            setTheme(android.R.style.ThemeOverlay_Material_Dark_ActionBar);
        } else {
            darkTheme = false;
            setTheme(android.R.style.ThemeOverlay_Material_Light);
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
            String directory = getDataDir().toString();
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

}
