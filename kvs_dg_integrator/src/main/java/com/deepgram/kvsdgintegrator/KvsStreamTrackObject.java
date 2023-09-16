package com.deepgram.kvsdgintegrator;

import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;

import java.io.FileOutputStream;

public class KvsStreamTrackObject {
    private StreamingMkvReader streamingMkvReader;
    private KvsContactTagProcessor tagProcessor;
    private FragmentMetadataVisitor fragmentVisitor;
    private FileOutputStream outputStream;
    private String trackName;

    public KvsStreamTrackObject(StreamingMkvReader streamingMkvReader,
                                KvsContactTagProcessor tagProcessor, FragmentMetadataVisitor fragmentVisitor,
                                FileOutputStream outputStream, String trackName) {
        this.streamingMkvReader = streamingMkvReader;
        this.tagProcessor = tagProcessor;
        this.fragmentVisitor = fragmentVisitor;
        this.outputStream = outputStream;
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

    public FileOutputStream getOutputStream() {
        return outputStream;
    }

    public String getTrackName() {
        return trackName;
    }
}