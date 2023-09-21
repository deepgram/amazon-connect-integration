package com.deepgram.kvsdgintegrator;

import org.apache.commons.lang3.Validate;
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

public class DeepgramStreamingClient implements AutoCloseable {

	private final URI deepgramStreamingUrl;
	private final Map<String, String> deepgramHeaders;

	public DeepgramStreamingClient(Map<String, List<String>> dgParams, String deepgramApiKey) throws URISyntaxException {
		this.deepgramStreamingUrl = getDeepgramStreamingUrl(dgParams);
		this.deepgramHeaders = getDeepgramHeaders(deepgramApiKey);
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
	private URI getDeepgramStreamingUrl(Map<String, List<String>> dgParams) throws URISyntaxException {
		String baseUri = "wss://api.deepgram.com/v1/listen";
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

		return new URI(baseUri + "?" + queryString);
	}

	private Map<String, String> getDeepgramHeaders(String deepgramApiKey) {
		return Map.of("Authorization", "Token " + deepgramApiKey);
	}

	public CompletableFuture<Void> startStreamingToDeepgram(final Publisher<ByteBuffer> publisher) {
		Validate.notNull(publisher);

		CompletableFuture<Void> future = new CompletableFuture<>();

		final WebSocketClient wsClient = new WebSocketClient(deepgramStreamingUrl, deepgramHeaders) {
			@Override
			public void onOpen(ServerHandshake serverHandshake) {
				registerSubscriber(this, publisher, future);
			}

			@Override
			public void onMessage(String message) {
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

	@Override
	public void close() throws Exception {
		// TODO: close websocket to Deepgram
	}
}
