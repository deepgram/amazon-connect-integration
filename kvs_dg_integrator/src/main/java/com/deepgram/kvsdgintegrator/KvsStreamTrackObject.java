package com.deepgram.kvsdgintegrator;

import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;

public class KvsStreamTrackObject {
    private StreamingMkvReader streamingMkvReader;
    private KvsContactTagProcessor tagProcessor;
    private FragmentMetadataVisitor fragmentVisitor;
    private String trackName;

    public KvsStreamTrackObject(StreamingMkvReader streamingMkvReader,
                                KvsContactTagProcessor tagProcessor, FragmentMetadataVisitor fragmentVisitor,
                                String trackName) {
        this.streamingMkvReader = streamingMkvReader;
        this.tagProcessor = tagProcessor;
        this.fragmentVisitor = fragmentVisitor;
        this.trackName = trackName;
    }

    public StreamingMkvReader getStreamingMkvReader() {
        return streamingMkvReader;
    }

    public KvsContactTagProcessor getTagProcessor() {
        return tagProcessor;
    }

    public FragmentMetadataVisitor getFragmentVisitor() {
        return fragmentVisitor;
    }

    public String getTrackName() {
        return trackName;
    }
}