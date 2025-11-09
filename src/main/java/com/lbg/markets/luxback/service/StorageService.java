package com.lbg.markets.luxback.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class StorageService {
    public boolean exists(String path) {
        return false;
    }

    public String readString(String path) {
        return null;
    }

    public void append(String path, String string) {

    }

    public void writeString(String path, String string) {

    }

    public List<String> listFiles(String auditIndexPath) {
        return null;
    }
}
