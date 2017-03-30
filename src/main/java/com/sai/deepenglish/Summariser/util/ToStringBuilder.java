package com.sai.deepenglish.Summariser.util;

import java.io.Serializable;

public class ToStringBuilder implements Serializable {
    StringBuffer output = new StringBuffer("");


    public ToStringBuilder(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        output = new StringBuffer(o.getClass().getName() + " " );
    }


    public ToStringBuilder append(String name, Object o) {
        if (o == null) {
            output.append(name + ": null");
        } else {
            output.append(name + ": " + o.toString());
        }
        return this;
    }

    public ToStringBuilder append(String name, double num) {
        output.append(name + ": " + num);
        return this;
    }

    public String toString() {
        return output.toString();
    }


}