package com.viame.model;

import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;

/**
 * Concrete subtype representing a platform Administrator / Dispatcher.
 * Admins have no dedicated profile table; identity lives entirely in {@code user}.
 */
public class Admin extends User {

    public Admin(long id, String email, String phone, String passwordHash, String passwordSalt, UserStatus status) {
        super(id, email, phone, passwordHash, passwordSalt, UserRole.ADMIN, status);
    }
}

