package xyz.duncanruns.julti.resetting;

import xyz.duncanruns.julti.Julti;
import xyz.duncanruns.julti.JultiOptions;
import xyz.duncanruns.julti.affinity.AffinityManager;
import xyz.duncanruns.julti.instance.InstanceState;
import xyz.duncanruns.julti.instance.MinecraftInstance;
import xyz.duncanruns.julti.management.ActiveWindowManager;
import xyz.duncanruns.julti.management.InstanceManager;
import xyz.duncanruns.julti.util.DoAllFastUtil;
import xyz.duncanruns.julti.util.SleepBGUtil;

import java.awt.*;
import java.util.List;
import java.util.*;

import static xyz.duncanruns.julti.util.SleepUtil.sleep;

public class WallResetManager extends ResetManager {
    private static final WallResetManager INSTANCE = new WallResetManager();

    private final List<MinecraftInstance> lockedInstances = new ArrayList<>();

    private MinecraftInstance lastAttemptedJoin = null;
    private long lastAttemptedJoinTime = 0L;

    public static WallResetManager getWallResetManager() {
        return INSTANCE;
    }

    @Override
    public List<ActionResult> doReset() {

        List<MinecraftInstance> instances = InstanceManager.getInstanceManager().getInstances();
        // Return if no instances
        if (instances.isEmpty()) {
            return Collections.emptyList();
        }

        // Get selected instance, return if no selected instance
        MinecraftInstance selectedInstance = InstanceManager.getInstanceManager().getSelectedInstance();
        if (selectedInstance == null || selectedInstance.isWindowMarkedMissing()) {
            return Collections.emptyList();
        }

        // if there is only a single instance, reset it and return.
        if (instances.size() == 1) {
            selectedInstance.reset();
            return Collections.singletonList(ActionResult.INSTANCE_RESET);
        }

        // Only place leaveInstance is used, but it is a big method
        List<ActionResult> out = this.leaveInstance(selectedInstance);

        super.doReset();

        return out;
    }

    @Override
    public List<ActionResult> doBGReset() {
        MinecraftInstance selectedInstance = InstanceManager.getInstanceManager().getSelectedInstance();
        if (selectedInstance == null || selectedInstance.isWindowMarkedMissing()) {
            return Collections.emptyList();
        }
        List<ActionResult> out = this.resetNonLockedExcept(selectedInstance);
        AffinityManager.ping();
        return out;
    }

    @Override
    public List<ActionResult> doWallFullReset() {
        if (!ActiveWindowManager.isWallActive()) {
            return Collections.emptyList();
        }
        List<MinecraftInstance> lockedInstances = new ArrayList<>(this.getLockedInstances());
        List<ActionResult> actionResults = new ArrayList<>();
        DoAllFastUtil.doAllFast(instance -> {
            if (lockedInstances.contains(instance)) {
                return;//(continue;)
            }
            if (this.resetInstance(instance)) {
                synchronized (actionResults) {
                    actionResults.add(ActionResult.INSTANCE_RESET);
                }
            }
        });

        AffinityManager.ping();
        return actionResults;
    }

    @Override
    public List<ActionResult> doWallSingleReset(Point mousePosition) {
        if (!ActiveWindowManager.isWallActive()) {
            return Collections.emptyList();
        }
        MinecraftInstance selectedInstance = this.getHoveredWallInstance(mousePosition);
        if (selectedInstance == null || selectedInstance.isWindowMarkedMissing()) {
            return Collections.emptyList();
        }
        AffinityManager.ping();
        return this.resetInstance(selectedInstance) ? Collections.singletonList(ActionResult.INSTANCE_RESET) : Collections.emptyList();
    }

    @Override
    public List<ActionResult> doWallLock(Point mousePosition) {
        if (!ActiveWindowManager.isWallActive()) {
            return Collections.emptyList();
        }
        MinecraftInstance clickedInstance = this.getHoveredWallInstance(mousePosition);
        if (clickedInstance == null) {
            return Collections.emptyList();
        }
        boolean out = this.lockInstance(clickedInstance);
        AffinityManager.ping();
        return out ? Collections.singletonList(ActionResult.INSTANCE_LOCKED) : Collections.emptyList();
    }

    @Override
    public List<ActionResult> doWallFocusReset(Point mousePosition) {
        if (!ActiveWindowManager.isWallActive()) {
            return Collections.emptyList();
        }

        // Regular play instance method
        MinecraftInstance clickedInstance = this.getHoveredWallInstance(mousePosition);
        if (clickedInstance == null) {
            return Collections.emptyList();
        }

        // Get list of instances to reset
        List<MinecraftInstance> toReset = new ArrayList<>(InstanceManager.getInstanceManager().getInstances());
        toReset.removeAll(this.lockedInstances);
        toReset.remove(clickedInstance);

        List<ActionResult> actionResults = new ArrayList<>(this.playInstanceFromWall(clickedInstance, false));

        // Reset all others
        DoAllFastUtil.doAllFast(toReset, instance -> {
            if (this.resetInstance(instance)) {
                synchronized (actionResults) {
                    actionResults.add(ActionResult.INSTANCE_RESET);
                }
            }
        });

        AffinityManager.ping();
        return actionResults;
    }

