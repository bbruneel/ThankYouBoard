package org.bruneel.thankyouboard.util;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public final class DomainIds {

    private DomainIds() {
    }

    public static UUID newDomainId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
