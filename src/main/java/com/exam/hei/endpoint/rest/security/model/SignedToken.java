package com.exam.hei.endpoint.rest.security.model;

import com.exam.hei.repository.model.AppUser;
import java.time.Instant;

public record SignedToken(AppUser user, Instant issuedAt) {}
