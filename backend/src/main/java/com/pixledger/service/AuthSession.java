package com.pixledger.service;

import com.pixledger.domain.UserDO;

public record AuthSession(String accessToken, String refreshToken, UserDO user) {}
