package com.aalsikke.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumbersTest {

    @Test
    void localMobileWithLeadingZero() {
        assertEquals("+9613000000", PhoneNumbers.normalize("03000000"));
    }

    @Test
    void spacesAndDashesAreStripped() {
        assertEquals("+9613000000", PhoneNumbers.normalize("03 000-000"));
    }

    @Test
    void internationalWithPlusAndSpaces() {
        assertEquals("+9613000000", PhoneNumbers.normalize("+961 3 000 000"));
    }

    @Test
    void doubleZeroPrefix() {
        assertEquals("+9613000000", PhoneNumbers.normalize("009613000000"));
    }

    @Test
    void countryCodeWithoutPlus() {
        assertEquals("+9613000000", PhoneNumbers.normalize("9613000000"));
    }

    @Test
    void plusWithExtraLocalZero() {
        assertEquals("+9613000000", PhoneNumbers.normalize("+961 03 000 000"));
    }

    @Test
    void eightDigitMobileWithoutZero() {
        assertEquals("+96171234567", PhoneNumbers.normalize("71 234 567"));
    }

    @Test
    void foreignNumberIsKept() {
        assertEquals("+442079460958", PhoneNumbers.normalize("+44 20 7946 0958"));
    }

    @Test
    void blankOrNullReturnsNull() {
        assertNull(PhoneNumbers.normalize(null));
        assertNull(PhoneNumbers.normalize("   "));
    }

    @Test
    void lettersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumbers.normalize("abc"));
    }

    @Test
    void tooShortIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumbers.normalize("12345"));
    }
}