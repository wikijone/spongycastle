package org.spongycastle.crypto.tls.test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Vector;

import org.spongycastle.asn1.x509.Certificate;
import org.spongycastle.crypto.tls.AlertLevel;
import org.spongycastle.crypto.tls.CertificateRequest;
import org.spongycastle.crypto.tls.CipherSuite;
import org.spongycastle.crypto.tls.ClientCertificateType;
import org.spongycastle.crypto.tls.DefaultTlsServer;
import org.spongycastle.crypto.tls.HashAlgorithm;
import org.spongycastle.crypto.tls.ProtocolVersion;
import org.spongycastle.crypto.tls.SignatureAlgorithm;
import org.spongycastle.crypto.tls.SignatureAndHashAlgorithm;
import org.spongycastle.crypto.tls.TlsEncryptionCredentials;
import org.spongycastle.crypto.tls.TlsSignerCredentials;
import org.spongycastle.crypto.tls.TlsUtils;
import org.spongycastle.util.Arrays;

class MockTlsServer
    extends DefaultTlsServer
{
    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Exception cause)
    {
        PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
        out.println("TLS server raised alert (AlertLevel." + alertLevel + ", AlertDescription." + alertDescription
            + ")");
        if (message != null)
        {
            out.println("> " + message);
        }
        if (cause != null)
        {
            cause.printStackTrace(out);
        }
    }

    public void notifyAlertReceived(short alertLevel, short alertDescription)
    {
        PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
        out.println("TLS server received alert (AlertLevel." + alertLevel + ", AlertDescription."
            + alertDescription + ")");
    }

    protected int[] getCipherSuites()
    {
        return Arrays.concatenate(super.getCipherSuites(),
            new int[]
            {
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_ESTREAM_SALSA20_SHA1,
                CipherSuite.TLS_ECDHE_RSA_WITH_SALSA20_SHA1,
                CipherSuite.TLS_RSA_WITH_ESTREAM_SALSA20_SHA1,
                CipherSuite.TLS_RSA_WITH_SALSA20_SHA1,
            });
    }

    protected ProtocolVersion getMaximumVersion()
    {
        return ProtocolVersion.TLSv12;
    }

    public ProtocolVersion getServerVersion() throws IOException
    {
        ProtocolVersion serverVersion = super.getServerVersion();

        System.out.println("TLS server negotiated " + serverVersion);

        return serverVersion;
    }

    public CertificateRequest getCertificateRequest() throws IOException
    {
        Vector serverSigAlgs = null;

        if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(serverVersion))
        {
            short[] hashAlgorithms = new short[]{ HashAlgorithm.sha512, HashAlgorithm.sha384, HashAlgorithm.sha256,
                HashAlgorithm.sha224, HashAlgorithm.sha1 };
            short[] signatureAlgorithms = new short[]{ SignatureAlgorithm.rsa };

            serverSigAlgs = new Vector();
            for (int i = 0; i < hashAlgorithms.length; ++i)
            {
                for (int j = 0; j < signatureAlgorithms.length; ++j)
                {
                    serverSigAlgs.addElement(new SignatureAndHashAlgorithm(hashAlgorithms[i],
                        signatureAlgorithms[j]));
                }
            }
        }

        Vector certificateAuthorities = new Vector();
        certificateAuthorities.add(TlsTestUtils.loadCertificateResource("x509-ca.pem").getSubject());

        return new CertificateRequest(new short[]{ ClientCertificateType.rsa_sign }, serverSigAlgs, certificateAuthorities);
    }

    public void notifyClientCertificate(org.spongycastle.crypto.tls.Certificate clientCertificate)
        throws IOException
    {
        Certificate[] chain = clientCertificate.getCertificateList();
        System.out.println("TLS server received client certificate chain of length " + chain.length);
        for (int i = 0; i != chain.length; i++)
        {
            Certificate entry = chain[i];
            // TODO Create fingerprint based on certificate signature algorithm digest
            System.out.println("    fingerprint:SHA-256 " + TlsTestUtils.fingerprint(entry) + " ("
                + entry.getSubject() + ")");
        }
    }

    protected TlsEncryptionCredentials getRSAEncryptionCredentials()
        throws IOException
    {
        return TlsTestUtils.loadEncryptionCredentials(context, new String[]{"x509-server.pem", "x509-ca.pem"},
            "x509-server-key.pem");
    }

    protected TlsSignerCredentials getRSASignerCredentials()
        throws IOException
    {
        /*
         * TODO Note that this code fails to provide default value for the client supported
         * algorithms if it wasn't sent.
         */
        SignatureAndHashAlgorithm signatureAndHashAlgorithm = null;
        Vector sigAlgs = supportedSignatureAlgorithms;
        if (sigAlgs != null)
        {
            for (int i = 0; i < sigAlgs.size(); ++i)
            {
                SignatureAndHashAlgorithm sigAlg = (SignatureAndHashAlgorithm)
                    sigAlgs.elementAt(i);
                if (sigAlg.getSignature() == SignatureAlgorithm.rsa)
                {
                    signatureAndHashAlgorithm = sigAlg;
                    break;
                }
            }

            if (signatureAndHashAlgorithm == null)
            {
                return null;
            }
        }

        return TlsTestUtils.loadSignerCredentials(context, new String[]{"x509-server.pem", "x509-ca.pem"},
            "x509-server-key.pem", signatureAndHashAlgorithm);
    }
}