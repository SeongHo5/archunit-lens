package com.example.constants;

import com.example.constants.Constants;

import static com.example.constants.Constants.INT;
import static com.example.constants.Constants.TEXT;

class ConstantReads {
    Object[] read(Constants receiver) {
        return new Object[] {
            Constants.BOOLEAN, Constants.BYTE, Constants.SHORT, Constants.CHAR,
            Constants.INT, Constants.LONG, Constants.FLOAT, Constants.DOUBLE,
            Constants.TEXT, Constants.EXPRESSION, receiver.INSTANCE,
            INT, TEXT, com.example.constants.Constants.INT,
            receiver.INT, receiver.TEXT
        };
    }
}
