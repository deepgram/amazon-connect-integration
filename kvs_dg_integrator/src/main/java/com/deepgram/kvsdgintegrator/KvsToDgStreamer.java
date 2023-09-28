package com.deepgram.kvsdgintegrator;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.regions.Regions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

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
public class KvsToDgStreamer {

	private static final Regions REGION = Regions.fromName(System.getenv("APP_REGION"));
	private static final Logger logger = LogManager.getLogger(KvsToDgStreamer.class);

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
			client.startStreamingToDeepgram(publisher).get();
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