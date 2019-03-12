package landmaster.apcs.blackjack;

import java.io.*;
import java.nio.*;
import java.util.concurrent.*;

import org.eclipse.jetty.websocket.api.*;

public interface IPacket<T> extends Serializable {
	CompletionStage<? extends IPacket<?>> handle(Session session, T context);
	
	default Future<Void> sendTo(RemoteEndpoint endpoint) throws IOException {
		//System.out.println(this);
		try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
			try (ObjectOutputStream objStream = new ObjectOutputStream(byteStream)) {
				objStream.writeObject(this);
				return endpoint.sendBytesByFuture(ByteBuffer.wrap(byteStream.toByteArray()));
			}
		}
	}
}
