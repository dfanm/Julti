package xyz.duncanruns.julti.gui;

import xyz.duncanruns.julti.Julti;
import xyz.duncanruns.julti.JultiOptions;
import xyz.duncanruns.julti.management.OBSStateManager;
import xyz.duncanruns.julti.script.ScriptManager;
import xyz.duncanruns.julti.util.*;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OptionsGUI extends JFrame {
    private static OptionsGUI instance = null;
    private boolean closed = false;
    private JTabbedPane tabbedPane;
    private final List<String> tabNames = new ArrayList<>();

    public OptionsGUI() {
        this.setupWindow();
        this.reloadComponents();
    }

    @Nullable
    public static OptionsGUI getGUI() {
        return instance;
    }

    public static OptionsGUI openGUI() {
        if (instance == null || instance.isClosed()) {
            instance = new OptionsGUI();
        }
        instance.setExtendedState(NORMAL);
        instance.requestFocus();
        return instance;
    }

    private static void changeProfile(String profile) {
        Julti.waitForExecute(() -> Julti.getJulti().changeProfile(profile));
    }

    public static void reloadIfOpen() {
        if (instance != null && !instance.isClosed()) {
            instance.reload();
        }
    }

    private JTabbedPane getTabbedPane() {
        return this.tabbedPane;
    }

    private void setupWindow() {
        Point location = JultiGUI.getJultiGUI().getLocation();
        this.setLocation(location.x, location.y + 30);
        this.setLayout(null);
        this.setTitle("Julti Options");
        this.setIconImage(JultiGUI.getLogo());
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                OptionsGUI.this.onClose();
            }
        });
        this.setSize(750, 400);
        this.setVisible(true);
    }

    private void reloadComponents() {
        this.getContentPane().removeAll();
        this.tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        this.tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        this.setContentPane(this.tabbedPane);
        this.addComponentsWindow();
        this.addComponentsHotkey();
        this.addComponentsOBS();
        this.addComponentsLaunching();
        this.addComponentsOther();
        this.revalidate();
        this.repaint();
    }

    private void addComponentsLaunching() {
        JPanel panel = this.createNewOptionsPanel("Launching");

        JultiOptions options = JultiOptions.getJultiOptions();

        panel.add(GUIUtil.leftJustify(new JLabel("Program Launching")));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.leftJustify(new JLabel("Right click for action menu")));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.createSeparator());
        panel.add(GUIUtil.createSpacer());

        DefaultListModel<String> model = new DefaultListModel<>();
        options.launchingProgramPaths.forEach(model::addElement);

        JList<String> programList = new JList<>(model);
        programList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    // If right click is over already selected item(s) do nothing, but if right click on unselected
                    // item, select it and unselect previous selected.
                    int clickIndex = programList.locationToIndex(e.getPoint());
                    if (!programList.isSelectedIndex(clickIndex)) {
                        programList.setSelectedIndex(clickIndex);
                    }

                    JPopupMenu menu = new JPopupMenu();

                    JMenuItem add = new JMenuItem("Add");
                    add.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            OptionsGUI.this.browseForLauncherProgram();
                            OptionsGUI.this.reload();
                        }
                    });

                    JMenuItem remove = new JMenuItem("Remove");
                    remove.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            List<String> paths = programList.getSelectedValuesList();
                            paths.forEach(path -> options.launchingProgramPaths.remove(path));
                            OptionsGUI.this.reload();
                        }
                    });

                    menu.add(add);
                    menu.add(remove);

                    menu.show(programList, e.getX(), e.getY());
                }
            }
        });

        JScrollPane sp = new JScrollPane(programList);

        panel.add(GUIUtil.leftJustify(sp));
    }

    private void browseForLauncherProgram() {
        FileDialog dialog = new FileDialog((Frame) null, "Julti: Choose Program");
        dialog.setMode(FileDialog.LOAD);
        dialog.setMultipleMode(true);
        dialog.setVisible(true);
        if (dialog.getFiles() != null) {
            for (File file : dialog.getFiles()) {
                JultiOptions.getJultiOptions().launchingProgramPaths.add(file.getAbsolutePath());
            }
        }
        dialog.dispose();
    }

    private void addComponentsOther() {
        JPanel panel = this.createNewOptionsPanel("Other");

        panel.add(GUIUtil.leftJustify(new JLabel("Other Settings")));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.createSeparator());
        panel.add(GUIUtil.createSpacer());

        panel.add(GUIUtil.leftJustify(GUIUtil.createCheckBoxFromOption("Minimize Julti To System Tray", "Minimizing Julti will move it to an icon in the system tray (bottom right).", "minimizeToTray", JultiGUI.getJultiGUI().getJultiIcon()::setTrayIconListener)));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.leftJustify(GUIUtil.createCheckBoxFromOption("Utility Mode", "utilityMode", b -> this.reload())));

        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.leftJustify(GUIUtil.createCheckBoxFromOption("Show Debug Messages", "showDebug")));

        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.leftJustify(GUIUtil.createCheckBoxFromOption("Prevent Window Naming", "preventWindowNaming")));

        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.leftJustify(GUIUtil.createCheckBoxFromOption("Resizeable Borderless", "Allows the window to be resized, restored and maximized when Use Borderless is checked.", "resizeableBorderless", b -> this.reload())));

    }

    private void addComponentsOBS() {
        JPanel panel = this.createNewOptionsPanel("OBS");

        JultiOptions options = JultiOptions.getJultiOptions();

        panel.add(GUIUtil.leftJustify(new JLabel("OBS Settings")));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.createSeparator());
        panel.add(GUIUtil.createSpacer());

        panel.add(GUIUtil.leftJustify(GUIUtil.createCheckBoxFromOption("Align Active Instance to Center", "Mainly for EyeZoom/TallMacro/stretched window users", "centerAlignActiveInstance", b -> this.reload())));
        if (options.centerAlignActiveInstance) {
            panel.add(GUIUtil.createSpacer());
            JPanel scalePanel = GUIUtil.createActiveInstanceScalePanel();
            panel.add(GUIUtil.leftJustify(scalePanel));
        }

        panel.add(GUIUtil.leftJustify(new JLabel("No settings here are required to use the OBS Link Script.")));
    }

    private void addComponentsHotkey() {
        JPanel panel = this.createNewOptionsPanel("Hotkeys");

        panel.add(GUIUtil.leftJustify(new JLabel("Hotkeys")));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.leftJustify(new JLabel("Right click to clear (disable) hotkey")));
        panel.add(GUIUtil.leftJustify(new JLabel("Checkboxes = Allow extra keys (ignore ctrl/shift/alt)")));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.createSeparator());
        panel.add(GUIUtil.createSpacer());

        panel.add(GUIUtil.leftJustify(new JLabel("Script Hotkeys")));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.leftJustify(GUIUtil.createHotkeyChangeButton("cancelScriptHotkey", "Cancel Running Script", true)));

        for (String scriptName : ScriptManager.getHotkeyableScriptNames()) {
            panel.add(GUIUtil.createSpacer());
            panel.add(GUIUtil.leftJustify(GUIUtil.createScriptHotkeyChangeButton(scriptName, this::reload)));
        }
    }

    private JPanel createNewOptionsPanel(String name) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        this.tabbedPane.add(name, scrollPane);
        this.tabNames.add(name);
        return panel;
    }

    public void reload() {
        // Get current index
        int index = this.getTabbedPane().getSelectedIndex();
        // Get current scroll
        int s = ((JScrollPane) this.getTabbedPane().getSelectedComponent()).getVerticalScrollBar().getValue();
        // Reload
        this.reloadComponents();
        // Set index
        this.getTabbedPane().setSelectedIndex(index);
        // Set scroll
        ((JScrollPane) this.getTabbedPane().getSelectedComponent()).getVerticalScrollBar().setValue(s);

    }

    private void addComponentsWindow() {
        JPanel panel = this.createNewOptionsPanel("Window");

        JultiOptions options = JultiOptions.getJultiOptions();

        panel.add(GUIUtil.leftJustify(new JLabel("Window Settings")));
        panel.add(GUIUtil.createSpacer());
        panel.add(GUIUtil.createSeparator());
        panel.add(GUIUtil.createSpacer());

        panel.add(GUIUtil.leftJustify(GUIUtil.createCheckBoxFromOption("Let Julti Manage Windows", "letJultiMoveWindows", b -> this.reload())));

        if (!options.letJultiMoveWindows) {
            return;
        }
        panel.add(GUIUtil.createSpacer());

        panel.add(GUIUtil.leftJustify(GUIUtil.createCheckBoxFromOption("Use Borderless", "useBorderless", b -> this.reload())));
        panel.add(GUIUtil.createSpacer());

        panel.add(GUIUtil.createSeparator());
        panel.add(GUIUtil.createSpacer());

        WindowOptionComponent windowOptions = new WindowOptionComponent();
        panel.add(windowOptions);
        panel.add(GUIUtil.createSpacer());

        OptionsGUI thisGUI = this;
        panel.add(GUIUtil.leftJustify(GUIUtil.getButtonWithMethod(new JButton("Choose Monitor"), actionEvent -> {
            MonitorUtil.Monitor[] monitors = MonitorUtil.getAllMonitors();
            StringBuilder monitorOptionsText = new StringBuilder();
            Object[] buttons = new Object[monitors.length];

            List<MonitorUtil.Monitor> sortedMonitors = new ArrayList<>();
            sortedMonitors.add(MonitorUtil.getPrimaryMonitor());
            Arrays.stream(monitors).filter(monitor -> !monitor.isPrimary).forEach(sortedMonitors::add);

            int i = 0;
            for (MonitorUtil.Monitor monitor : sortedMonitors) {
                buttons[i] = String.valueOf(i + 1);
                monitorOptionsText.append("\n#").append(++i);
                if (monitor.isPrimary) {
                    monitorOptionsText.append(" (Primary)");
                }
                monitorOptionsText.append(" - ").append("Size: ").append(monitor.width).append("x").append(monitor.height).append(", Position: (").append(monitor.x).append(",").append(monitor.y).append(")");
            }

            int ans = JOptionPane.showOptionDialog(thisGUI, "Choose a monitor:\n" + monitorOptionsText.toString().trim(), "Julti: Choose Monitor", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, buttons, null);
            if (ans == -1) {
                return;
            }
            MonitorUtil.Monitor monitor = sortedMonitors.get(ans);
            Julti.waitForExecute(() -> {
                options.windowPos = monitor.centerPosition;
                options.windowPosIsCenter = true;
                options.playingWindowSize = monitor.size;
            });
            windowOptions.reload();
            this.revalidate();
        })));

    }

    public void openTab(String tabName) {
        this.getTabbedPane().setSelectedIndex(this.tabNames.indexOf(tabName));
    }

    public void setScroll(int scroll) {
        ((JScrollPane) this.getTabbedPane().getSelectedComponent()).getVerticalScrollBar().setValue(scroll);
    }

    public boolean isClosed() {
        return this.closed;
    }

    private void onClose() {
        this.closed = true;
        Julti.doLater(() -> {
            if (!JultiOptions.getJultiOptions().utilityMode) {
                OBSStateManager.getOBSStateManager().tryOutputLSInfo();
                MistakesUtil.checkStartupMistakes();
            }
            SleepBGUtil.disableLock();
            Julti.resetInstancePositions();
        });
    }
}
