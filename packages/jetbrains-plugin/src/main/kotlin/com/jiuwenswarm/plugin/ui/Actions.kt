package com.jiuwenswarm.plugin.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager
import com.jiuwenswarm.plugin.JiuwenSwarmService

class NewSessionAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ApplicationManager.getApplication().invokeLater {
            ToolWindowManager.getInstance(project).getToolWindow("JiuwenSwarm")?.show()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = JiuwenSwarmService.instance().ws.isConnected()
    }
}

class SendSelectionAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selection = editor.selectionModel.selectedText ?: return
        val fileName = e.getData(CommonDataKeys.VIRTUAL_FILE)?.name ?: "file"

        val prefillContent = "[File: $fileName]\n```\n$selection\n```\n\n"

        ApplicationManager.getApplication().invokeLater {
            val tw = ToolWindowManager.getInstance(project).getToolWindow("JiuwenSwarm")
            tw?.show()
            val comp = tw?.contentManager?.selectedContent?.component
            val chatPanel = comp?.getClientProperty("jiuwenswarm.panel") as? ChatPanel
            chatPanel?.dispatchToWebview(mapOf("type" to "prefill", "content" to prefillContent))
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor?.selectionModel?.hasSelection() == true
    }
}
