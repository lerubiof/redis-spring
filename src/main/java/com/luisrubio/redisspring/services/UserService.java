package com.luisrubio.redisspring.services;

import com.luisrubio.redisspring.entities.User;
import com.luisrubio.redisspring.repositories.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<Long, User> userRedisTemplate;

    public UserService(UserRepository userRepository, RedisTemplate<Long, User> userRedisTemplate) {
        this.userRepository = userRepository;
        this.userRedisTemplate = userRedisTemplate;
    }

    public User create(User user) {
        return this.userRepository.save(user);
    }

    public User getUserByid(Long id) {
        User user = this.userRedisTemplate.opsForValue().get(id);

        if(Objects.nonNull(user))
            return user;

        user = this.userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found by id"));

        this.userRedisTemplate.opsForValue()
                .set(id, user);

        return user;
    }

    @Cacheable(value = "user", key = "#id")
    public User getUserByid2(Long id) {
        User user = this.userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found by id"));
        return user;
    }
}
