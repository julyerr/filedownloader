package com.julyerr.filedownloader;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

//保存下载进度
@XmlRootElement(namespace = "com.julyerr.downloader")
@XmlAccessorType(XmlAccessType.NONE)
public class DownloadMission {

    public static final int READY = 1;
    public static final int DOWNLOADING = 2;
    public static final int PAUSED = 3;
    public static final int FINISHED = 4;

    public static int DEFAULT_THREAD_COUNT = 4;

    @XmlElement(name = "URL")
    protected String mUrl;
    @XmlElement(name = "SaveDirectory")
    protected String mSaveDirectory;
    @XmlElement(name = "SaveName")
    protected String mSaveName;
    protected int mMissionID = MISSION_ID_COUNTER++;
    @XmlElementWrapper(name = "Downloadings")
    @XmlElement(name = "Downloading")
//    任务启动的线程数组
    private ArrayList<DownloadRunnable> mDownloadParts = new ArrayList<>();
    //    恢复任务数组
    private ArrayList<RecoveryRunnableInfo> mRecoveryRunnableInfos = new ArrayList<>();

    @XmlElement(name = "MissionStatus")
    private int mMissionStatus = READY;

    private String mProgressDir;
    private String mProgressFileName;
    @XmlElement(name = "FileSize")
    private long mFileSize;
    private int mThreadCount = DEFAULT_THREAD_COUNT;
    private boolean isFinished = false;

    @XmlElement(name = "MissionMonitor")
    protected MissionMonitor mMonitor = new MissionMonitor(this);
    @XmlElement(name = "SpeedMonitor")
    protected SpeedMonitor mSpeedMonitor = new SpeedMonitor(this);

    protected StoreMonitor mStoreMonitor = new StoreMonitor();
    protected Timer mSpeedTimer = new Timer();
    protected Timer mStoreTimer = new Timer();

    //    传入的线程池引用
    protected DownloadThreadPool mThreadPoolRef;

    private static int MISSION_ID_COUNTER = 0;

    static class RecoveryRunnableInfo {

        private long mStartPosition;
        private long mEndPosition;
        private long mCurrentPosition;
        private boolean isFinished = false;

        public RecoveryRunnableInfo(long start, long current, long end) {
            if (end > start && current > start) {
                mStartPosition = start;
                mEndPosition = end;
                mCurrentPosition = current;
            }
            if (mCurrentPosition >= mEndPosition) {
                isFinished = true;
            }
        }

        public long getStartPosition() {
            return mStartPosition;
        }

        public long getEndPosition() {
            return mEndPosition;
        }

        public long getCurrentPosition() {
            return mCurrentPosition;
        }

        public boolean isFinished() {
            return isFinished;
        }
    }

    @XmlRootElement(name = "MissionMonitor")
    @XmlAccessorType(XmlAccessType.NONE)
    static class MissionMonitor {
        //多线程可能同时进行操作
        @XmlElement(name = "DownloadedSize")
        @XmlJavaTypeAdapter(AtomicLongAdapter.class)
        private AtomicLong mDownloadedSize = new AtomicLong();
        public DownloadMission mHostMission;

        public MissionMonitor() {
            mHostMission = null;
        }

        public MissionMonitor(DownloadMission monitorBelongsTo) {
            mHostMission = monitorBelongsTo;
        }

        public void down(int size) {
            mDownloadedSize.addAndGet(size);
            if (mDownloadedSize.longValue() == mHostMission.getFileSize()) {
                mHostMission.setDownloadStatus(FINISHED);
            }
        }

        public long getDownloadedSize() {
            return mDownloadedSize.get();
        }

    }

    @XmlRootElement(name = "SpeedMonitor")
    @XmlAccessorType(XmlAccessType.NONE)
    private static class SpeedMonitor extends TimerTask {

