package core.auth;

import core.config.Config;

import core.gui.GuiManager;
import core.messages.Messages;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Class to handle retrieving authentication details from either user input, or the relevant launcher files.
 */
public abstract class AuthDetailsManager {
    private AuthDetails details;
    private String username;

    public AuthDetailsManager() { }

    public AuthDetailsManager(String username) {
        this.username = username;
    }

    private static AuthDetails retrieveDetailsFromProcess() throws IOException {
        return new AuthDetailsFromProcess().getDetails();
    }

    private static AuthDetails retrieveDetailsFromMicrosoft() {
        MicrosoftAuthHandler msAuth = Config.getMicrosoftAuth();
        if (msAuth == null) {
            return AuthDetails.INVALID;
        }
        return msAuth.getAuthDetails();
    }

    /**
     * Get the auth details from the profiles file. If launcher_accounts.json exists, we use that accessToken instead
     * because the other one won't be valid in this case.
     */
    public static AuthDetails loadAuthDetails() throws IOException {
        GuiManager.setStatusMessage(Messages.gui("gui.auth.getting_details"));

        return switch (Config.getAuthMethod()) {
            case AUTOMATIC -> retrieveDetailsFromProcess();
            case MICROSOFT -> retrieveDetailsFromMicrosoft();
            case MANUAL -> Config.getManualAuthDetails();
        };
    }

    public static void validateAuthStatus(Consumer<String> onSuccess, Consumer<String> onError) {
        try {
            AuthDetails details = loadAuthDetails();

            boolean isValid = details.isValid();
            if (isValid) {
                onSuccess.accept(details.getUsername());
            } else {
                AuthenticationMethod method = Config.getAuthMethod();
                onError.accept(method.getErrorMessage());
            }
        } catch (IOException e) {
            onError.accept("Exception occurred: " + e.getMessage());
        }
    }

    public AuthDetails getAuthDetails() throws IOException {
        if (this.details == null) {
            this.details = loadAuthDetails();
        }
        return this.details;
    }

    public void reset() {
        this.details = null;
    }

    protected void printAuthErrorMessage() {
        System.err.println(Messages.console("console.auth.error"));

        if (Config.inGuiMode()) {
            System.err.println(Messages.console("console.auth.gui_help1"));
            System.err.println(Messages.console("console.auth.gui_help2"));
            System.err.println(Messages.console("console.auth.gui_help3"));
        } else {
            System.err.println(Messages.console("console.auth.cli_help1"));
            System.err.println(Messages.console("console.auth.cli_help2"));
        }

        System.err.println(Messages.console("console.auth.wiki"));
        System.err.println();
    }
}