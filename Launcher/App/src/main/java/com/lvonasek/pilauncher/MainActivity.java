package com.lvonasek.pilauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.esafirm.imagepicker.features.ImagePicker;
import com.esafirm.imagepicker.model.Image;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity
{
    private static final String CUSTOM_THEME = "theme.png";
    private static final boolean DEFAULT_NAMES = true;
    private static final int DEFAULT_OPACITY = 7;
    private static final int DEFAULT_SCALE = 2;
    private static final int DEFAULT_THEME = 0;
    public static final int PICK_ICON_CODE = 450;
    public static final int PICK_THEME_CODE = 95;

    private static final int[] SCALES = {60, 77, 101, 141, 219};
    private static final int[] THEMES = {
            R.drawable.bkg_default,
            R.drawable.bkg_glass,
            R.drawable.bkg_rgb,
            R.drawable.bkg_skin,
            R.drawable.bkg_underwater
    };
    private static ImageView[] mTempViews;

    private GridView mAppGrid;
    private ImageView mBackground;
    private ListView mGroupPanel;
    private LinearLayout mSidePanel;
    private TextView mSlide;

    private static MainActivity instance = null;
    private SharedPreferences mPreferences;
    private SettingsProvider mSettings;

    public static void reset(Context context) {
        try {
            if (instance != null) {
                instance.finish();
                instance = null;
            }
            context.startActivity(context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        mSettings = SettingsProvider.getInstance(this);
        ButtonManager.restoreVolume(this, false);
        instance = this;

        // Get UI instances
        mAppGrid = findViewById(R.id.gridview);
        mBackground = findViewById(R.id.background);
        mGroupPanel = findViewById(R.id.listView);
        mSidePanel = findViewById(R.id.sidePanel);

        // Handle group click listener
        mGroupPanel.setOnItemClickListener((parent, view, position, id) -> {
            List<String> groups = mSettings.getAppGroupsSorted(false);
            if (position == groups.size()) {
                mSettings.selectGroup(GroupsAdapter.HIDDEN_GROUP);
            } else if (position == groups.size() + 1) {
                mSettings.selectGroup(mSettings.addGroup());
            } else {
                mSettings.selectGroup(groups.get(position));
            }
            reloadUI();
        });

        // Multiple group selection
        mGroupPanel.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!mPreferences.getBoolean(SettingsProvider.KEY_EDITMODE, false)) {
                List<String> groups = mSettings.getAppGroupsSorted(false);
                Set<String> selected = mSettings.getSelectedGroups();

                String item = groups.get(position);
                if (selected.contains(item)) {
                    selected.remove(item);
                } else {
                    selected.add(item);
                }
                if (selected.isEmpty()) {
                    selected.add(groups.get(0));
                }
                mSettings.setSelectedGroups(selected);
                reloadUI();
            }
            return true;
        });

        // Set pi button
        findViewById(R.id.pi).setOnClickListener(view -> showSettingsMain());

        // Set slide button
        mSlide = findViewById(R.id.slide);
        mSlide.setOnClickListener(view -> {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putBoolean(SettingsProvider.KEY_SIDEBAR, !mPreferences.getBoolean(SettingsProvider.KEY_SIDEBAR, true));
            editor.commit();
            reloadUI();
        });
    }

    @Override
    public void onBackPressed() {
        showSettingsMain();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadUI();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean output = super.onKeyUp(keyCode, event);;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ButtonManager.restoreVolume(this, true);
        }
        return output;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_ICON_CODE) {
            if (resultCode == RESULT_OK) {
                for (Image image : ImagePicker.getImages(data)) {
                    ((AppsAdapter)mAppGrid.getAdapter()).onImageSelected(image.getPath());
                    break;
                }
            } else {
                ((AppsAdapter)mAppGrid.getAdapter()).onImageSelected(null);
            }
        } else if (requestCode == PICK_THEME_CODE) {
            if (resultCode == RESULT_OK) {

                for (Image image : ImagePicker.getImages(data)) {
                    Bitmap bitmap = ImageUtils.getResizedBitmap(BitmapFactory.decodeFile(image.getPath()), 1280);
                    ImageUtils.saveBitmap(bitmap, new File(getApplicationInfo().dataDir, CUSTOM_THEME));
                    setTheme(mTempViews, THEMES.length);
                    reloadUI();
                    break;
                }
            }
        }
    }

    public String getSelectedPackage() {
        return ((AppsAdapter)mAppGrid.getAdapter()).getSelectedPackage();
    }

    public void reloadUI() {

        // set sidepanel
        boolean editMode = mPreferences.getBoolean(SettingsProvider.KEY_EDITMODE, false);
        if (editMode) {
            mSidePanel.setVisibility(View.VISIBLE);
            mSlide.setVisibility(View.GONE);
        } else {
            mSlide.setVisibility(View.VISIBLE);

            boolean visible = mPreferences.getBoolean(SettingsProvider.KEY_SIDEBAR, true);
            mSidePanel.setVisibility(visible ? View.VISIBLE : View.GONE);
            mSlide.setText(visible ? "\u00ab" : "\u00bb");
        }

        // set customization
        boolean names = mPreferences.getBoolean(SettingsProvider.KEY_CUSTOM_NAMES, DEFAULT_NAMES);
        int opacity = mPreferences.getInt(SettingsProvider.KEY_CUSTOM_OPACITY, DEFAULT_OPACITY);
        int theme = mPreferences.getInt(SettingsProvider.KEY_CUSTOM_THEME, DEFAULT_THEME);
        int scale = getPixelFromDip(SCALES[mPreferences.getInt(SettingsProvider.KEY_CUSTOM_SCALE, DEFAULT_SCALE)]);
        mAppGrid.setColumnWidth(scale);
        if (theme < THEMES.length) {
            Drawable d = getDrawable(THEMES[theme]);
            d.setAlpha(255 * opacity / 10);
            mBackground.setImageDrawable(d);
        } else {
            File file = new File(getApplicationInfo().dataDir, CUSTOM_THEME);
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            Drawable d = new BitmapDrawable(getResources(), bitmap);
            d.setAlpha(255 * opacity / 10);
            mBackground.setImageDrawable(d);
        }

        // set context
        scale += getPixelFromDip(8);
        mAppGrid.setAdapter(new AppsAdapter(this, editMode, scale, names));
        mGroupPanel.setAdapter(new GroupsAdapter(this, editMode));
    }

    public void setTheme(ImageView[] views, int index) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(SettingsProvider.KEY_CUSTOM_THEME, index);
        editor.commit();
        reloadUI();

        for (ImageView image : views) {
            image.setBackgroundColor(Color.TRANSPARENT);
            image.setAlpha(255);
        }
        views[index].setBackgroundColor(Color.WHITE);
        views[index].setAlpha(255 * mPreferences.getInt(SettingsProvider.KEY_CUSTOM_OPACITY, DEFAULT_OPACITY) / 10);
    }

    public Dialog showPopup(int layout) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        AlertDialog dialog = builder.create();
        dialog.show();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = 660;
        lp.height = 480;
        dialog.getWindow().setAttributes(lp);
        dialog.findViewById(R.id.layout).requestLayout();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bkg_dialog);
        return dialog;
    }

    private void showSettingsMain() {

        Dialog dialog = showPopup(R.layout.dialog_settings);
        SettingsGroup apps = dialog.findViewById(R.id.settings_apps);
        boolean editMode = !mPreferences.getBoolean(SettingsProvider.KEY_EDITMODE, false);
        apps.setIcon(editMode ? R.drawable.ic_editing_on : R.drawable.ic_editing_off);
        apps.setText(getString(editMode ? R.string.settings_apps_enable : R.string.settings_apps_disable));
        apps.setOnClickListener(view1 -> {
            ArrayList<String> selected = mSettings.getAppGroupsSorted(true);
            if (editMode && (selected.size() > 1)) {
                Set<String> selectFirst = new HashSet<>();
                selectFirst.add(selected.get(0));
                mSettings.setSelectedGroups(selectFirst);
            }
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putBoolean(SettingsProvider.KEY_EDITMODE, editMode);
            editor.putBoolean(SettingsProvider.KEY_SIDEBAR, true);
            editor.commit();
            reloadUI();
            dialog.dismiss();
        });

        dialog.findViewById(R.id.settings_look).setOnClickListener(view -> showSettingsLook());
        dialog.findViewById(R.id.settings_intergration).setOnClickListener(view -> showSettingsIntegration());
        dialog.findViewById(R.id.settings_device).setOnClickListener(view -> startActivityForResult(new Intent(android.provider.Settings.ACTION_SETTINGS), 0));
    }

    private void showSettingsLook() {
        Dialog d = showPopup(R.layout.dialog_look);

        CheckBox names = d.findViewById(R.id.checkbox_names);
        names.setChecked(mPreferences.getBoolean(SettingsProvider.KEY_CUSTOM_NAMES, DEFAULT_NAMES));
        names.setOnCheckedChangeListener((compoundButton, value) -> {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putBoolean(SettingsProvider.KEY_CUSTOM_NAMES, value);
            editor.commit();
            reloadUI();
        });

        SeekBar opacity = d.findViewById(R.id.bar_opacity);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                SharedPreferences.Editor editor = mPreferences.edit();
                editor.putInt(SettingsProvider.KEY_CUSTOM_OPACITY, value);
                editor.commit();
                reloadUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        opacity.setProgress(mPreferences.getInt(SettingsProvider.KEY_CUSTOM_OPACITY, DEFAULT_OPACITY));
        opacity.setMax(10);
        opacity.setMin(0);

        SeekBar scale = d.findViewById(R.id.bar_scale);
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                SharedPreferences.Editor editor = mPreferences.edit();
                editor.putInt(SettingsProvider.KEY_CUSTOM_SCALE, value);
                editor.commit();
                reloadUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        scale.setProgress(mPreferences.getInt(SettingsProvider.KEY_CUSTOM_SCALE, DEFAULT_SCALE));
        scale.setMax(SCALES.length - 1);
        scale.setMin(0);

        int theme = mPreferences.getInt(SettingsProvider.KEY_CUSTOM_THEME, DEFAULT_THEME);
        ImageView[] views = {
                d.findViewById(R.id.theme0),
                d.findViewById(R.id.theme1),
                d.findViewById(R.id.theme2),
                d.findViewById(R.id.theme3),
                d.findViewById(R.id.theme4),
                d.findViewById(R.id.theme_custom)
        };
        for (ImageView image : views) {
            image.setBackgroundColor(Color.TRANSPARENT);
            image.setAlpha(255);
        }
        views[theme].setBackgroundColor(Color.WHITE);
        views[theme].setAlpha(255 * mPreferences.getInt(SettingsProvider.KEY_CUSTOM_OPACITY, DEFAULT_OPACITY) / 10);
        for (int i = 0; i < views.length; i++) {
            int index = i;
            views[i].setOnClickListener(view12 -> {
                if (index >= THEMES.length) {
                    mTempViews = views;
                    ImageUtils.showImagePicker(this, PICK_THEME_CODE);
                } else {
                    setTheme(views, index);
                }
            });
        }
    }

    private void showSettingsIntegration() {
        Dialog d = showPopup(R.layout.dialog_integration);

        d.findViewById(R.id.button_explore).setOnClickListener(view -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:com.oculus.explore"));
            startActivity(intent);
        });

        d.findViewById(R.id.button_start).setOnClickListener(view -> {
            ButtonManager.isAccesibilityInitialized(this);
            ButtonManager.requestAccessibility(this);
        });

        CheckBox boot = d.findViewById(R.id.checkbox_boot);
        boot.setChecked(mPreferences.getBoolean(SettingsProvider.KEY_BOOT, false));
        boot.setOnCheckedChangeListener((compoundButton, value) -> {
            String[] permissions = { Manifest.permission.RECEIVE_BOOT_COMPLETED };
            boolean bootAllowed = checkSelfPermission(permissions[0]) == PackageManager.PERMISSION_GRANTED;
            boolean overlayAllowed = Settings.canDrawOverlays(getApplicationContext());
            if ((!bootAllowed || !overlayAllowed) && value) {
                if (!bootAllowed) {
                    requestPermissions(permissions, 0);
                }
                if (!overlayAllowed) {
                    Intent myIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    myIntent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivityForResult(myIntent, 0);
                }
                boot.setChecked(false);
            } else {
                SharedPreferences.Editor e = mPreferences.edit();
                e.putBoolean(SettingsProvider.KEY_BOOT, value);
                e.commit();
            }
        });
    }

    private int getPixelFromDip(int dip) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics());
    }
}
