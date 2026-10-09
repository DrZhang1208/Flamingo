package com.hchen.superlyricapi;

import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/** Parcelable ABI recovered from the APK's bundled SuperLyricApi 2.4. */
public class SuperLyricData implements Parcelable {
    private static final String KEY_ENHANCED_LRC_DATA = "key_enhanced_lrc_data";
    private static final String KEY_TRANSLATION = "key_translation";

    public static final Creator<SuperLyricData> CREATOR = new Creator<SuperLyricData>() {
        @Override public SuperLyricData createFromParcel(Parcel source) { return new SuperLyricData(source); }
        @Override public SuperLyricData[] newArray(int size) { return new SuperLyricData[size]; }
    };

    private String lyric = "";
    private String packageName = "";
    @Deprecated private String base64Icon = "";
    private int delay;
    private MediaMetadata mediaMetadata;
    private PlaybackState playbackState;
    private Bundle extra;

    public SuperLyricData() {}

    private SuperLyricData(Parcel source) {
        String readLyric = source.readString();
        String readPackage = source.readString();
        String readIcon = source.readString();
        lyric = readLyric == null ? "" : readLyric;
        packageName = readPackage == null ? "" : readPackage;
        base64Icon = readIcon == null ? "" : readIcon;
        delay = source.readInt();
        mediaMetadata = source.readParcelable(MediaMetadata.class.getClassLoader());
        playbackState = source.readParcelable(PlaybackState.class.getClassLoader());
        extra = source.readBundle(SuperLyricData.class.getClassLoader());
    }

    public static SuperLyricData unmarshall(Parcel parcel) {
        parcel.setDataPosition(0);
        return new SuperLyricData(parcel);
    }

    public String getLyric() { return lyric; }
    public String getPackageName() { return packageName; }
    @Deprecated public String getBase64Icon() { return base64Icon; }
    public int getDelay() { return delay; }
    public MediaMetadata getMediaMetadata() { return mediaMetadata; }
    public PlaybackState getPlaybackState() { return playbackState; }
    public Bundle getExtra() { return extra; }
    public String getTranslation() { return extra == null ? null : extra.getString(KEY_TRANSLATION); }
    public String getTitle() { return metadataString(MediaMetadata.METADATA_KEY_TITLE); }
    public String getArtist() { return metadataString(MediaMetadata.METADATA_KEY_ARTIST); }
    public String getAlbum() { return metadataString(MediaMetadata.METADATA_KEY_ALBUM); }

    private String metadataString(String key) {
        String value = mediaMetadata == null ? null : mediaMetadata.getString(key);
        return value == null ? "Unknown" : value;
    }

    public EnhancedLRCData[] getEnhancedLRCData() {
        if (extra == null) return null;
        Parcelable[] values;
        if (Build.VERSION.SDK_INT >= 33) {
            values = extra.getParcelableArray(KEY_ENHANCED_LRC_DATA, EnhancedLRCData.class);
        } else {
            values = extra.getParcelableArray(KEY_ENHANCED_LRC_DATA);
        }
        return values == null ? null : Arrays.copyOf(values, values.length, EnhancedLRCData[].class);
    }

    public boolean isExistLyric() { return !lyric.isEmpty(); }
    public boolean isExistPackageName() { return !packageName.isEmpty(); }
    @Deprecated public boolean isExistBase64Icon() { return !base64Icon.isEmpty(); }
    public boolean isExistDelay() { return delay > 0; }
    public boolean isExistMediaMetadata() { return mediaMetadata != null; }
    public boolean isExistPlaybackState() { return playbackState != null; }
    public boolean isExistExtra() { return extra != null; }
    public boolean isExistTranslation() { return extra != null && extra.containsKey(KEY_TRANSLATION); }
    public boolean isExistEnhancedLRCData() { return extra != null && extra.containsKey(KEY_ENHANCED_LRC_DATA); }

