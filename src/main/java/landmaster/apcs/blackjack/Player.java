package landmaster.apcs.blackjack;

import java.io.*;
import java.util.*;

public class Player implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private Hand hand;
	private int wins, losses;
	
	public Player(Hand hand) {
		this.hand = hand;
		this.wins = this.losses = 0;
	}
	public Player(Player other) {
		this.hand = new Hand(other.hand);
		this.wins = other.wins;
		this.losses = other.losses;
	}
	
	public Hand getHand() { return hand; }
	public void setHand(Hand hand) { this.hand = hand; }
	public int getWins() { return wins; }
	public int getLosses() { return losses; }
	public void incrementWins() { ++wins; }
	public void incrementLosses() { ++losses; }
	
	@Override
	public String toString() {
		return String.format(Locale.US, "Player[wins=%d, losses=%d, hand=%s]", wins, losses, hand);
	}
}
