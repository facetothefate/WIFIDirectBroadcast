package com.facework.tool;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class CPUMeasurement {
	public static long getTotalCpuTime() { // 获取系统总CPU使用时间
	    String[] cpuInfos = null;
	    try {
	        BufferedReader reader = new BufferedReader(new InputStreamReader(
	                new FileInputStream("/proc/stat")), 1000);
	        String load = reader.readLine();
	        reader.close();
	        cpuInfos = load.split(" ");
	    } catch (IOException ex) {
	        ex.printStackTrace();
	    }
	    long totalCpu = Long.parseLong(cpuInfos[2])
	            + Long.parseLong(cpuInfos[3]) + Long.parseLong(cpuInfos[4])
	            + Long.parseLong(cpuInfos[6]) + Long.parseLong(cpuInfos[5])
	            + Long.parseLong(cpuInfos[7]) + Long.parseLong(cpuInfos[8]);
	    return totalCpu;
	}
	 
	public static long getAppCpuTime() { // 获取应用占用的CPU时间
	    String[] cpuInfos = null;
	    try {
	        int pid = android.os.Process.myPid();
	        BufferedReader reader = new BufferedReader(new InputStreamReader(
	                new FileInputStream("/proc/" + pid + "/stat")), 1000);
	        String load = reader.readLine();
	        reader.close();
	        cpuInfos = load.split(" ");
	    } catch (IOException ex) {
	        ex.printStackTrace();
	    }
	    long appCpuTime = Long.parseLong(cpuInfos[13])
	            + Long.parseLong(cpuInfos[14]) + Long.parseLong(cpuInfos[15])
	            + Long.parseLong(cpuInfos[16]);
	    return appCpuTime;
	}

}
