package com.lbg.markets.luxback.security;

/**
 * Application roles for access control
 */
public enum Role {
    /**
     * Regular user - can upload files
     */
    USER,

    /**
     * Administrator - can upload files and browse/download all files
     */
    ADMIN
}
