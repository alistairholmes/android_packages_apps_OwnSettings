package com.own.settings.javas;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.own.settings.javas.CustomDialogPref;
import com.android.settings.R;

import cyanogenmod.providers.CMSettings;

import com.own.settings.button.tabs.NavigationButton;

public class ButtonBacklightBrightness extends CustomDialogPref<AlertDialog> implements
        SeekBar.OnSeekBarChangeListener {
    private static final int DEFAULT_BUTTON_TIMEOUT = 5;

    public static final String KEY_BUTTON_BACKLIGHT = "pre_navbar_button_backlight";

    private BrightnessControl mButtonBrightness;
    private BrightnessControl mKeyboardBrightness;
    private BrightnessControl mActiveControl;

    private ViewGroup mTimeoutContainer;
    private SeekBar mTimeoutBar;
    private TextView mTimeoutValue;

    private ContentResolver mResolver;

    public ButtonBacklightBrightness(Context context, AttributeSet attrs) {
        super(context, attrs);

        mResolver = context.getContentResolver();

        setDialogLayoutResource(R.layout.button_backlight);

        if (isKeyboardSupported()) {
            mKeyboardBrightness = new BrightnessControl(
                    CMSettings.Secure.KEYBOARD_BRIGHTNESS, false);
            mActiveControl = mKeyboardBrightness;
        }
        if (isButtonSupported()) {
            boolean isSingleValue = !context.getResources().getBoolean(
                    com.android.internal.R.bool.config_deviceHasVariableButtonBrightness);

            int defaultBrightness = context.getResources().getInteger(
                    com.android.internal.R.integer.config_buttonBrightnessSettingDefault);

            mButtonBrightness = new BrightnessControl(
                    CMSettings.Secure.BUTTON_BRIGHTNESS, isSingleValue, defaultBrightness);
            mActiveControl = mButtonBrightness;
        }

        updateSummary();
    }

    @Override
    protected void onClick(AlertDialog d, int which) {
        super.onClick(d, which);

        updateBrightnessPreview();
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
        super.onPrepareDialogBuilder(builder, listener);
        builder.setNeutralButton(R.string.reset,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    @Override
    protected boolean onDismissDialog(AlertDialog dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            mTimeoutBar.setProgress(DEFAULT_BUTTON_TIMEOUT);
            if (mButtonBrightness != null) {
                mButtonBrightness.reset();
            }
            if (mKeyboardBrightness != null) {
                mKeyboardBrightness.reset();
            }
            return false;
        }
        return true;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mTimeoutContainer = (ViewGroup) view.findViewById(R.id.timeout_container);
        mTimeoutBar = (SeekBar) view.findViewById(R.id.timeout_seekbar);
        mTimeoutValue = (TextView) view.findViewById(R.id.timeout_value);
        mTimeoutBar.setMax(30);
        mTimeoutBar.setOnSeekBarChangeListener(this);
        mTimeoutBar.setProgress(getTimeout());
        handleTimeoutUpdate(mTimeoutBar.getProgress());

        ViewGroup buttonContainer = (ViewGroup) view.findViewById(R.id.button_container);
        if (mButtonBrightness != null) {
            mButtonBrightness.init(buttonContainer);
        } else {
            buttonContainer.setVisibility(View.GONE);
            mTimeoutContainer.setVisibility(View.GONE);
        }

        ViewGroup keyboardContainer = (ViewGroup) view.findViewById(R.id.keyboard_container);
        if (mKeyboardBrightness != null) {
            mKeyboardBrightness.init(keyboardContainer);
        } else {
            keyboardContainer.setVisibility(View.GONE);
        }

        if (mButtonBrightness == null || mKeyboardBrightness == null) {
            view.findViewById(R.id.button_keyboard_divider).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (!positiveResult) {
            return;
        }

        if (mButtonBrightness != null) {
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .edit()
                    .putInt(KEY_BUTTON_BACKLIGHT, mButtonBrightness.getBrightness(false))
                    .apply();
        }

        applyTimeout(mTimeoutBar.getProgress());
        if (mButtonBrightness != null) {
            mButtonBrightness.applyBrightness();
        }
        if (mKeyboardBrightness != null) {
            mKeyboardBrightness.applyBrightness();
        }

        updateSummary();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (getDialog() == null || !getDialog().isShowing()) {
            return superState;
        }

        // Save the dialog state
        final SavedState myState = new SavedState(superState);
        myState.timeout = mTimeoutBar.getProgress();
        if (mButtonBrightness != null) {
            myState.button = mButtonBrightness.getBrightness(false);
        }
        if (mKeyboardBrightness != null) {
            myState.keyboard = mKeyboardBrightness.getBrightness(false);
        }

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());

        mTimeoutBar.setProgress(myState.timeout);
        if (mButtonBrightness != null) {
            mButtonBrightness.setBrightness(myState.button);
        }
        if (mKeyboardBrightness != null) {
            mKeyboardBrightness.setBrightness(myState.keyboard);
        }
    }

    public boolean isButtonSupported() {
        final Resources res = getContext().getResources();
        final int deviceKeys = res.getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);
        // All hardware keys besides volume and camera can possibly have a backlight
        boolean hasBacklightKey = (deviceKeys & NavigationButton.KEY_MASK_HOME) != 0
                || (deviceKeys & NavigationButton.KEY_MASK_BACK) != 0
                || (deviceKeys & NavigationButton.KEY_MASK_MENU) != 0
                || (deviceKeys & NavigationButton.KEY_MASK_ASSIST) != 0
                || (deviceKeys & NavigationButton.KEY_MASK_APP_SWITCH) != 0;
        boolean hasBacklight = res.getInteger(
                com.android.internal.R.integer.config_buttonBrightnessSettingDefault) > 0;

        return hasBacklightKey && hasBacklight;
    }

    public boolean isKeyboardSupported() {
        return getContext().getResources().getInteger(
                com.android.internal.R.integer.config_keyboardBrightnessSettingDefault) > 0;
    }

    public void updateSummary() {
        if (mButtonBrightness != null) {
            int buttonBrightness = mButtonBrightness.getBrightness(true);
            int timeout = getTimeout();

            if (buttonBrightness == 0) {
                setSummary(R.string.backlight_summary_disabled);
            } else if (timeout == 0) {
                setSummary(R.string.backlight_timeout_unlimited);
            } else {
                setSummary(getContext().getString(R.string.backlight_summary_enabled_with_timeout,
                        getTimeoutString(timeout)));
            }
        } else if (mKeyboardBrightness != null && mKeyboardBrightness.getBrightness(true) != 0) {
            setSummary(R.string.backlight_summary_enabled);
        } else {
            setSummary(R.string.backlight_summary_disabled);
        }
    }

    private String getTimeoutString(int timeout) {
        return getContext().getResources().getQuantityString(
                R.plurals.backlight_timeout_time, timeout, timeout);
    }

    private int getTimeout() {
        return CMSettings.Secure.getInt(mResolver,
                CMSettings.Secure.BUTTON_BACKLIGHT_TIMEOUT, DEFAULT_BUTTON_TIMEOUT * 1000) / 1000;
    }

    private void applyTimeout(int timeout) {
        CMSettings.Secure.putInt(mResolver,
                CMSettings.Secure.BUTTON_BACKLIGHT_TIMEOUT, timeout * 1000);
    }

    private void updateBrightnessPreview() {
        if (getDialog() == null || getDialog().getWindow() == null) {
            return;
        }
        Window window = getDialog().getWindow();
        LayoutParams params = window.getAttributes();
        if (mActiveControl != null) {
            params.buttonBrightness = (float) mActiveControl.getBrightness(false) / 255.0f;
        } else {
            params.buttonBrightness = -1;
        }
        window.setAttributes(params);
    }

    private void updateTimeoutEnabledState() {
        int buttonBrightness = mButtonBrightness != null
                ? mButtonBrightness.getBrightness(false) : 0;
        int count = mTimeoutContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            mTimeoutContainer.getChildAt(i).setEnabled(buttonBrightness != 0);
        }
    }

    private void handleTimeoutUpdate(int timeout) {
        if (timeout == 0) {
            mTimeoutValue.setText(R.string.backlight_timeout_unlimited);
        } else {
            mTimeoutValue.setText(getTimeoutString(timeout));
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        handleTimeoutUpdate(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Do nothing here
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Do nothing here
    }

    private static class SavedState extends BaseSavedState {
        int timeout;
        int button;
        int keyboard;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            timeout = source.readInt();
            button = source.readInt();
            keyboard = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(timeout);
            dest.writeInt(button);
            dest.writeInt(keyboard);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private class BrightnessControl implements
            SeekBar.OnSeekBarChangeListener, CheckBox.OnCheckedChangeListener {
        private String mSetting;
        private boolean mIsSingleValue;
        private int mDefaultBrightness;
        private CheckBox mCheckBox;
        private SeekBar mSeekBar;
        private TextView mValue;

        public BrightnessControl(String setting, boolean singleValue, int defaultBrightness) {
            mSetting = setting;
            mIsSingleValue = singleValue;
            mDefaultBrightness = defaultBrightness;
        }

        public BrightnessControl(String setting, boolean singleValue) {
            this(setting, singleValue, 255);
        }

        public void init(ViewGroup container) {
            int brightness = getBrightness(true);

            if (mIsSingleValue) {
                container.findViewById(R.id.seekbar_container).setVisibility(View.GONE);
                mCheckBox = (CheckBox) container.findViewById(R.id.backlight_switch);
                mCheckBox.setChecked(brightness != 0);
                mCheckBox.setOnCheckedChangeListener(this);
            } else {
                container.findViewById(R.id.checkbox_container).setVisibility(View.GONE);
                mSeekBar = (SeekBar) container.findViewById(R.id.seekbar);
                mValue = (TextView) container.findViewById(R.id.value);

                mSeekBar.setMax(255);
                mSeekBar.setProgress(brightness);
                mSeekBar.setOnSeekBarChangeListener(this);
            }

            handleBrightnessUpdate(brightness);
        }

        public int getBrightness(boolean persisted) {
            if (mCheckBox != null && !persisted) {
                return mCheckBox.isChecked() ? mDefaultBrightness : 0;
            } else if (mSeekBar != null && !persisted) {
                return mSeekBar.getProgress();
            }
            return CMSettings.Secure.getInt(mResolver, mSetting, mDefaultBrightness);
        }

        public void applyBrightness() {
            CMSettings.Secure.putInt(mResolver, mSetting, getBrightness(false));
        }

        /* Behaviors when it's a seekbar */
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            handleBrightnessUpdate(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mActiveControl = this;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // Do nothing here
        }

        /* Behaviors when it's a plain checkbox */
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mActiveControl = this;
            updateBrightnessPreview();
            updateTimeoutEnabledState();
        }

        public void setBrightness(int value) {
            if (mIsSingleValue) {
                mCheckBox.setChecked(value != 0);
            } else {
                mSeekBar.setProgress(value);
            }
        }

        public void reset() {
            setBrightness(mDefaultBrightness);
        }

        private void handleBrightnessUpdate(int brightness) {
            updateBrightnessPreview();
            if (mValue != null) {
                mValue.setText(String.format("%d%%", (int)((brightness * 100) / 255)));
            }
            updateTimeoutEnabledState();
        }
    }
}
