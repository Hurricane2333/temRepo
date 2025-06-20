package com.ld.reborn.im.websocket;

import lombok.Data;

@Data
public class ImMessage {

    private Integer messageType;

    private String content;

    private Integer fromId;

    private Integer toId;

    private Integer groupId;

    private String avatar;

    private String username;

    // 音视频通话相关字段
    private String callType; // video 或 audio

    private Object offer; // WebRTC offer

    private Object answer; // WebRTC answer

    private Object candidate; // ICE candidate
}
