package landmaster.apcs.blackjack;

import java.util.*;

import org.apache.commons.lang3.*;

public enum Rank {
	ACE,_2,_3,_4,_5,_6,_7,_8,_9,_10,JACK,QUEEN,KING;
	
	public static final Rank[] VALUES = values();
	
	@Override
	public String toString() {
		switch (this) {
		case JACK:
		case QUEEN:
		case KING:
		case ACE:
			return StringUtils.capitalize(this.name().toLowerCase(Locale.US));
		default:
			return this.name().substring(1);
		}
	}
	
	public int[] getValues() {
		switch (this) {
		case JACK:
		case QUEEN:
		case KING:
			return new int[] {10};
		case ACE:
			return new int[] {1, 11};
		default:
			return new int[] {Integer.parseInt(this.name().substring(1))};
		}
	}
}