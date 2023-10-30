package com.deepgram.kvsdgintegrator;

import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadata;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Optional;

/**
 * An MkvTagProcessor that will ensure that we are only reading until end of stream OR the contact id changes
 * from what is expected.
 */
public class KvsContactTagProcessor implements FragmentMetadataVisitor.MkvTagProcessor {
    private static final Logger logger = LogManager.getLogger(KvsContactTagProcessor.class);
    private final String contactId;
    private boolean sameContact = true;

    public KvsContactTagProcessor(String contactId) {
        this.contactId = contactId;
    }

    public void process(MkvTag mkvTag, Optional<FragmentMetadata> currentFragmentMetadata) {
        if ("ContactId".equals(mkvTag.getTagName())) {
            if (contactId.equals(mkvTag.getTagValue())) {
                sameContact = true;
            }
            else {
                logger.info(String.format("Contact Id in tag does not match expected, will stop streaming. "
                                + "contact id: %s, expected: %s",
                        mkvTag.getTagValue(), contactId));
                sameContact = false;
            }
        }
    }

    public boolean shouldStopProcessing() {
        return !sameContact;
    }
}
