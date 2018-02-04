package com.test.session.serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SerializerDeserializerService;
import com.test.session.api.SessionConfigurationService;

/**
 * Encrypts and decrypts session data before storing it in session repository.
 * Session is encrypted using AES/CBC/PKCS5Padding transformation. The
 * implementation delegates serializing and deserializing to a wrapped
 * {@link SerializerDeserializerService}.
 * <p>
 * The key must be provided either by calling {@link #initKey(String)} or via
 * configuration property.
 */
@Component(immediate = true)
@Service
public class SerialiserDeserialiserServiceImpl implements SerializerDeserializerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerialiserDeserialiserServiceImpl.class);

    // TODO: For now using JDK serialiser, need to switch back to JSON
    // private static final Gson GSON = new Gson();

    private SecureRandom random = new SecureRandom();

    @Reference
    private SessionConfigurationService configurationService;

    @Override
    public byte[] serialize(Object value) {
        byte[] arrayToEncrypt = jdkSerialise(value);

        if (!configurationService.isUsingEncryption()) {
            return arrayToEncrypt;
        }

        byte[] iv = new byte[16];
        random.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, initKey(configurationService.getEncryptionKey()), new IvParameterSpec(iv));

            ByteBuffer output = ByteBuffer.allocate(iv.length + cipher.getOutputSize(arrayToEncrypt.length));
            output.put(iv);
            cipher.doFinal(ByteBuffer.wrap(arrayToEncrypt), output);

            return output.array();
        } catch (Exception e) {
            LOGGER.error("Unable to encrypt data.", e);
            return arrayToEncrypt;
        }
    }

    @Override
    public Object deserialize(byte[] data) {
        if (!configurationService.isUsingEncryption()) {
            return jdkDeserialize(data);
        }

        byte[] iv = new byte[16];
        System.arraycopy(data, 0, iv, 0, iv.length);
        byte[] decrypted = null;

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, initKey(configurationService.getEncryptionKey()), new IvParameterSpec(iv));

            decrypted = cipher.doFinal(data, iv.length, data.length - iv.length);
        } catch (Exception e) {
            LOGGER.error("Unable to decrypt data.", e);
        }

        return jdkDeserialize(decrypted);
    }

    private static SecretKeySpec initKey(String key) throws Exception {
        try {
            byte[] keyArray = key.getBytes("UTF-8");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");

            keyArray = sha.digest(keyArray);
            keyArray = Arrays.copyOf(keyArray, 16);

            return new SecretKeySpec(keyArray, "AES");
        } catch (Exception e) {
            LOGGER.error("Key cannot be initialised. Falling back to no encryption mode.", e);
            throw e;
        }
    }

    private byte[] jdkSerialise(Object value) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(value);
            byte[] ba = bos.toByteArray();

            return ba;
        } catch (IOException e) {
            LOGGER.error("Unable to serialize object. See stacktrace for more information.", e);
            return null;
        }
    }

    private Object jdkDeserialize(byte[] data) {
        if (data == null) {
            return null;
        }

        // For deserialisation objects we use specific class loader of the OSGi bundle
        // to insure it was the same one used when creating serialized objects.
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        try (ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream is = new ClassLoaderObjectInputStream(Thread.currentThread().getContextClassLoader(), in)) {
            Object obj = is.readObject();

            return obj;
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.error("Unable to deserialize object. See stacktrace for more information.", e);
            return null;
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }
}
