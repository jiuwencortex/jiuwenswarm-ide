package com.jiuwenswarm.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class SettingsConfigurable : Configurable {

    private val settings = JiuwenSwarmSettings.instance()

    private val hostField = JBTextField(settings.host, 20)
    private val portSpinner = JSpinner(SpinnerNumberModel(settings.port, 1, 65535, 1))
    private val channelIdField = JBTextField(settings.channelId, 10)
    private val autoConnectBox = JBCheckBox("Connect automatically on IDE startup", settings.autoConnect)
    private val approveEditsBox = JBCheckBox(
        "Require approval before applying agent file edits",
        settings.approveEdits,
    )
    private val autoApplyEditsBox = JBCheckBox(
        "Auto-apply file edits (skip diff dialog)",
        settings.autoApplyEdits,
    )
    private val runInTerminalBox = JBCheckBox(
        "Run bash / shell commands in IDE terminal",
        settings.runCommandsInTerminal,
    )
    private val keepAliveBox = JBCheckBox(
        "Send keep-alive pings to server",
        settings.keepAliveEnabled,
    )
    private val keepAliveIntervalSpinner = JSpinner(
        SpinnerNumberModel(settings.keepAliveInterval, 5, 300, 5)
    )

    private var panel: JPanel? = null

    override fun getDisplayName() = "JiuwenSwarm"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Server host:"), hostField, 1, false)
            .addLabeledComponent(JBLabel("Server port:"), portSpinner, 1, false)
            .addLabeledComponent(JBLabel("Channel ID:"), channelIdField, 1, false)
            .addComponent(autoConnectBox, 1)
            .addComponent(approveEditsBox, 1)
            .addComponent(autoApplyEditsBox, 1)
            .addComponent(runInTerminalBox, 1)
            .addComponent(keepAliveBox, 1)
            .addLabeledComponent(JBLabel("Keep-alive interval (seconds):"), keepAliveIntervalSpinner, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean =
        hostField.text != settings.host ||
        (portSpinner.value as Int) != settings.port ||
        channelIdField.text != settings.channelId ||
        autoConnectBox.isSelected != settings.autoConnect ||
        approveEditsBox.isSelected != settings.approveEdits ||
        autoApplyEditsBox.isSelected != settings.autoApplyEdits ||
        runInTerminalBox.isSelected != settings.runCommandsInTerminal ||
        keepAliveBox.isSelected != settings.keepAliveEnabled ||
        (keepAliveIntervalSpinner.value as Int) != settings.keepAliveInterval

    override fun apply() {
        settings.host = hostField.text.trim()
        settings.port = portSpinner.value as Int
        settings.channelId = channelIdField.text.trim()
        settings.autoConnect = autoConnectBox.isSelected
        settings.approveEdits = approveEditsBox.isSelected
        settings.autoApplyEdits = autoApplyEditsBox.isSelected
        settings.runCommandsInTerminal = runInTerminalBox.isSelected
        settings.keepAliveEnabled = keepAliveBox.isSelected
        settings.keepAliveInterval = (keepAliveIntervalSpinner.value as Int).coerceIn(5, 300)
    }

    override fun reset() {
        hostField.text = settings.host
        portSpinner.value = settings.port
        channelIdField.text = settings.channelId
        autoConnectBox.isSelected = settings.autoConnect
        approveEditsBox.isSelected = settings.approveEdits
        autoApplyEditsBox.isSelected = settings.autoApplyEdits
        runInTerminalBox.isSelected = settings.runCommandsInTerminal
        keepAliveBox.isSelected = settings.keepAliveEnabled
        keepAliveIntervalSpinner.value = settings.keepAliveInterval
    }

    override fun disposeUIResources() {
        panel = null
    }
}
