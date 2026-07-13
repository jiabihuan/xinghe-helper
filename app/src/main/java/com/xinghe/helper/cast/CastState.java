package com.xinghe.helper.cast;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CastState {

    public interface StateListener {
        void onPlay(String url, String mimeType);
        void onPause();
        void onStop();
        void onSeek(long position);
        void onVolume(int volume);
        void onMute(boolean mute);
    }

    private static CastState instance;
    private final List<StateListener> listeners = new CopyOnWriteArrayList<>();

    private String currentUrl = "";
    private String currentMimeType = "";
    private String currentTitle = "";
    private String currentCreator = "";

    private boolean playing = false;
    private long duration = 0;
    private long position = 0;
    private int volume = 100;
    private boolean mute = false;
    private String transportState = "STOPPED";

    private CastState() {}

    public static synchronized CastState getInstance() {
        if (instance == null) {
            instance = new CastState();
        }
        return instance;
    }

    public synchronized void addListener(StateListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public synchronized void removeListener(StateListener l) {
        listeners.remove(l);
    }

    public synchronized String getCurrentUrl() { return currentUrl; }
    public synchronized String getCurrentMimeType() { return currentMimeType; }
    public synchronized String getCurrentTitle() { return currentTitle; }
    public synchronized String getCurrentCreator() { return currentCreator; }
    public synchronized boolean isPlaying() { return playing; }
    public synchronized long getDuration() { return duration; }
    public synchronized long getPosition() { return position; }
    public synchronized int getVolume() { return volume; }
    public synchronized boolean isMute() { return mute; }
    public synchronized String getTransportState() { return transportState; }

    public synchronized void setPosition(long pos) { this.position = pos; }
    public synchronized void setDuration(long dur) { this.duration = dur; }
    public synchronized void setPlaying(boolean p) {
        this.playing = p;
        this.transportState = p ? "PLAYING" : (transportState.equals("STOPPED") ? "STOPPED" : "PAUSED_PLAYBACK");
    }

    public synchronized void setAVTransportURI(String url, String metaData) {
        this.currentUrl = url;
        this.currentMimeType = guessMimeType(url);
        this.currentTitle = extractTitle(metaData);
        this.currentCreator = extractCreator(metaData);
        this.position = 0;
        this.duration = 0;
        this.playing = false;
        this.transportState = "STOPPED";
    }

    public synchronized void play() {
        this.playing = true;
        this.transportState = "PLAYING";
        notifyPlay(currentUrl, currentMimeType);
    }

    public synchronized void pause() {
        this.playing = false;
        this.transportState = "PAUSED_PLAYBACK";
        for (StateListener l : listeners) {
            l.onPause();
        }
    }

    public synchronized void stop() {
        this.playing = false;
        this.transportState = "STOPPED";
        this.position = 0;
        for (StateListener l : listeners) {
            l.onStop();
        }
    }

    public synchronized void seek(long pos) {
        this.position = pos;
        for (StateListener l : listeners) {
            l.onSeek(pos);
        }
    }

    public synchronized void setVolume(int vol) {
        this.volume = vol;
        for (StateListener l : listeners) {
            l.onVolume(vol);
        }
    }

    public synchronized void setMute(boolean m) {
        this.mute = m;
        for (StateListener l : listeners) {
            l.onMute(m);
        }
    }

    private void notifyPlay(String url, String mimeType) {
        for (StateListener l : listeners) {
            l.onPlay(url, mimeType);
        }
    }

    private String guessMimeType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".mp4") || lower.contains("video/mp4")) return "video/mp4";
        if (lower.contains(".mkv")) return "video/x-matroska";
        if (lower.contains(".avi")) return "video/x-msvideo";
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".m3u8")) return "application/vnd.apple.mpegurl";
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".mp3")) return "audio/mpeg";
        if (lower.contains(".aac")) return "audio/aac";
        if (lower.contains(".wav")) return "audio/wav";
        return "video/mp4";
    }

    private String extractTitle(String metaData) {
        if (metaData == null || metaData.isEmpty()) return "";
        int start = metaData.indexOf("<dc:title>");
        int end = metaData.indexOf("</dc:title>");
        if (start >= 0 && end > start) {
            return metaData.substring(start + 10, end);
        }
        start = metaData.indexOf("<title>");
        end = metaData.indexOf("</title>");
        if (start >= 0 && end > start) {
            return metaData.substring(start + 7, end);
        }
        return "";
    }

    private String extractCreator(String metaData) {
        if (metaData == null || metaData.isEmpty()) return "";
        int start = metaData.indexOf("<dc:creator>");
        int end = metaData.indexOf("</dc:creator>");
        if (start >= 0 && end > start) {
            return metaData.substring(start + 12, end);
        }
        return "";
    }
}
