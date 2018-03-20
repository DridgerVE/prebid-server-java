package org.prebid.server.bidder;

import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.exception.PreBidException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Basic {@link Bidder} implementation containing common logic functionality and helper methods.
 */
public abstract class OpenrtbBidder<T> implements Bidder<T> {

    private static final Logger logger = LoggerFactory.getLogger(OpenrtbBidder.class);

    public static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    protected static BidResponse parseResponse(HttpResponse httpResponse) {
        final int statusCode = httpResponse.getStatusCode();

        if (statusCode == 204) {
            return null;
        }

        if (statusCode != 200) {
            throw new PreBidException(
                    String.format("Unexpected status code: %d. Run with request.test = 1 for more info", statusCode));
        }

        try {
            return Json.mapper.readValue(httpResponse.getBody(), BidResponse.class);
        } catch (IOException e) {
            logger.warn("Error occurred parsing bid response", e);
            throw new PreBidException(e.getMessage());
        }
    }

    protected static MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
    }

    protected static List<BidderError> errors(List<String> errors) {
        return errors.stream().map(BidderError::create).collect(Collectors.toList());
    }
}
