package org.deepstack.ai.kernel.file;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileInfo implements Serializable {
    private String fileUrl;
    private String fileMd5;
    private String fileName;
    private String fileId;
    private String contentType;
    private Long size;
    private String localPath;
    private String url;
}
