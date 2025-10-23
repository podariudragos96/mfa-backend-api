package org.mfa.service;

import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioVerifyService {

    private final String accountSid;
    private final String authToken;
    private final String verifyServiceSid;

    public TwilioVerifyService(
            @Value("${twilio.account-sid}") String accountSid,
            @Value("${twilio.auth-token}") String authToken,
            @Value("${twilio.verify.service-sid}") String verifyServiceSid) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.verifyServiceSid = verifyServiceSid;
        Twilio.init(accountSid, authToken);
    }

    public void sendSms(String toE164) {
        Verification.creator(verifyServiceSid, toE164, "sms").create();
    }

    public boolean checkCode(String toE164, String code) {
        var check = VerificationCheck.creator(verifyServiceSid)
                .setTo(toE164)
                .setCode(code)
                .create();
        return "approved".equalsIgnoreCase(check.getStatus());
    }
}
