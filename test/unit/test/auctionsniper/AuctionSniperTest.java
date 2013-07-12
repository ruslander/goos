package test.auctionsniper;

import auctionsniper.*;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.States;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import static auctionsniper.AuctionEventListener.PriceSource;
import static auctionsniper.SniperState.*;
import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(JMock.class)
public class AuctionSniperTest {
  private static final String ITEM_ID = "dummyItemId";
  private final Mockery context = new Mockery();
  private final SniperListener sniperListener = context.mock(SniperListener.class);
  private final Auction auction = context.mock(Auction.class);
  private final AuctionSniper sniper = new AuctionSniper(ITEM_ID, auction, sniperListener);
  private final States sniperState = context.states("sniper");

  public void reportsLostIfAuctionClosesImmediately() {
    context.checking(new Expectations() {{
      atLeast(1).of(sniperListener).sniperStateChanged(
                with(aSniperThatHas(LOST)));
    }});
    sniper.auctionClosed();
  }

  @Test
  public void bidsHigherAndReportsBiddingWhenNewPriceArrives() {
    final int price = 1001;
    final int increment = 25;
    final int bid = price + increment;
    context.checking(new Expectations() {{
      one(auction).bid(bid);
      atLeast(1).of(sniperListener).sniperStateChanged(
          new SniperSnapshot(ITEM_ID, price, bid, BIDDING)
      );
    }});
    sniper.currentPrice(price, increment, PriceSource.FromOtherBidder);
  }

  @Test
  public void reportsIsWinningWhenCurrentPriceComesFromSniper() {
    context.checking(new Expectations() {{
      ignoring(auction);
      allowing(sniperListener).sniperStateChanged(
          with(aSniperThatIs(BIDDING)));
              then(sniperState.is("bidding"));
      atLeast(1).of(sniperListener).sniperStateChanged(
          new SniperSnapshot(ITEM_ID, 135, 135, WINNING));
              when(sniperState.is("bidding"));
    }});
    sniper.currentPrice(123, 12, PriceSource.FromOtherBidder);
    sniper.currentPrice(135, 45, PriceSource.FromSniper);
  }

  @Test
  public void reportsLostIfAuctionClosesWhenBidding() {
    context.checking(new Expectations() {{
      ignoring(auction);
      allowing(sniperListener).sniperStateChanged(
          with(aSniperThatIs(BIDDING)));
              then(sniperState.is("bidding"));
      atLeast(1).of(sniperListener).sniperStateChanged(
          new SniperSnapshot(ITEM_ID, 1001, 1026, LOST));
              when(sniperState.is("bidding"));
    }});

    sniper.currentPrice(1001, 25, PriceSource.FromOtherBidder);
    sniper.auctionClosed();
  }

  @Test
  public void reportsWonIfAuctionClosesWhenWinning() {
    context.checking(new Expectations() {{
      ignoring(auction);
      allowing(sniperListener).sniperStateChanged(
          with(aSniperThatIs(WINNING)));
              then(sniperState.is("winning"));
      atLeast(1).of(sniperListener).sniperStateChanged(
          // Last Bid is 0 - a test smell!
          new SniperSnapshot(ITEM_ID, 1001, 0, WON));
              when(sniperState.is("winning"));
    }});

    sniper.currentPrice(1001, 25, PriceSource.FromSniper);
    sniper.auctionClosed();
  }

  private Matcher<SniperSnapshot> aSniperThatIs(final SniperState state) {
    return getFeatureMatcher(state);
  }

  private Matcher<SniperSnapshot> aSniperThatHas(final SniperState state) {
    return getFeatureMatcher(state);
  }

  private FeatureMatcher<SniperSnapshot, SniperState> getFeatureMatcher(final SniperState state) {
    return new FeatureMatcher<SniperSnapshot, SniperState>(
        equalTo(state), "sniper that is ", "was")
    {
      @Override
      protected SniperState featureValueOf(SniperSnapshot actual) {
        return actual.state;
      }
    };
  }
}