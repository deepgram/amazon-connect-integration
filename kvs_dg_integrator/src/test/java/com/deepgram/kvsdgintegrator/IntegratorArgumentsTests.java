package com.deepgram.kvsdgintegrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegratorArgumentsTests {

    @Test
    void validJsonDeserializesCorrectly() throws JsonProcessingException {
        String json = """
                {
                    "contactId": "4a573372-1f28-4e26-b97b-XXXXXXXXXXX",
                    "kvsStream": {
                        "arn": "arn:aws:kinesisvideo::eu-west-2:111111111111:stream/instance-alias-contact-ddddddd-bbbb-dddd-eeee-ffffffffffff/9999999999999",
                        "startFragmentNumber": "100"
                    },
                    "dgParams": {
                        "model": "nova",
                        "tag": ["someTag1", "someTag2"],
                        "callback": "https://example.com/4a573372-1f28-4e26-b97b-XXXXXXXXXXX"
                    },
                    "enforceRealtime": true
                }""";
        IntegratorArguments expected = new IntegratorArguments(
                "4a573372-1f28-4e26-b97b-XXXXXXXXXXX",
                new IntegratorArguments.KvsStream(
                        "arn:aws:kinesisvideo::eu-west-2:111111111111:stream/instance-alias-contact-ddddddd-bbbb-dddd-eeee-ffffffffffff/9999999999999",
                        "100"),
                Map.of("model", List.of("nova"),
                        "tag", List.of("someTag1", "someTag2"),
                        "callback", List.of("https://example.com/4a573372-1f28-4e26-b97b-XXXXXXXXXXX")),
                true
        );
        IntegratorArguments actual = IntegratorArguments.fromJson(json);
        assertEquals(expected, actual);
    }

    @Test
    void jsonWithExtraFieldThrows() {
        String json = """
                {
                    "contactId": "4a573372-1f28-4e26-b97b-XXXXXXXXXXX",
                    "kvsStream": {
                        "arn": "arn:aws:kinesisvideo::eu-west-2:111111111111:stream/instance-alias-contact-ddddddd-bbbb-dddd-eeee-ffffffffffff/9999999999999",
                        "startFragmentNumber": "100"
                    },
                    "dgParams": {
                        "model": "nova",
                        "tag": ["someTag1", "someTag2"],
                        "callback": "https://example.com/4a573372-1f28-4e26-b97b-XXXXXXXXXXX"
                    },
                    "enforceRealtime": true,
                    "something": "else"
                }""";
        assertThrows(Exception.class, () -> IntegratorArguments.fromJson(json));
    }

    @Test
    void jsonWithMissingContactIdThrows() {
        String json = """
                {
                    "kvsStream": {
                        "arn": "arn:aws:kinesisvideo::eu-west-2:111111111111:stream/instance-alias-contact-ddddddd-bbbb-dddd-eeee-ffffffffffff/9999999999999",
                        "startFragmentNumber": "100"
                    },
                    "dgParams": {
                        "model": "nova",
                        "tag": ["someTag1", "someTag2"],
                        "callback": "https://example.com/4a573372-1f28-4e26-b97b-XXXXXXXXXXX"
                    },
                    "enforceRealtime": true
                }""";
        assertThrows(Exception.class, () -> IntegratorArguments.fromJson(json));
    }

    @Test
    void jsonWithMissingKvsStreamThrows() {
        String json = """
                {
                    "contactId": "4a573372-1f28-4e26-b97b-XXXXXXXXXXX",
                    "dgParams": {
                        "model": "nova",
                        "tag": ["someTag1", "someTag2"],
                        "callback": "https://example.com/4a573372-1f28-4e26-b97b-XXXXXXXXXXX"
                    },
                    "enforceRealtime": true
                }""";
        assertThrows(Exception.class, () -> IntegratorArguments.fromJson(json));
    }

    @Test
    void jsonWithMissingDgParamsThrows() {
        String json = """
                {
                    "contactId": "4a573372-1f28-4e26-b97b-XXXXXXXXXXX",
                    "kvsStream": {
                        "arn": "arn:aws:kinesisvideo::eu-west-2:111111111111:stream/instance-alias-contact-ddddddd-bbbb-dddd-eeee-ffffffffffff/9999999999999",
                        "startFragmentNumber": "100"
                    },
                    "enforceRealtime": true
                }""";
        assertThrows(Exception.class, () -> IntegratorArguments.fromJson(json));
    }

    @Test
    void jsonWithNullContactIdThrows() {
        String json = """
                {
                    "contactId": null,
                    "kvsStream": {
                        "arn": "arn:aws:kinesisvideo::eu-west-2:111111111111:stream/instance-alias-contact-ddddddd-bbbb-dddd-eeee-ffffffffffff/9999999999999",
                        "startFragmentNumber": "100"
                    },
                    "dgParams": {
                        "model": "nova",
                        "tag": ["someTag1", "someTag2"],
                        "callback": "https://example.com/4a573372-1f28-4e26-b97b-XXXXXXXXXXX"
                    },
                    "enforceRealtime": true
                }""";
        assertThrows(Exception.class, () -> IntegratorArguments.fromJson(json));
    }

    @Test
    void jsonWithNullKvsStreamThrows() {
        String json = """
                {
                    "contactId": "4a573372-1f28-4e26-b97b-XXXXXXXXXXX",
                    "kvsStream": null,
                    "dgParams": {
                        "model": "nova",
                        "tag": ["someTag1", "someTag2"],
                        "callback": "https://example.com/4a573372-1f28-4e26-b97b-XXXXXXXXXXX"
                    },
                    "enforceRealtime": true
                }""";
        assertThrows(Exception.class, () -> IntegratorArguments.fromJson(json));
    }

    @Test
    void jsonWithNullDgParamsThrows() {
        String json = """
                {
                    "contactId": "4a573372-1f28-4e26-b97b-XXXXXXXXXXX",
                    "kvsStream": {
                        "arn": "arn:aws:kinesisvideo::eu-west-2:111111111111:stream/instance-alias-contact-ddddddd-bbbb-dddd-eeee-ffffffffffff/9999999999999",
                        "startFragmentNumber": "100"
                    },
                    "dgParams": null,
                    "enforceRealtime": true
                }""";
        assertThrows(Exception.class, () -> IntegratorArguments.fromJson(json));
    }
}
