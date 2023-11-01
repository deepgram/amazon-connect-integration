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
 * Amazon Connect => AWS KVS => KVS DG Integrator => Deepgram
 */
public class KvsToDgStreamer {

	private static final Regions REGION = Regions.fromName(System.getenv("APP_REGION"));
	private static final Logger logger = LogManager.getLogger(KvsToDgStreamer.class);

	/**
	 * Streams a call from KVS to Deepgram, blocking until the streaming session is finished.
	 */
	public static void doStreamingSession(
			IntegratorArguments integratorArguments,
			String deepgramApi,
			String deepgramApiKey,
			boolean enforceRealtime
	) throws Exception {
		String streamARN = integratorArguments.kvsStream().arn();
		String startFragmentNum = integratorArguments.kvsStream().startFragmentNumber();
		String contactId = integratorArguments.contactId();

		String streamName = streamARN.substring(streamARN.indexOf("/") + 1, streamARN.lastIndexOf("/"));

		KvsStreamTrack fromCustomerTrack = getKvsStreamTrack(
				streamName, startFragmentNum, KvsUtils.TrackName.AUDIO_FROM_CUSTOMER.getName(), contactId);
		KvsStreamTrack toCustomerTrack = getKvsStreamTrack(
				streamName, startFragmentNum, KvsUtils.TrackName.AUDIO_TO_CUSTOMER.getName(), contactId);

		DeepgramStreamingClient client = new DeepgramStreamingClient(
				deepgramApi, deepgramApiKey, integratorArguments.dgParams());
		KvsStreamPublisher publisher = new KvsStreamPublisher(fromCustomerTrack, toCustomerTrack, enforceRealtime);

		client.startStreamingToDeepgram(publisher).get();
	}

	private static KvsStreamTrack getKvsStreamTrack(
			String streamName,
			String startFragmentNum,
			String trackName,
			String contactId
	) {
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
	 * Publishes {@link ByteBuffer}s containing merged multichannel audio of the two tracks.
	 */
	public record KvsStreamPublisher(
			KvsStreamTrack fromCustomerTrack,
			KvsStreamTrack toCustomerTrack,
			boolean enforceRealtime
	) implements Publisher<ByteBuffer> {
		@Override
		public void subscribe(Subscriber<? super ByteBuffer> s) {
			s.onSubscribe(new KvsStreamSubscription(s, fromCustomerTrack, toCustomerTrack, enforceRealtime));
		}
	}
}
