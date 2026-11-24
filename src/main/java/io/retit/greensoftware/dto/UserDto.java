package io.retit.greensoftware.dto;

import java.util.Set;

/**
 * The logged-in user.
 *
 * @param name  null if nobody is logged in, which only happens in dev mode
 * @param roles the user's roles of this application: "user", "admin"
 */
public record UserDto(String name, Set<String> roles) {
}
