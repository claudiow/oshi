/**
 * Oshi (https://github.com/dblock/oshi)
 *
 * Copyright (c) 2010 - 2016 The Oshi Project Team
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Maintainers:
 * dblock[at]dblock[dot]org
 * widdis[at]gmail[dot]com
 * enrico.bianchi[at]gmail[dot]com
 *
 * Contributors:
 * https://github.com/dblock/oshi/graphs/contributors
 */
package oshi.hardware.common;

import java.lang.management.ManagementFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oshi.hardware.CentralProcessor;
import oshi.util.ParseUtil;

/**
 * A CPU as defined in Linux /proc.
 *
 * @author alessandro[at]perucchi[dot]org
 * @author alessio.fachechi[at]gmail[dot]com
 * @author widdis[at]gmail[dot]com
 */
@SuppressWarnings("restriction")
public abstract class AbstractCentralProcessor implements CentralProcessor {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCentralProcessor.class);

    /**
     * Instantiate an OperatingSystemMXBean for future convenience
     */
    private final java.lang.management.OperatingSystemMXBean OS_MXBEAN = ManagementFactory.getOperatingSystemMXBean();

    /**
     * Calling OperatingSystemMxBean too rapidly results in NaN. Store the
     * latest value to return if polling is too rapid
     */
    private double lastCpuLoad = 0d;

    /**
     * Keep track of last CPU Load poll to OperatingSystemMXBean to ensure
     * enough time has elapsed
     */
    private long lastCpuLoadTime = 0;

    /**
     * Keep track whether MXBean supports Oracle JVM methods
     */
    private boolean sunMXBean;

    {
        try {
            Class.forName("com.sun.management.OperatingSystemMXBean");
            // Initialize CPU usage
            lastCpuLoad = ((com.sun.management.OperatingSystemMXBean) OS_MXBEAN).getSystemCpuLoad();
            lastCpuLoadTime = System.currentTimeMillis();
            sunMXBean = true;
            LOG.debug("Oracle MXBean detected.");
        } catch (ClassNotFoundException e) {
            sunMXBean = false;
            LOG.debug("Oracle MXBean not detected.");
            LOG.trace("", e);
        }
    }

    // Logical and Physical Processor Counts
    protected int logicalProcessorCount = 0;

    protected int physicalProcessorCount = 0;

    // Maintain previous ticks to be used for calculating usage between them.
    // System ticks
    protected long tickTime;

    protected long[] prevTicks;

    protected long[] curTicks;

    // Per-processor ticks [cpu][type]
    protected long procTickTime;

    protected long[][] prevProcTicks;

    protected long[][] curProcTicks;

    // Processor info
    protected String cpuVendor;

    protected String cpuName;

    protected String cpuSerialNumber = null;

    protected String cpuIdentifier;

    protected String cpuStepping;

    protected String cpuModel;

    protected String cpuFamily;

    protected Long cpuVendorFreq;

    protected Boolean cpu64;

    /**
     * Create a Processor
     */
    public AbstractCentralProcessor() {
        // Initialize processor counts
        calculateProcessorCounts();
    }

    /**
     * Initializes tick arrays
     */
    protected synchronized void initTicks() {
        // System ticks
        this.prevTicks = new long[TickType.values().length];
        this.curTicks = new long[TickType.values().length];
        updateSystemTicks();

        // Per-processor ticks
        this.prevProcTicks = new long[logicalProcessorCount][TickType.values().length];
        this.curProcTicks = new long[logicalProcessorCount][TickType.values().length];
        updateProcessorTicks();
    }

    /**
     * Updates logical and physical processor counts
     */
    protected abstract void calculateProcessorCounts();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVendor() {
        if (this.cpuVendor == null) {
            setVendor("");
        }
        return this.cpuVendor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVendor(String vendor) {
        this.cpuVendor = vendor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        if (this.cpuName == null) {
            setName("");
        }
        return this.cpuName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.cpuName = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getVendorFreq() {
        if (this.cpuVendorFreq == null) {
            Pattern pattern = Pattern.compile("@ (.*)$");
            Matcher matcher = pattern.matcher(getName());

            if (matcher.find()) {
                String unit = matcher.group(1);
                this.cpuVendorFreq = Long.valueOf(ParseUtil.parseHertz(unit));
            } else {
                this.cpuVendorFreq = Long.valueOf(-1L);
            }
        }
        return this.cpuVendorFreq.longValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVendorFreq(long freq) {
        this.cpuVendorFreq = Long.valueOf(freq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIdentifier() {
        if (this.cpuIdentifier == null) {
            StringBuilder sb = new StringBuilder();
            if (getVendor().contentEquals("GenuineIntel")) {
                sb.append(isCpu64bit() ? "Intel64" : "x86");
            } else {
                sb.append(getVendor());
            }
            sb.append(" Family ").append(getFamily());
            sb.append(" Model ").append(getModel());
            sb.append(" Stepping ").append(getStepping());
            setIdentifier(sb.toString());
        }
        return this.cpuIdentifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIdentifier(String identifier) {
        this.cpuIdentifier = identifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCpu64bit() {
        if (this.cpu64 == null) {
            setCpu64(false);
        }
        return this.cpu64;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCpu64(boolean value) {
        this.cpu64 = Boolean.valueOf(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStepping() {
        if (this.cpuStepping == null) {
            if (this.cpuIdentifier == null) {
                return "?";
            }
            setStepping(parseIdentifier("Stepping"));
        }
        return this.cpuStepping;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStepping(String stepping) {
        this.cpuStepping = stepping;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getModel() {
        if (this.cpuModel == null) {
            if (this.cpuIdentifier == null) {
                return "?";
            }
            setModel(parseIdentifier("Model"));
        }
        return this.cpuModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setModel(String model) {
        this.cpuModel = model;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFamily() {
        if (this.cpuFamily == null) {
            if (this.cpuIdentifier == null) {
                return "?";
            }
            setFamily(parseIdentifier("Family"));
        }
        return this.cpuFamily;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFamily(String family) {
        this.cpuFamily = family;
    }

    /**
     * Parses identifier string
     * 
     * @param id
     *            the id to retrieve
     * @return the string following id
     */
    private String parseIdentifier(String id) {
        String[] idSplit = getIdentifier().split("\\s+");
        boolean found = false;
        for (String s : idSplit) {
            // If id string found, return next value
            if (found) {
                return s;
            }
            found = s.equals(id);
        }
        // If id string not found, return empty string
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized double getSystemCpuLoadBetweenTicks() {
        // Check if > ~ 0.95 seconds since last tick count.
        long now = System.currentTimeMillis();
        LOG.trace("Current time: {}  Last tick time: {}", now, tickTime);
        if (now - tickTime > 950) {
            // Enough time has elapsed.
            updateSystemTicks();
        }
        // Calculate total
        long total = 0;
        for (int i = 0; i < curTicks.length; i++) {
            total += (curTicks[i] - prevTicks[i]);
        }
        // Calculate idle from difference in idle and IOwait
        long idle = curTicks[TickType.IDLE.getIndex()] + curTicks[TickType.IOWAIT.getIndex()]
                - prevTicks[TickType.IDLE.getIndex()] - prevTicks[TickType.IOWAIT.getIndex()];
        LOG.trace("Total ticks: {}  Idle ticks: {}", total, idle);

        return total > 0 && idle >= 0 ? (double) (total - idle) / total : 0d;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract long[] getSystemCpuLoadTicks();

    /**
     * Updates system tick information. Stores in array with seven elements
     * representing clock ticks or milliseconds (platform dependent) spent in
     * User (0), Nice (1), System (2), Idle (3), IOwait (4), IRQ (5), and
     * SoftIRQ (6) states. By measuring the difference between ticks across a
     * time interval, CPU load over that interval may be calculated.
     */
    protected void updateSystemTicks() {
        LOG.trace("Updating System Ticks");
        long[] ticks = getSystemCpuLoadTicks();
        // Skip update if ticks is all zero.
        // Iterate to find a nonzero tick value and return; this should quickly
        // find a nonzero value if one exists and be fast in checking 0's
        // through branch prediction if it doesn't
        for (int i = 0; i < ticks.length; i++) {
            if (ticks[i] != 0) {
                // We have a nonzero tick array, update and return!
                this.tickTime = System.currentTimeMillis();
                // Copy to previous
                System.arraycopy(curTicks, 0, prevTicks, 0, curTicks.length);
                System.arraycopy(ticks, 0, curTicks, 0, ticks.length);
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getSystemCpuLoad() {
        if (sunMXBean) {
            long now = System.currentTimeMillis();
            // If called too recently, return latest value
            if (now - lastCpuLoadTime < 200) {
                return lastCpuLoad;
            }
            lastCpuLoad = ((com.sun.management.OperatingSystemMXBean) OS_MXBEAN).getSystemCpuLoad();
            lastCpuLoadTime = now;
            return lastCpuLoad;
        }
        return getSystemCpuLoadBetweenTicks();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getSystemLoadAverage() {
        return getSystemLoadAverage(1)[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract double[] getSystemLoadAverage(int nelem);

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] getProcessorCpuLoadBetweenTicks() {
        // Check if > ~ 0.95 seconds since last tick count.
        long now = System.currentTimeMillis();
        LOG.trace("Current time: {}  Last tick time: {}", now, procTickTime);
        if (now - procTickTime > 950) {
            // Enough time has elapsed.
            // Update latest
            updateProcessorTicks();
        }
        double[] load = new double[logicalProcessorCount];
        for (int cpu = 0; cpu < logicalProcessorCount; cpu++) {
            long total = 0;
            for (int i = 0; i < this.curProcTicks[cpu].length; i++) {
                total += (this.curProcTicks[cpu][i] - this.prevProcTicks[cpu][i]);
            }
            // Calculate idle from difference in idle and IOwait
            long idle = this.curProcTicks[cpu][TickType.IDLE.getIndex()]
                    + this.curProcTicks[cpu][TickType.IOWAIT.getIndex()]
                    - this.prevProcTicks[cpu][TickType.IDLE.getIndex()]
                    - this.prevProcTicks[cpu][TickType.IOWAIT.getIndex()];
            LOG.trace("CPU: {}  Total ticks: {}  Idle ticks: {}", cpu, total, idle);
            // update
            load[cpu] = total > 0 && idle >= 0 ? (double) (total - idle) / total : 0d;
        }
        return load;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract long[][] getProcessorCpuLoadTicks();

    /**
     * Updates per-processor tick information. Stores in 2D array; an array for
     * each logical processor with with seven elements representing clock ticks
     * or milliseconds (platform dependent) spent in User (0), Nice (1), System
     * (2), Idle (3), IOwait (4), IRQ (5), and SoftIRQ (6) states. By measuring
     * the difference between ticks across a time interval, CPU load over that
     * interval may be calculated.
     */
    protected void updateProcessorTicks() {
        LOG.trace("Updating Processor Ticks");
        long[][] ticks = getProcessorCpuLoadTicks();
        // Skip update if ticks is all zero.
        // Iterate to find a nonzero tick value and return; this should quickly
        // find a nonzero value if one exists and be fast in checking 0's
        // through branch prediction if it doesn't
        for (int i = 0; i < ticks.length; i++) {
            for (int j = 0; j < ticks[i].length; j++) {
                if (ticks[i][j] != 0L) {
                    // We have a nonzero tick array, update and return!
                    this.procTickTime = System.currentTimeMillis();
                    // Copy to previous
                    for (int cpu = 0; cpu < logicalProcessorCount; cpu++) {
                        System.arraycopy(curProcTicks[cpu], 0, prevProcTicks[cpu], 0, curProcTicks[cpu].length);
                    }
                    for (int cpu = 0; cpu < logicalProcessorCount; cpu++) {
                        System.arraycopy(ticks[cpu], 0, curProcTicks[cpu], 0, ticks[cpu].length);
                    }
                    return;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract long getSystemUptime();

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract String getSystemSerialNumber();

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLogicalProcessorCount() {
        return this.logicalProcessorCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPhysicalProcessorCount() {
        return this.physicalProcessorCount;
    }

    @Override
    public String toString() {
        return getName();
    }
}
