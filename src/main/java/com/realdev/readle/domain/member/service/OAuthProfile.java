package com.realdev.readle.domain.member.service;

import com.realdev.readle.domain.member.entity.OAuthProvider;

public record OAuthProfile(
    OAuthProvider provider,
    String subject,
    String email,
    String displayName,
    String profileImageUrl) {}
