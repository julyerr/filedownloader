package com.julyerr.filedownloader;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;

import static com.julyerr.filedownloader.DownloadMission.newDownloadMission;

public class Filedownloader {
    public static void main(String[] args) throws ParseException {
//        参数解析
        Options opts = new Options();
        opts.addOption("h", "help", true, "show usage");
        opts.addOption("d", "dir", true, "target directory,default \".\"");
        opts.addOption("n", "name", true, "save filename,default url's name");
        CommandLineParser parser = new DefaultParser();
        CommandLine cl = null;
        boolean valid = true;
        try {
            cl = parser.parse(opts, args);
            if (cl.hasOption('h') || cl.getArgs().length != 1 || !cl.getArgs()[0].startsWith("http")) {
                valid = false;
            }
        } catch (ParseException e) {
            valid = false;
        }
        if (!valid) {
            HelpFormatter hf = new HelpFormatter();
            hf.printHelp("java -jar filedownloader-${version}.jar http://filename", opts);
            return;
        }

        String dir = cl.getOptionValue("d");
        String name = cl.getOptionValue("n");
        String target = cl.getArgs()[0];

//        实际下载
        DownloadManager downloadManager = DownloadManager.getInstance();

        try {
            DownloadMission mission = null;
            if (dir != null && name != null) {
                mission = newDownloadMission(target, dir, name);
            } else if (dir != null) {
                mission = newDownloadMission(target, dir);
            } else if (name != null) {
                mission = newDownloadMission(target, new File(".").getCanonicalPath(), name);
            } else {
                mission = newDownloadMission(target);
            }

            downloadManager.addMission(mission);
            downloadManager.start();
            while (true) {
                System.out.println("Downloader information Speed:"
                        + downloadManager.getReadableTotalSpeed()
                        + " Down Size:"
                        + downloadManager.getReadableDownloadSize());
                Thread.sleep(1000);
                if (downloadManager.isAllTasksFinished()) {
                    break;
                }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        downloadManager.shutdownSafely();
        System.exit(0);
    }
}
