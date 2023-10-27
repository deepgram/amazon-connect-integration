package com.deepgram.kvsdgintegrator;

import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.ResourceNotFoundException;

public class Warmer {
	/**
	 * Calls the major libraries used in this integration with dummy data in order to trigger classloading. If we don't
	 * do this, the first call through the system will experience high latency because the JVM must load all of these
	 * classes.
	 */
	public static void warmUpApplication() {
		AmazonKinesisVideo amazonKinesisVideo = AmazonKinesisVideoClientBuilder.standard().build();
		try {
			String dummyEndpoint = amazonKinesisVideo.getDataEndpoint(new GetDataEndpointRequest()
					.withAPIName(APIName.GET_MEDIA)
					.withStreamName("dummy")).getDataEndpoint();
		} catch(ResourceNotFoundException e) {
			// Do nothing as this is the expected result
		}
	}
}
