package com.deepgram.kvsdgintegrator;

import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;

public class KVSStreamTrackObject {
    private StreamingMkvReader streamingMkvReader;
    private KVSContactTagProcessor tagProcessor;
    private FragmentMetadataVisitor fragmentVisitor;
    private FileOutputStream outputStream;
    private String trackName;

    public KVSStreamTrackObject(StreamingMkvReader streamingMkvReader,
                                KVSContactTagProcessor tagProcessor, FragmentMetadataVisitor fragmentVisitor,
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

    public KVSContactTagProcessor getTagProcessor() {
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