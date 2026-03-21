package org.bruneel.thankyouboard.images;

import java.util.UUID;

public interface ImagePresignService {
    PresignedUpload presign(UUID boardId, String contentType, long contentLengthBytes);
}

