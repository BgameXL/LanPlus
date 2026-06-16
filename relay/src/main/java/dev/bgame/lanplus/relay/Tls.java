package dev.bgame.lanplus.relay;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class Tls {

    private Tls() {}

    static SSLContext fromPem(RelayConfig cfg) throws Exception {
        List<X509Certificate> chain = new ArrayList<>();
        try (InputStream in = Files.newInputStream(Path.of(cfg.certPath))) {
            for (Certificate c : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                chain.add((X509Certificate) c);
            }
        }
        if (chain.isEmpty()) {
            throw new IllegalStateException("no certificates found in " + cfg.certPath);
        }
        PrivateKey key = readPrivateKey(Path.of(cfg.keyPath));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("relay", key, new char[0], chain.toArray(new Certificate[0]));

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    private static PrivateKey readPrivateKey(Path path) throws Exception {
        String base64 = Files.readString(path)
                .replaceAll("-----BEGIN [^-]*-----", "")
                .replaceAll("-----END [^-]*-----", "")
                .replaceAll("\\s", "");
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64));
        for (String alg : new String[]{"RSA", "EC"}) {
            try {
                return KeyFactory.getInstance(alg).generatePrivate(spec);
            } catch (Exception ignore) {
            }
        }
        throw new IllegalArgumentException("unsupported private key in " + path + " (need PKCS#8 RSA or EC)");
    }
}
