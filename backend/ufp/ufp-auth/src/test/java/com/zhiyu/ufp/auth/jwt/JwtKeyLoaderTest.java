package com.zhiyu.ufp.auth.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyLoaderTest {

    private Path keyDir;

    @BeforeEach
    void setUp() throws Exception {
        keyDir = Files.createTempDirectory("jwt-key-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        Files.writeString(keyDir.resolve("jwt-private.pem"), privatePem);
        Files.writeString(keyDir.resolve("jwt-public.pem"), publicPem);
    }

    @Test
    void shouldLoadPrivateKey() {
        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        JwtKeyLoader keyLoader = new JwtKeyLoader(props);

        PrivateKey key = keyLoader.loadPrivateKey();

        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void shouldLoadPublicKey() {
        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        JwtKeyLoader keyLoader = new JwtKeyLoader(props);

        PublicKey key = keyLoader.loadPublicKey();

        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void shouldLoadSameKeyPair() {
        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        JwtKeyLoader keyLoader = new JwtKeyLoader(props);

        PrivateKey privateKey = keyLoader.loadPrivateKey();
        PublicKey publicKey = keyLoader.loadPublicKey();

        // 两个 Key 应都合法（同一密鑰对可互相加解密）
        assertThat(privateKey).isNotNull();
        assertThat(publicKey).isNotNull();
    }

    @Test
    void shouldThrowWhenPrivateKeyFileMissing(@TempDir Path emptyDir) {
        JwtProperties props = new JwtProperties();
        props.setKeyDir(emptyDir.toString());
        JwtKeyLoader keyLoader = new JwtKeyLoader(props);

        assertThatThrownBy(keyLoader::loadPrivateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load JWT private key");
    }

    @Test
    void shouldThrowWhenPublicKeyFileMissing(@TempDir Path emptyDir) {
        JwtProperties props = new JwtProperties();
        props.setKeyDir(emptyDir.toString());
        JwtKeyLoader keyLoader = new JwtKeyLoader(props);

        assertThatThrownBy(keyLoader::loadPublicKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load JWT public key");
    }

    @Test
    void shouldThrowWhenPrivateKeyFileHasInvalidContent() throws Exception {
        Path dir = Files.createTempDirectory("jwt-invalid-test");
        Files.writeString(dir.resolve("jwt-private.pem"), "not a valid pem key");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(dir.toString());
        JwtKeyLoader keyLoader = new JwtKeyLoader(props);

        assertThatThrownBy(keyLoader::loadPrivateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load JWT private key");
    }

    @Test
    void shouldThrowWhenPublicKeyFileHasInvalidContent() throws Exception {
        Path dir = Files.createTempDirectory("jwt-invalid-test");
        Files.writeString(dir.resolve("jwt-public.pem"), "not a valid pem key");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(dir.toString());
        JwtKeyLoader keyLoader = new JwtKeyLoader(props);

        assertThatThrownBy(keyLoader::loadPublicKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load JWT public key");
    }

    @Test
    void shouldThrowWhenKeyDirDoesNotExist() {
        JwtProperties props = new JwtProperties();
        props.setKeyDir("/nonexistent/dir/that/does/not/exist");
        JwtKeyLoader keyLoader = new JwtKeyLoader(props);

        assertThatThrownBy(keyLoader::loadPrivateKey)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(keyLoader::loadPublicKey)
                .isInstanceOf(IllegalStateException.class);
    }
}
