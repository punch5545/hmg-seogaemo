package com.seogaemo.hmg.core.update;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Accept a normally validated chain from the system or the signed-update CA bundle. */
public final class UpdateTrust {
    private UpdateTrust() {}

    public static X509TrustManager create(InputStream roots) throws Exception {
        TrustManagerFactory system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        system.init((KeyStore) null);
        return either(x509(system.getTrustManagers()), bundled(roots));
    }

    static X509TrustManager bundled(InputStream roots) throws Exception {
        KeyStore bundledStore = KeyStore.getInstance(KeyStore.getDefaultType());
        bundledStore.load(null, null);
        Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509").generateCertificates(roots);
        if (certificates.isEmpty()) throw new CertificateException("empty update CA bundle");
        int index = 0;
        for (Certificate certificate : certificates)
            bundledStore.setCertificateEntry("update-" + index++, certificate);
        TrustManagerFactory bundled = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        bundled.init(bundledStore);
        return x509(bundled.getTrustManagers());
    }

    private static X509TrustManager x509(TrustManager[] managers) throws CertificateException {
        for (TrustManager manager : managers)
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        throw new CertificateException("X.509 trust manager unavailable");
    }

    static X509TrustManager either(final X509TrustManager system, final X509TrustManager bundled) {
        return new X509TrustManager() {
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try { system.checkServerTrusted(chain, authType); }
                catch (CertificateException systemError) {
                    try { bundled.checkServerTrusted(chain, authType); }
                    catch (CertificateException bundledError) {
                        bundledError.addSuppressed(systemError);
                        throw bundledError;
                    }
                }
            }
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                system.checkClientTrusted(chain, authType);
            }
            public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] first = system.getAcceptedIssuers();
                X509Certificate[] second = bundled.getAcceptedIssuers();
                X509Certificate[] all = new X509Certificate[first.length + second.length];
                System.arraycopy(first, 0, all, 0, first.length);
                System.arraycopy(second, 0, all, first.length, second.length);
                return all;
            }
        };
    }
}
