package com.zhiyu.ufp.auth.webauthn;

public record WebAuthnStartResult(String challengeId, String optionsJson) { }
