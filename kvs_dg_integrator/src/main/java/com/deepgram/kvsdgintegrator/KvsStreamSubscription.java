package com.deepgram.kvsdgintegrator;

import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import org.apache.commons.lang3.Validate;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This Subscription converts audio bytes received from the KVS stream into AudioEvents
 * that can be sent to the Transcribe service. It implements a simple demand system that will read chunks of bytes
 * from a KVS stream using the KVS parser library
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

	private static final int CHUNK_SIZE_IN_KB = 4;
	private ExecutorService executor = Executors.newFixedThreadPool(1); // Change nThreads here!! used in SubmissionPublisher not subscription
	private AtomicLong demand = new AtomicLong(0); // state container
	private final Subscriber<? super ByteBuffer> subscriber;
	private final StreamingMkvReader streamingMkvReader;
	private final KvsContactTagProcessor tagProcessor;
	private final FragmentMetadataVisitor fragmentVisitor;
	private final String track;

	public KvsStreamSubscription(Subscriber<? super ByteBuffer> s, StreamingMkvReader streamingMkvReader,
								 KvsContactTagProcessor tagProcessor,
								 FragmentMetadataVisitor fragmentVisitor, String track) {
		this.subscriber = Validate.notNull(s);
		this.streamingMkvReader = Validate.notNull(streamingMkvReader);
		this.tagProcessor = Validate.notNull(tagProcessor);
		this.fragmentVisitor = Validate.notNull(fragmentVisitor);
		this.track = Validate.notNull(track);
	}

	@Override
	public void request(long n) {
		if (n <= 0) {
			subscriber.onError(new IllegalArgumentException("Demand must be positive"));
		}

		demand.getAndAdd(n);
		//We need to invoke this in a separate thread because the call to subscriber.onNext(...) is recursive
		executor.submit(() -> {
			try {
				while (demand.get() > 0) {
					// return byteBufferDetails and consume this with an input stream then feed to output stream
					ByteBuffer audioBuffer = KvsUtils.getByteBufferFromStream(streamingMkvReader, fragmentVisitor, tagProcessor, CHUNK_SIZE_IN_KB, track);

					if (audioBuffer.remaining() > 0) {
						subscriber.onNext(audioBuffer);
					} else {
						subscriber.onComplete();
						break;
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
