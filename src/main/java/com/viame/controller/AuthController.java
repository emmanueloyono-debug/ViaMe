package com.viame.controller;

import com.viame.service.AuthenticationService;

/**
 * Alias controller matching the architecture diagram reference ({@code AuthController}).
 * Inherits all authentication handlers and FXML bindings from {@link LoginController}.
 */
public class AuthController extends LoginController {

    public AuthController() {
        super();
    }

    public AuthController(AuthenticationService authenticationService) {
        super(authenticationService);
    }
}

