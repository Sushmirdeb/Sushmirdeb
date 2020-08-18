package io.jsonwebtoken.impl.security


import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.Encoders
import io.jsonwebtoken.security.*
import org.junit.Test

import javax.crypto.SecretKey
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.interfaces.ECKey

import static org.junit.Assert.*

class JwksTest {

    private static final SecretKey SKEY = SignatureAlgorithms.HS256.generateKey();
    private static final KeyPair EC_PAIR = SignatureAlgorithms.ES256.generateKeyPair();

    private static String srandom() {
        byte[] random = new byte[16];
        Randoms.secureRandom().nextBytes(random)
        return Encoders.BASE64URL.encode(random);
    }

    static void testProperty(String name, String id, def val, def expectedFieldValue=val) {
        String cap = "${name.capitalize()}"
        def key = name == 'publicKeyUse' || name == 'x509CertificateChain' ? EC_PAIR.public : SKEY

        //test non-null value:
        def builder = Jwks.builder().setKey(key)
        builder."set${cap}"(val)
        def jwk = builder.build()
        assertEquals val, jwk."get${cap}"()
        assertEquals expectedFieldValue, jwk."${id}"

        //test null value:
        builder = Jwks.builder().setKey(key)
        try {
            builder."set${cap}"(null)
            fail("IAE should have been thrown")
        } catch (IllegalArgumentException ignored) {
        }
        jwk = builder.build()
        assertNull jwk."get${cap}"()
        assertNull jwk."$id"
        assertFalse jwk.containsKey(id)

        //test empty string value
        builder = Jwks.builder().setKey(key)
        if (val instanceof String) {
            try {
                builder."set${cap}"('   ' as String)
                fail("IAE should have been thrown")
            } catch (IllegalArgumentException ignored) {
            }
            jwk = builder.build()
            assertNull jwk."get${cap}"()
            assertNull jwk."$id"
            assertFalse jwk.containsKey(id)
        }

        //test empty value
        if (val instanceof List) {
            val = Collections.emptyList()
        } else if (val instanceof Set) {
            val = Collections.emptySet()
        }
        if (val instanceof Collection) {
            try {
                builder."set${cap}"(val)
                fail("IAE should have been thrown")
            } catch (IllegalArgumentException ignored) {
            }
            jwk = builder.build()
            assertNull jwk."get${cap}"()
            assertNull jwk."$id"
            assertFalse jwk.containsKey(id)
        }
    }

    @Test
    void testBuilderWithSecretKey() {
        def jwk = Jwks.builder().setKey(SKEY).build()
        assertEquals 'oct', jwk.getType()
        assertEquals 'oct', jwk.kty
        assertNotNull jwk.k
        assertTrue jwk.k instanceof String
        assertTrue MessageDigest.isEqual(SKEY.encoded, Decoders.BASE64URL.decode(jwk.k as String))
    }

    @Test
    void testAlgorithm() {
        testProperty('algorithm', 'alg', srandom())
    }

    @Test
    void testId() {
        testProperty('id', 'kid', srandom())
    }

    @Test
    void testOperations() {
        testProperty('operations', 'key_ops', ['foo', 'bar'] as Set<String>)
    }

    @Test
    void testPublicKeyUse() {
        testProperty('publicKeyUse', 'use', srandom())
    }

    @Test
    void testX509CertChain() {
        //get a test cert:
        X509Certificate cert = CertUtils.readTestCertificate(SignatureAlgorithms.RS256)
        def sval = JwkX509StringConverter.INSTANCE.applyTo(cert)
        testProperty('x509CertificateChain', 'x5c', [cert], [sval])
    }

    @Test
    void testSecretJwks() {
        Collection<SecretKeySignatureAlgorithm> algs = SignatureAlgorithms.values().findAll({it instanceof SecretKeySignatureAlgorithm}) as Collection<SecretKeySignatureAlgorithm>
        for(def alg : algs) {
            SecretKey secretKey = alg.generateKey()
            def jwk = Jwks.builder().setKey(secretKey).setId('id').build()
            assertEquals 'oct', jwk.getType()
            assertTrue jwk.containsKey('k')
            assertEquals 'id', jwk.getId()
            assertEquals secretKey, jwk.toKey()
        }
    }

    @Test
    void testAsymmetricJwks() {

        Collection<AsymmetricKeySignatureAlgorithm> algs = SignatureAlgorithms.values().findAll({it instanceof AsymmetricKeySignatureAlgorithm}) as Collection<AsymmetricKeySignatureAlgorithm>

        for(def alg : algs) {

            def pair = alg.generateKeyPair()
            PublicKey pub = pair.getPublic()
            PrivateKey priv = pair.getPrivate()

            // test individual keys
            PublicJwk pubJwk = Jwks.builder().setKey(pub).setPublicKeyUse("sig").build()
            assertEquals pub, pubJwk.toKey()
            PrivateJwk privJwk = Jwks.builder().setKey(priv).setPublicKeyUse("sig").build()
            assertEquals priv, privJwk.toKey()
            PublicJwk privPubJwk = privJwk.toPublicJwk()
            assertEquals pubJwk, privPubJwk
            assertEquals pub, pubJwk.toKey()
            def jwkPair = privJwk.toKeyPair()
            assertEquals pub, jwkPair.getPublic()
            assertEquals priv, jwkPair.getPrivate()

            // test pair
            privJwk = pub instanceof ECKey ?
                    Jwks.builder().setKeyPairEc(pair).setPublicKeyUse("sig").build() :
                    Jwks.builder().setKeyPairRsa(pair).setPublicKeyUse("sig").build()
            assertEquals priv, privJwk.toKey()
            privPubJwk = privJwk.toPublicJwk()
            assertEquals pubJwk, privPubJwk
            assertEquals pub, pubJwk.toKey()
            jwkPair = privJwk.toKeyPair()
            assertEquals pub, jwkPair.getPublic()
            assertEquals priv, jwkPair.getPrivate()
        }
    }
}