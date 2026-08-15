package com.webchat.platformapi.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record FindOrCreateResult(UserEntity user, boolean isNew) {}

    public FindOrCreateResult findOrCreateByPhone(String phone) {
        Optional<UserEntity> existing = userRepository.findByPhoneAndDeletedAtIsNull(phone);
        if (existing.isPresent()) return new FindOrCreateResult(existing.get(), false);

        UserEntity user = new UserEntity();
        user.setPhone(phone);
        try {
            return new FindOrCreateResult(userRepository.save(user), true);
        } catch (DataIntegrityViolationException e) {
            // concurrent create: fallback query
            Optional<UserEntity> fallback = userRepository.findByPhoneAndDeletedAtIsNull(phone);
            if (fallback.isPresent()) return new FindOrCreateResult(fallback.get(), false);
            throw e;
        }
    }

    public FindOrCreateResult findOrCreateByEmail(String email) {
        Optional<UserEntity> existing = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (existing.isPresent()) return new FindOrCreateResult(existing.get(), false);

        UserEntity user = new UserEntity();
        user.setEmail(email);
        try {
            return new FindOrCreateResult(userRepository.save(user), true);
        } catch (DataIntegrityViolationException e) {
            // concurrent create: fallback query
            Optional<UserEntity> fallback = userRepository.findByEmailAndDeletedAtIsNull(email);
            if (fallback.isPresent()) return new FindOrCreateResult(fallback.get(), false);
            throw e;
        }
    }
}
