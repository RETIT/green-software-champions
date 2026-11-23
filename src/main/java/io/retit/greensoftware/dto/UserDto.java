package io.retit.greensoftware.dto;

import java.util.Set;

/**
 * The logged-in user, as shown by the frontend.
 *
 * @param name  The user name, or null if nobody is logged in, e.g. in dev mode,
 *              where the security checks are disabled.
 * @param roles The user's roles of this application ("user", "admin").
 */
public record UserDto(String name, Set<String> roles) {
}
