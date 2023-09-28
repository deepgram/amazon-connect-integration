package com.deepgram.kvsdgintegrator;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Streams Amazon Connect calls to Deepgram for transcription. The data flow is:
 * <p>
 * Amazon Connect => AWS KVS => KvsToDgIntegrator => Deepgram
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
public class KvsToDgStreamer implements RequestHandler<IntegratorArguments, String> {

	private static final Regions REGION = Regions.fromName(System.getenv("APP_REGION"));
	private static final Logger logger = LogManager.getLogger(KvsToDgStreamer.class);

	/**
	 * Handler function for when the integrator is being run as a Lambda. In production, the integrator should not be
	 * run as a Lambda unless you're OK with it shutting down after 15 minutes.
	 */
	@Override
	public String handleRequest(IntegratorArguments request, Context context) {
		logger.info("received request : " + request.toString());
		logger.info("received context: " + context.toString());

		String deepgramApiKey = System.getenv("DEEPGRAM_API_KEY");
		if (deepgramApiKey == null) {
			System.out.println("ERROR: this task expects an environment variable DEEPGRAM_API_KEY");
			return "{ \"result\": \"Failed\" }";
		}

		try {
			startKvsToDgStreaming(request, deepgramApiKey);
			return "{ \"result\": \"Success\" }";

		} catch (Exception e) {
			logger.error("KVS to Transcribe Streaming failed with: ", e);
			return "{ \"result\": \"Failed\" }";
		}
	}

	public static void startKvsToDgStreaming(IntegratorArguments integratorArguments, String deepgramApiKey) throws Exception {
		String streamARN = integratorArguments.kvsStream().arn();
		String startFragmentNum = integratorArguments.kvsStream().startFragmentNumber();
		String contactId = integratorArguments.contactId();

		String streamName = streamARN.substring(streamARN.indexOf("/") + 1, streamARN.lastIndexOf("/"));

		KvsStreamTrack fromCustomerTrack = getKvsStreamTrack(
				streamName, startFragmentNum, KvsUtils.TrackName.AUDIO_FROM_CUSTOMER.getName(), contactId);
		KvsStreamTrack toCustomerTrack = getKvsStreamTrack(
				streamName, startFragmentNum, KvsUtils.TrackName.AUDIO_TO_CUSTOMER.getName(), contactId);

		try (DeepgramStreamingClient client = new DeepgramStreamingClient(integratorArguments.dgParams(), deepgramApiKey)) {
			KvsStreamPublisher publisher = new KvsStreamPublisher(fromCustomerTrack, toCustomerTrack);
			CompletableFuture<Void> streamToDeepgramFuture = client.startStreamingToDeepgram(publisher);

			// Synchronous wait for stream to close, and close client connection
			// Timeout of 890 seconds because the Lambda function can be run for at most 15 mins (~890 secs)
			// TODO: Remove this timeout when packaging for Fargate
			streamToDeepgramFuture.get(890, TimeUnit.SECONDS);

		} catch (TimeoutException e) {
			logger.debug("Timing out KVS to Transcribe Streaming after 890 sec");

		} catch (Exception e) {
			logger.error("Error during streaming: ", e);
			throw e;
		}
	}

	private static KvsStreamTrack getKvsStreamTrack(String streamName, String startFragmentNum, String trackName,
																String contactId) {
		logger.trace("Creating KVS track object for track %s".formatted(trackName));

		InputStream kvsInputStream = KvsUtils.getInputStreamFromKVS(streamName, REGION, startFragmentNum, getAWSCredentials());
		StreamingMkvReader streamingMkvReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(kvsInputStream));

		KvsContactTagProcessor tagProcessor = new KvsContactTagProcessor(contactId);
		FragmentMetadataVisitor fragmentVisitor = FragmentMetadataVisitor.create(Optional.of(tagProcessor));

		return new KvsStreamTrack(streamingMkvReader, tagProcessor, fragmentVisitor, trackName);
	}

	/**
	 * @return AWS credentials to be used to connect to KVS
	 */
	private static AWSCredentialsProvider getAWSCredentials() {
		return DefaultAWSCredentialsProviderChain.getInstance();
	}

	/**
	 * Publishes `ByteBuffers` containing merged multichannel audio of the two tracks.
	 */
	private record KvsStreamPublisher(KvsStreamTrack fromCustomerTrack,
									  KvsStreamTrack toCustomerTrack) implements Publisher<ByteBuffer> {

		@Override
		public void subscribe(Subscriber<? super ByteBuffer> s) {
			s.onSubscribe(new KvsStreamSubscription(s, fromCustomerTrack, toCustomerTrack));
		}
	}
}