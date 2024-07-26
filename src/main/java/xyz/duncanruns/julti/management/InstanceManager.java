package xyz.duncanruns.julti.management;

import org.apache.logging.log4j.Level;
import xyz.duncanruns.julti.Julti;
import xyz.duncanruns.julti.JultiOptions;
import xyz.duncanruns.julti.instance.InstanceState;
import xyz.duncanruns.julti.instance.MinecraftInstance;
import xyz.duncanruns.julti.plugin.PluginEvents;
import xyz.duncanruns.julti.util.DoAllFastUtil;
import xyz.duncanruns.julti.util.MouseUtil;
import xyz.duncanruns.julti.util.SleepBGUtil;
import xyz.duncanruns.julti.win32.User32;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static xyz.duncanruns.julti.util.SleepUtil.sleep;

public final class InstanceManager {
    private static final InstanceManager INSTANCE = new InstanceManager();
    private final ArrayList<MinecraftInstance> instances = new ArrayList<>();

    private boolean instancesMissing = true;

    private InstanceManager() {
    }

    public static InstanceManager getInstanceManager() {
        return INSTANCE;
    }

    public List<MinecraftInstance> getInstances() {
        return this.instances;
    }

    public void onOptionsLoad() {
        this.checkOpenedInstances();
    }

    public void checkOpenedInstances() {
        Set<MinecraftInstance> replacements = InstanceChecker.getInstanceChecker().getAllOpenedInstances();

        // For each of Julti's instances
        AtomicBoolean instancesFound = new AtomicBoolean(false);
        for (int i = 0; i < this.instances.size(); i++) {
            // If the instance is not missing a window, skip
            if (!this.instances.get(i).checkWindowMissing()) {
                continue;
            }
            // Get the path and store a final i
            Path path = this.instances.get(i).getPath();
            final int finalI = i;
            // Look through the potential replacements, if any match the path, replace Julti's instance with it
            replacements.stream().filter(instance -> instance.getPath().equals(path)).findAny().ifPresent(instance -> {
                this.instances.set(finalI, instance);
                instance.discoverInformation();
                instancesFound.set(true);
                Julti.log(Level.INFO, "Found instance: " + instance.getName());
            });
        }

        if (instancesFound.get() && !this.checkInstancesMarkedMissing()) {
            this.onAllInstancesFound();
        }

        // Recheck missing instances
        this.checkInstancesMarkedMissing();
    }

    private void onAllInstancesFound() {
        this.renameWindows();
        SleepBGUtil.disableLock();
        DoAllFastUtil.doAllFast(MinecraftInstance::discoverInformation);
        this.instances.forEach(instance -> {
            instance.activate(true);
            MouseUtil.clickTopLeft(instance.getHwnd());
            sleep(50);
        });
        OBSStateManager.getOBSStateManager().tryOutputLSInfo();
        PluginEvents.RunnableEventType.ALL_INSTANCES_FOUND.runAll();
    }


    private boolean checkInstancesMarkedMissing() {
        boolean instancesMissing = false;
        for (MinecraftInstance instance : this.instances) {
            if (instance.isWindowMarkedMissing()) {
                instancesMissing = true;
                break;
            }
        }
        this.instancesMissing = instancesMissing;
        return this.instancesMissing;
    }

    public void tick(long cycles) {
        this.tickUtility(cycles);
    }

    private void tickUtility(long cycles) {
        if (cycles % 5000 == 0 && this.getSelectedInstance() == null) {
            this.runChecksUtility();
        }
        this.instancesMissing = false;
    }

    private void runChecksUtility() {
        this.instances.forEach(MinecraftInstance::checkWindowMissing);
        this.instances.removeIf(MinecraftInstance::isWindowMarkedMissing);
        InstanceChecker.getInstanceChecker().getAllOpenedInstances().stream().filter(i -> !this.instances.contains(i)).forEach(i -> {
            i.discoverInformation();
            this.instances.add(i);
            i.ensurePlayingWindowState();
        });
        this.checkForWindowRename();
    }

    public void tickInstances() {
        for (MinecraftInstance instance : this.instances) {
            if (instance.isWindowMarkedMissing()) {
                continue;
            }
            instance.tick();
        }
    }

    public boolean areInstancesMissing() {
        return this.instancesMissing;
    }

    public MinecraftInstance getSelectedInstance() {
        for (MinecraftInstance instance : this.getInstances()) {
            if (ActiveWindowManager.isWindowActive(instance.getHwnd())) {
                return instance;
            }
        }
        return null;
    }

    public int getSize() {
        return this.instances.size();
    }

    public int getInstanceNum(MinecraftInstance instance) {
        return this.getInstanceIndex(instance) + 1;
    }

    public int getInstanceIndex(MinecraftInstance instance) {
        return this.instances.indexOf(instance);
    }

    public void renameWindows() {
        if (JultiOptions.getJultiOptions().preventWindowNaming) {
            return;
        }
        int i = 1;
        for (MinecraftInstance instance : this.instances) {
            if (!instance.isWindowMarkedMissing()) {
                User32.INSTANCE.SetWindowTextA(instance.getHwnd(), "Minecraft* - Instance " + (i++));
            }
        }
    }
    public MinecraftInstance getMatchingInstance(MinecraftInstance instance) {
        if (instance == null) {
            return null;
        }
        for (MinecraftInstance newInstance : this.instances) {
            if (newInstance.getPath().equals(instance.getPath())) {
                return newInstance;
            }
        }
        return instance;
    }
    private void checkForWindowRename() {
        if (this.instances.stream().anyMatch(instance -> instance.getStateTracker().isCurrentState(InstanceState.TITLE))) {
            this.renameWindows();
        }
    }}
