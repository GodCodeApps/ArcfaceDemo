package com.arcsoft.arcfacedemo.model;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Generated;

@Entity
public class UserInfo {
    @Id(autoincrement = true)
    private Long Id;
    private int faceId;
    private long updateTime;
    private boolean isOpen;
    private int boxSize;//0,1,2
    private int boxNumber;
    private int vip;//0,1,2,3
    private String faceImageUri;
    private String fileName;
    @Generated(hash = 264016716)
    public UserInfo(Long Id, int faceId, long updateTime, boolean isOpen,
            int boxSize, int boxNumber, int vip, String faceImageUri,
            String fileName) {
        this.Id = Id;
        this.faceId = faceId;
        this.updateTime = updateTime;
        this.isOpen = isOpen;
        this.boxSize = boxSize;
        this.boxNumber = boxNumber;
        this.vip = vip;
        this.faceImageUri = faceImageUri;
        this.fileName = fileName;
    }
    @Generated(hash = 1279772520)
    public UserInfo() {
    }
    public Long getId() {
        return this.Id;
    }
    public void setId(Long Id) {
        this.Id = Id;
    }
    public int getFaceId() {
        return this.faceId;
    }
    public void setFaceId(int faceId) {
        this.faceId = faceId;
    }
    public long getUpdateTime() {
        return this.updateTime;
    }
    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }
    public boolean getIsOpen() {
        return this.isOpen;
    }
    public void setIsOpen(boolean isOpen) {
        this.isOpen = isOpen;
    }
    public int getBoxSize() {
        return this.boxSize;
    }
    public void setBoxSize(int boxSize) {
        this.boxSize = boxSize;
    }
    public int getBoxNumber() {
        return this.boxNumber;
    }
    public void setBoxNumber(int boxNumber) {
        this.boxNumber = boxNumber;
    }
    public int getVip() {
        return this.vip;
    }
    public void setVip(int vip) {
        this.vip = vip;
    }
    public String getFaceImageUri() {
        return this.faceImageUri;
    }
    public void setFaceImageUri(String faceImageUri) {
        this.faceImageUri = faceImageUri;
    }
    public String getFileName() {
        return this.fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

}
