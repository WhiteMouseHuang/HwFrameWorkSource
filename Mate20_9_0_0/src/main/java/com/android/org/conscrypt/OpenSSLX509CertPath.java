package com.android.org.conscrypt;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class OpenSSLX509CertPath extends CertPath {
    private static final List<String> ALL_ENCODINGS = Collections.unmodifiableList(Arrays.asList(new String[]{Encoding.PKI_PATH.apiName, Encoding.PKCS7.apiName}));
    private static final Encoding DEFAULT_ENCODING = Encoding.PKI_PATH;
    private static final byte[] PKCS7_MARKER = new byte[]{(byte) 45, (byte) 45, (byte) 45, (byte) 45, (byte) 45, (byte) 66, (byte) 69, (byte) 71, (byte) 73, (byte) 78, (byte) 32, (byte) 80, (byte) 75, (byte) 67, (byte) 83, (byte) 55};
    private static final int PUSHBACK_SIZE = 64;
    private static final long serialVersionUID = -3249106005255170761L;
    private final List<? extends X509Certificate> mCertificates;

    private enum Encoding {
        PKI_PATH("PkiPath"),
        PKCS7("PKCS7");
        
        private final String apiName;

        private Encoding(String apiName) {
            this.apiName = apiName;
        }

        static Encoding findByApiName(String apiName) throws CertificateEncodingException {
            for (Encoding element : values()) {
                if (element.apiName.equals(apiName)) {
                    return element;
                }
            }
            return null;
        }
    }

    static Iterator<String> getEncodingsIterator() {
        return ALL_ENCODINGS.iterator();
    }

    OpenSSLX509CertPath(List<? extends X509Certificate> certificates) {
        super("X.509");
        this.mCertificates = certificates;
    }

    public List<? extends Certificate> getCertificates() {
        return Collections.unmodifiableList(this.mCertificates);
    }

    private byte[] getEncoded(Encoding encoding) throws CertificateEncodingException {
        OpenSSLX509Certificate[] certs = new OpenSSLX509Certificate[this.mCertificates.size()];
        long[] certRefs = new long[certs.length];
        int i = 0;
        for (int j = certs.length - 1; j >= 0; j--) {
            X509Certificate cert = (X509Certificate) this.mCertificates.get(i);
            if (cert instanceof OpenSSLX509Certificate) {
                certs[j] = (OpenSSLX509Certificate) cert;
            } else {
                certs[j] = OpenSSLX509Certificate.fromX509Der(cert.getEncoded());
            }
            certRefs[j] = certs[j].getContext();
            i++;
        }
        switch (encoding) {
            case PKI_PATH:
                return NativeCrypto.ASN1_seq_pack_X509(certRefs);
            case PKCS7:
                return NativeCrypto.i2d_PKCS7(certRefs);
            default:
                throw new CertificateEncodingException("Unknown encoding");
        }
    }

    public byte[] getEncoded() throws CertificateEncodingException {
        return getEncoded(DEFAULT_ENCODING);
    }

    public byte[] getEncoded(String encoding) throws CertificateEncodingException {
        Encoding enc = Encoding.findByApiName(encoding);
        if (enc != null) {
            return getEncoded(enc);
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Invalid encoding: ");
        stringBuilder.append(encoding);
        throw new CertificateEncodingException(stringBuilder.toString());
    }

    public Iterator<String> getEncodings() {
        return getEncodingsIterator();
    }

    private static CertPath fromPkiPathEncoding(InputStream inStream) throws CertificateException {
        OpenSSLBIOInputStream bis = new OpenSSLBIOInputStream(inStream, true);
        boolean markable = inStream.markSupported();
        if (markable) {
            inStream.mark(PUSHBACK_SIZE);
        }
        try {
            long[] certRefs = NativeCrypto.ASN1_seq_unpack_X509_bio(bis.getBioContext());
            bis.release();
            if (certRefs == null) {
                return new OpenSSLX509CertPath(Collections.emptyList());
            }
            List<OpenSSLX509Certificate> certs = new ArrayList(certRefs.length);
            int i = certRefs.length - 1;
            while (true) {
                int i2 = i;
                if (i2 < 0) {
                    return new OpenSSLX509CertPath(certs);
                }
                if (certRefs[i2] != 0) {
                    try {
                        certs.add(new OpenSSLX509Certificate(certRefs[i2]));
                    } catch (ParsingException e) {
                        throw new CertificateParsingException(e);
                    }
                }
                i = i2 - 1;
            }
        } catch (Exception e2) {
            if (markable) {
                try {
                    inStream.reset();
                } catch (IOException e3) {
                }
            }
            throw new CertificateException(e2);
        } catch (Throwable th) {
            bis.release();
        }
    }

    private static CertPath fromPkcs7Encoding(InputStream inStream) throws CertificateException {
        if (inStream != null) {
            try {
                if (inStream.available() != 0) {
                    boolean markable = inStream.markSupported();
                    if (markable) {
                        inStream.mark(PUSHBACK_SIZE);
                    }
                    PushbackInputStream pbis = new PushbackInputStream(inStream, PUSHBACK_SIZE);
                    try {
                        byte[] buffer = new byte[PKCS7_MARKER.length];
                        int len = pbis.read(buffer);
                        if (len >= 0) {
                            pbis.unread(buffer, 0, len);
                            if (len == PKCS7_MARKER.length && Arrays.equals(PKCS7_MARKER, buffer)) {
                                return new OpenSSLX509CertPath(OpenSSLX509Certificate.fromPkcs7PemInputStream(pbis));
                            }
                            return new OpenSSLX509CertPath(OpenSSLX509Certificate.fromPkcs7DerInputStream(pbis));
                        }
                        throw new ParsingException("inStream is empty");
                    } catch (Exception e) {
                        if (markable) {
                            try {
                                inStream.reset();
                            } catch (IOException e2) {
                            }
                        }
                        throw new CertificateException(e);
                    }
                }
            } catch (IOException e3) {
                throw new CertificateException("Problem reading input stream", e3);
            }
        }
        return new OpenSSLX509CertPath(Collections.emptyList());
    }

    private static CertPath fromEncoding(InputStream inStream, Encoding encoding) throws CertificateException {
        switch (encoding) {
            case PKI_PATH:
                return fromPkiPathEncoding(inStream);
            case PKCS7:
                return fromPkcs7Encoding(inStream);
            default:
                throw new CertificateEncodingException("Unknown encoding");
        }
    }

    static CertPath fromEncoding(InputStream inStream, String encoding) throws CertificateException {
        if (inStream != null) {
            Encoding enc = Encoding.findByApiName(encoding);
            if (enc != null) {
                return fromEncoding(inStream, enc);
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Invalid encoding: ");
            stringBuilder.append(encoding);
            throw new CertificateException(stringBuilder.toString());
        }
        throw new CertificateException("inStream == null");
    }

    static CertPath fromEncoding(InputStream inStream) throws CertificateException {
        if (inStream != null) {
            return fromEncoding(inStream, DEFAULT_ENCODING);
        }
        throw new CertificateException("inStream == null");
    }
}
