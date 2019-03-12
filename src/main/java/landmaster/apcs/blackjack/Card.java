package landmaster.apcs.blackjack;

import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.stream.*;

import javax.imageio.ImageIO;

public class Card implements Serializable {
	private static final long serialVersionUID = 1L;
	
	protected static final BufferedImage cardSprite;
	static {
		try {
			cardSprite = ImageIO.read(Card.class.getResource("/card_sprite.png"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Stream<Card> all() {
		return IntStream.range(0, Suit.VALUES.length * Rank.VALUES.length)
				.mapToObj(Card::new);
	}
	
	/**
	 * suit*13 + rank
	 */
	private final int _internal;
	
	public Card(Suit suit, Rank rank) {
		_internal = suit.ordinal()*Rank.VALUES.length + rank.ordinal();
	}
	
	private Card(int _internal) {
		this._internal = _internal;
	}
	
	public Suit getSuit() {
		return Suit.VALUES[_internal / Rank.VALUES.length];
	}
	
	public Rank getRank() {
		return Rank.VALUES[_internal % Rank.VALUES.length];
	}
	
	@Override
	public boolean equals(Object other) {
		return other instanceof Card && ((Card)other)._internal == _internal;
	}
	
	@Override
	public String toString() {
		return String.format(Locale.US, "%s of %s", getRank(), getSuit());
	}
	
	public BufferedImage getSpritePortion() {
		return cardSprite.getSubimage(getRank().ordinal() * CARD_WIDTH, getSuit().ordinal() * CARD_HEIGHT, CARD_WIDTH, CARD_HEIGHT);
	}
	
	public static final int CARD_WIDTH = 72, CARD_HEIGHT = 96;
	
	public static BufferedImage getCardbackSprite() {
		return cardSprite.getSubimage(Rank.VALUES.length * CARD_WIDTH, 0, CARD_WIDTH, CARD_HEIGHT);
	}
}