        @XmlElement(name = "LastSecondSize")
        private long mLastSecondSize;
        @XmlElement(name = "CurrentSecondSize")
        private long mCurrentSecondSize;
        @XmlElement(name = "Speed")
        private long mSpeed;
        @XmlElement(name = "MaxSpeed")
        private long mMaxSpeed;
        @XmlElement(name = "AverageSpeed")
        private long mAverageSpeed;
        @XmlElement(name = "TimePassed")
        private int mCounter;

        private DownloadMission mHostMission;

        private SpeedMonitor() {
            // never use , for annotation
        }

        public long getMaxSpeed() {
            return mMaxSpeed;
        }

        public SpeedMonitor(DownloadMission missionBelongTo) {
            mHostMission = missionBelongTo;
        }

        @Override
//		每一秒时间更新速度
        public void run() {
            mCounter++;
            mCurrentSecondSize = mHostMission.getDownloadedSize();
            mSpeed = mCurrentSecondSize - mLastSecondSize;
            mLastSecondSize = mCurrentSecondSize;
            if (mSpeed > mMaxSpeed) {
                mMaxSpeed = mSpeed;
            }

            mAverageSpeed = mCurrentSecondSize / mCounter;
        }

        public int getDownloadedTime() {
            return mCounter;
        }

        public long getSpeed() {
            return mSpeed;
        }

        public long getAverageSpeed() {
            return mAverageSpeed;
        }
    }

    private class StoreMonitor extends TimerTask {
        @Override
        public void run() {
//			每5秒更新文件内容
            storeProgress();
        }
    }

    private DownloadMission() {
        // just for annotation
    }

    public static DownloadMission newDownloadMission(String url)
            throws IOException {
        File tmp = new File(".");
        String saveDirectory = tmp.getCanonicalPath();
        String saveName = url.substring(url.lastIndexOf("/") + 1);
        return newMissionInterface(url, saveDirectory, saveName);
    }

    public static DownloadMission newDownloadMission(String url, String saveDirectory)
            throws IOException {
        String saveName = url.substring(url.lastIndexOf("/") + 1);
        return newMissionInterface(url, saveDirectory, saveName);
    }

    public static DownloadMission newDownloadMission(String url, String saveDirectory, String saveName)
            throws IOException {
        return newMissionInterface(url, saveDirectory, saveName);
    }

    private static DownloadMission newMissionInterface(String url, String saveDirectory, String saveName)
            throws IOException {
        //        如果任务已经下载完成，返回
        if (isMission_Finished(url, saveDirectory, saveName)) {
            return null;
        }
        createTargetFile(saveDirectory, saveName);
        createProgessFile(saveDirectory, saveName);

        return recoverMissionFromProgressFile(url, saveDirectory, saveName);
    }

    public static boolean isMission_Finished(String url, String saveDirectory, String saveName)
            throws IOException {
        long size = getContentLength(url);
        if (saveDirectory.endsWith("/")) {
            saveDirectory = saveDirectory.substring(0, saveDirectory.length() - 1);
        }
        File file = new File(saveDirectory + "/" + saveName);
//        下载文件大小和网络请求所获文件大小相同
        if (file.length() == size) {
            System.out.println("file has already downloaded");
            return true;
        }
        return false;
    }

    public static void createTargetFile(String saveDir, String saveName)
            throws IOException {
        if (saveDir.lastIndexOf(File.separator) == saveDir.length() - 1) {
            saveDir = saveDir.substring(0, saveDir.length() - 1);
        }
        File dirFile = new File(saveDir);
        if (dirFile.exists() == false) {
            if (dirFile.mkdirs() == false) {
                throw new RuntimeException("Error to create directory");
            }
        }

        File file = new File(dirFile.getPath() + File.separator + saveName);
        if (file.exists() == false) {
            file.createNewFile();
        }
    }

    public int getMissionID() {
        return mMissionID;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String Url) {
        this.mUrl = Url;
    }

    public String getSaveDirectory() {
        return mSaveDirectory;
    }

    public void setSaveDirectory(String SaveDirectory) {
        this.mSaveDirectory = SaveDirectory;
    }

