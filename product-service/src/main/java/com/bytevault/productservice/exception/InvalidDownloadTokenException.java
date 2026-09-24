package com.bytevault.productservice.exception;

public class InvalidDownloadTokenException extends RuntimeException {

    public InvalidDownloadTokenException() {
        super("Download link is invalid or has expired");
    }
}
