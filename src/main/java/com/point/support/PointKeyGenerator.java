package com.point.support;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PointKeyGenerator {

    public String generate(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
