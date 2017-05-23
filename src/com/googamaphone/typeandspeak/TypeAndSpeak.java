
package com.googamaphone.typeandspeak;

import com.googamaphone.GoogamaphoneActivity;
import com.googamaphone.PinnedDialog;
import com.googamaphone.PinnedDialogManager;
import com.googamaphone.compat.AudioManagerCompatUtils;
import com.googamaphone.typeandspeak.FileSynthesizer.FileSynthesizerListener;
import com.googamaphone.typeandspeak.utils.CharSequenceIterator;
import com.googamaphone.typeandspeak.utils.GranularTextToSpeech;
import com.googamaphone.typeandspeak.utils.GranularTextToSpeech.SingAlongListener;
import com.googamaphone.typeandspeak.utils.ReferencedHandler;

import de.l3s.boilerpipe.extractors.ArticleExtractor;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.provider.MediaStore.MediaColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import java.io.IOException;
import java.net.URL;
import java.text.BreakIterator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TypeAndSpeak extends GoogamaphoneActivity {
    private static final String TAG = TypeAndSpeak.class.getSimpleName();

    /** Stream to use for TTS output and volume control. */
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    // Preference keys.
    private static final String PREF_TEXT = "PREF_TEXT";
    private static final String PREF_LOCALE = "PREF_LOCALE";
    private static final String PREF_PITCH = "PREF_PITCH";
    private static final String PREF_SPEED = "PREF_SPEED";
    private static final String PREF_SPEAK_WHILE_TYPING = "PREF_SPEAK_WHILE_TYPING";
    private static final String PREF_USE_LARGER_FONT = "PREF_USE_LARGER_FONT";

    // Dialog identifiers.
    private static final int DIALOG_INSTALL_DATA = 1;
    private static final int DIALOG_CANNOT_INSTALL_DATA = 2;
    private static final int DIALOG_EXTRACTING_TEXT = 3;

    // Pinned dialog identifiers.
    private static final int PINNED_PROPERTIES = 1;
    private static final int PINNED_SAVE = 2;
    private static final int PINNED_LANGUAGES = 3;
    private static final int PINNED_NO_TEXT = 4;
    private static final int PINNED_CONFIRM_CLEAR = 5;

    // Activity request identifiers.
    private static final int REQUEST_CHECK_DATA = 1;
    private static final int REQUEST_INSTALL_DATA = 2;

    private static final float DEFAULT_FONT = 16;
    private static final float LARGER_FONT = 36;

    /** Speech parameters. */
    private final HashMap<String, String> mParams = new HashMap<String, String>();

    /** Handler used for transferring TTS callbacks to the main thread. */
    private final TypeAndSpeakHandler mHandler = new TypeAndSpeakHandler(this);

    /** Default text-to-speech engine. */
    private String mTtsEngine;

    /** Text-to-speech service used for speaking. */
    private TextToSpeech mTts;

    /** Audio manager used to gain audio focus. */
    private AudioManager mAudioManager;

    /** Sing-along manager used to iterate through the edit text. */
    private GranularTextToSpeech mTtsWrapper;

    /** Synthesizer for writing speech to file. Lazily initialized. */
    private FileSynthesizer mSynth;

    // Interface components.
    private ViewGroup mSpeakControls;
    private ViewGroup mDefaultControls;
    private View mSpeakButton;
    private View mPauseButton;
    private View mResumeButton;
    private View mSaveButton;
    private EditText mInputText;
    private ArrayAdapter<Locale> mLanguagesAdapter;

    // Speech properties.
    private Locale mLocale;
    private int mLocalePosition;
    private int mPitch;
    private int mSpeed;
    private boolean mSpeakWhileTyping;
    private boolean mUseLargerFont;

    // Extraction task.
    private ExtractionTask mExtractionTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        setupUserInterface();

        // Ensure that volume control is appropriate.
        setVolumeControlStream(STREAM_TYPE);

        // Set up text-to-speech.
        final ContentResolver resolver = getContentResolver();
        final TextToSpeech.OnInitListener initListener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                mHandler.transferOnTtsInitialized(status);
            }
        };

        mParams.put(Engine.KEY_PARAM_UTTERANCE_ID, TAG);
        mTtsEngine = Settings.Secure.getString(resolver, Settings.Secure.TTS_DEFAULT_SYNTH);
        mTts = new TextToSpeech(this, initListener);
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mInputText.setText(prefs.getString(PREF_TEXT, ""));
        mInputText.addTextChangedListener(mTextWatcher);

        mTtsWrapper = new GranularTextToSpeech(this, mTts, mLocale);
        mTtsWrapper.setListener(mSingAlongListener);

        // Load text from intent.
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            restoreState(intent.getExtras(), true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Load saved preferences.
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        final String defaultLocale = Locale.getDefault().toString();
        mLocale = new Locale(prefs.getString(PREF_LOCALE, defaultLocale));
        mPitch = prefs.getInt(PREF_PITCH, 50);
        mSpeed = prefs.getInt(PREF_SPEED, 50);
        mSpeakWhileTyping = prefs.getBoolean(PREF_SPEAK_WHILE_TYPING, false);
        mUseLargerFont = prefs.getBoolean(PREF_USE_LARGER_FONT, false);

        // Never load the ADD_MORE locale as the default!
        if (LanguageAdapter.LOCALE_ADD_MORE.equals(mLocale)) {
            mLocale = Locale.getDefault();
        }

        mInputText.setTextSize(mUseLargerFont ? LARGER_FONT : DEFAULT_FONT);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save preferences.
        final SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putInt(PREF_PITCH, mPitch);
        editor.putInt(PREF_SPEED, mSpeed);
        editor.putBoolean(PREF_SPEAK_WHILE_TYPING, mSpeakWhileTyping);
        editor.putBoolean(PREF_USE_LARGER_FONT, mUseLargerFont);
        editor.putString(PREF_LOCALE, mLocale.toString());
        editor.putString(PREF_TEXT, mInputText.getText().toString());
        editor.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mTts.shutdown();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_INSTALL_DATA: {
                final DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE: {
                                final Intent intent = new Intent(Engine.ACTION_INSTALL_TTS_DATA);
                                intent.setPackage(mTtsEngine);

                                final PackageManager pm = getPackageManager();
                                final List<ResolveInfo> activities = pm.queryIntentActivities(
                                        intent, 0);

                                if ((activities == null) || activities.isEmpty()) {
                                    showDialog(DIALOG_CANNOT_INSTALL_DATA);
                                    break;
                                }

                                startActivityForResult(intent, REQUEST_INSTALL_DATA);
                                break;
                            }
                        }
                    }
                };

                return new Builder(this).setMessage(R.string.install_data_message)
                        .setTitle(R.string.install_data_title)
                        .setPositiveButton(android.R.string.ok, clickListener)
                        .setNegativeButton(android.R.string.no, null).create();
            }
            case DIALOG_CANNOT_INSTALL_DATA: {
                return new Builder(this)
                .setTitle(R.string.tts_failed)
                .setMessage(R.string.cannot_install_tts_data)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).create();
            }
            case DIALOG_EXTRACTING_TEXT: {
                final ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setCancelable(true);
                progressDialog.setTitle(R.string.extracting_title);
                progressDialog.setMessage(getString(R.string.extracting_message));
                progressDialog.setIndeterminate(true);
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (mExtractionTask != null) {
                            mExtractionTask.cancel(true);
                        }
                    }
                });
                return progressDialog;
            }
        }

        return super.onCreateDialog(id);
    }

    private final PinnedDialogManager mPinnedDialogManager = new PinnedDialogManager() {
        @Override
        protected PinnedDialog onCreatePinnedDialog(int id) {
            switch (id) {
                case PINNED_CONFIRM_CLEAR: {
                    final PinnedDialog dialog = new PinnedDialog(TypeAndSpeak.this)
                            .setContentView(R.layout.pinned_confirm_clear);

                    final View.OnClickListener clickListener = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            switch (v.getId()) {
                                case R.id.confirm_clear:
                                    clear();
                                    break;
                            }
                            dialog.dismiss();
                        }
                    };

                    dialog.findViewById(R.id.cancel_clear).setOnClickListener(clickListener);
                    dialog.findViewById(R.id.confirm_clear).setOnClickListener(clickListener);

                    return dialog;
                }
                case PINNED_NO_TEXT: {
                    return new PinnedDialog(TypeAndSpeak.this)
                            .setContentView(R.layout.pinned_no_text);
                }
                case PINNED_LANGUAGES: {
                    final PinnedDialog dialog = new PinnedDialog(TypeAndSpeak.this)
                            .setContentView(R.layout.pinned_languages);

                    final ListView listView = (ListView) dialog.findViewById(R.id.languages);
                    final OnItemClickListener onItemClickListener = new OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
                            final Locale selected = (Locale) parent.getItemAtPosition(position);
                            mLocale = selected;
                            mLocalePosition = position;
                            dialog.dismiss();
                        }
                    };

                    listView.setAdapter(mLanguagesAdapter);
                    listView.setOnItemClickListener(onItemClickListener);

                    final View.OnClickListener onCLickListener = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse("market://search?q=tts&c=apps"));
                            startActivity(intent);
                        }
                    };

                    dialog.findViewById(R.id.more_languages).setOnClickListener(onCLickListener);

                    return dialog;
                }
                case PINNED_PROPERTIES: {
                    final PinnedDialog dialog = new PinnedDialog(TypeAndSpeak.this)
                            .setContentView(R.layout.pinned_properties);

                    ((SeekBar) dialog.findViewById(R.id.seekPitch))
                            .setOnSeekBarChangeListener(mSeekListener);
                    ((SeekBar) dialog.findViewById(R.id.seekSpeed))
                            .setOnSeekBarChangeListener(mSeekListener);
                    ((CheckBox) dialog.findViewById(R.id.speak_while_typing))
                            .setOnCheckedChangeListener(mCheckBoxListener);
                    ((CheckBox) dialog.findViewById(R.id.use_larger_font))
                            .setOnCheckedChangeListener(mCheckBoxListener);

                    return dialog;
                }
                case PINNED_SAVE: {
                    final PinnedDialog dialog = new PinnedDialog(TypeAndSpeak.this)
                            .setContentView(R.layout.pinned_save);
                    final EditText editText = (EditText) dialog.findViewById(R.id.input);
                    final View confirmSave = dialog.findViewById(R.id.confirm_save);

                    final LayoutParams params = dialog.getParams();
                    params.flags = ~(~params.flags | LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    params.softInputMode = LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
                    dialog.setParams(params);

                    editText.setOnKeyListener(new View.OnKeyListener() {
                        @Override
                        public boolean onKey(View v, int keyCode, KeyEvent event) {
                            if ((event.getAction() == KeyEvent.ACTION_UP)
                                    && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                                confirmSave.performClick();
                                return true;
                            }
                            return false;
                        }
                    });

                    final View.OnClickListener clickListener = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            switch (v.getId()) {
                                case R.id.confirm_save:
                                    final String filename = editText.getText().toString();
                                    final String text = mInputText.getText().toString();
                                    mSynth.writeInput(text, mLocale, mPitch, mSpeed, filename);
                                    dialog.dismiss();
                                    break;
                            }
                            dialog.dismiss();
                        }
                    };

                    confirmSave.setOnClickListener(clickListener);
                    dialog.findViewById(R.id.cancel_save).setOnClickListener(clickListener);

                    return dialog;
                }
            }

            return super.onCreatePinnedDialog(id);
        }

        @Override
        protected void onPreparePinnedDialog(int id, PinnedDialog dialog, Bundle arguments) {
            switch (id) {
                case PINNED_LANGUAGES: {
                    ((ListView) dialog.findViewById(R.id.languages)).setSelection(mLocalePosition);
                    ((ListView) dialog.findViewById(R.id.languages)).setItemChecked(
                            mLocalePosition, true);
                    break;
                }
                case PINNED_PROPERTIES: {
                    ((SeekBar) dialog.findViewById(R.id.seekPitch)).setProgress(mPitch);
                    ((SeekBar) dialog.findViewById(R.id.seekSpeed)).setProgress(mSpeed);
                    ((CheckBox) dialog.findViewById(R.id.speak_while_typing)).setChecked(mSpeakWhileTyping);
                    ((CheckBox) dialog.findViewById(R.id.use_larger_font)).setChecked(mUseLargerFont);
                    break;
                }
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHECK_DATA:
                onTtsCheck(resultCode, data);
                break;
            case REQUEST_INSTALL_DATA:
                onTtsInitialized(TextToSpeech.SUCCESS);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            default:
                super.onPrepareDialog(id, dialog);
        }
    }

    /**
     * Loads interface components into variables and sets listeners.
     */
    private void setupUserInterface() {
        mSpeakButton = findViewById(R.id.speak);
        mSpeakButton.setOnClickListener(mOnClickListener);
        mSpeakButton.setVisibility(View.VISIBLE);

        mSpeakControls = (ViewGroup) findViewById(R.id.play_controls);
        mSpeakControls.setVisibility(View.GONE);

        mDefaultControls = (ViewGroup) findViewById(R.id.default_controls);
        mDefaultControls.setVisibility(View.VISIBLE);

        mSpeakControls.findViewById(R.id.stop).setOnClickListener(mOnClickListener);
        mSpeakControls.findViewById(R.id.rewind).setOnClickListener(mOnClickListener);
        mSpeakControls.findViewById(R.id.fast_forward).setOnClickListener(mOnClickListener);

        mPauseButton = mSpeakControls.findViewById(R.id.pause);
        mPauseButton.setOnClickListener(mOnClickListener);
        mPauseButton.setVisibility(View.VISIBLE);

        mResumeButton = mSpeakControls.findViewById(R.id.resume);
        mResumeButton.setOnClickListener(mOnClickListener);
        mResumeButton.setVisibility(View.GONE);

        mSaveButton = findViewById(R.id.write);
        mSaveButton.setOnClickListener(mOnClickListener);

        findViewById(R.id.clear).setOnClickListener(mOnClickListener);
        findViewById(R.id.prefs).setOnClickListener(mOnClickListener);
        findViewById(R.id.language).setOnClickListener(mOnClickListener);
        findViewById(R.id.library).setOnClickListener(mOnClickListener);

        mInputText = (EditText) findViewById(R.id.input_text);
    }

    /**
     * Restores a previously saved state.
     *
     * @param savedInstanceState The previously saved state.
     * @boolean fromIntent Whether the state is coming from an intent.
     */
    private void restoreState(Bundle savedInstanceState, boolean fromIntent) {
        String text = savedInstanceState.getString(Intent.EXTRA_TEXT);

        if (text == null) {
            return;
        }

        // The extraction library depends on java.lang.String.getBytes(Charset),
        // which is only available in SDK 9 and above.
        if ((Build.VERSION.SDK_INT >= 9) && fromIntent
                && (text.startsWith("http://") || text.startsWith("https://"))) {
            mExtractionTask = new ExtractionTask() {
                @Override
                @SuppressWarnings("deprecation")
                protected void onPreExecute() {
                    showDialog(DIALOG_EXTRACTING_TEXT);
                }

                @SuppressWarnings("deprecation")
                @Override
                protected void onPostExecute(CharSequence result) {
                    try {
                        dismissDialog(DIALOG_EXTRACTING_TEXT);
                    } catch (IllegalArgumentException e) {
                        // Do nothing.
                    }
                    mInputText.setText(result);
                }
            };
            mExtractionTask.execute(text);
        } else {
            mInputText.setText(text);
        }
    }

    private static class ExtractionTask extends AsyncTask<String, Void, CharSequence> {
        @Override
        protected CharSequence doInBackground(String... params) {
            final StringBuilder output = new StringBuilder();
            final ArticleExtractor extractor = ArticleExtractor.getInstance();

            try {
                for (String param : params) {
                    final URL url = new URL(param);
                    final String extracted = extractor.getText(url);

                    if (!TextUtils.isEmpty(extracted)) {
                        output.append(extracted);
                        output.append('\n');
                    }
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }

            return output;
        }
    }

    /**
     * Shows the media playback dialog for the given values.
     *
     * @param contentValues The content values for the media.
     * @param contentUri The URI for the media.
     */
    private void showPlaybackDialog(ContentValues contentValues) {
        final PlaybackDialog playback = new PlaybackDialog(this, false);

        try {
            final String path = contentValues.getAsString(MediaColumns.DATA);
            playback.setFile(path);
            playback.show();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Speaks the current text aloud.
     */
    private void speak() {
        final CharSequence text = mInputText.getText();

        if (TextUtils.isEmpty(text)) {
            mPinnedDialogManager.showPinnedDialog(PINNED_NO_TEXT, mSpeakButton);
            mHandler.dismissDialogDelayed(PINNED_NO_TEXT, 1000);
            return;
        }

        mTtsWrapper.setLocale(mLocale);

        if (mLocale != null) {
            mTts.setLanguage(mLocale);
        }

        mTts.setPitch(mPitch / 50.0f);
        mTts.setSpeechRate(mSpeed / 50.0f);

        mTtsWrapper.setText(text);
        mTtsWrapper.setSegmentFromCursor(mInputText.getSelectionStart());
        mTtsWrapper.speak();
    }

    private void manageAudioFocus(boolean gain) {
        if (gain) {
            AudioManagerCompatUtils.requestAudioFocus(mAudioManager, null,
                    AudioManager.STREAM_MUSIC, AudioManagerCompatUtils.AUDIOFOCUS_GAIN_TRANSIENT);
        } else {
            AudioManagerCompatUtils.abandonAudioFocus(mAudioManager, null);
        }
    }

    /**
     * Writes the current text to file.
     */
    private void write(View pinnedView) {
        final CharSequence text = mInputText.getText();

        if (TextUtils.isEmpty(text)) {
            mPinnedDialogManager.showPinnedDialog(PINNED_NO_TEXT, mSaveButton);
            mHandler.dismissDialogDelayed(PINNED_NO_TEXT, 1000);
            return;
        }

        if (mSynth == null) {
            mSynth = new FileSynthesizer(this, mTts);
            mSynth.setListener(new FileSynthesizerListener() {
                @Override
                public void onFileSynthesized(ContentValues contentValues) {
                    showPlaybackDialog(contentValues);
                }
            });
        }

        mPinnedDialogManager.showPinnedDialog(PINNED_SAVE, pinnedView);
    }

    /**
     * Clears the text input area.
     */
    private void clear() {
        mInputText.setText("");
    }

    /**
     * Populates the language adapter with the specified locales. Attempts to
     * set the current selection based on {@link #mLocale}.
     *
     * @param locales The locales to populate.
     */
    private void populateAdapter(Set<Locale> locales) {
        mLanguagesAdapter = new LanguageAdapter(this, R.layout.language, R.id.text, R.id.image);
        mLanguagesAdapter.setDropDownViewResource(R.layout.language_dropdown);
        mLanguagesAdapter.clear();

        final String preferredLocale = ((mLocale == null) ? null : mLocale.toString());
        int preferredSelection = 0;

        // Add the available locales to the adapter, watching for the preferred
        // locale.
        for (final Locale locale : locales) {
            mLanguagesAdapter.add(locale);

            if (locale.toString().equalsIgnoreCase(preferredLocale)) {
                preferredSelection = (mLanguagesAdapter.getCount() - 1);
            }
        }

        // Set up the language spinner.
        mLocalePosition = preferredSelection;
    }

    /**
     * Handles the text-to-speech language check callback.
     *
     * @param resultCode The result code.
     * @param data The returned data.
     */
    @SuppressWarnings("deprecation")
    private void onTtsCheck(int resultCode, Intent data) {
        // If data is null, always prompt the user to install voice data.
        if (data == null) {
            mSpeakButton.setEnabled(false);
            mSaveButton.setEnabled(false);
            showDialog(DIALOG_INSTALL_DATA);
            return;
        }

        mSpeakButton.setEnabled(true);
        mSaveButton.setEnabled(true);

        final boolean passed = (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS);
        final Set<Locale> locales = TextToSpeechUtils.loadTtsLanguages(mTts, data);

        if (!locales.isEmpty() || passed) {
            mSpeakButton.setEnabled(true);
            mSaveButton.setEnabled(true);
            populateAdapter(locales);
            return;
        }

        // Failed to find languages, prompt the user to install voice data.
        mSpeakButton.setEnabled(false);
        mSaveButton.setEnabled(false);
        showDialog(DIALOG_INSTALL_DATA);
    }

    /**
     * Handles the text-to-speech initialization callback.
     *
     * @param status The initialization status.
     */
    private void onTtsInitialized(int status) {
        switch (status) {
            case TextToSpeech.SUCCESS:
                try {
                    final Intent intent = new Intent(Engine.ACTION_CHECK_TTS_DATA);
                    intent.setPackage(mTtsEngine);
                    startActivityForResult(intent, REQUEST_CHECK_DATA);
                    break;
                } catch (final ActivityNotFoundException e) {
                    e.printStackTrace();
                }
                //$FALL-THROUGH$
            default:
                Toast.makeText(this, R.string.failed_init, Toast.LENGTH_LONG).show();
        }

        mSpeakButton.setEnabled(true);
        mSaveButton.setEnabled(true);
    }

    private final OnCheckedChangeListener mCheckBoxListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switch (buttonView.getId()) {
                case R.id.speak_while_typing:
                    mSpeakWhileTyping = buttonView.isChecked();
                    break;
                case R.id.use_larger_font:
                    mUseLargerFont = buttonView.isChecked();
                    mInputText.setTextSize(mUseLargerFont ? LARGER_FONT : DEFAULT_FONT);
                    break;
            }
        }
    };

    /**
     * Listens for seek bar changes and updates the pitch and speech rate.
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        @Override
        public void onStopTrackingTouch(SeekBar v) {
            // Do nothing.
        }

        @Override
        public void onStartTrackingTouch(SeekBar v) {
            // Do nothing.
        }

        @Override
        public void onProgressChanged(SeekBar v, int progress, boolean fromUser) {
            if (!fromUser) {
                return;
            }

            switch (v.getId()) {
                case R.id.seekPitch:
                    mPitch = progress;
                    break;
                case R.id.seekSpeed:
                    mSpeed = progress;
                    break;
            }
        }
    };

    /**
     * Listens for clicks.
     */
    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.prefs:
                    mPinnedDialogManager.showPinnedDialog(PINNED_PROPERTIES, view);
                    break;
                case R.id.language:
                    mPinnedDialogManager.showPinnedDialog(PINNED_LANGUAGES, view);
                    break;
                case R.id.clear:
                    mPinnedDialogManager.showPinnedDialog(PINNED_CONFIRM_CLEAR, view);
                    break;
                case R.id.speak:
                    speak();
                    break;
                case R.id.write:
                    write(view);
                    break;
                case R.id.library:
                    startActivity(new Intent(TypeAndSpeak.this, LibraryActivity.class));
                    break;
                case R.id.stop:
                    mTtsWrapper.stop();
                    break;
                case R.id.pause:
                    mTtsWrapper.pause();
                    mPauseButton.setVisibility(View.GONE);
                    mResumeButton.setVisibility(View.VISIBLE);
                    break;
                case R.id.resume:
                    mTtsWrapper.setSegmentFromCursor(mInputText.getSelectionStart());
                    mTtsWrapper.resume();
                    mResumeButton.setVisibility(View.GONE);
                    mPauseButton.setVisibility(View.VISIBLE);
                    break;
                case R.id.rewind:
                    mTtsWrapper.previous();
                    break;
                case R.id.fast_forward:
                    mTtsWrapper.next();
                    break;
            }
        }
    };

    private final SingAlongListener mSingAlongListener = new SingAlongListener() {
        @Override
        public void onSequenceStarted() {
            mSpeakControls.setVisibility(View.VISIBLE);
            mDefaultControls.setVisibility(View.GONE);
            mPauseButton.setVisibility(View.VISIBLE);
            mResumeButton.setVisibility(View.GONE);

            manageAudioFocus(true);
        }

        @Override
        public void onUnitSelected(int start, int end) {
            if ((start < 0) || (end > mInputText.length())) {
                // The text changed while we were speaking.
                // TODO: We should be able to handle this.
                mTtsWrapper.stop();
                return;
            }

            mInputText.setSelection(start, end);
        }

        @Override
        public void onSequenceCompleted() {
            mInputText.setSelection(0, 0);

            mSpeakControls.setVisibility(View.GONE);
            mDefaultControls.setVisibility(View.VISIBLE);
            manageAudioFocus(false);
        }
    };

    private final TextWatcher mTextWatcher = new TextWatcher() {
        private final BreakIterator mWordIterator = BreakIterator.getWordInstance();
        private final CharSequenceIterator mCharSequence = new CharSequenceIterator("");

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!mSpeakWhileTyping || (before > 0) || (count != 1)) {
                return;
            }

            mCharSequence.setCharSequence(s);
            mWordIterator.setText(mCharSequence);

            if (mWordIterator.isBoundary(start)) {
                final int unitEnd = start;
                final int unitStart = mWordIterator.preceding(unitEnd);
                if (unitStart == BreakIterator.DONE) {
                    return;
                }

                final CharSequence unit = TextUtils.substring(s, unitStart, unitEnd);
                if (TextUtils.getTrimmedLength(unit) == 0)  {
                    return;
                }

                mTts.speak(unit.toString(), TextToSpeech.QUEUE_FLUSH, null);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Do nothing.
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (mTtsWrapper.isSpeaking()) {
                mTtsWrapper.setText(s);
            }
        }
    };

    /**
     * Transfers callbacks to the main thread.
     */
    private static class TypeAndSpeakHandler extends ReferencedHandler<TypeAndSpeak> {
        private static final int TTS_INITIALIZED = 1;
        private static final int DISMISS_DIALOG = 2;

        public TypeAndSpeakHandler(TypeAndSpeak parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, TypeAndSpeak parent) {
            switch (msg.what) {
                case TTS_INITIALIZED:
                    parent.onTtsInitialized(msg.arg1);
                    break;
                case DISMISS_DIALOG:
                    parent.mPinnedDialogManager.dismissPinnedDialog(msg.arg1);
                    break;
            }
        }

        public void transferOnTtsInitialized(int status) {
            obtainMessage(TTS_INITIALIZED, status, 0).sendToTarget();
        }

        public void dismissDialogDelayed(int id, long delay) {
            sendMessageDelayed(obtainMessage(DISMISS_DIALOG, id, 0), delay);
        }
    }
}
