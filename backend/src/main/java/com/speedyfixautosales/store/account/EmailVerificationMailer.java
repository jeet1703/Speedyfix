package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.common.UnavailableException;
import com.speedyfixautosales.store.mail.EmailSender;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends the sign-up emails through Resend (the same key as the order emails), from MAIL_ACCOUNT_FROM. They're sent
 * straight away rather than queued, since the shopper is waiting for the code. Without a key, each email is written
 * to the server log instead, code included, so the flow can be tried locally; never switch email sign-in on for the
 * live site like that.
 */
@Service
class EmailVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationMailer.class);

    private final EmailSender sender;
    private final String from;
    private final String accountUrl;
    private final String shopName;

    EmailVerificationMailer(EmailSender sender, @Value("${mail.account-from:}") String from,
                            @Value("${site.public-url:}") String publicUrl, @Value("${site.name:SpeedyFix Auto Sales}") String shopName) {
        this.sender = sender;
        this.from = from.trim();
        this.accountUrl = publicUrl.trim().replaceAll("/+$", "") + "/account";
        this.shopName = shopName;
    }

    @Override
    public void sendCode(String email, String name, String code) {
        if (!sendsEmail()) {
            log.warn("Email isn’t set up, so this wasn’t sent. Sign-up code for {}: {}", email, code);
            return;
        }
        String first = name.trim().split("\\s+")[0];
        String minutes = Long.toString(EmailSignInService.CODE_LIFETIME.toMinutes());
        send(email, code + " is your " + shopName + " sign-up code",
                p("Hi " + esc(first) + ",") + p("Your code to finish signing up is:")
                        + "<p style=\"font-size:30px;font-weight:bold;letter-spacing:6px;margin:16px 0\">" + code + "</p>"
                        + p("It works for " + minutes + " minutes. If you didn’t try to sign up, ignore this email: no account is made without the code."),
                "Hi " + first + ",\n\nYour code to finish signing up is: " + code + "\n\nIt works for " + minutes
                        + " minutes. If you didn't try to sign up, ignore this email: no account is made without the code.\n");
    }

    @Override
    public void sendResetCode(String email, String name, String code) {
        if (!sendsEmail()) {
            log.warn("Email isn’t set up, so this wasn’t sent. Password reset code for {}: {}", email, code);
            return;
        }
        String first = name.trim().split("\\s+")[0];
        String minutes = Long.toString(EmailSignInService.CODE_LIFETIME.toMinutes());
        send(email, code + " is your " + shopName + " password reset code",
                p("Hi " + esc(first) + ",") + p("Your code to set a new password is:")
                        + "<p style=\"font-size:30px;font-weight:bold;letter-spacing:6px;margin:16px 0\">" + code + "</p>"
                        + p("It works for " + minutes + " minutes, and you can ask for one reset code a day. If you didn’t ask to reset your password, ignore this email: your password stays the same."),
                "Hi " + first + ",\n\nYour code to set a new password is: " + code + "\n\nIt works for " + minutes
                        + " minutes, and you can ask for one reset code a day. If you didn't ask to reset your password, ignore this email: your password stays the same.\n");
    }

    @Override
    public void sendAlreadyRegistered(String email, CustomerService.SignInMethod method) {
        if (!sendsEmail()) {
            log.warn("Email isn’t set up, so this wasn’t sent. {} already has an account (signs in with {}).", email, method);
            return;
        }
        String how = method == CustomerService.SignInMethod.GOOGLE
                ? "with Google: use the “Sign in with Google” button" : "with your email address and password";
        send(email, "You already have a " + shopName + " account",
                p("Someone, probably you, tried to sign up with this email address, but it already has an account.")
                        + p("Sign in " + how + " on <a href=\"" + esc(accountUrl) + "\" style=\"color:#F26B1D\">your account page</a>.")
                        + p("If this wasn’t you, you can ignore this email. Nothing has changed."),
                "Someone, probably you, tried to sign up with this email address, but it already has an account.\n\n"
                        + "Sign in " + how.replace('“', '"').replace('”', '"') + ": " + accountUrl
                        + "\n\nIf this wasn't you, you can ignore this email. Nothing has changed.\n");
    }

    @Override
    public boolean sendsEmail() {
        return sender.enabled();
    }

    private void send(String to, String subject, String body, String text) {
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#111;max-width:560px;margin:0 auto\">"
                + "<div style=\"background:#111;color:#fff;padding:16px 24px;border-bottom:5px solid #F26B1D\">"
                + "<strong style=\"font-size:20px;letter-spacing:1px\">SPEEDY<span style=\"color:#F26B1D\">FIX</span></strong></div>"
                + "<div style=\"padding:8px 24px 20px\">" + body + "</div></div>";
        try {
            sender.send(new EmailSender.Email(to, subject, html, text, List.of(), from), UUID.randomUUID().toString());
        } catch (IOException e) {
            log.warn("Sign-up email to {} not sent: {}", to, e.getMessage());
            throw new UnavailableException("We couldn’t send the email just now. Try again in a minute.");
        }
    }

    private static String p(String html) {
        return "<p style=\"font-size:15px;line-height:1.5;margin:14px 0\">" + html + "</p>";
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }
}