    public SuperLyricData setLyric(String value) { lyric = value == null ? "" : value; return this; }
    public SuperLyricData setPackageName(String value) { packageName = value == null ? "" : value; return this; }
    @Deprecated public SuperLyricData setBase64Icon(String value) { base64Icon = value == null ? "" : value; return this; }
    public SuperLyricData setDelay(int value) { delay = value; return this; }
    public SuperLyricData setMediaMetadata(MediaMetadata value) { mediaMetadata = value; return this; }
    public SuperLyricData setPlaybackState(PlaybackState value) { playbackState = value; return this; }
    public SuperLyricData setTranslation(String value) {
        if (value != null) ensureExtra().putString(KEY_TRANSLATION, value);
        return this;
    }
    public SuperLyricData setEnhancedLRCData(EnhancedLRCData[] value) {
        if (value != null) ensureExtra().putParcelableArray(KEY_ENHANCED_LRC_DATA, value);
        return this;
    }
    public SuperLyricData setExtra(Bundle value) {
        if (value == null) return this;
        if (extra == null) extra = new Bundle(value); else extra.putAll(value);
        return this;
    }

    private Bundle ensureExtra() { if (extra == null) extra = new Bundle(); return extra; }

    public Parcel marshall() { Parcel parcel = Parcel.obtain(); writeToParcel(parcel, 0); return parcel; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(lyric);
        dest.writeString(packageName);
        dest.writeString(base64Icon);
        dest.writeInt(delay);
        dest.writeParcelable(mediaMetadata, flags);
        dest.writeParcelable(playbackState, flags);
        dest.writeBundle(extra);
    }

    @Override public boolean equals(Object value) {
        if (!(value instanceof SuperLyricData)) return false;
        SuperLyricData other = (SuperLyricData) value;
        return delay == other.delay && Objects.equals(lyric, other.lyric)
            && Objects.equals(packageName, other.packageName) && Objects.equals(base64Icon, other.base64Icon)
            && Objects.equals(mediaMetadata, other.mediaMetadata) && Objects.equals(playbackState, other.playbackState)
            && Objects.equals(extra, other.extra);
    }
    @Override public int hashCode() { return Objects.hash(lyric, packageName, base64Icon, delay, mediaMetadata, playbackState, extra); }

    public static class EnhancedLRCData implements Parcelable {
        public static final Creator<EnhancedLRCData> CREATOR = new Creator<EnhancedLRCData>() {
            @Override public EnhancedLRCData createFromParcel(Parcel source) { return new EnhancedLRCData(source); }
            @Override public EnhancedLRCData[] newArray(int size) { return new EnhancedLRCData[size]; }
        };
        private String word = "";
        @Deprecated private int delay;
        private int startTime;
        private int endTime;

        public EnhancedLRCData(String value, int start, int end) { word = value == null ? "" : value; startTime = start; endTime = end; }
        public EnhancedLRCData(String value, int duration) { word = value == null ? "" : value; delay = duration; }
        private EnhancedLRCData(Parcel source) {
            String value = source.readString();
            word = value == null ? "" : value;
            delay = source.readInt();
            startTime = source.readInt();
            endTime = source.readInt();
        }
        public String getWord() { return word; }
        public int getDelay() { return delay != 0 ? delay : (startTime == 0 || endTime == 0 ? 0 : endTime - startTime); }
        public int getStartTime() { return startTime; }
        public int getEndTime() { return endTime; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(word); dest.writeInt(delay); dest.writeInt(startTime); dest.writeInt(endTime); }
        @Override public boolean equals(Object value) {
            if (!(value instanceof EnhancedLRCData)) return false;
            EnhancedLRCData other = (EnhancedLRCData) value;
            return delay == other.delay && startTime == other.startTime && endTime == other.endTime && Objects.equals(word, other.word);
        }
        @Override public int hashCode() { return Objects.hash(word, delay, startTime, endTime); }
    }
}
