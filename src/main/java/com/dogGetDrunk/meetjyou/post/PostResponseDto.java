package com.dogGetDrunk.meetjyou.post;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PostResponseDto {
    private LocalDateTime createdAt;
    private LocalDateTime lastEditedAt;
    private int postStatus;
    private String title;
    private String body;
    private String preview;
    private int views;
}
