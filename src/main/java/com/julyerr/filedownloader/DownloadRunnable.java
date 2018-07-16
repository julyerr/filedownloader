package com.julyerr.filedownloader;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.julyerr.filedownloader.DownloadMission.MissionMonitor;

@XmlRootElement(name = "Downloading")
@XmlAccessorType(XmlAccessType.NONE)
public class DownloadRunnable implements Runnable {

    private static final int BUFFER_SIZE = 1024;

    private static int counter = 0;
    private String mFileUrl;
    private String mSaveDirectory;
    private String mSaveFileName;
    @XmlElement(name = "StartPosition")
    private long mStartPosition;
    @XmlElement(name = "EndPosition")
    private long mEndPosition;
    public final int MISSION_ID;
    public final int ID = counter++;

    @XmlElement(name = "CurrentPosition")
    private long mCurrentPosition;

    //    为了即时退出程序
    private InputStream inputStream;

    private MissionMonitor mDownloadMonitor;

    private DownloadRunnable() {
        // just use for annotation
        // -1 is meanningless
        MISSION_ID = -1;
    }

    //	指定起始位置和当下位置相同
    public DownloadRunnable(MissionMonitor monitor, String mFileUrl,
                            String mSaveDirectory, String mSaveFileName, long mStartPosition,
                            long mEndPosition) {
        super();
        this.mFileUrl = mFileUrl;
        this.mSaveDirectory = mSaveDirectory;
        this.mSaveFileName = mSaveFileName;
        this.mStartPosition = mStartPosition;
        this.mEndPosition = mEndPosition;
        this.mDownloadMonitor = monitor;
        this.mCurrentPosition = this.mStartPosition;
        MISSION_ID = monitor.mHostMission.mMissionID;
    }

    //	指定当下位置
    public DownloadRunnable(MissionMonitor monitor, String mFileUrl,
                            String mSaveDirectory, String mSaveFileName, long mStartPosition,
                            long mCurrentPosition, long mEndPosition) {
        this(monitor, mFileUrl, mSaveDirectory, mSaveFileName, mStartPosition,
                mEndPosition);
        this.mCurrentPosition = mCurrentPosition;
    }

    @Override
    public void run() {
        File targetFile;
//			创建文件
        targetFile = new File(mSaveDirectory + File.separator
                + mSaveFileName);

        System.out.println("Download Task ID:" + Thread.currentThread().getId()
                + " has been started! Range From " + mCurrentPosition + " To "
                + mEndPosition);
        BufferedInputStream bufferedInputStream = null;
        RandomAccessFile randomAccessFile = null;
        byte[] buf = new byte[BUFFER_SIZE];
        URLConnection urlConnection = null;
        if (mStartPosition < mEndPosition) {
            try {
                try {
                    //            打开URL连接，设置读取数据段
                    URL url = new URL(mFileUrl);
                    urlConnection = url.openConnection();
                    urlConnection.setRequestProperty("Range", "bytes="
                            + mCurrentPosition + "-" + mEndPosition);
                    randomAccessFile = new RandomAccessFile(targetFile, "rw");
//            设置当下位置
                    randomAccessFile.seek(mCurrentPosition);
//               以前版本的一个bug，需要建立好连接才能获取到输出和输出流
                    urlConnection.connect();
                    this.inputStream = urlConnection.getInputStream();
                    bufferedInputStream = new BufferedInputStream(
                            urlConnection.getInputStream());
                    while (mCurrentPosition < mEndPosition) {
//                如果发生了中断，退出
                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("Download TaskID:"
                                    + Thread.currentThread().getId()
                                    + " was interrupted, Start:" + mStartPosition
                                    + " Current:" + mCurrentPosition + " End:"
                                    + mEndPosition);
                            break;
                        }
                        int len = bufferedInputStream.read(buf, 0, BUFFER_SIZE);
                        if (len == -1)
                            break;
                        else {
                            randomAccessFile.write(buf, 0, len);
                            mCurrentPosition += len;
                            mDownloadMonitor.down(len);
                        }
                    }
                } finally {
                    bufferedInputStream.close();
                    randomAccessFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public DownloadRunnable split() {
        long end = mEndPosition;
        long remaining = mEndPosition - mCurrentPosition;
        long remainingCenter = remaining >> 1;
        System.out.print("CurrentPosition:" + mCurrentPosition
                + " EndPosition:" + mEndPosition + "Rmaining:" + remaining
                + " ");
//        10*1024×1024
        if (remainingCenter > 10485760) {
            long centerPosition = remainingCenter + mCurrentPosition;
            System.out.print(" Center position:" + centerPosition);
            //    将任务分段,多线程直接操作mEndPosition是有风险的，但是下载速度很慢，mEndPosition变化在BUF_SIZE(远小于1M)，发生错误很小很小
            mEndPosition = centerPosition;

            DownloadRunnable newSplitedRunnable = new DownloadRunnable(
                    mDownloadMonitor, mFileUrl, mSaveDirectory, mSaveFileName,
                    centerPosition + 1, end);
            mDownloadMonitor.mHostMission.addPartedMission(newSplitedRunnable);
            return newSplitedRunnable;
        } else {
            System.out.println(toString() + " can not be splited ,less than 10M");
            return null;
        }
    }

    public void interrupt() {
        try {
            this.inputStream.close();
        } catch (IOException e) {
        }
    }

    public boolean isFinished() {
        return mCurrentPosition >= mEndPosition;
    }

    public long getCurrentPosition() {
        return mCurrentPosition;
    }

    public long getEndPosition() {
        return mEndPosition;
    }

    public long getStartPosition() {
        return mStartPosition;
    }

}
