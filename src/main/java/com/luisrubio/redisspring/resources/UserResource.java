package com.luisrubio.redisspring.resources;

import com.luisrubio.redisspring.entities.User;
import com.luisrubio.redisspring.services.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("user")
public class UserResource {

    private final UserService userService;

    public UserResource(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return userService.create(user);
    }

    @GetMapping("/{id}")
    public User getUserByid(@PathVariable Long id) {
        return this.userService.getUserByid(id);
    }

    @GetMapping("/short-way/{id}")
    public User getUserByid2(@PathVariable Long id) {
        return this.userService.getUserByid2(id);
    }
}
