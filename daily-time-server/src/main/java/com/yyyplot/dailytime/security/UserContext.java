package com.yyyplot.dailytime.security;

public record UserContext(
        String userId,
        boolean administrator) {

    public static UserContext apiUser(String userId) {
        return new UserContext(userId, false);
    }
}
