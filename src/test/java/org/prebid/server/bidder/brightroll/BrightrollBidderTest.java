package org.prebid.server.bidder.brightroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.brightroll.ExtImpBrightroll;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class BrightrollBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://brightroll.com";
    private static final BigDecimal BID_FLOOR = new BigDecimal("0.30");

    private BrightrollBidder brightrollBidder;

    @Before
    public void setUp() {
        final Map<String, BigDecimal> publisherIdToBidFloor = new HashMap<>();
        publisherIdToBidFloor.put("testPublisher", BID_FLOOR);
        publisherIdToBidFloor.put("publisher", null);
        brightrollBidder = new BrightrollBidder(ENDPOINT_URL, jacksonMapper, publisherIdToBidFloor);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBodyHeadersAndMethod()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("testPublisher")))).build()))
                .device(Device.builder().ua("ua").ip("192.168.0.1").language("en").dnt(1).build())
                .user(User.builder().ext(ExtUser.builder().consent("consent").build()).build())
                .regs(Regs.builder().coppa(0).ext(ExtRegs.of(1, null, null)).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("http://brightroll.com?publisher=testPublisher");
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "ua"),
                        tuple(HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(), "en"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "192.168.0.1"),
                        tuple(HttpUtil.DNT_HEADER.toString(), "1"),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsExactly(mapper.writeValueAsBytes(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .bidfloor(BID_FLOOR)
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("testPublisher"))))
                                .build()))
                        .device(Device.builder().ua("ua").ip("192.168.0.1").language("en").dnt(1).build())
                        .user(User.builder()
                                .ext(ExtUser.builder().consent("consent").build())
                                .build())
                        .regs(Regs.builder().coppa(0).ext(ExtRegs.of(1, null, null)).build())
                        .at(1)
                        .build()));
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenBannerOrVideoAreAbsentInImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("ext.bidder not provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenImpExtIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, null))).build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("ext.bidder not provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenImpExtIsIncorrectFormat() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(mapper.createObjectNode().put("bidder", 4)).build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("ext.bidder.publisher not provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenImpExtPublisherIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of(null)))).build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("publisher is empty"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldUpdateBannerWhenWAndHMissedAndFormatIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().format(singletonList(Format.builder().w(200).h(100).build())).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("publisher")))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsExactly(mapper.writeValueAsBytes(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().w(200).h(100)
                                        .format(singletonList(Format.builder().w(200).h(100).build())).build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("publisher"))))
                                .build()))
                        .at(1)
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldOverrideBadvAndBcatWhenPublisherIsInBidderAccounts() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("publisher"))))
                                .build(),
                        Imp.builder()
                                .video(Video.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("publisher"))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(2)
                .extracting(Imp::getBanner, Imp::getVideo)
                .containsOnly(
                        tuple(Banner.builder().build(), null),
                        tuple(null, Video.builder().build()));
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getBcat, BidRequest::getBadv)
                .containsOnly(tuple(null, null));
    }

    @Test
    public void makeHttpRequestsShouldNotSetRequestBcatIfExtPublisherIsNotInTheBidderAccountList() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("publisher"))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getBcat)
                .containsNull();
    }

    @Test
    public void makeHttpRequestShouldDropNotValidImpsFromRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("publisher")))).build(),
                        Imp.builder().build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsExactly(mapper.writeValueAsBytes(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of("publisher"))))
                                .build()))
                        .at(1)
                        .build()));
    }

    @Test
    public void makeHttpRequestShouldReturnRequestWithoutDeviceHeadersWhenDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBrightroll.of(
                                "publisher")))).build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestShouldReturnRequestWithoutErrorsOneOfTheImpsAndBidRequestAreValid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpBrightroll.of("publisher")))).build(),
                        Imp.builder().id("impId2").build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("http://brightroll.com?publisher=publisher");
    }

    @Test
    public void makeHttpRequestShouldReturnRequestWithoutUserAgentHeaderWhenUaIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpBrightroll.of("publisher")))).build()))
                .device(Device.builder().ip("192.168.0.1").language("en").dnt(1).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .doesNotContain(tuple(HttpUtil.USER_AGENT_HEADER.toString(), "ua"));
    }

    @Test
    public void makeHttpRequestShouldReturnRequestWithoutForwardHeaderWhenIpIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpBrightroll.of("publisher")))).build()))
                .device(Device.builder().ua("ua").language("en").dnt(1).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .doesNotContain(tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "192.168.0.1"));
    }

    @Test
    public void makeHttpRequestShouldReturnRequestWithoutLanguageHeaderWhenLanguageIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpBrightroll.of("publisher")))).build()))
                .device(Device.builder().ua("ua").ip("192.168.0.1").dnt(1).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .doesNotContain(tuple(HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(), "en"));
    }

    @Test
    public void makeHttpRequestShouldReturnRequestWithoutDntHeaderWhenDntIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpBrightroll.of("publisher")))).build()))
                .device(Device.builder().ua("ua").ip("192.168.0.1").language("en").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = brightrollBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .doesNotContain(tuple(HttpUtil.DNT_HEADER.toString(), "1"));
    }

    @Test
    public void makeBidsShouldReturnBidWithoutErrors() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId")
                .banner(Banner.builder().build()).build())).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = brightrollBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnReturnBidderBidWithBannerTypeWhenCorrespondingImpWasNotFound()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId2").build())).build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId1").build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = brightrollBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId2").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithVideoTypeWhenBannerAndVideoBothPresentInImp()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId1").build())).build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .video(Video.builder().build())
                                .id("impId1")
                                .build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = brightrollBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId1").build(), BidType.video, null));
    }

    @Test
    public void makeBidsShouldCheckForBidsOnlyFromFirstSeatBid() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(asList(
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().impid("impId1").build())).build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().impid("impId2").build())).build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(asList(
                Imp.builder().id("impId1").build(), Imp.builder().id("impId2").build())).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = brightrollBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId1").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnMultipleBidderBidsFromFirstSeatBid() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(
                        SeatBid.builder()
                                .bid(asList(
                                        Bid.builder().impid("impId1").build(),
                                        Bid.builder().impid("impId2").build())).build()))
                .build());

        final BidRequest bidRequest = BidRequest.builder().imp(asList(
                Imp.builder().id("impId1").build(), Imp.builder().id("impId2").build())).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = brightrollBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId1").build(), BidType.banner, "EUR"),
                        BidderBid.of(Bid.builder().impid("impId2").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithBannerTypeWhenVideoAndBannerAreNotPresentInImp()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId1").build())).build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                        Imp.builder().id("impId1").build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = brightrollBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId1").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorListsIfSeatBidIsNotPresentInResponse()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = brightrollBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCantBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("{");

        // when
        final Result<List<BidderBid>> result = brightrollBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse(
                        "Failed to decode: Unexpected end-of-input: expected close marker for Object (start marker at"
                                + " [Source: (String)\"{\"; line: 1, column: 1])\n at [Source: (String)\"{\"; line: 1, "
                                + "column: 2]"));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(200, null, body), null);
    }
}
