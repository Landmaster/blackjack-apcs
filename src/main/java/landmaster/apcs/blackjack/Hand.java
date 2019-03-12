package landmaster.apcs.blackjack;

import java.io.*;
import java.util.*;

import it.unimi.dsi.fastutil.ints.*;

public class Hand implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private transient IntSortedSet values;
	private final List<Card> cards;
	
	public Hand() {
		values = new IntRBTreeSet(new int[] {0});
		cards = new ArrayList<>();
	}
	public Hand(Hand other) {
		values = new IntRBTreeSet(other.values);
		cards = new ArrayList<>(other.cards);
	}
	public Hand(Card... cards) {
		this(Arrays.asList(cards));
	}
	public Hand(Collection<Card> cards) {
		values = new IntRBTreeSet(new int[] {0});
		this.cards = new ArrayList<>(cards);
		this.cards.forEach(this::addValue);
	}
	
	public void hit(Card card) {
		cards.add(card);
		addValue(card);
	}
	
	protected void addValue(Card card) {
		IntSortedSet newValues = new IntRBTreeSet();
		values.forEach((int oldVal) -> {
			for (int cardVal: card.getRank().getValues()) {
				newValues.add(oldVal+cardVal);
			}
		});
		values = newValues;
	}
	
	@Override
	public String toString() {
		return Arrays.toString(cards.toArray());
	}
	
	public List<Card> backingList() {
		return Collections.unmodifiableList(cards);
	}
	
	public IntSortedSet getScores() {
		return IntSortedSets.unmodifiable(values);
	}
	
	public boolean isBlackjack() {
		return cards.size() == 2 && values.contains(21);
	}
	
	private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		values = new IntRBTreeSet(new int[] {0});
		cards.forEach(this::addValue);
	}
}
