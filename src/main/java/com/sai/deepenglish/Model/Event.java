package com.sai.deepenglish.Model;

import java.util.List;

public class Event {

    private Boolean success;
    private List<Data> data;
    public Boolean getSuccess ()
    {
        return success;
    }
    public List<Data> getData(){
        return data;
    }
}