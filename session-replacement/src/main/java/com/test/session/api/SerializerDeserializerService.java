package com.test.session.api;

/**
 * Implementations of this interface provide serialization/deserialization logic
 * for objects in session.
 */
public interface SerializerDeserializerService {
    /**
     * Serializes object into a byte array.
     *
     * @param value
     *            the object to serialize
     * @return byte array containing serialized object
     */
    byte[] serialize(Object value);

    /**
     * Deserializes object from byte array
     *
     * @param data
     *            serialized form of the object
     * @return deserialized object
     */
    Object deserialize(byte[] data);
}
