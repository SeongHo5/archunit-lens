package com.example.constants;

import com.example.constants.Constants;

import static com.example.constants.Constants.RUNTIME;
import static com.example.constants.Constants.MUTABLE;

class RealReads {
    Object[] read(Constants receiver) {
        return new Object[] {
            Constants.RUNTIME, Constants.RUNTIME_TEXT, Constants.OBJECT,
            Constants.MUTABLE, Constants.INITIALIZED, receiver.instanceInitialized,
            RUNTIME, MUTABLE
        };
    }
}
