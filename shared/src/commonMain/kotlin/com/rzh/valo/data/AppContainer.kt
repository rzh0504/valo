package com.rzh.valo.data

/**
 * 依赖容器：平台入口（Android Application / iOS 入口）各自构造一次并持有。
 */
class AppContainer(filesDir: String) {
    val settingsStore: SettingsStore = SettingsStore.create(filesDir)
    val repository: MatchRepository = MatchRepository(HaojiaoApi(), SnapshotStore(filesDir))
}