    public String getSaveName() {
        return mSaveName;
    }

    public void setSaveName(String SaveName) {
        this.mSaveName = SaveName;
    }

    public void setMissionThreadCount(int thread_count) {
        mThreadCount = thread_count;
    }

    public int getMissionThreadCount() {
        return mThreadCount;
    }

    public void setDefaultThreadCount(int default_thread_count) {
        if (default_thread_count > 0)
            DEFAULT_THREAD_COUNT = default_thread_count;
    }

    public int getDefaultThreadCount() {
        return DEFAULT_THREAD_COUNT;
    }

    //    将整个文件平均分给每个线程
    private ArrayList<DownloadRunnable> splitDownload(int thread_count) {
        ArrayList<DownloadRunnable> runnables = new ArrayList<>();
        try {
            long size = getContentLength(mUrl);
            mFileSize = size;
            long sublen = size / thread_count;
            for (int i = 0; i < thread_count; i++) {
                long startPos = sublen * i;
                long endPos = (i == thread_count - 1) ? size
                        : (sublen * (i + 1) - 1);
                DownloadRunnable runnable = new DownloadRunnable(this.mMonitor,
                        mUrl, mSaveDirectory, mSaveName, startPos, endPos);
                runnables.add(runnable);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return runnables;
    }

    //    分为恢复和重新下载两个逻辑
    public void startMission(DownloadThreadPool threadPool) {
        mThreadPoolRef = threadPool;
        if (mRecoveryRunnableInfos.size() != 0) {
//            将恢复结果剩余的未下载完毕的文件下载完毕
            for (RecoveryRunnableInfo runnableInfo : mRecoveryRunnableInfos) {
                if (runnableInfo.isFinished == false) {
                    setDownloadStatus(DOWNLOADING);
                    DownloadRunnable runnable = new DownloadRunnable(mMonitor,
                            mUrl, mSaveDirectory, mSaveName,
                            runnableInfo.getStartPosition(),
                            runnableInfo.getCurrentPosition(),
                            runnableInfo.getEndPosition());
                    mDownloadParts.add(runnable);
                    threadPool.submit(runnable);
                }
                mMonitor.mDownloadedSize.addAndGet(runnableInfo.mCurrentPosition - runnableInfo.mStartPosition);
            }
        } else {
//            重新下载
            setDownloadStatus(DOWNLOADING);
            for (DownloadRunnable runnable : splitDownload(mThreadCount)) {
                mDownloadParts.add(runnable);
                threadPool.submit(runnable);
            }
        }
//        利于垃圾回收
        mRecoveryRunnableInfos = null;
        mSpeedTimer.scheduleAtFixedRate(mSpeedMonitor, 0, 1000);
        mStoreTimer.scheduleAtFixedRate(mStoreMonitor, 0, 1000);
    }

    public boolean isFinished() {
//        如果结束顺便将对象空间清除
        if (isFinished) {
            mThreadPoolRef.cancel(mMissionID);
        }
        return isFinished;
    }

    public void addPartedMission(DownloadRunnable runnable) {
        mDownloadParts.add(runnable);
    }

    private static long getContentLength(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        URLConnection connection = url.openConnection();
        return connection.getContentLengthLong();
    }

    public static void createProgessFile(String dir, String filename)
            throws IOException {
        if (dir.lastIndexOf(File.separator) == dir.length() - 1) {
            dir = dir.substring(0, dir.length() - 1);
        }
        File dirFile = new File(dir);
        if (dirFile.exists() == false) {
            if (dirFile.mkdirs() == false) {
                throw new RuntimeException("Error to create directory");
            }
        }
        File file = new File(dirFile.getPath() + File.separator + filename
                + ".xml");
        if (file.exists() == false) {
            file.createNewFile();
        }
    }

    public File getProgressFile() {
        return new File(mProgressDir + File.separator + mProgressFileName);
    }

    public File getDownloadFile() {
        return new File(mSaveDirectory + File.separator + mSaveName);
    }

    public String getProgressDir() {
        return mProgressDir;
    }

    public String getProgressFileName() {
        return mProgressFileName;
    }

    public long getDownloadedSize() {
        return mMonitor.getDownloadedSize();
    }

    public String getReadableSize() {
        return DownloadUtils.getReadableSize(getDownloadedSize());
    }

    public long getSpeed() {
        return mSpeedMonitor.getSpeed();
    }

    public String getReadableSpeed() {
        return DownloadUtils.getReadableSpeed(getSpeed());
    }

    public long getMaxSpeed() {
        return mSpeedMonitor.getMaxSpeed();
    }

    public String getReadableMaxSpeed() {
        return DownloadUtils.getReadableSpeed(getMaxSpeed());
    }

    public long getAverageSpeed() {
        return mSpeedMonitor.getAverageSpeed();
    }

    public String getReadableAverageSpeed() {
        return DownloadUtils.getReadableSpeed(mSpeedMonitor.getAverageSpeed());
    }

    public int getTimePassed() {
        return mSpeedMonitor.getDownloadedTime();
    }

    public int getActiveTheadCount() {
        return mThreadPoolRef.getActiveCount();
    }

    public long getFileSize() {
        return mFileSize;
    }

    public void storeProgress() {
        try {
            JAXBContext context = JAXBContext
                    .newInstance(DownloadMission.class);
            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            m.marshal(this, getProgressFile());
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }

    public void pause() {
        if (mMissionStatus == FINISHED) {
            return;
        }
        setDownloadStatus(PAUSED);
        storeProgress();
        mThreadPoolRef.pause(mMissionID);
    }

    private void setDownloadStatus(int status) {
//        先设置好状态，然后保存到xml
        mMissionStatus = status;
        if (status == FINISHED) {
//            可以取消该任务
            mSpeedMonitor.mSpeed = 0;
            mSpeedTimer.cancel();
            mStoreMonitor.cancel();
            mDownloadParts.clear();
            deleteProgressFile();
            isFinished = true;
        }
    }

    public static DownloadMission recoverMissionFromProgressFile(
            String url, String dir, String name)
            throws IOException {
        DownloadMission mission = null;
        try {
            File progressFile = new File(
                    FileUtils.getSafeDirPath(dir)
                            + File.separator + name + ".xml");

            JAXBContext context = JAXBContext
                    .newInstance(DownloadMission.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            mission = (DownloadMission) unmarshaller
                    .unmarshal(progressFile);
            System.out.println("resume form file");
        } catch (JAXBException e) {
            mission = new DownloadMission();
        }
        mission.mUrl = url;
        mission.mSaveDirectory = dir;
        mission.mSaveName = name;
        mission.mProgressDir = dir;
        mission.mProgressFileName = name + ".xml";
//        unmarshall过程this无效
        mission.mSpeedMonitor.mHostMission = mission;
        mission.mMonitor.mHostMission = mission;
        mission.mMonitor.mDownloadedSize.set(0);
//            mission.mMissionID = MISSION_ID_COUNTER++;
        ArrayList<RecoveryRunnableInfo> recoveryRunnableInfos = mission
                .getDownloadProgress();
        for (DownloadRunnable runnable : mission.mDownloadParts) {
            mission.mRecoveryRunnableInfos.add(new RecoveryRunnableInfo(runnable
                    .getStartPosition(), runnable.getCurrentPosition(),
                    runnable.getEndPosition()));
        }
        mission.mDownloadParts.clear();
        return mission;
    }

    private void deleteProgressFile() {
        getProgressFile().delete();
    }

    public ArrayList<RecoveryRunnableInfo> getDownloadProgress() {
        return mRecoveryRunnableInfos;
    }


    public void cancel() {
//        速度置零
        mSpeedMonitor.mSpeed = 0;
        mSpeedTimer.cancel();
        mStoreMonitor.cancel();
        mDownloadParts.clear();
        mThreadPoolRef.cancel(mMissionID);
        deleteProgressFile();
    }
}
