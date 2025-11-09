package com.lbg.markets.luxback.model;
import lombok.Data;

@Data
public class FileMetadata {

    String originalFilename;
    String storedFilename;
    long size;
    String contentType;

}
