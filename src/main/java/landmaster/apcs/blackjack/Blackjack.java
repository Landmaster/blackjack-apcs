package landmaster.apcs.blackjack;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import javax.swing.*;
import javax.swing.border.*;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;

import com.google.common.base.Throwables;

@SuppressWarnings("serial")
public class Blackjack extends JFrame {
	protected Player player;
	
	protected Card firstHouse;
	protected Hand fullHouse;
	
	protected boolean dealerPlay;
	
	protected final JButton hitButton, standButton;
	
	protected final JLabel winsLosses;
	
	protected WebSocketClient client;
	protected Socket socket;
	
	protected void updateWinsLosses() {
		winsLosses.setText(String.format(Locale.US, "Wins: %d; Losses: %d", player.getWins(), player.getLosses()));
	}
	
	public Blackjack(URI serverURL) {
		super("Blackjack");
		
		this.setLayout(new BorderLayout());
		
		cardDisplay = new CardDisplay();
		this.add(cardDisplay, BorderLayout.CENTER);
		
		JPanel options = new JPanel();
		options.setLayout(new BoxLayout(options, BoxLayout.X_AXIS));
		
		hitButton = new JButton("Hit"); standButton = new JButton("Stand");
		hitButton.addActionListener(e -> {
			Optional.ofNullable(socket.session)
			.map(Session::getRemote)
			.ifPresent(endpoint -> {
				try {
					new BlackjackServer.PacketHit().sendTo(endpoint);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			});
		});
		standButton.addActionListener(e -> {
			Optional.ofNullable(socket.session)
			.map(Session::getRemote)
			.ifPresent(endpoint -> {
				try {
					hitButton.setVisible(false);
					standButton.setVisible(false);
					new BlackjackServer.PacketStand().sendTo(endpoint);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			});
		});
		hitButton.setVisible(false);
		standButton.setVisible(false);
		//newGameButton.addActionListener(e -> reset());
		//quitButton.addActionListener(e -> System.exit(0));
		options.add(hitButton);
		options.add(standButton);
		options.add(Box.createHorizontalGlue());
		options.add( (winsLosses = new JLabel()) ); winsLosses.setBorder(new EmptyBorder(10, 10, 10, 10));
		
		this.add(options, BorderLayout.SOUTH);
		
		//reset();
		
		pack();
		setSize(600, 600);
		
		client = new WebSocketClient();
		socket = new Socket();
		try {
			client.start();
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			client.connect(socket, serverURL, request);
		} catch (Exception e) {
			Throwables.throwIfUnchecked(e);
			throw new RuntimeException(e);
		}
	}
	
	protected void checkBlackjack() {
		/*
		if (player.isBlackjack()) {
			wins += (house.isBlackjack() ? 0 : 1);
			this.updateWinsLosses();
			JOptionPane.showMessageDialog(this, house.isBlackjack() ? "Double blackjack! You tied" : "Blackjack! You win");
			//reset();
		}*/
	}
	
	protected final CardDisplay cardDisplay;
	
	protected class CardDisplay extends JPanel {
		protected final Font scoreDisplayFont = new Font(Font.SANS_SERIF, Font.PLAIN, 30);
		
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			
			g.setColor(Color.GREEN);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
			
			if ((dealerPlay ? fullHouse : firstHouse) == null || player == null) {
				return;
			}
			
			if (dealerPlay) {
				int idx = 0;
				for (Card card: fullHouse.backingList()) {
					g.drawImage(card.getSpritePortion(), 10 + Card.CARD_WIDTH*idx/2, 60, this);
					++idx;
				}
				while (idx++ < 2) {
					g.drawImage(Card.getCardbackSprite(), 10 + Card.CARD_WIDTH/2, 60, this);
				}
			} else {
				g.drawImage(firstHouse.getSpritePortion(), 10, 60, this);
				g.drawImage(Card.getCardbackSprite(), 10 + Card.CARD_WIDTH/2, 60, this);
			}
			
			{
				int idx = 0;
				for (Card card: player.getHand().backingList()) {
					g.drawImage(card.getSpritePortion(), 10 + Card.CARD_WIDTH*idx/2, this.getHeight() - 10 - Card.CARD_HEIGHT, this);
					++idx;
				}
			}
			
			g.setColor(Color.BLACK);
			g.setFont(scoreDisplayFont); 
			
			String houseHeader = "House";
			if (dealerPlay && fullHouse.backingList().size() >= 2) {
				houseHeader += ": "+String.join(" or ", (Iterable<String>)(() -> fullHouse.getScores().stream().map(v -> v.toString()).iterator()));
			}
			float ascent = g.getFontMetrics().getLineMetrics(houseHeader, g).getAscent();
			g.drawString(houseHeader, 10, (int)(10+ascent));
			
			String playerHeader = "Player: "+String.join(" or ", (Iterable<String>)(() -> player.getHand().getScores().stream().map(v -> v.toString()).iterator()));
			ascent = g.getFontMetrics().getLineMetrics(playerHeader, g).getAscent();
			g.drawString(playerHeader, 10, (int)(this.getHeight() - 60 - Card.CARD_HEIGHT+ascent));
		}
	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			URI serverURL;
			for (;;) {
				try {
					String str = JOptionPane.showInputDialog("Enter server URL:");
					if (str == null) {
						System.exit(0);
					}
					serverURL = new URI(str);
					break;
				} catch (URISyntaxException e) {
					// continue
				}
			}
			JFrame mainFrame = new Blackjack(serverURL);
			mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			mainFrame.setVisible(true);
		});
	}
	
	@WebSocket(maxIdleTime = 20*60*1000)
	public class Socket {
		Session session;
		
		@SuppressWarnings("unchecked")
		@OnWebSocketMessage
		public void onMessage(Session session, byte[] buf, int off, int len) throws IOException {
			try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(buf, off, len))) {
				Object obj = ois.readObject();
				//System.out.println(obj);
				if (obj instanceof IPacket) {
					Optional.ofNullable(((IPacket<Blackjack>)obj).handle(session, Blackjack.this))
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
		
		@OnWebSocketConnect
		public void onConnect(Session session) {
			this.session = session;
		}
		
		@OnWebSocketClose
		public void onClose(Session session, int statusCode, String reason) {
			this.session = null;
		}
	}
	
	public static class PacketUpdateCards implements IPacket<Blackjack> {
		private Player player;
		private Card firstHouse;
		
		public PacketUpdateCards() {
		}
		public PacketUpdateCards(Card firstHouse, Player player) {
			this.firstHouse = firstHouse;
			this.player = player;
		}
		
		@Override
		public CompletionStage<IPacket<?>> handle(Session session, Blackjack context) {
			context.firstHouse = firstHouse;
			context.player = player;
			context.cardDisplay.repaint();
			context.updateWinsLosses();
			return null;
		}
		
	}
	
	public static class PacketStartTurn implements IPacket<Blackjack> {
		@Override
		public CompletionStage<IPacket<?>> handle(Session session, Blackjack context) {
			context.hitButton.setVisible(true);
			context.standButton.setVisible(true);
			return null;
		}
	}
	
	public static class PacketRevealWinner implements IPacket<Blackjack> {
		private Hand house;
		private String message;
		
		public PacketRevealWinner() {
		}
		public PacketRevealWinner(Hand house, String message) {
			this.house = house;
			this.message = message;
		}

		@Override
		public CompletionStage<IPacket<?>> handle(Session session, Blackjack context) {
			context.hitButton.setVisible(false);
			context.standButton.setVisible(false);
			context.fullHouse = this.house;
			context.dealerPlay = true;
			context.cardDisplay.repaint();
			JOptionPane.showMessageDialog(context, message);
			context.dealerPlay = false;
			return CompletableFuture.completedFuture(new BlackjackServer.PacketRequestUpdate());
		}
		
	}
}
