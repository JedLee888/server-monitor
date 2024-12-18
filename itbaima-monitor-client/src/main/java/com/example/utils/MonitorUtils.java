package com.example.utils;

import com.example.entity.BaseDetail;
import com.example.entity.ConnectionConfig;
import com.example.entity.RuntimeDetail;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component
public class MonitorUtils {

    @Lazy
    @Resource
    ConnectionConfig config;

    private final SystemInfo info = new SystemInfo();
    private final Properties properties = System.getProperties();

    public BaseDetail monitorBaseDetail() {
        OperatingSystem os = info.getOperatingSystem();
        HardwareAbstractionLayer hardware = info.getHardware();
        double memory = hardware.getMemory().getTotal() / 1024.0 / 1024 /1024;
        double diskSize = Arrays.stream(File.listRoots()).mapToLong(File::getTotalSpace).sum() / 1024.0 / 1024 / 1024;
        String ip = Objects.requireNonNull(this.findNetworkInterface(hardware)).getIPv4addr()[0];
        return new BaseDetail()
                .setOsArch(properties.getProperty("os.arch"))
                .setOsName(os.getFamily())
                .setOsVersion(os.getVersionInfo().getVersion())
                .setOsBit(os.getBitness())
                .setCpuName(hardware.getProcessor().getProcessorIdentifier().getName())
                .setCpuCore(hardware.getProcessor().getLogicalProcessorCount())
                .setMemory(memory)
                .setDisk(diskSize)
                .setIp(ip);
    }

    public RuntimeDetail monitorRuntimeDetail() {
        double statisticTime = 0.5;
        try {
            HardwareAbstractionLayer hardware = info.getHardware();
            NetworkIF networkInterface = Objects.requireNonNull(this.findNetworkInterface(hardware));
            CentralProcessor processor = hardware.getProcessor();
            double upload = networkInterface.getBytesSent(), download = networkInterface.getBytesRecv();
            double read = hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum();
            double write = hardware.getDiskStores().stream().mapToLong(HWDiskStore::getWriteBytes).sum();
            long[] ticks = processor.getSystemCpuLoadTicks();
            Thread.sleep((long) (statisticTime * 1000));
            networkInterface = Objects.requireNonNull(this.findNetworkInterface(hardware));
            upload = (networkInterface.getBytesSent() - upload) / statisticTime;
            download =  (networkInterface.getBytesRecv() - download) / statisticTime;
            read = (hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum() - read) / statisticTime;
            write = (hardware.getDiskStores().stream().mapToLong(HWDiskStore::getWriteBytes).sum() - write) / statisticTime;
            double memory = (hardware.getMemory().getTotal() - hardware.getMemory().getAvailable()) / 1024.0 / 1024 / 1024;
            double disk = Arrays.stream(File.listRoots())
                    .mapToLong(file -> file.getTotalSpace() - file.getFreeSpace()).sum() / 1024.0 / 1024 / 1024;
            return new RuntimeDetail()
                    .setCpuUsage(this.calculateCpuUsage(processor, ticks))
                    .setMemoryUsage(memory)
                    .setDiskUsage(disk)
                    .setNetworkUpload(upload / 1024)
                    .setNetworkDownload(download / 1024)
                    .setDiskRead(read / 1024/ 1024)
                    .setDiskWrite(write / 1024 / 1024)
                    .setTimestamp(new Date().getTime());
        } catch (Exception e) {
            log.error("读取运行时数据出现问题", e);
        }
        return null;
    }

    private double calculateCpuUsage(CentralProcessor processor, long[] prevTicks) {
        long[] ticks = processor.getSystemCpuLoadTicks();
        long nice = ticks[CentralProcessor.TickType.NICE.getIndex()]
                - prevTicks[CentralProcessor.TickType.NICE.getIndex()];
        long irq = ticks[CentralProcessor.TickType.IRQ.getIndex()]
                - prevTicks[CentralProcessor.TickType.IRQ.getIndex()];
        long softIrq = ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()]
                - prevTicks[CentralProcessor.TickType.SOFTIRQ.getIndex()];
        long steal = ticks[CentralProcessor.TickType.STEAL.getIndex()]
                - prevTicks[CentralProcessor.TickType.STEAL.getIndex()];
        long cSys = ticks[CentralProcessor.TickType.SYSTEM.getIndex()]
                - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()];
        long cUser = ticks[CentralProcessor.TickType.USER.getIndex()]
                - prevTicks[CentralProcessor.TickType.USER.getIndex()];
        long ioWait = ticks[CentralProcessor.TickType.IOWAIT.getIndex()]
                - prevTicks[CentralProcessor.TickType.IOWAIT.getIndex()];
        long idle = ticks[CentralProcessor.TickType.IDLE.getIndex()]
                - prevTicks[CentralProcessor.TickType.IDLE.getIndex()];
        long totalCpu = cUser + nice + cSys + idle + ioWait + irq + softIrq + steal;
        return (cSys + cUser) * 1.0 / totalCpu;
    }

    public List<String> listNetworkInterfaceName() {
        HardwareAbstractionLayer hardware = info.getHardware();
        return hardware.getNetworkIFs()
                .stream()
                .map(NetworkIF::getName)
                .toList();
    }

    private NetworkIF findNetworkInterface(HardwareAbstractionLayer hardware) {
        try {
            String target = config.getNetworkInterface();
            List<NetworkIF> ifs = hardware.getNetworkIFs()
                    .stream()
                    .filter(inter -> inter.getName().equals(target))
                    .toList();
            if (!ifs.isEmpty()) {
                return ifs.get(0);
            } else {
                throw new IOException("网卡信息错误，找不到网卡: " + target);
            }
        } catch (IOException e) {
            log.error("读取网络接口信息时出错", e);
        }
        return null;
    }
}
