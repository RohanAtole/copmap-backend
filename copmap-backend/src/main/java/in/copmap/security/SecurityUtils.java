package in.copmap.security;

import in.copmap.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility to access the currently authenticated principal from anywhere in the application.
 */
@Component
public class SecurityUtils {

    public static User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return (User) auth.getPrincipal();
    }

    public static boolean hasRole(User.UserRole role) {
        return getCurrentUser().getRole() == role;
    }

    public static boolean isStationOfficerOrAbove() {
        User.UserRole role = getCurrentUser().getRole();
        return role == User.UserRole.STATION_OFFICER || role == User.UserRole.SUPER_ADMIN;
    }
}
