package com.julyerr;

import com.julyerr.filedownloader.DownloadManager;
import com.julyerr.filedownloader.DownloadMission;

import java.io.IOException;

import static com.julyerr.filedownloader.DownloadMission.newDownloadMission;

public class Test {
    public static void main(String[] args) {
        DownloadManager downloadManager = DownloadManager.getInstance();
        String qQString = "https://mirrors.aliyun.com/docker-ce/linux/centos/7/x86_64/stable/Packages/docker-ce-18.03.1.ce-1.el7.centos.x86_64.rpm";

        /*** type you save direcotry ****/
        String saveDirectory = "/home/julyerr/github/yourself/repo/filedownloader/target";
        try {
            DownloadMission mission = newDownloadMission(qQString,
                    saveDirectory);
            downloadManager.addMission(mission);
            downloadManager.start();
            int counter = 0;
            while (true) {
                System.out.println("Downloader information Speed:"
                        + downloadManager.getReadableTotalSpeed()
                        + " Down Size:"
                        + downloadManager.getReadableDownloadSize());
                Thread.sleep(1000);
                counter++;
                if (downloadManager.isAllTasksFinished()) {
//                    让其他线程处理运行完成
                    Thread.sleep(500);
                    break;
                }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        downloadManager.shutdDownloadRudely();
    }
}
