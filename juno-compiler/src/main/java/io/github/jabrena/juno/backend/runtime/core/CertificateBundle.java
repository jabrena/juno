package io.github.jabrena.juno.backend.runtime.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trusted root certificates in the binary bundle format the ESP32 core's {@code WiFiClientSecure::setCACertBundle}
 * reads: a big-endian 16-bit count, then for each root its subject name's length and its public key's length (16 bits
 * each), the DER subject name and the DER {@code SubjectPublicKeyInfo}. Roots are sorted by subject name, since the
 * core finds a server chain's issuer with a binary search over them.
 */
final class CertificateBundle {
    private static final int BYTES_PER_LINE = 16;
    private static final int MAX_FIELD = 0xFFFF;
    private static final Pattern PEM_BLOCK =
            Pattern.compile("-----BEGIN CERTIFICATE-----\\R.*?-----END CERTIFICATE-----\\R?", Pattern.DOTALL);

    private final List<Root> roots;

    private CertificateBundle(List<Root> roots) {
        this.roots = roots;
    }

    /** The bundle of every certificate in the PEM text {@code pem}; text between the certificates is ignored. */
    static CertificateBundle fromPem(String pem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            List<Root> roots = new ArrayList<>();
            Matcher block = PEM_BLOCK.matcher(pem);
            while (block.find()) {
                X509Certificate x509 = (X509Certificate) factory.generateCertificate(
                        new ByteArrayInputStream(block.group().getBytes(StandardCharsets.US_ASCII)));
                roots.add(new Root(x509.getSubjectX500Principal().getEncoded(), x509.getPublicKey().getEncoded()));
            }
            roots.sort(Comparator.comparing(Root::subject, Arrays::compareUnsigned));
            return new CertificateBundle(List.copyOf(roots));
        } catch (CertificateException exception) {
            throw new IllegalStateException("Cannot read the trusted root certificates", exception);
        }
    }

    int size() {
        return roots.size();
    }

    byte[] bytes() {
        ByteArrayOutputStream bundle = new ByteArrayOutputStream();
        writeLength(bundle, roots.size());
        for (Root root : roots) {
            writeLength(bundle, root.subject().length);
            writeLength(bundle, root.publicKey().length);
            bundle.writeBytes(root.subject());
            bundle.writeBytes(root.publicKey());
        }
        return bundle.toByteArray();
    }

    /** The bundle as a C++ {@code static const uint8_t name[]} definition, sixteen bytes a line. */
    String cArray(String name) {
        byte[] bytes = bytes();
        StringBuilder array = new StringBuilder("static const uint8_t ").append(name).append("[] = {");
        for (int index = 0; index < bytes.length; index++) {
            array.append(index % BYTES_PER_LINE == 0 ? "\n  " : " ")
                    .append(String.format("0x%02x", bytes[index] & 0xFF))
                    .append(index + 1 < bytes.length ? "," : "");
        }
        return array.append("\n};\n").toString();
    }

    /** A custom PEM trust anchor as a C++ raw string for an ESP32 {@code WiFiClientSecure}. */
    static String cPem(String name, String pem) {
        Matcher blocks = PEM_BLOCK.matcher(pem);
        StringBuilder certificates = new StringBuilder();
        while (blocks.find()) {
            certificates.append(blocks.group().stripTrailing()).append('\n');
        }
        if (certificates.isEmpty()) {
            throw new IllegalStateException("A custom TLS CA must contain at least one PEM certificate");
        }
        String value = certificates.toString();
        if (value.contains(")JUNO_CA\"")) {
            throw new IllegalStateException("A custom TLS CA contains the reserved C++ raw-string delimiter");
        }
        return "static const char " + name + "[] = R\"JUNO_CA(\n" + value + ")JUNO_CA\";\n";
    }

    private static void writeLength(ByteArrayOutputStream bundle, int length) {
        if (length > MAX_FIELD) {
            throw new IllegalStateException("A certificate bundle field of " + length + " bytes does not fit 16 bits");
        }
        bundle.write(length >> 8);
        bundle.write(length & 0xFF);
    }

    /** One trusted root: its DER subject name and DER public key, as the bundle stores them. */
    private static final class Root {
        private final byte[] subject;
        private final byte[] publicKey;

        Root(byte[] subject, byte[] publicKey) {
            this.subject = subject;
            this.publicKey = publicKey;
        }

        byte[] subject() {
            return subject;
        }

        byte[] publicKey() {
            return publicKey;
        }
    }
}
