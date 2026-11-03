package com.viame.util;

import com.viame.model.Admin;
import com.viame.model.Passenger;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager RBAC Singleton Tests")
class SessionManagerTest {

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().logout();
    }

    @Test
    @DisplayName("Tracks authenticated user and enforces role-based access control")
    void testLoginAndRoleEnforcement() {
        SessionManager session = SessionManager.getInstance();
        session.logout();
        assertFalse(session.isAuthenticated());

        Passenger alice = new Passenger(4L, "alice@viame.cm", "+237670000004", "h", "s",
                UserStatus.ACTIVE, "Alice Mengue", PaymentMethod.WALLET, BigDecimal.TEN);
        session.login(alice);

        assertTrue(session.isAuthenticated());
        assertTrue(session.hasRole(UserRole.PASSENGER));
        assertFalse(session.hasRole(UserRole.ADMIN));
        assertEquals(alice, session.requireRole(UserRole.PASSENGER));
        assertThrows(SecurityException.class, () -> session.requireRole(UserRole.ADMIN));

        Admin admin = new Admin(1L, "admin@viame.cm", "+237670000001", "h", "s", UserStatus.ACTIVE);
        session.login(admin);
        assertEquals(admin, session.requireRole(UserRole.ADMIN));
    }
}

