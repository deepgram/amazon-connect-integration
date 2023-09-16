package com.deepgram.kvsdgintegrator;

import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import org.apache.commons.lang3.Validate;

/**
 * Represents a track within a KVS stream (i.e. the "from customer" or "to customer" track) and our progress in reading
 * that track from KVS.
 */
public record KvsStreamTrack(StreamingMkvReader streamingMkvReader, KvsContactTagProcessor tagProcessor,
                             FragmentMetadataVisitor fragmentVisitor, String trackName) {
    public KvsStreamTrack(StreamingMkvReader streamingMkvReader,
                          KvsContactTagProcessor tagProcessor, FragmentMetadataVisitor fragmentVisitor,
                          String trackName) {
        this.streamingMkvReader = Validate.notNull(streamingMkvReader);
        this.tagProcessor = Validate.notNull(tagProcessor);
        this.fragmentVisitor = Validate.notNull(fragmentVisitor);
        this.trackName = Validate.notNull(trackName);
    }
}