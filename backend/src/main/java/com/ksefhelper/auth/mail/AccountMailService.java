package com.ksefhelper.auth.mail;

import com.ksefhelper.users.entity.User;

public interface AccountMailService {
    void sendVerification(User user, String token);

    void sendPasswordReset(User user, String token);
}
