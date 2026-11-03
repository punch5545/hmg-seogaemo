package com.seogaemo.hmg.core.update;

import java.io.FileInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import javax.net.ssl.X509TrustManager;

public final class UpdateTrustTest {
    private static final X509TrustManager REJECT = new X509TrustManager() {
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("system trust unavailable");
        }
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("client trust unavailable");
        }
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    public static void main(String[] args) throws Exception {
        FileInputStream roots = new FileInputStream(args[0]);
        X509TrustManager bundled;
        try { bundled = UpdateTrust.bundled(roots); }
        finally { roots.close(); }
        FileInputStream countInput = new FileInputStream(args[0]);
        try {
            if (CertificateFactory.getInstance("X.509").generateCertificates(countInput).size() != 9)
                throw new AssertionError("CA bundle did not parse");
        } finally { countInput.close(); }
        X509TrustManager either = UpdateTrust.either(REJECT, bundled);
        for (int i = 1; i < args.length; i++) {
            FileInputStream file = new FileInputStream(args[i]);
            Collection<? extends Certificate> parsed;
            try { parsed = CertificateFactory.getInstance("X.509").generateCertificates(file); }
            finally { file.close(); }
            X509Certificate[] chain = parsed.toArray(new X509Certificate[parsed.size()]);
            either.checkServerTrusted(chain, "ECDHE_ECDSA");
            try { REJECT.checkServerTrusted(chain, "EC"); throw new AssertionError("rejecting manager accepted chain"); }
            catch (CertificateException expected) { /* fallback above is required */ }
        }
        System.out.println("update CA bundle and saved GitHub chains validated");
    }
}
