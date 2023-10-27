package com.deepgram.kvsdgintegrator;

import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DeepgramStreamingClient {

	private final URI deepgramStreamingUrl;
	private final Map<String, String> deepgramHeaders;

	private static final Logger logger = LogManager.getLogger(DeepgramStreamingClient.class);


	public DeepgramStreamingClient(String deepgramApi, String deepgramApiKey, Map<String, List<String>> dgParams) throws Exception {
		Validate.notNull(deepgramApi);
		Validate.notNull(deepgramApiKey);
		Validate.notNull(dgParams);

		this.deepgramStreamingUrl = buildDeepgramStreamingUrl(deepgramApi, dgParams);
		this.deepgramHeaders = buildDeepgramHeaders(deepgramApiKey);
	}

	/**
	 * For example, if our `dgParams` are:
	 * <pre><code>
	 * {
	 *   "model": ["nova"],
	 *   "callback": ["https://www.example.com"],
	 *   "tag": ["someTag1", "someTag2"]
	 * }</code></pre>
	 * <p>
	 * Then this function uses the query params:
	 * <pre><code>
	 * model=nova&callback=https%3A%2F%2Fwww.example.com&tag=someTag1&tag=someTag2</code></pre>
	 * <p>
	 * Also, there are some default query params like `encoding` and `sample_rate` that are the same regardless of
	 * the `dgParams`.
	 */
	private URI buildDeepgramStreamingUrl(String deepgramApi, Map<String, List<String>> dgParams) throws Exception {
		StringBuilder queryString = new StringBuilder();
		queryString.append("encoding=linear16&sample_rate=8000&multichannel=true&channels=2");

		for (Map.Entry<String, List<String>> entry : dgParams.entrySet()) {
			for (String value : entry.getValue()) {
				queryString.append("&")
						.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
						.append("=")
						.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
			}
		}

		return new URI(deepgramApi + "?" + queryString);
	}

	private Map<String, String> buildDeepgramHeaders(String deepgramApiKey) {
		return Map.of("Authorization", "Token " + deepgramApiKey);
	}

	public CompletableFuture<Void> startStreamingToDeepgram(final Publisher<ByteBuffer> publisher) {
		Validate.notNull(publisher);

		CompletableFuture<Void> future = new CompletableFuture<>();

		String requestId = ThreadContext.get("requestId");
		final WebSocketClient wsClient = new WebSocketClient(deepgramStreamingUrl, deepgramHeaders) {
			@Override
			public void onOpen(ServerHandshake serverHandshake) {
				// Propagate request id into the websocket thread so that it appears in logs
				ThreadContext.put("requestId", requestId);

				registerSubscriber(this, publisher, future);
			}

			@Override
			public void onMessage(String message) {
				logger.debug("Message from Deepgram websocket: " + message);
			}

			@Override
			public void onClose(int i, String s, boolean b) {
				future.complete(null);
			}

			@Override
			public void onError(Exception e) {
				future.completeExceptionally(e);
			}
		};
		wsClient.connect();

		logger.info("Connecting to Deepgram at URL: " + deepgramStreamingUrl);

		return future;
	}

	private void registerSubscriber(
			final WebSocketClient client,
			final Publisher<ByteBuffer> publisher,
			final CompletableFuture<Void> future) {
		publisher.subscribe(new Subscriber<>() {
			@Override
			public void onSubscribe(Subscription subscription) {
				subscription.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(ByteBuffer audioBytes) {
				client.send(audioBytes);
			}

			@Override
			public void onError(Throwable throwable) {
				future.completeExceptionally(throwable);
			}

			@Override
			public void onComplete() {
				client.close();
			}
		});
	}
}
