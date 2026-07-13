package com.xinghe.helper.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xinghe.helper.R;
import com.xinghe.helper.cast.CastState;

import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class CastPlayerActivity extends AppCompatActivity {

    private static final String TAG = "CastPlayerActivity";
    private static final int SEEK_STEP = 10000;
    private static final int MAX_RETRY = 5;
    private static final long BUFFERING_TIMEOUT = 60000;

    private SurfaceView surfaceView;
    private ImageView imageView;
    private TextView statusText;
    private ProgressBar bufferingProgress;
    private View controlBar;
    private Button btnPlayPause;
    private Button btnStop;
    private Button btnForward;
    private Button btnBackward;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;

    private IjkMediaPlayer ijkPlayer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private Runnable bufferingTimeoutRunnable;

    private String currentUrl = "";
    private String currentMimeType = "";
    private int retryCount = 0;
    private boolean userPaused = false;
    private boolean controlBarVisible = false;
    private boolean pendingPlay = false;
    private String pendingUrl = "";
    private String pendingMimeType = "";
    private boolean isPrepared = false;
    private long duration = 0;

    private final Runnable hideControlBarRunnable = this::hideControlBar;

    private final CastState.StateListener playerListener = new CastState.StateListener() {
        @Override
        public void onPlay(String url, String mimeType) {
            mainHandler.post(() -> {
                if (surfaceView != null && surfaceView.getHolder().getSurface().isValid()) {
                    playMedia(url, mimeType);
                } else {
                    pendingPlay = true;
                    pendingUrl = url;
                    pendingMimeType = mimeType;
                }
            });
        }

        @Override
        public void onPause() {
            mainHandler.post(() -> pausePlay());
        }

        @Override
        public void onStop() {
            mainHandler.post(() -> {
                stopPlay();
                finish();
            });
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

        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

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
            Log.w(TAG, "缓冲超时，重试播放");
            handlePlaybackError();
        };

        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        btnStop.setOnClickListener(v -> {
            stopPlay();
            CastState.getInstance().stop();
        });

        btnForward.setOnClickListener(v -> {
            if (ijkPlayer != null && isPrepared) {
                long pos = ijkPlayer.getCurrentPosition();
                long dur = ijkPlayer.getDuration();
                long target = Math.min(pos + SEEK_STEP, dur);
                seekTo(target);
                CastState.getInstance().setPosition(target);
            }
        });

        btnBackward.setOnClickListener(v -> {
            if (ijkPlayer != null && isPrepared) {
                long pos = ijkPlayer.getCurrentPosition();
                long target = Math.max(pos - SEEK_STEP, 0);
                seekTo(target);
                CastState.getInstance().setPosition(target);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && ijkPlayer != null && isPrepared && duration > 0) {
                    long target = duration * progress / 100;
                    currentTimeText.setText(formatTime(target));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (ijkPlayer != null && isPrepared && duration > 0) {
                    long target = duration * seekBar.getProgress() / 100;
                    seekTo(target);
                    CastState.getInstance().setPosition(target);
                }
            }
        });

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "surfaceCreated");
                if (pendingPlay && pendingUrl != null && !pendingUrl.isEmpty()) {
                    pendingPlay = false;
                    String url = pendingUrl;
                    String mime = pendingMimeType;
                    pendingUrl = "";
                    pendingMimeType = "";
                    playMedia(url, mime);
                } else if (ijkPlayer != null) {
                    ijkPlayer.setDisplay(holder);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed");
            }
        });

        CastState state = CastState.getInstance();
        if (state.getCurrentUrl() != null && !state.getCurrentUrl().isEmpty() && state.isPlaying()) {
            if (surfaceView.getHolder().getSurface().isValid()) {
                playMedia(state.getCurrentUrl(), state.getCurrentMimeType());
            } else {
                pendingPlay = true;
                pendingUrl = state.getCurrentUrl();
                pendingMimeType = state.getCurrentMimeType();
            }
        }
    }

    private void togglePlayPause() {
        if (ijkPlayer == null || !isPrepared) return;
        if (ijkPlayer.isPlaying()) {
            pausePlay();
            CastState.getInstance().pause();
        } else {
            resumePlay();
            CastState.getInstance().setPlaying(true);
        }
    }

    private void playMedia(String url, String mimeType) {
        if (url == null || url.isEmpty()) return;

        if (url.equals(currentUrl) && ijkPlayer != null && isPrepared) {
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
        isPrepared = false;
        duration = 0;

        try {
            ijkPlayer = new IjkMediaPlayer();
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rw_timeout", 30000000);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 10000000);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024 * 1024 * 10);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_SWS, "fast", 1);

            ijkPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            ijkPlayer.setDisplay(surfaceView.getHolder());
            ijkPlayer.setDataSource(url);

            ijkPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(IMediaPlayer mp) {
                    Log.d(TAG, "onPrepared");
                    isPrepared = true;
                    duration = mp.getDuration();
                    cancelBufferingTimeout();
                    showBuffering(false);
                    showControlBar();
                    totalTimeText.setText(formatTime(duration));
                    CastState.getInstance().setDuration(duration);
                    if (!userPaused) {
                        mp.start();
                        CastState.getInstance().setPlaying(true);
                        btnPlayPause.setText("暂停");
                    }
                }
            });

            ijkPlayer.setOnVideoSizeChangedListener(new IMediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
                    Log.d(TAG, "onVideoSizeChanged: " + width + "x" + height);
                }
            });

            ijkPlayer.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(IMediaPlayer mp) {
                    Log.d(TAG, "onCompletion");
                    cancelBufferingTimeout();
                    showBuffering(false);
                    CastState.getInstance().setPlaying(false);
                    btnPlayPause.setText("重播");
                }
            });

            ijkPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(IMediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "onError: what=" + what + ", extra=" + extra);
                    cancelBufferingTimeout();
                    showBuffering(false);
                    handlePlaybackError();
                    return true;
                }
            });

            ijkPlayer.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                    switch (what) {
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_START");
                            showBuffering(true);
                            startBufferingTimeout();
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_END");
                            cancelBufferingTimeout();
                            showBuffering(false);
                            break;
                    }
                    return true;
                }
            });

            ijkPlayer.prepareAsync();
            surfaceView.setKeepScreenOn(true);

        } catch (Exception e) {
            Log.e(TAG, "playVideo error", e);
            cancelBufferingTimeout();
            showBuffering(false);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("播放失败：" + e.getMessage());
        }
    }

    private void handlePlaybackError() {
        if (currentUrl == null || currentUrl.isEmpty()) {
            return;
        }
        if (retryCount < MAX_RETRY) {
            retryCount++;
            Log.w(TAG, "重试播放 " + retryCount + "/" + MAX_RETRY);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("网络异常，重试中(" + retryCount + "/" + MAX_RETRY + ")...");
            String url = currentUrl;
            currentUrl = "";
            long delay = retryCount == 1 ? 1000 : 2000L * retryCount;
            mainHandler.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    playVideo(url);
                }
            }, delay);
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
        if (ijkPlayer != null && isPrepared) {
            ijkPlayer.pause();
            userPaused = true;
            btnPlayPause.setText("播放");
        }
    }

    private void resumePlay() {
        if (ijkPlayer != null && isPrepared) {
            ijkPlayer.start();
            userPaused = false;
            btnPlayPause.setText("暂停");
        }
    }

    private void stopPlay() {
        cancelBufferingTimeout();
        mainHandler.removeCallbacksAndMessages(null);
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
        currentMimeType = "";
        userPaused = false;
        pendingPlay = false;
        pendingUrl = "";
        pendingMimeType = "";
        retryCount = 0;
        isPrepared = false;
        duration = 0;
    }

    private void seekTo(long position) {
        if (ijkPlayer != null && isPrepared) {
            ijkPlayer.seekTo(position);
        }
    }

    private void releasePlayer() {
        if (ijkPlayer != null) {
            try {
                ijkPlayer.stop();
                ijkPlayer.setDisplay(null);
                ijkPlayer.release();
            } catch (Exception ignored) {}
            ijkPlayer = null;
        }
        userPaused = false;
        isPrepared = false;
        duration = 0;
        if (surfaceView != null) {
            surfaceView.setKeepScreenOn(false);
        }
    }

    private void startBufferingTimeout() {
        cancelBufferingTimeout();
        mainHandler.postDelayed(bufferingTimeoutRunnable, BUFFERING_TIMEOUT);
    }

    private void cancelBufferingTimeout() {
        mainHandler.removeCallbacks(bufferingTimeoutRunnable);
    }

    private void updateProgress() {
        if (ijkPlayer != null && isPrepared && duration > 0) {
            try {
                long pos = ijkPlayer.getCurrentPosition();
                seekBar.setProgress((int) (pos * 100 / duration));
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
        mainHandler.postDelayed(hideControlBarRunnable, 8000);
        btnPlayPause.requestFocus();
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
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (controlBarVisible) {
                    return super.onKeyDown(keyCode, event);
                }
                if (ijkPlayer != null && isPrepared) {
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
                if (!controlBarVisible && ijkPlayer != null && isPrepared) {
                    showControlBar();
                    btnPlayPause.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (ijkPlayer != null && isPrepared) {
                    togglePlayPause();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (ijkPlayer != null && isPrepared) {
                    long pos = ijkPlayer.getCurrentPosition();
                    long dur = ijkPlayer.getDuration();
                    seekTo(Math.min(pos + SEEK_STEP, dur));
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (ijkPlayer != null && isPrepared) {
                    long pos = ijkPlayer.getCurrentPosition();
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
        IjkMediaPlayer.native_profileEnd();
    }
}
