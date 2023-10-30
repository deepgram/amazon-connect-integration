package com.deepgram.kvsdgintegrator;

import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>This Subscription reads the FROM_CUSTOMER and TO_CUSTOMER audio tracks from KVS. It interleaves them into
 * 2-channel audio (with FROM_CUSTOMER on the first channel) and publishes them to the Subscriber as a series of
 * {@link ByteBuffer}s.
 *
 * <p>The audio remains in linear16 format with a sample rate of 8000hz, just as it is received from KVS. The emitted
 * {@link ByteBuffer}s are 2048 bytes each. That is:
 * <ul>
 * 	<li>1024 bytes per channel</li>
 *	<li>512 samples per channel</li>
 *	<li>64ms of audio</li>
 * </ul>
 *
 * <p>Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.</p>
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class KvsStreamSubscription implements Subscription {
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final AtomicLong demand = new AtomicLong(0); // state container
	private final Subscriber<? super ByteBuffer> subscriber;
	private final KvsStreamTrack fromCustomerTrack;
	private final KvsStreamTrack toCustomerTrack;
	private final boolean enforceRealtime;
	private static final Logger logger = LogManager.getLogger(KvsStreamSubscription.class);

	/**
	 * @param s                 The subscriber who will receive audio from this subscription
	 * @param fromCustomerTrack The KVS track containing everything the customer says
	 * @param toCustomerTrack   The KVS track containing everything the customer hears
	 * @param enforceRealtime   If this is true we publish audio at its real resolution, waiting 64ms between each 64ms
	 *                          audio buffer. If it's false we publish audio as quickly as we can read it, which means
	 *                          we rapidly catch up to the current point in the call.
	 */
	public KvsStreamSubscription(
			Subscriber<? super ByteBuffer> s, KvsStreamTrack fromCustomerTrack, KvsStreamTrack toCustomerTrack, boolean enforceRealtime) {
		this.subscriber = Validate.notNull(s);
		this.fromCustomerTrack = Validate.notNull(fromCustomerTrack);
		this.toCustomerTrack = Validate.notNull(toCustomerTrack);
		this.enforceRealtime = enforceRealtime;
	}

	@Override
	public void request(long n) {
		if (n <= 0) {
			subscriber.onError(new IllegalArgumentException("Demand must be positive"));
		}

		demand.getAndAdd(n);

		String requestId = ThreadContext.get("requestId");
		executor.submit(() -> {
			// Propagate request ID to the newly created thread so it appears in log messages
			ThreadContext.put("requestId", requestId);

			try {
				// If enforceRealtime=true, we have to track the last published audio time so we can ensure a delay of at
				// least 64ms between publishing each 64ms buffer
				long lastPublishedAudioInUnixTime = -1;

				while (demand.get() > 0) {
					ByteBuffer fromCustomerBytes = KvsUtils.getByteBufferFromStream(fromCustomerTrack);
					ByteBuffer toCustomerBytes = KvsUtils.getByteBufferFromStream(toCustomerTrack);

					if (fromCustomerBytes.remaining() == 0 || toCustomerBytes.remaining() == 0) {
						logger.info("One or both KVS tracks ended; now closing session");

						if (fromCustomerBytes.remaining() != 0) {
							logger.warn("FROM_CUSTOMER track still had some audio left; discarding it");
						} else if (toCustomerBytes.remaining() != 0) {
							logger.warn("TO_CUSTOMER track still had some audio left; discarding it");
						} else {
							logger.info("FROM_CUSTOMER and TO_CUSTOMER tracks ended at the same time");
						}

						subscriber.onComplete();
						break;
					} else if (fromCustomerBytes.remaining() == 1024 && toCustomerBytes.remaining() == 1024) {
						logger.trace("Both tracks had valid frame sizes");
						ByteBuffer interleavedBytes = ByteBuffer.allocate(2048);
						for (int i = 0; i < 512; i++) {
							interleavedBytes.put(fromCustomerBytes.get());
							interleavedBytes.put(fromCustomerBytes.get());
							interleavedBytes.put(toCustomerBytes.get());
							interleavedBytes.put(toCustomerBytes.get());
						}
						interleavedBytes.flip();

						if (this.enforceRealtime) {
							long msSinceLastAudio = System.currentTimeMillis() - lastPublishedAudioInUnixTime;
							if (msSinceLastAudio < 64) {
								Thread.sleep(64 - msSinceLastAudio);
							}
							lastPublishedAudioInUnixTime = System.currentTimeMillis();
						}

						subscriber.onNext(interleavedBytes);
					} else {
						// In my experience this occurs once per call, very close to the end of the call, regardless of
						// the call's length.
						logger.warn("Unusual frame size in KVS stream. FROM_CUSTOMER = %s, TO_CUSTOMER = %s"
								.formatted(fromCustomerBytes.remaining(), toCustomerBytes.remaining()));
					}

					demand.getAndDecrement();
				}
			} catch (Exception e) {
				subscriber.onError(e);
			}
		});
	}

	@Override
	public void cancel() {
		executor.shutdown();
	}
}
