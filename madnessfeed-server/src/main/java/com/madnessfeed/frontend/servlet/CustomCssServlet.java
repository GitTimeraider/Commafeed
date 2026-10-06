package com.madnessfeed.frontend.servlet;

import com.madnessfeed.backend.dao.UserSettingsDAO;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.model.UserSettings;
import com.madnessfeed.security.AuthenticationContext;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import lombok.RequiredArgsConstructor;

import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Objects;

@Path("/custom_css.css")
@Produces("text/css")
@RequiredArgsConstructor
@Singleton
public class CustomCssServlet {

    private final AuthenticationContext authenticationContext;
    private final UserSettingsDAO userSettingsDAO;

    @GET
    @Transactional
    @Operation(hidden = true)
    public String get() {
        User user = authenticationContext.getCurrentUser();
        if (user == null) {
            return "";
        }

        UserSettings settings = userSettingsDAO.findByUser(user);
        if (settings == null) {
            return "";
        }

        return Objects.toString(settings.getCustomCss(), "");
    }
}