    @Override
    public List<ActionResult> doWallPlay(Point mousePosition) {
        if (!ActiveWindowManager.isWallActive()) {
            return Collections.emptyList();
        }
        MinecraftInstance clickedInstance = this.getHoveredWallInstance(mousePosition);
        if (clickedInstance == null) {
            return Collections.emptyList();
        }


        boolean forceEnter = false;
        long currentTime = System.currentTimeMillis();
        if (Objects.equals(this.lastAttemptedJoin, clickedInstance) && currentTime - this.lastAttemptedJoinTime < 350) {
            forceEnter = true;
        }
        this.lastAttemptedJoin = clickedInstance;
        this.lastAttemptedJoinTime = currentTime;

        List<ActionResult> out = this.playInstanceFromWall(clickedInstance, forceEnter);
        AffinityManager.ping();
        return out;
    }

    @Override
    public List<ActionResult> doWallPlayLock(Point mousePosition) {
        if (!ActiveWindowManager.isWallActive()) {
            return Collections.emptyList();
        }
        if (this.lockedInstances.isEmpty()) {
            return Collections.emptyList();
        }
        List<MinecraftInstance> instancePool = new ArrayList<>(this.lockedInstances);
        instancePool.sort(Comparator.comparingInt(i -> -i.getResetSortingNum()));
        List<ActionResult> out = this.playInstanceFromWall(instancePool.get(0), false);
        AffinityManager.ping();
        return out;
    }

    @Override
    public void notifyPreviewLoaded(MinecraftInstance instance) {
        super.notifyPreviewLoaded(instance);
    }

    @Override
    public List<MinecraftInstance> getLockedInstances() {
        return Collections.unmodifiableList(this.lockedInstances);
    }

    @Override
    public void onMissingInstancesUpdate() {
        super.onMissingInstancesUpdate();
        this.lockedInstances.clear();
    }

    public boolean resetInstance(MinecraftInstance instance) {
        return this.resetInstance(instance, false);
    }

    public boolean resetInstance(MinecraftInstance instance, boolean bypassConditions) {
        this.unlockInstance(instance);
        if (bypassConditions || instance.isResettable()) {
            instance.reset();
            return true;
        }
        return false;
    }

    @Override
    public boolean lockInstance(MinecraftInstance instance) {
        if (!this.lockedInstances.contains(instance)) {
            this.lockedInstances.add(instance);
            // Calling super.lockInstance to do unsquish check
            super.lockInstance(instance);
            return true;
        }
        return false;
    }

    @Override
    public void reload() {
        this.lockedInstances.replaceAll(InstanceManager.getInstanceManager()::getMatchingInstance);
        super.reload();
    }

    protected List<ActionResult> playInstanceFromWall(MinecraftInstance instance, boolean bypassLoadCheck) {
        JultiOptions options = JultiOptions.getJultiOptions();

        if (!bypassLoadCheck && options.wallLockInsteadOfPlay && !(instance.getStateTracker().isCurrentState(InstanceState.INWORLD))) {

            return new ArrayList<>(this.lockInstance(instance) ? Collections.singletonList(ActionResult.INSTANCE_LOCKED) : Collections.emptyList());

        }

        Julti.getJulti().activateInstance(instance);
        SleepBGUtil.enableLock();
        return Collections.singletonList(ActionResult.INSTANCE_ACTIVATED);
    }

    private List<ActionResult> resetNonLockedExcept(MinecraftInstance clickedInstance) {
        List<ActionResult> actionResults = new ArrayList<>();
        DoAllFastUtil.doAllFast(instance -> {
            if (instance.equals(clickedInstance) || this.lockedInstances.contains(instance)) {
                return;//(continue;)
            }
            if (this.resetInstance(instance)) {
                synchronized (actionResults) {
                    actionResults.add(ActionResult.INSTANCE_RESET);
                }
            }
        });

        return actionResults;
    }

    public List<ActionResult> leaveInstance(MinecraftInstance selectedInstance) {
        JultiOptions options = JultiOptions.getJultiOptions();

        boolean resetFirst = options.coopMode && options.wallBypass;

        selectedInstance.ensureNotFullscreen(true);

        // Unlock instance
        this.unlockInstance(selectedInstance);

        if (resetFirst) {
            this.resetInstance(selectedInstance, true);
            sleep(100);
        }
        if (!resetFirst) {
            this.resetInstance(selectedInstance, true);
        }

        return List.of();
    }


    private void unlockInstance(MinecraftInstance nextInstance) {
        this.lockedInstances.remove(nextInstance);
    }

}
