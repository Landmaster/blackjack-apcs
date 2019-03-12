package landmaster.apcs.blackjack;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import org.eclipse.jetty.websocket.api.*;

public class PacketMulti<T> implements IPacket<T> {
	private static final long serialVersionUID = 1L;
	
	private List<IPacket<? super T>> packets;
	
	@SafeVarargs
	public PacketMulti(IPacket<? super T>...packets) {
		this(Arrays.asList(packets));
	}
	public PacketMulti(List<IPacket<? super T>> packets) {
		this.packets = packets;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public CompletionStage<IPacket<?>> handle(Session session, T context) {
		CompletableFuture<IPacket<?>>[] replies = packets.stream()
				.map(pkt -> pkt.handle(session, context))
				.filter(repl -> repl != null)
				.map(CompletionStage::toCompletableFuture)
				.toArray(CompletableFuture[]::new);
		return replies.length == 0
				? null
				: CompletableFuture.allOf(replies).thenApply(dummy -> {
					return new PacketMulti(Arrays.stream(replies)
							.map(CompletableFuture::join)
							.<IPacket>flatMap(repl -> {
								if (repl instanceof PacketMulti) {
									return ((PacketMulti)repl).packets.stream();
								}
								return Stream.of(repl);
							})
							.collect(Collectors.toList()));
				});
	}
	
	@Override
	public String toString() {
		return "PacketMulti"+Arrays.toString(packets.toArray());
	}
	
}
