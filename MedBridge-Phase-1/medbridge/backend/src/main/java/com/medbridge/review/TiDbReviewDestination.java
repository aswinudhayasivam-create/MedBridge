package com.medbridge.review;

import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

/** Immutable build-time destination approval; environment variables cannot broaden it. */
final class TiDbReviewDestination {
    record Approval(String host, int port, String prefix) {}
    static Approval approved() {
        try (InputStream in=TiDbReviewDestination.class.getResourceAsStream("/tidb-review-destination.properties")) {
            if(in==null) throw new IllegalStateException();
            Properties p=new Properties();p.load(in);
            return new Approval(p.getProperty("host",""),Integer.parseInt(p.getProperty("port","0")),p.getProperty("username-prefix",""));
        } catch(Exception invalid) { throw new IllegalStateException("TiDB review destination is not approved."); }
    }
    static void require(String url, String configuredHost, String username) {
        require(approved(),url,configuredHost,username);
    }
    static void require(Approval pin,String url,String configuredHost,String username) {
        try {
            if(!pin.host().matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.tidbcloud\\.com") || pin.port()!=4000 || !pin.prefix().matches("[A-Za-z0-9]{1,32}")) throw new IllegalArgumentException();
            String expected="jdbc:mysql://"+pin.host()+":"+pin.port()+"/"+ReviewModePolicy.DATABASE+"?sslMode=VERIFY_IDENTITY&serverTimezone=UTC";
            if(!expected.equals(url) || !pin.host().equals(configuredHost) || !username.matches(java.util.regex.Pattern.quote(pin.prefix())+"\\.mb_review_[a-z0-9_]{1,32}")) throw new IllegalArgumentException();
            URI uri=URI.create(url.substring(5));
            if(!pin.host().equals(uri.getHost()) || uri.getUserInfo()!=null || uri.getFragment()!=null) throw new IllegalArgumentException();
        } catch(RuntimeException invalid) { throw new IllegalStateException("TiDB review destination is not approved."); }
    }
}
