package com.test.session.idprovider;

import java.security.SecureRandom;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionIdProvider;
import com.test.session.models.SessionConstants;

/**
 * Generates id consisting of random character strings of a given length in
 * bytes.
 *
 * Characters in string are one of following:
 * <code>ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_</code>
 */
@Component(immediate = true, name = SessionConstants.RANDOM_ID_PROVIDER_VALUE)
@Service
public class RandomIdProvider implements SessionIdProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomIdProvider.class);

    private static final int FILLER_CHARACTER_INDEX = 63;
    private static final int MASK_6_BITS = 0x3F;
    private static final int DIVIDE_BY_64 = 6;
    private static final int MULTIPLY_BY_256 = 8;
    private static final int BYTES_IN_BLOCK = 3;
    private static final int CHARACTERS_IN_BLOCK = 4;
    private static final char[] SESSION_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private final SecureRandom random = new SecureRandom();

    @Reference
    private SessionConfigurationService sessionConfigurationService;

    @Override
    public String newId() {
        LOGGER.debug("Creating new ID.");

        final byte[] bytes = new byte[sessionConfigurationService.getSessionIdLength()];
        random.nextBytes(bytes);

        return new String(encode(bytes));
    }

    @Override
    public String readId(String value) {
        LOGGER.debug("Reading the ID {}.", value);
        value = StringUtils.trim(value);

        if (StringUtils.length(value) != getLengthInCharacters()) {
            return null;
        }

        return value;
    }

    private int getLengthInCharacters() {
        return getLengthInCharacters(sessionConfigurationService.getSessionIdLength());
    }

    private static char[] encode(byte[] data) {
        char[] out = new char[getLengthInCharacters(data.length)];
        char[] alphabet = SESSION_ID_ALPHABET;

        // 3 bytes encode to 4 chars. Output is always an even multiple of 4 characters.
        for (int i = 0, index = 0; i < data.length; i++, index += CHARACTERS_IN_BLOCK) {
            boolean quad = false;
            boolean trip = false;

            int val = byteValue(data[i]);
            val <<= MULTIPLY_BY_256;
            i++; // each loop is actually i+3, and we increment counter inside loop

            if (i < data.length) {
                val |= byteValue(data[i]);
                trip = true;
            }

            val <<= MULTIPLY_BY_256;
            i++; // each loop is actually i+3, and we increment counter inside loop

            if (i < data.length) {
                val |= byteValue(data[i]);
                quad = true;
            }

            out[index + 3] = alphabet[(quad ? (val & MASK_6_BITS) : FILLER_CHARACTER_INDEX)]; // 3 is not magic!
            val >>= DIVIDE_BY_64;
            out[index + 2] = alphabet[(trip ? (val & MASK_6_BITS) : FILLER_CHARACTER_INDEX)]; // 2 is not magic!
            val >>= DIVIDE_BY_64;
            out[index + 1] = alphabet[val & MASK_6_BITS];
            val >>= DIVIDE_BY_64;
            out[index] = alphabet[val & MASK_6_BITS];
        }

        return out;
    }

    private static int byteValue(byte data) {
        return 0xFF & data;
    }

    private static int getLengthInCharacters(int len) {
        return ((len + (BYTES_IN_BLOCK - 1)) / BYTES_IN_BLOCK) * CHARACTERS_IN_BLOCK;
    }
}
