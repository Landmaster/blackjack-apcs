package landmaster.apcs.blackjack;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import javax.servlet.*;
import javax.servlet.annotation.*;

import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.servlet.*;

import com.google.common.collect.*;

import it.unimi.dsi.fastutil.objects.*;

@SuppressWarnings("serial")
@WebServlet(urlPatterns="/", loadOnStartup = 1)
public class BlackjackServer extends WebSocketServlet {
	private Deque<Card> deck;
	private Hand house;
	private final Object2ObjectSortedMap<Session, Player> playerHands = new Object2ObjectLinkedOpenHashMap<>();
	private final Set<Session> alreadyCalculatedResults = new ObjectOpenHashSet<>();
	private Session current = null;
	private final ListMultimap<Session, Blackjack.PacketUpdateCards> resultsQueue = Multimaps.newListMultimap(new HashMap<>(), LinkedList::new);
	
	protected Card drawCard() {
		Card top = deck.removeFirst();
		deck.addLast(top);
		return top;
	}
	
	@Override
	public void init() throws ServletException {
		super.init();
		
		List<Card> temp = Card.all().collect(Collectors.toCollection(ArrayList::new));
		Collections.shuffle(temp);
		deck = new ArrayDeque<>(temp);
		
		house = new Hand();
		for (int i=0; i<2; ++i) {
			house.hit(drawCard());
		}
	}
	
	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.setCreator((req, res) -> {
			//System.out.println("Creating");
			return new Socket();
		});
	}
	
	protected void runTurn(Runnable afterAdvance, boolean deferStartTurn) {
		try {
			if (!playerHands.isEmpty()) {
				boolean first = (current == null);
				boolean advanced = advanceCurrent();
				afterAdvance.run();
				if (advanced) {
					Optional<Player> player = Optional.ofNullable(playerHands.get(current));
					if (first && house.isBlackjack()) {
						calcWinner();
					} else if (player
							.map(Player::getHand)
							.filter(Hand::isBlackjack)
							.isPresent()) {
						player.get().incrementWins();
						new PacketMulti<>(
								new Blackjack.PacketUpdateCards(house.backingList().get(0), player.get()),
								new Blackjack.PacketRevealWinner(new Hand(house.backingList().get(0)), "Blackjack! You win"))
						.sendTo(current.getRemote());
						alreadyCalculatedResults.add(current);
						runTurn(() -> {}, false);
					} else {
						if (!deferStartTurn) {
							new Blackjack.PacketUpdateCards(house.backingList().get(0), player.get()).sendTo(current.getRemote());
							new Blackjack.PacketStartTurn().sendTo(current.getRemote());
						}
					}
				} else {
					calcWinner();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			//session.close();
		}
	}
	
	protected boolean advanceCurrent() {
		if (current == null) {
			current = playerHands.firstKey();
		} else {
			Iterator<Session> it = playerHands.keySet().iterator(current);
			if (it.hasNext()) {
				current = it.next();
			} else {
				current = null;
				return false;
			}
		}
		return true;
	}
	
	protected void calcWinner() {
		while (house.getScores().lastInt() < 17) {
			house.hit(drawCard());
		}
		for (Map.Entry<Session, Player> ent: Object2ObjectMaps.fastIterable(playerHands)) {
			if (!alreadyCalculatedResults.contains(ent.getKey())) {
				String message = "You tied";
				if (house.getScores().firstInt() > 21) {
					message = "Dealer busted! You win";
					ent.getValue().incrementWins();
				} else {
					int bestHouseScore = house.getScores().headSet(22).lastInt();
					int bestPlayerScore = ent.getValue().getHand().getScores().headSet(22).lastInt();
					if (bestHouseScore > bestPlayerScore) {
						message = "You lose";
						ent.getValue().incrementLosses();
					} else if (bestHouseScore < bestPlayerScore) {
						message = "You win";
						ent.getValue().incrementWins();
					}
				}
				if (house.isBlackjack()) {
					message = "House blackjack! " + message;
				}
				try {
					new Blackjack.PacketRevealWinner(house, message).sendTo(ent.getKey().getRemote());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			Hand newHand = new Hand();
			for (int i=0; i<2; ++i) {
				newHand.hit(this.drawCard());
			}
			ent.getValue().setHand(newHand);
		}
		
		house = new Hand();
		for (int i=0; i<2; ++i) {
			house.hit(drawCard());
		}
		alreadyCalculatedResults.clear();
		
		for (Map.Entry<Session, Player> ent: Object2ObjectMaps.fastIterable(playerHands)) {
			resultsQueue.put(ent.getKey(),
					new Blackjack.PacketUpdateCards(
							house.backingList().get(0),
							new Player(ent.getValue())
							)
					);
		}
		
		runTurn(() -> {}, true);
	}
	
	protected boolean checkBusted(Session session, Player player) {
		return player.getHand().getScores().firstInt() > 21;
	}
	
	@WebSocket(maxIdleTime = 20*60*1000)
	public class Socket {
		@OnWebSocketConnect
		public void onConnect(Session session) {
			//if (true) throw new RuntimeException();
			Hand hand = new Hand();
			for (int i=0; i<2; ++i) {
				hand.hit(drawCard());
			}
			Player player = new Player(hand);
			boolean initial = playerHands.isEmpty();
			playerHands.put(session, player);
			if (initial) {
				runTurn(() -> {}, false);
			}
			try {
				//System.out.println("Sending!");
				new Blackjack.PacketUpdateCards(house.backingList().get(0), player).sendTo(session.getRemote());
			} catch (IOException e) {
				e.printStackTrace();
				//session.close();
			}
		}
		
		@SuppressWarnings("unchecked")
		@OnWebSocketMessage
		public void onMessage(Session session, byte[] buf, int off, int len) throws IOException {
			try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(buf, off, len))) {
				Object obj = ois.readObject();
				//System.out.println(obj);
				if (obj instanceof IPacket) {
					Optional.ofNullable(((IPacket<BlackjackServer>)obj).handle(session, BlackjackServer.this))
					.ifPresent(reply -> {
						reply.thenAccept(pkt -> {
							try {
								pkt.sendTo(session.getRemote());
							} catch (IOException e) {
								e.printStackTrace();
							}
						});
					});
				}
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		
		@OnWebSocketClose
		public void onClose(Session session, int statusCode, String reason) {
			if (session == current) {
				runTurn(() -> {
					playerHands.remove(session);
				}, false);
			} else {
				playerHands.remove(session);
			}
		}
	}
	
	public static class PacketHit implements IPacket<BlackjackServer> {

		@Override
		public CompletionStage<? extends IPacket<?>> handle(Session session, BlackjackServer context) {
			if (session != context.current) {
				return null;
			}
			Optional.ofNullable(context.playerHands.get(session))
			.ifPresent(player -> {
				player.getHand().hit(context.drawCard());
				List<IPacket<? super Blackjack>> packets = new ArrayList<>(2);
				boolean busted = context.checkBusted(session, player);
				if (busted) {
					player.incrementLosses();
				}
				packets.add(new Blackjack.PacketUpdateCards(context.house.backingList().get(0), player));
				if (busted) {
					packets.add(new Blackjack.PacketRevealWinner(new Hand(context.house.backingList().get(0)), "Busted!"));
					context.alreadyCalculatedResults.add(session);
				}
				try {
					new PacketMulti<Blackjack>(packets).sendTo(session.getRemote());
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (busted) {
					context.runTurn(() -> {}, false);
				}
			});
			return null;
		}
		
	}
	
	public static class PacketStand implements IPacket<BlackjackServer> {

		@Override
		public CompletionStage<? extends IPacket<?>> handle(Session session, BlackjackServer context) {
			context.runTurn(() -> {}, false);
			return null;
		}
		
	}
	
	public static class PacketRequestUpdate implements IPacket<BlackjackServer> {

		@Override
		public CompletionStage<? extends IPacket<?>> handle(Session session, BlackjackServer context) {
			//System.out.println(Arrays.toString(context.resultsQueue.get(session).toArray()));
			if (!context.resultsQueue.get(session).isEmpty()) {
				List<IPacket<? super Blackjack>> pkts = new ArrayList<>(2);
				pkts.add(context.resultsQueue.get(session).remove(0));
				if (context.current == session) {
					pkts.add(new Blackjack.PacketStartTurn());
				}
				return CompletableFuture.completedFuture(new PacketMulti<>(pkts));
			}
			return null;
		}
		
	}
}
