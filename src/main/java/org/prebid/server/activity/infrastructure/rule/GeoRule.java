package org.prebid.server.activity.infrastructure.rule;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityCallPayload;

import java.util.List;
import java.util.Set;

public final class GeoRule implements Rule {

    private final ComponentRule componentRule;
    private final boolean sidsMatched;
    private final List<GeoCode> geoCodes;
    private final boolean allowed;

    public GeoRule(Set<ComponentType> componentTypes,
                   Set<String> componentNames,
                   boolean sidsMatched,
                   List<GeoCode> geoCodes,
                   boolean allowed) {

        this.componentRule = new ComponentRule(componentTypes, componentNames, allowed);
        this.sidsMatched = sidsMatched;
        this.geoCodes = geoCodes;
        this.allowed = allowed;
    }

    @Override
    public boolean matches(ActivityCallPayload activityCallPayload) {
        return sidsMatched
                && (geoCodes == null || matchesOneOfGeoCodes(activityCallPayload))
                && componentRule.matches(activityCallPayload);
    }

    private boolean matchesOneOfGeoCodes(ActivityCallPayload activityCallPayload) {
        if (activityCallPayload instanceof GeoActivityCallPayload geoPayload) {
            return geoCodes.stream().anyMatch(geoCode -> matchesGeoCode(geoCode, geoPayload));
        }

        return true;
    }

    private static boolean matchesGeoCode(GeoCode geoCode, GeoActivityCallPayload geoPayload) {
        final String region = geoCode.getRegion();
        return StringUtils.equalsIgnoreCase(geoCode.getCountry(), geoPayload.country())
                && (region == null || StringUtils.equalsIgnoreCase(region, geoPayload.region()));
    }

    @Override
    public boolean allowed() {
        return allowed;
    }

    @Value(staticConstructor = "of")
    public static class GeoCode {

        String country;

        String region;
    }
}
