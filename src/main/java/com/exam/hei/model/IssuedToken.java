package com.exam.hei.model;

/**
 * A freshly signed JWT, together with the lifetime it was issued with.
 *
 * <p>The lifetime travels with the token rather than being recomputed from it, so the {@code
 * expires_in} field of the REST response always matches what {@link
 * com.exam.hei.endpoint.rest.security.JwtService} actually signed.
 */
public record IssuedToken(String token, long expiresInSeconds) {}
