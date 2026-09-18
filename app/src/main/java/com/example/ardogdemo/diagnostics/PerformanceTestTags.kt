package com.example.ardogdemo.diagnostics

object PerformanceTestTags {
    const val Screen = "ardog_screen"
    const val Camera = "camera_preview"
    const val Scene = "model_scene"
    const val PermissionGate = "camera_permission_gate"
    const val OpenAccessories = "open_accessories"
    const val Punch = "action_punch"
    const val Dance = "action_dance"
    const val Howl = "action_howl"
    const val MultiModel = "activate_multi_model"
    const val Joystick = "movement_joystick"
    const val ScaleSlider = "model_scale_slider"
    const val StartMission = "start_mission"

    fun accessory(id: String) = "accessory_$id"
    fun mission(id: String) = "mission_$id"
    fun formation(id: String) = "formation_$id"
}
