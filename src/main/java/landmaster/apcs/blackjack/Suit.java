package landmaster.apcs.blackjack;

import java.util.*;

import org.apache.commons.lang3.*;

public enum Suit {
	CLUBS, SPADES, HEARTS, DIAMONDS;
	
	public static final Suit[] VALUES = values();
	
	@Override
	public String toString() {
		return StringUtils.capitalize(this.name().toLowerCase(Locale.US));
	}
}