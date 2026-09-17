package com.siteflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.IdempotencyKeyRecord;
import com.siteflow.mapper.IdempotencyKeyMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages database-backed idempotency and duplicate submission locks.
 * Guarantees at the database level (via uq_idempotency_keys_value) that
 * duplicate or concurrent identical submissions are detected and rejected.
 */
@Slf4j
@Service
public class IdempotencyService {

    private final IdempotencyKeyMapper idempotencyKeyMapper;

    public IdempotencyService(IdempotencyKeyMapper idempotencyKeyMapper) {
        this.idempotencyKeyMapper = idempotencyKeyMapper;
    }

    /**
     * Attempts to acquire an atomic idempotency lock in a separate transaction.
     * Uses REQUIRES_NEW so the lock is immediately visible to concurrent transactions
     * and remains committed even if the outer business logic rolls back.
     *
     * @param key      the idempotency or fingerprint key
     * @param userId   the user performing the operation
     * @param endpoint the API endpoint being called
     * @return the claimed key
     * @throws IllegalStateException if the key already exists (concurrent/duplicate submission)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String acquireOrThrow(String key, Long userId, String endpoint) {
        if (key == null || key.isBlank()) {
            return null;
        }

        IdempotencyKeyRecord record = IdempotencyKeyRecord.builder()
                .keyValue(key)
                .userId(userId != null ? userId : 0L)
                .endpoint(endpoint != null ? endpoint : "unknown")
                .status("PROCESSING")
                .createdAt(LocalDateTime.now())
                .build();

        try {
            idempotencyKeyMapper.insert(record);
            return key;
        } catch (DataIntegrityViolationException ex) {
            IdempotencyKeyRecord existing = idempotencyKeyMapper.findByKeyValue(key);
            String state = (existing != null) ? existing.getStatus() : "UNKNOWN";
            log.warn("Duplicate submission detected for key '{}' on endpoint '{}' (current state: {})", key, endpoint, state);

            if ("PROCESSING".equals(state)) {
                throw new IllegalStateException("Duplicate submission detected: operation is currently processing.");
            } else if ("COMPLETED".equals(state)) {
                throw new IllegalStateException("Duplicate submission detected: operation was already completed.");
            } else {
                throw new IllegalStateException("Duplicate submission detected for this operation.");
            }
        }
    }

    /**
     * Marks an idempotency lock as completed upon successful business execution.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key) {
        if (key != null && !key.isBlank()) {
            idempotencyKeyMapper.updateStatus(key, "COMPLETED");
        }
    }

    /**
     * Releases (deletes) the lock if the business operation failed due to a transient,
     * validation, or business rule failure, allowing the user to fix their input and retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key) {
        if (key != null && !key.isBlank()) {
            idempotencyKeyMapper.deleteByKeyValue(key);
        }
    }

    /**
     * Derives a deterministic fingerprint key from request content when an explicit
     * Idempotency-Key header is omitted by the client.
     */
    public String buildFingerprint(String operationPrefix, Long userId, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = (userId != null ? userId : 0L) + ":" + payload;
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return operationPrefix + ":" + (userId != null ? userId : 0L) + ":" + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return operationPrefix + ":" + (userId != null ? userId : 0L) + ":" + payload.hashCode();
        }
    }

    /**
     * Resets all keys (useful for test setup).
     */
    public void resetAll() {
        idempotencyKeyMapper.deleteAll();
    }
}
