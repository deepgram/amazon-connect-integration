package com.deepgram.kvsdgintegrator;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.mkv.MkvDataElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvValue;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTrackMetadata;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClientBuilder;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetMediaRequest;
import com.amazonaws.services.kinesisvideo.model.GetMediaResult;
import com.amazonaws.services.kinesisvideo.model.StartSelector;
import com.amazonaws.services.kinesisvideo.model.StartSelectorType;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Utility class to interact with KVS streams
 *
 * <p>Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.</p>
 *
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
public final class KvsUtils {

    public enum TrackName {
        AUDIO_FROM_CUSTOMER("AUDIO_FROM_CUSTOMER"),
        AUDIO_TO_CUSTOMER("AUDIO_TO_CUSTOMER");

        private final String name;

        TrackName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static final Logger logger = LogManager.getLogger(KvsUtils.class);

    /**
     * Fetches the next ByteBuffer of size 1024 bytes from the KVS stream by parsing the frame from the MkvElement.
     * Each frame has a ByteBuffer of size 1024.
     *
     * <p>Actually, the above is the comment from Amazon Connect, but in reality some ByteBuffers returned by this function
     * have a nonzero size less than 1024.
     *
     * <p>In my experience, each Connect call will have exactly one FROM_CUSTOMER buffer with an unusual number of bytes,
     * and exactly one TO_CUSTOMER buffer with an unusual number of bytes. These will occur together right before the
     * end of the call. After these unusual buffers, each track will yield ~5 more 1024-byte buffers and then the stream
     * will end.
     */
    public static ByteBuffer getByteBufferFromStream(KvsStreamTrack kvsStreamTrack) throws MkvElementVisitException {
        StreamingMkvReader streamingMkvReader = kvsStreamTrack.streamingMkvReader();
        KvsContactTagProcessor tagProcessor = kvsStreamTrack.tagProcessor();
        FragmentMetadataVisitor fragmentVisitor = kvsStreamTrack.fragmentVisitor();
        String trackName = kvsStreamTrack.trackName();

        while (streamingMkvReader.mightHaveNext()) {
            Optional<MkvElement> mkvElementOptional = streamingMkvReader.nextIfAvailable();
            if (mkvElementOptional.isPresent()) {
                if (tagProcessor.shouldStopProcessing()) {
                    return ByteBuffer.allocate(0);
                }
                MkvElement mkvElement = mkvElementOptional.get();
                mkvElement.accept(fragmentVisitor);
                if (MkvTypeInfos.SIMPLEBLOCK.equals(mkvElement.getElementMetaData().getTypeInfo())) {
                    MkvDataElement dataElement = (MkvDataElement) mkvElement;
                    @SuppressWarnings("unchecked")
                    Frame frame = ((MkvValue<Frame>) dataElement.getValueCopy()).getVal();
                    ByteBuffer audioBuffer = frame.getFrameData();

                    long trackNumber = frame.getTrackNumber();
                    MkvTrackMetadata metadata = fragmentVisitor.getMkvTrackMetadata(trackNumber);
                    if (trackName.equals(metadata.getTrackName())) {
                        logger.trace("Returning KVS frame for track %s: %s".formatted(trackName, frame.toString()));
                        return audioBuffer;
                    } else if ("Track_audio/L16".equals(metadata.getTrackName()) && TrackName.AUDIO_FROM_CUSTOMER.getName().equals(trackName)) {
                        logger.trace("Returning KVS frame for track %s (backward compatible): %s".formatted(trackName, frame.toString()));
                        // backwards compatibility
                        return audioBuffer;
                    }

                    // do nothing
                }
            }
        }

        return ByteBuffer.allocate(0);
    }

    /**
     * Makes a GetMedia call to KVS and retrieves the InputStream corresponding to the given streamName and startFragmentNum
     */
    public static InputStream getInputStreamFromKVS(String streamName,
                                                    Regions region,
                                                    String startFragmentNum,
                                                    AWSCredentialsProvider awsCredentialsProvider) {
        Validate.notNull(streamName);
        Validate.notNull(region);
        Validate.notNull(startFragmentNum);
        Validate.notNull(awsCredentialsProvider);

        AmazonKinesisVideo amazonKinesisVideo = AmazonKinesisVideoClientBuilder.standard().build();

        String endPoint = amazonKinesisVideo.getDataEndpoint(new GetDataEndpointRequest()
                .withAPIName(APIName.GET_MEDIA)
                .withStreamName(streamName)).getDataEndpoint();

        AmazonKinesisVideoMediaClientBuilder amazonKinesisVideoMediaClientBuilder = AmazonKinesisVideoMediaClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endPoint, region.getName()))
                .withCredentials(awsCredentialsProvider);
        AmazonKinesisVideoMedia amazonKinesisVideoMedia = amazonKinesisVideoMediaClientBuilder.build();

        StartSelector startSelector = new StartSelector()
                .withStartSelectorType(StartSelectorType.FRAGMENT_NUMBER)
                .withAfterFragmentNumber(startFragmentNum);
        logger.info("StartSelector set to FRAGMENT_NUMBER: " + startFragmentNum);

        GetMediaResult getMediaResult = amazonKinesisVideoMedia.getMedia(new GetMediaRequest()
                .withStreamName(streamName)
                .withStartSelector(startSelector));

        logger.info("GetMedia called on stream {} response {} requestId {}", streamName,
                getMediaResult.getSdkHttpMetadata().getHttpStatusCode(),
                getMediaResult.getSdkResponseMetadata().getRequestId());

        return getMediaResult.getPayload();
    }
}
