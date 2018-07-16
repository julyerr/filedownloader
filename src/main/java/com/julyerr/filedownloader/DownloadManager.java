package com.julyerr.filedownloader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class DownloadManager {

    private static DownloadManager instance;

    private static DownloadThreadPool mThreadPool;

    public static final int DEFAULT_MISSION_THREAD_COUNT = 2;
    public static final int DEFAULT_CORE_POOL_SIZE = 12;

    public static final int DEFAULT_MAX_POOL_SIZE = Integer.MAX_VALUE;
    public static final int DEFAULT_KEEP_ALIVE_TIME = 0;

    private static int ID = 0;
    //    防止多线程操作统一个DownLoadMission
    private ConcurrentHashMap<Integer, DownloadMission> mMissions = new ConcurrentHashMap<>();

    private DownloadManager() {
        mThreadPool = new DownloadThreadPool(DEFAULT_CORE_POOL_SIZE,
                DEFAULT_MAX_POOL_SIZE, DEFAULT_KEEP_ALIVE_TIME,
                TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
    }

    public static DownloadManager getInstance() {
        if (instance == null) {
            instance = new DownloadManager();
        }
        if (mThreadPool.isShutdown()) {
            mThreadPool = new DownloadThreadPool(DEFAULT_CORE_POOL_SIZE,
                    DEFAULT_MAX_POOL_SIZE, DEFAULT_KEEP_ALIVE_TIME,
                    TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
        }
        return instance;
    }

    public void setMaxThreadCount(int MaxCount) {
        if (MaxCount > 0)
            mThreadPool.setCorePoolSize(MaxCount);
    }

    public void addMission(DownloadMission downloadTask) {
        if (downloadTask == null) {
            return;
        }
        mMissions.put(ID++, downloadTask);
    }

    public DownloadMission getMission(int MissionID) {
        return mMissions.get(MissionID);
    }

    public void start() {
        for (DownloadMission mission : mMissions.values()) {
            mission.startMission(mThreadPool);
        }
    }

    public Boolean isAllTasksFinished() {
        for (Integer mission_id : mMissions.keySet()) {
            if (isTaskFinished(mission_id) == false) {
                return false;
            }
        }
        return true;
    }

    public Boolean isTaskFinished(int mission_id) {
        DownloadMission mission = mMissions.get(mission_id);
        return mission.isFinished();
    }

    public void pauseAllMissions() {
        for (Integer missionID : mMissions.keySet()) {
            pauseMission(missionID);
        }
    }

    public void pauseMission(int MissionID) {
        if (mMissions.contains(MissionID)) {
            DownloadMission mission = mMissions.get(MissionID);
            mission.pause();
        }
    }

    public void cancelAllMissions() {
        for (Integer missionId : mMissions.keySet()) {
            cancelMission(missionId);
        }
    }

    public void cancelMission(int MissionID) {
        if (mMissions.contains(MissionID)) {
            DownloadMission mission = mMissions.remove(MissionID);
            mission.cancel();
        }
    }

    public void shutdownSafely() {
        for (Integer mission_id : mMissions.keySet()) {
            mMissions.get(mission_id).pause();
        }
        mThreadPool.shutdownNow();
    }

    public int getTotalDownloadedSize() {
        int size = 0;
        for (DownloadMission mission : mMissions.values()) {
            size += mission.getDownloadedSize();
        }
        return size;
    }

    public String getReadableDownloadSize() {
        return DownloadUtils.getReadableSize(getTotalDownloadedSize());
    }

    public int getTotalSpeed() {
        int speed = 0;
        for (DownloadMission mission : mMissions.values()) {
            speed += mission.getSpeed();
        }
        return speed;
    }

    public String getReadableTotalSpeed() {
        return DownloadUtils.getReadableSpeed(getTotalSpeed());
    }

    public void shutdDownloadRudely() {
        mThreadPool.shutdownNow();
    }
}
