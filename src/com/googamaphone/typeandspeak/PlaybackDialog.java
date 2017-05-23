
package com.googamaphone.typeandspeak;

import java.io.File;
import java.io.IOException;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.googamaphone.compat.AudioManagerCompatUtils;
import com.googamaphone.typeandspeak.utils.ReferencedHandler;

public class PlaybackDialog extends AlertDialog {
    private final MediaPlayer mMediaPlayer;
    private final View mContentView;
    private final SeekBar mProgress;
    private final ImageButton mPlayButton;
    private final ImageButton mShareButton;
    private final AudioManager mAudioManager;

    private final boolean mFromLibrary;

    private File mSavedFile;

    private boolean mAdvanceSeekBar;
    private boolean mMediaPlayerReleased;
    private boolean mMediaPlayerPrepared;

    public PlaybackDialog(Context context, boolean fromLibrary) {
        super(context);

        mFromLibrary = fromLibrary;
        mAdvanceSeekBar = true;

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(mOnPreparedListener);
        mMediaPlayer.setOnCompletionListener(mOnCompletionListener);

        mContentView = LayoutInflater.from(context).inflate(R.layout.playback, null);

        mPlayButton = (ImageButton) mContentView.findViewById(R.id.play);
        mPlayButton.setOnClickListener(mViewClickListener);

        mShareButton = (ImageButton) mContentView.findViewById(R.id.share);
        mShareButton.setOnClickListener(mViewClickListener);

        mProgress = (SeekBar) mContentView.findViewById(R.id.progress);
        mProgress.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (fromLibrary) {
            mShareButton.setVisibility(View.GONE);
        } else {
            setTitle(R.string.saved_title);
            setMessage(context.getString(R.string.saved_message));
            setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.menu_library),
                    mDialogClickListener);
        }

        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                mDialogClickListener);
        setView(mContentView);
    }

    @Override
    public void onStop() {
        mPoller.stopPolling();
        mMediaPlayer.release();

        mMediaPlayerReleased = true;
        
        manageAudioFocus(false);
    }

    private void manageAudioFocus(boolean gain) {
        if (gain) {
            AudioManagerCompatUtils.requestAudioFocus(mAudioManager, null,
                    AudioManager.STREAM_MUSIC, AudioManagerCompatUtils.AUDIOFOCUS_GAIN_TRANSIENT);
        } else {
            AudioManagerCompatUtils.abandonAudioFocus(mAudioManager, null);
        }
    }

    public void setFile(String path) throws IOException {
        if (mMediaPlayerReleased) {
            throw new IOException("Media player was already released!");
        }

        if (!mFromLibrary) {
            setMessage(getContext().getString(R.string.saved_message, path));
        }

        mSavedFile = new File(path);

        mMediaPlayer.setDataSource(mSavedFile.getAbsolutePath());
        mMediaPlayer.prepare();
    }

    private final MediaPoller mPoller = new MediaPoller(this);

    private final MediaPlayer.OnCompletionListener mOnCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            manageAudioFocus(false);
            mp.seekTo(0);

            mProgress.setProgress(0);
            mPlayButton.setImageResource(android.R.drawable.ic_media_play);
            mPoller.stopPolling();
        }
    };

    private final View.OnClickListener mViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.play: {
                    final ImageButton button = (ImageButton) v;

                    if (!mMediaPlayerPrepared) {
                        // The media player isn't ready yet, do nothing.
                    } else if (mMediaPlayer.isPlaying()) {
                        button.setImageResource(android.R.drawable.ic_media_play);
                        mMediaPlayer.pause();
                        manageAudioFocus(false);
                        mPoller.stopPolling();
                    } else {
                        button.setImageResource(android.R.drawable.ic_media_pause);
                        mMediaPlayer.start();
                        manageAudioFocus(true);
                        mPoller.startPolling();
                    }

                    break;
                }
                case R.id.share: {
                    final Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mSavedFile));
                    shareIntent.setType("audio/wav");

                    final Context context = getContext();
                    final Intent chooserIntent = Intent.createChooser(shareIntent,
                            context.getString(R.string.share_to));

                    context.startActivity(chooserIntent);
                }
            }
        }
    };

    private final OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mAdvanceSeekBar = false;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mAdvanceSeekBar = true;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!mMediaPlayerPrepared) {
                // The media player isn't ready yet, do nothing.
            } else if (fromUser) {
                mMediaPlayer.seekTo(progress);
            }
        }
    };

    private final MediaPlayer.OnPreparedListener mOnPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            mMediaPlayerPrepared = true;
        }
    };

    private final DialogInterface.OnClickListener mDialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_NEGATIVE:
                    final Context context = getContext();
                    final Intent intent = new Intent(context, LibraryActivity.class);
                    context.startActivity(intent);
                    break;
            }
        }
    };

    private static class MediaPoller extends ReferencedHandler<PlaybackDialog> {
        private static final int MSG_CHECK_PROGRESS = 1;

        private boolean mStopPolling;

        public MediaPoller(PlaybackDialog parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, PlaybackDialog parent) {
            switch (msg.what) {
                case MSG_CHECK_PROGRESS:
                    if (!mStopPolling && parent.mMediaPlayer.isPlaying()) {
                        if (parent.mAdvanceSeekBar) {
                            parent.mProgress.setMax(parent.mMediaPlayer.getDuration());
                            parent.mProgress.setProgress(parent.mMediaPlayer.getCurrentPosition());
                        }

                        startPolling();
                    }

                    break;
            }
        }

        public void stopPolling() {
            mStopPolling = true;
            removeMessages(MSG_CHECK_PROGRESS);
        }

        public void startPolling() {
            mStopPolling = false;
            removeMessages(MSG_CHECK_PROGRESS);
            sendEmptyMessageDelayed(MSG_CHECK_PROGRESS, 200);
        }
    }
}
