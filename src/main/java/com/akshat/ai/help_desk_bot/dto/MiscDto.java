package com.akshat.ai.help_desk_bot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiscDto {
    private String message;
    private Map<String, Object> metadata;
}

