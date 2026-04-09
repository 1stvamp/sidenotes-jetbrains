package com.sidenotes.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "SidenotesSettings",
    storages = [Storage("sidenotes.xml")]
)
class SidenotesSettings : PersistentStateComponent<SidenotesSettings.State> {

    data class State(
        var annotationsDir: String = ".annotations",
        var railsRoot: String = "",
        var showInlayHints: Boolean = true,
        var showGutterIcons: Boolean = true
    )

    private var state = State()

    val annotationsDir: String get() = state.annotationsDir
    val railsRoot: String get() = state.railsRoot
    val showInlayHints: Boolean get() = state.showInlayHints
    val showGutterIcons: Boolean get() = state.showGutterIcons

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SidenotesSettings =
            project.getService(SidenotesSettings::class.java)
    }
}
