package org.bouncycastle.jcajce.provider.symmetric;

import java.io.IOException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import javax.crypto.spec.PBEParameterSpec;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PKCS12PBEParams;
import org.bouncycastle.jcajce.provider.config.ConfigurableProvider;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseAlgorithmParameters;
import org.bouncycastle.jcajce.provider.util.AlgorithmProvider;

public class PBEPKCS12 {

    public static class AlgParams extends BaseAlgorithmParameters {
        PKCS12PBEParams params;

        protected byte[] engineGetEncoded() {
            try {
                return this.params.getEncoded(ASN1Encoding.DER);
            } catch (IOException e) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Oooops! ");
                stringBuilder.append(e.toString());
                throw new RuntimeException(stringBuilder.toString());
            }
        }

        protected byte[] engineGetEncoded(String str) {
            return isASN1FormatString(str) ? engineGetEncoded() : null;
        }

        protected void engineInit(AlgorithmParameterSpec algorithmParameterSpec) throws InvalidParameterSpecException {
            if (algorithmParameterSpec instanceof PBEParameterSpec) {
                PBEParameterSpec pBEParameterSpec = (PBEParameterSpec) algorithmParameterSpec;
                this.params = new PKCS12PBEParams(pBEParameterSpec.getSalt(), pBEParameterSpec.getIterationCount());
                return;
            }
            throw new InvalidParameterSpecException("PBEParameterSpec required to initialise a PKCS12 PBE parameters algorithm parameters object");
        }

        protected void engineInit(byte[] bArr) throws IOException {
            this.params = PKCS12PBEParams.getInstance(ASN1Primitive.fromByteArray(bArr));
        }

        protected void engineInit(byte[] bArr, String str) throws IOException {
            if (isASN1FormatString(str)) {
                engineInit(bArr);
                return;
            }
            throw new IOException("Unknown parameters format in PKCS12 PBE parameters object");
        }

        protected String engineToString() {
            return "PKCS12 PBE Parameters";
        }

        protected AlgorithmParameterSpec localEngineGetParameterSpec(Class cls) throws InvalidParameterSpecException {
            if (cls == PBEParameterSpec.class) {
                return new PBEParameterSpec(this.params.getIV(), this.params.getIterations().intValue());
            }
            throw new InvalidParameterSpecException("unknown parameter spec passed to PKCS12 PBE parameters object.");
        }
    }

    public static class Mappings extends AlgorithmProvider {
        private static final String PREFIX = PBEPKCS12.class.getName();

        public void configure(ConfigurableProvider configurableProvider) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(PREFIX);
            stringBuilder.append("$AlgParams");
            configurableProvider.addAlgorithm("AlgorithmParameters.PKCS12PBE", stringBuilder.toString());
        }
    }

    private PBEPKCS12() {
    }
}