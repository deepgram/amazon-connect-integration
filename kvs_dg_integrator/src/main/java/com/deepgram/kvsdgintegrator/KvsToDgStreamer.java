package com.deepgram.kvsdgintegrator;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Demonstrate Amazon Connect's real-time transcription feature using AWS Kinesis Video Streams and AWS Transcribe.
 * The data flow is :
 * <p>
 * Amazon Connect => AWS KVS => AWS Transcribe => AWS DynamoDB
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
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

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

		KVSStreamTrackObject kvsStreamTrackObjectFromCustomer = getKVSStreamTrackObject(
				streamName, startFragmentNum, KVSUtils.TrackName.AUDIO_FROM_CUSTOMER.getName(), contactId);
		KVSStreamTrackObject kvsStreamTrackObjectToCustomer = getKVSStreamTrackObject(
				streamName, startFragmentNum, KVSUtils.TrackName.AUDIO_TO_CUSTOMER.getName(), contactId);

		try (DeepgramStreamingClient client = new DeepgramStreamingClient(integratorArguments.dgParams(), deepgramApiKey)) {

			logger.info("Calling Transcribe service..");
			CompletableFuture<Void> fromCustomerResult = getStartStreamingTranscriptionFuture(
					kvsStreamTrackObjectFromCustomer, client, KVSUtils.TrackName.AUDIO_FROM_CUSTOMER.getName());

			CompletableFuture<Void> toCustomerResult = getStartStreamingTranscriptionFuture(
					kvsStreamTrackObjectToCustomer, client, KVSUtils.TrackName.AUDIO_TO_CUSTOMER.getName());

			// Synchronous wait for stream to close, and close client connection
			// Timeout of 890 seconds because the Lambda function can be run for at most 15 mins (~890 secs)
			// TODO: Remove this timeout when packaging for Fargate
			fromCustomerResult.get(890, TimeUnit.SECONDS);
//			toCustomerResult.get(890, TimeUnit.SECONDS);

		} catch (TimeoutException e) {
			logger.debug("Timing out KVS to Transcribe Streaming after 890 sec");

		} catch (Exception e) {
			logger.error("Error during streaming: ", e);
			throw e;
		}
	}

	/**
	 * Create all objects necessary for KVS streaming from each track
	 */
	private static KVSStreamTrackObject getKVSStreamTrackObject(String streamName, String startFragmentNum, String trackName,
																String contactId) throws FileNotFoundException {
		InputStream kvsInputStream = KVSUtils.getInputStreamFromKVS(streamName, REGION, startFragmentNum, getAWSCredentials());
		StreamingMkvReader streamingMkvReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(kvsInputStream));

		KVSContactTagProcessor tagProcessor = new KVSContactTagProcessor(contactId);
		FragmentMetadataVisitor fragmentVisitor = FragmentMetadataVisitor.create(Optional.of(tagProcessor));

		String fileName = String.format("%s_%s_%s.raw", contactId, DATE_FORMAT.format(new Date()), trackName);
		Path saveAudioFilePath = Paths.get("/tmp", fileName);
		FileOutputStream fileOutputStream = new FileOutputStream(saveAudioFilePath.toString());

		return new KVSStreamTrackObject(streamingMkvReader, tagProcessor, fragmentVisitor, fileOutputStream, trackName);
	}


	private static CompletableFuture<Void> getStartStreamingTranscriptionFuture(KVSStreamTrackObject kvsStreamTrackObject,
																				DeepgramStreamingClient client,
																				String channel) {
		return client.startStreamingToDeepgram(
				new KVSAudioStreamPublisher(
						kvsStreamTrackObject.getStreamingMkvReader(),
						kvsStreamTrackObject.getOutputStream(),
						kvsStreamTrackObject.getTagProcessor(),
						kvsStreamTrackObject.getFragmentVisitor(),
						kvsStreamTrackObject.getTrackName()),
				channel
		);
	}

	/**
	 * @return AWS credentials to be used to connect to s3 (for fetching and uploading audio) and KVS
	 */
	private static AWSCredentialsProvider getAWSCredentials() {
		return DefaultAWSCredentialsProviderChain.getInstance();
	}

	/**
	 * KVSAudioStreamPublisher implements audio stream publisher.
	 * It emits audio events from a KVS stream asynchronously in a separate thread
	 */
	private record KVSAudioStreamPublisher(
			StreamingMkvReader streamingMkvReader,
			OutputStream outputStream,
			KVSContactTagProcessor tagProcessor,
			FragmentMetadataVisitor fragmentVisitor,
			String track) implements Publisher<AudioStream> {

		@Override
		public void subscribe(Subscriber<? super AudioStream> s) {
			s.onSubscribe(new KVSByteToAudioEventSubscription(s, streamingMkvReader, outputStream, tagProcessor, fragmentVisitor, track));
		}
	}
}