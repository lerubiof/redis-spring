package com.luisrubio.redisspring.dtos;

import java.io.Serializable;

public record UserDTO(
        String name,
        String lastname,
        Integer age
) implements Serializable {

}
