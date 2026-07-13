package com.xinghe.helper.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.xinghe.helper.R;
import com.xinghe.helper.cast.CastState;

import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

public class CastPlayerActivity extends AppCompatActivity {

    private static final String TAG = "CastPlayerActivity";
    private static final int SEEK_STEP = 10000;
    private static final int MAX_RETRY = 3;
    private static final long BUFFERING_TIMEOUT = 20000;

    private SurfaceView surfaceView;
    private ImageView imageView;
    private TextView statusText;
    private ProgressBar bufferingProgress;
    private View controlBar;
    private TextView btnPlayPause;
    private TextView btnStop;
    private TextView btnForward;
    private TextView btnBackward;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;

    private ExoPlayer exoPlayer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private Runnable bufferingTimeoutRunnable;

    private String currentUrl = "";
    private String currentMimeType = "";
    private int retryCount = 0;
    private boolean userPaused = false;
    private boolean controlBarVisible = false;

    private final Runnable hideControlBarRunnable = this::hideControlBar;

    private final CastState.StateListener playerListener = new CastState.StateListener() {
        @Override
        public void onPlay(String url, String mimeType) {
            mainHandler.post(() -> playMedia(url, mimeType));
        }

        @Override
        public void onPause() {
            mainHandler.post(() -> pausePlay());
        }

        @Override
        public void onStop() {
            mainHandler.post(() -> stopPlay());
        }

        @Override
        public void onSeek(long position) {
            mainHandler.post(() -> seekTo(position));
        }

        @Override
        public void onVolume(int volume) {
            mainHandler.post(() -> {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC,
                            (int) (volume * max / 100.0f), 0);
                }
            });
        }

        @Override
        public void onMute(boolean mute) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cast_player);

        surfaceView = findViewById(R.id.surfaceView);
        imageView = findViewById(R.id.imageView);
        statusText = findViewById(R.id.statusText);
        bufferingProgress = findViewById(R.id.bufferingProgress);
        controlBar = findViewById(R.id.controlBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnStop = findViewById(R.id.btnStop);
        btnForward = findViewById(R.id.btnForward);
        btnBackward = findViewById(R.id.btnBackward);
        seekBar = findViewById(R.id.seekBar);
        currentTimeText = findViewById(R.id.currentTime);
        totalTimeText = findViewById(R.id.totalTime);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        CastState.getInstance().addListener(playerListener);

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.postDelayed(updateRunnable, 1000);

        bufferingTimeoutRunnable = () -> {
            if (exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
                Log.w(TAG, "缓冲超时，重试播放");
                handlePlaybackError();
            }
        };

        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        btnStop.setOnClickListener(v -> {
            stopPlay();
            CastState.getInstance().stop();
        });

        btnForward.setOnClickListener(v -> {
            if (exoPlayer != null) {
                long pos = exoPlayer.getCurrentPosition();
                long dur = exoPlayer.getDuration();
                long target = Math.min(pos + SEEK_STEP, dur);
                seekTo(target);
                CastState.getInstance().setPosition(target);
            }
        });

        btnBackward.setOnClickListener(v -> {
            if (exoPlayer != null) {
                long pos = exoPlayer.getCurrentPosition();
                long target = Math.max(pos - SEEK_STEP, 0);
                seekTo(target);
                CastState.getInstance().setPosition(target);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && exoPlayer != null && exoPlayer.getDuration() > 0) {
                    long dur = exoPlayer.getDuration();
                    long target = dur * progress / 100;
                    currentTimeText.setText(formatTime(target));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (exoPlayer != null && exoPlayer.getDuration() > 0) {
                    long dur = exoPlayer.getDuration();
                    long target = dur * seekBar.getProgress() / 100;
                    seekTo(target);
                    CastState.getInstance().setPosition(target);
                }
            }
        });

        // 如果已有投屏内容且正在播放，直接播放
        CastState state = CastState.getInstance();
        if (state.getCurrentUrl() != null && !state.getCurrentUrl().isEmpty()) {
            playMedia(state.getCurrentUrl(), state.getCurrentMimeType());
        }
    }

    private void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) {
            pausePlay();
            CastState.getInstance().pause();
        } else {
            resumePlay();
            CastState.getInstance().setPlaying(true);
        }
    }

    private void playMedia(String url, String mimeType) {
        if (url == null || url.isEmpty()) return;

        if (url.equals(currentUrl) && exoPlayer != null) {
            resumePlay();
            return;
        }

        currentUrl = url;
        currentMimeType = mimeType != null ? mimeType : "video/mp4";
        retryCount = 0;

        if (currentMimeType.startsWith("image/")) {
            playImage(url);
        } else {
            playVideo(url);
        }
    }

    private void playVideo(String url) {
        Log.d(TAG, "playVideo: " + url);
        releasePlayer();

        statusText.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        surfaceView.setVisibility(View.VISIBLE);
        showBuffering(true);
        startBufferingTimeout();

        try {
            exoPlayer = new ExoPlayer.Builder(this).build();
            exoPlayer.setVideoSurfaceView(surfaceView);
            exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);
            surfaceView.setKeepScreenOn(true);

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    switch (playbackState) {
                        case Player.STATE_READY:
                            Log.d(TAG, "STATE_READY");
                            cancelBufferingTimeout();
                            showBuffering(false);
                            showControlBar();
                            long dur = exoPlayer.getDuration();
                            totalTimeText.setText(formatTime(dur));
                            CastState.getInstance().setDuration(dur);
                            if (!userPaused) {
                                exoPlayer.setPlayWhenReady(true);
                                CastState.getInstance().setPlaying(true);
                                btnPlayPause.setText("暂停");
                            }
                            break;
                        case Player.STATE_BUFFERING:
                            Log.d(TAG, "STATE_BUFFERING");
                            showBuffering(true);
                            startBufferingTimeout();
                            break;
                        case Player.STATE_ENDED:
                            Log.d(TAG, "STATE_ENDED");
                            cancelBufferingTimeout();
                            showBuffering(false);
                            CastState.getInstance().setPlaying(false);
                            btnPlayPause.setText("重播");
                            break;
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "onPlayerError", error);
                    cancelBufferingTimeout();
                    showBuffering(false);
                    handlePlaybackError();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        btnPlayPause.setText("暂停");
                    } else if (exoPlayer != null
                            && exoPlayer.getPlaybackState() == Player.STATE_READY) {
                        btnPlayPause.setText("播放");
                    }
                }
            });

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(!userPaused);

        } catch (Exception e) {
            Log.e(TAG, "playVideo error", e);
            cancelBufferingTimeout();
            showBuffering(false);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("播放失败：" + e.getMessage());
        }
    }

    private void handlePlaybackError() {
        if (retryCount < MAX_RETRY) {
            retryCount++;
            Log.w(TAG, "重试播放 " + retryCount + "/" + MAX_RETRY);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("网络异常，重试中(" + retryCount + "/" + MAX_RETRY + ")...");
            String url = currentUrl;
            currentUrl = "";
            mainHandler.postDelayed(() -> playVideo(url), 2000L * retryCount);
        } else {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("播放失败，请检查网络或视频源");
            Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void playImage(String url) {
        releasePlayer();
        surfaceView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.GONE);
        showBuffering(true);

        new Thread(() -> {
            try {
                InputStream is = new URL(url).openStream();
                final Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                mainHandler.post(() -> {
                    showBuffering(false);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    } else {
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText("图片加载失败");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showBuffering(false);
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText("图片加载失败");
                });
            }
        }).start();
    }

    private void pausePlay() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            userPaused = true;
            btnPlayPause.setText("播放");
        }
    }

    private void resumePlay() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
            userPaused = false;
            btnPlayPause.setText("暂停");
        }
    }

    private void stopPlay() {
        cancelBufferingTimeout();
        releasePlayer();
        surfaceView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("等待投屏...");
        controlBar.setVisibility(View.GONE);
        currentTimeText.setText("00:00");
        totalTimeText.setText("00:00");
        seekBar.setProgress(0);
        currentUrl = "";
        userPaused = false;
    }

    private void seekTo(long position) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(position);
        }
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                exoPlayer.release();
            } catch (Exception ignored) {}
            exoPlayer = null;
        }
        userPaused = false;
    }

    private void startBufferingTimeout() {
        cancelBufferingTimeout();
        mainHandler.postDelayed(bufferingTimeoutRunnable, BUFFERING_TIMEOUT);
    }

    private void cancelBufferingTimeout() {
        mainHandler.removeCallbacks(bufferingTimeoutRunnable);
    }

    private void updateProgress() {
        if (exoPlayer != null && exoPlayer.getDuration() > 0) {
            try {
                long pos = exoPlayer.getCurrentPosition();
                long dur = exoPlayer.getDuration();
                seekBar.setProgress((int) (pos * 100 / dur));
                currentTimeText.setText(formatTime(pos));
                CastState.getInstance().setPosition(pos);
            } catch (Exception ignored) {}
        }
    }

    private void showBuffering(boolean show) {
        bufferingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showControlBar() {
        controlBarVisible = true;
        controlBar.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideControlBarRunnable);
        mainHandler.postDelayed(hideControlBarRunnable, 5000);
    }

    private void hideControlBar() {
        controlBarVisible = false;
        controlBar.setVisibility(View.GONE);
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (controlBarVisible) {
                    return super.onKeyDown(keyCode, event);
                }
                if (exoPlayer != null) {
                    togglePlayPause();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!controlBarVisible) {
                    showControlBar();
                    btnBackward.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!controlBarVisible) {
                    showControlBar();
                    btnForward.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (!controlBarVisible && exoPlayer != null) {
                    showControlBar();
                    btnPlayPause.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (exoPlayer != null) {
                    togglePlayPause();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (exoPlayer != null) {
                    long pos = exoPlayer.getCurrentPosition();
                    long dur = exoPlayer.getDuration();
                    seekTo(Math.min(pos + SEEK_STEP, dur));
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (exoPlayer != null) {
                    long pos = exoPlayer.getCurrentPosition();
                    seekTo(Math.max(pos - SEEK_STEP, 0));
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelBufferingTimeout();
        mainHandler.removeCallbacks(updateRunnable);
        mainHandler.removeCallbacks(hideControlBarRunnable);
        releasePlayer();
        CastState.getInstance().removeListener(playerListener);
    }
}
