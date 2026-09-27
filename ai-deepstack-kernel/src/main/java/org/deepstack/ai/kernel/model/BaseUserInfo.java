package org.deepstack.ai.kernel.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class BaseUserInfo implements Serializable {
    private Long userId;
    private String username;
}
