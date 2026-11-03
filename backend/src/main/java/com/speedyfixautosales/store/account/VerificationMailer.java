package com.speedyfixautosales.store.account;

/**
 * Sends the emails behind signing up with an email address (EmailVerificationMailer, through Resend). Until the
 * shop's email is set up, it writes them to the server log instead.
 */
public interface VerificationMailer {

    /** The 6-digit code that confirms the shopper owns this address. */
    void sendCode(String email, String name, String code);

    /**
     * Sent instead of a code when the address already has an account, so the sign-up form never reveals which
     * addresses have accounts.
     */
    void sendAlreadyRegistered(String email, CustomerService.SignInMethod method);

    /** The 6-digit code to set a new password ("Forgot password"). */
    void sendResetCode(String email, String name, String code);

    /** False while emails only go to the server log. */
    boolean sendsEmail();
}
