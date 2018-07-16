package com.julyerr;

import java.net.MalformedURLException;
import java.net.URL;

public class Test2 {
    public static void main(String[] args) {
        String url = "http://mirrors.163.com/deepin-cd/15.6/deepin-15.6-amd64.iso";
        try {
            URL u = new URL(url);
            System.out.println(u.getProtocol() + " is supported.");
        } catch (MalformedURLException e) {
            String protocol = url.substring(0, url.indexOf(":"));
            System.out.println(protocol + " is supported.");
        }
    }
}
