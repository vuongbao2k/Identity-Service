package com.exdev.identity_service.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.exdev.identity_service.constant.PredefinedRole;
import com.exdev.identity_service.dto.request.*;
import com.exdev.identity_service.entity.Role;
import com.exdev.identity_service.repository.httpclient.OutboundIdentityClient;
import com.exdev.identity_service.repository.httpclient.OutboundUserClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.exdev.identity_service.dto.response.AuthenticationResponse;
import com.exdev.identity_service.dto.response.IntrospectResponse;
import com.exdev.identity_service.entity.InvalidatedToken;
import com.exdev.identity_service.entity.User;
import com.exdev.identity_service.exception.AppException;
import com.exdev.identity_service.exception.ErrorCode;
import com.exdev.identity_service.repository.InvalidatedTokenRepository;
import com.exdev.identity_service.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    UserRepository userRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;
    OutboundIdentityClient outboundIdentityClient;
    OutboundUserClient outboundUserClient;

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long VALIDATION_DURATION;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    protected long REFRESHABLE_DURATION;

    @NonFinal
    @Value("${outbound.identity.client-id}")
    protected String CLIENT_ID;

    @NonFinal
    @Value("${outbound.identity.client-secret}")
    protected String CLIENT_SECRET;

    @NonFinal
    @Value("${outbound.identity.redirect-uri}")
    protected String REDIRECT_URI;

    @NonFinal
    protected final String GRANT_TYPE = "authorization_code";

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        var token = request.getToken();
        boolean isValid = true;

        try {
            verifyToken(token, false);
        } catch (AppException e) {
            isValid = false;
        }

        return IntrospectResponse.builder().valid(isValid).build();
    }

    public AuthenticationResponse outboundAuthenticate(String code) {
        var response = outboundIdentityClient.exchangeToken(ExchangeTokenRequest.builder()
                        .code(code)
                        .clientId(CLIENT_ID)
                        .clientSecret(CLIENT_SECRET)
                        .redirectUri(REDIRECT_URI)
                        .grantType(GRANT_TYPE)
                .build());

        log.info("Token response: {}", response);

        // Get user info
        var userInfo = outboundUserClient.getUserInfo("json", response.getAccessToken());
        log.info("User info: {}", userInfo);
        Set<Role> roles = new HashSet<>();
        roles.add(Role.builder().name(PredefinedRole.USER_ROLE).build());

        // Onboard user
        var user = userRepository.findByUsername(userInfo.getEmail()).orElseGet(
                () -> userRepository.save(User.builder()
                                .username(userInfo.getEmail())
                                .firstName(userInfo.getGivenName())
                                .lastName(userInfo.getFamilyName())
                                .roles(roles)
                        .build()));

        var token = generateToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        log.info("Signer key: {}", SIGNER_KEY);
        var user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        var token = generateToken(user);
        return AuthenticationResponse.builder().token(token).authenticated(true).build();
    }

    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {
            var signToken = verifyToken(request.getToken(), true);
            String jti = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();
            InvalidatedToken invalidatedToken =
                    InvalidatedToken.builder().id(jti).expiryTime(expiryTime).build();
            invalidatedTokenRepository.save(invalidatedToken);
        } catch (AppException e) {
            log.info("Token already expired");
        }
    }

    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        var signedJWT = verifyToken(request.getToken(), true);
        var jti = signedJWT.getJWTClaimsSet().getJWTID();
        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        InvalidatedToken invalidatedToken =
                InvalidatedToken.builder().id(jti).expiryTime(expiryTime).build();
        invalidatedTokenRepository.save(invalidatedToken);
        var username = signedJWT.getJWTClaimsSet().getSubject();
        var user =
                userRepository.findByUsername(username).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        var token = generateToken(user);
        return AuthenticationResponse.builder().token(token).authenticated(true).build();
    }

    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());

        SignedJWT signedJWT = SignedJWT.parse(token);
        Date expiration = (isRefresh)
                ? new Date(signedJWT
                        .getJWTClaimsSet()
                        .getIssueTime()
                        .toInstant()
                        .plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS)
                        .toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();
        var verified = signedJWT.verify(verifier);
        if (!(verified && expiration.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        return signedJWT;
    }

    private String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("jabao.com")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now()
                        .plus(VALIDATION_DURATION, ChronoUnit.SECONDS)
                        .toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", buildScope(user))
                .build();
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot generate token", e);
            throw new RuntimeException(e);
        }
    }

    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");
        if (!user.getRoles().isEmpty())
            user.getRoles().forEach(role -> {
                stringJoiner.add("ROLE_" + role.getName());
                if (!CollectionUtils.isEmpty(role.getPermissions()))
                    role.getPermissions().forEach(permission -> stringJoiner.add(permission.getName()));
            });
        return stringJoiner.toString();
    }
}
