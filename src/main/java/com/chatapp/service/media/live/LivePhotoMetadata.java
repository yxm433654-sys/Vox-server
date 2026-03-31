package com.chatapp.service.media.live;

import java.io.File;

public class LivePhotoMetadata {
    private String assetIdentifier;
    private String contentIdentifier;
    private File jpegFile;
    private File movFile;
    private float duration;
    private boolean verified;

    public String getAssetIdentifier() {
        return assetIdentifier;
    }

    public void setAssetIdentifier(String assetIdentifier) {
        this.assetIdentifier = assetIdentifier;
    }

    public String getContentIdentifier() {
        return contentIdentifier;
    }

    public void setContentIdentifier(String contentIdentifier) {
        this.contentIdentifier = contentIdentifier;
    }

    public File getJpegFile() {
        return jpegFile;
    }

    public void setJpegFile(File jpegFile) {
        this.jpegFile = jpegFile;
    }

    public File getMovFile() {
        return movFile;
    }

    public void setMovFile(File movFile) {
        this.movFile = movFile;
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
