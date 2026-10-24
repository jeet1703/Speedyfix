package com.speedyfixautosales.store.account;

/** Checks a Google Sign-In ID token (the "credential" Google's button hands the browser). */
public interface GoogleTokenVerifier {

    /** Who signed in with Google: sub is Google's stable id for the account. */
    record GoogleIdentity(String sub, String email, String name, String pictureUrl) {
    }

    /** False until GOOGLE_CLIENT_ID is set on the server. */
    boolean enabled();

    /** @throws com.speedyfixautosales.store.common.UnauthorizedException if the token isn't a valid, current Google sign-in for this site */
    GoogleIdentity verify(String credential);
}
